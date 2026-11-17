package com.example.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import com.example.data.mapper.LonLatBbox
import com.example.data.mapper.MRMS_SERVICE_INFO_URL
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.mrmsExportImageUrl
import com.example.domain.model.Place
import com.example.ui.radar.engine.CoalescingFetcher
import com.example.ui.radar.engine.CrtFilter
import com.example.ui.radar.engine.FetchResult
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Radar widget compositing (Item 4) — the Android half. CI-only: it
 * needs Bitmap/Canvas, so it never runs in the local pure pass; the
 * pure model it draws from ([RadarWidgetModel]) is covered locally.
 *
 * One bake per refresh, over the selected place:
 * 1. The Esri dark-gray basemap tiles for the widget's tile grid
 *    ([radarWidgetTileGrid], zoom 7 — ~8 tiles), fetched in parallel
 *    through a fresh [CoalescingFetcher] (single-flight, so a
 *    coincident in-app engine fetch of the same tile never doubles
 *    the request).
 * 2. The overlay for this refresh's step: the newest MRMS frame
 *    (one `exportImage` over the grid's exact bbox) for step 0, or
 *    the HRRR tiles for the +15/+30 frame (Item 1's
 *    [radarWidgetFutureFrame], fetched with the canonical IEM tile
 *    URL for its HRRR layer name) for steps 1/2. A forecast lead
 *    that has not published yet 503s — the centre tile is the
 *    coverage signal, so a failed centre tile fails the frame — but
 *    a failed forecast frame now falls back to the live MRMS frame
 *    (labelled LIVE NOW) instead of failing the whole bake. A live
 *    frame that fails or is blank steps back 5 min at a time and
 *    bakes the most recent frame that works.
 * 3. The CRT baked in on Canvas, in the same order and with the
 *    same numbers as [CrtFilterLayer] draws it in-app: warm
 *    phosphor tint, scanlines, aperture grille, then the vignette.
 *    The vignette is the FIXED one Worker A shipped: the multi-stop
 *    radial gradient from [CrtFilter.vignetteStops()] on a radius of
 *    the full centre-to-corner distance — transparent until 55% of
 *    centre-to-corner, then a ramp to 0.28 black at the corner —
 *    NOT the old two-stop gradient that darkened from the centre.
 *
 * Blank MRMS: the service answers HTTP 200 with a fully transparent
 * PNG for a bad/stale time as well as for a genuinely clear sky, so
 * a decoded-pixel check (any non-transparent pixel = echo) is
 * combined with the metadata extent exactly like [RadarLoopStore]
 * does: blank + time inside the extent = a ready, empty frame
 * (clear sky — the basemap bakes alone); blank + time outside the
 * extent, or with the extent unread, steps back to an older frame
 * (see Fallbacks below). The known-blank hash set cannot be used
 * here: it covers only the probed sizes (2048x2048, 1080x1000),
 * and the widget requests 830x290.
 *
 * Fallbacks (Item 4 refinement, 2026-11-16 — the 23:41 placement
 * bake failed on the LIVE step, not a forecast step, so the live
 * path is the one that must never blank the widget):
 * - A forecast step whose HRRR lead has not published falls back
 *   to the live MRMS frame, labelled "LIVE NOW · HH:MM" — the step
 *   cycle is unchanged, the next refresh still shows its own step.
 * - A live frame that fails to fetch/decode, or is blank for a time
 *   the extent cannot verify, steps back 5 min at a time
 *   ([radarWidgetMrmsCandidateTimes], 4 attempts) and bakes the
 *   most recent frame that works, labelled with ITS real valid time
 *   (and " · STALE" when it is not the newest).
 * - When the service answers but every candidate is blank and the
 *   extent was never read (service-info fetch failed), the sky is
 *   almost certainly clear — the basemap bakes alone, labelled with
 *   the newest attempted time and " · STALE" (freshness unverified),
 *   rather than blanking the widget. Probed 2026-11-17 ~04:51 UTC:
 *   the live service returns HTTP 200 + a 1,012 B fully transparent
 *   PNG for the widget's exact bbox at the newest frame, at
 *   newest-10, AND at a time 10 min ahead of the newest — blank is
 *   the service's normal clear-sky answer, and a failed service-info
 *   fetch used to turn that answer into a failed bake.
 * - Every failure is logged ([Log.d]) with its reason, so the next
 *   failed bake is traceable instead of vanishing into the updater's
 *   catch-all (the 23:41 failure left no trace at all).
 *
 * The result is an RGB_565 bitmap: 830x290x2 = 481,400 B
 * ([radarWidgetBitmapByteCount]), well inside the serialized
 * RemoteViews image limit.
 */
sealed class RadarWidgetBakeResult {
    /** Baked bitmap, its label line, and the frame's valid time (epoch ms). */
    data class Success(
        val bitmap: Bitmap,
        val label: String,
        val frameTimeMs: Long
    ) : RadarWidgetBakeResult()

    /** Nothing baked; the caller shows the last baked image labelled stale. */
    data class Failed(val reason: RadarWidgetBakeFailure) : RadarWidgetBakeResult()
}

enum class RadarWidgetBakeFailure {
    /** The forecast frame has no HRRR lead yet (centre tile 503'd). */
    FRAME_NOT_PUBLISHED,

    /** A fetch or decode failed (network, service, or bad bytes). */
    NETWORK
}

object RadarWidgetCompositor {

    private const val TAG = "RadarWidget"

    /** Navy behind any tile that fails: the engine's background. */
    private const val BASEMAP_FALLBACK_ARGB: Int = 0xFF0A1A33.toInt()

    /**
     * Bake one widget image for [place] at [now]. [fetcher] is
     * caller-owned (the updater creates one per bake and closes it);
     * [generationId] tags the fetcher's single-flight map.
     */
    suspend fun bake(
        place: Place,
        now: Instant,
        fetcher: CoalescingFetcher,
        generationId: Long
    ): RadarWidgetBakeResult {
        val step = radarWidgetStepIndex(now)
        val grid = radarWidgetTileGrid(place.point.lat, place.point.lon)
        val base = Bitmap.createBitmap(
            RADAR_WIDGET_WIDTH_PX,
            RADAR_WIDGET_HEIGHT_PX,
            Bitmap.Config.RGB_565
        )
        val canvas = Canvas(base)
        canvas.drawColor(BASEMAP_FALLBACK_ARGB)

        // Basemap: all grid tiles in parallel; a failed tile leaves
        // navy behind it (never a blank widget for one bad tile).
        val basemapTiles = fetchTiles(fetcher, generationId, grid) { x, y ->
            esriBasemapTileUrl(RADAR_WIDGET_ZOOM, x, y)
        }
        for ((x, y, tile) in basemapTiles) {
            tile?.let {
                canvas.drawBitmap(it, grid.tileDrawX(x).toFloat(), grid.tileDrawY(y).toFloat(), null)
                it.recycle()
            }
        }

        // Overlay for this step, then the CRT bake. A forecast step
        // whose HRRR lead has not published falls back to the live
        // MRMS frame instead of failing the bake (Item 4).
        val drawn: OverlayResult = when (step) {
            0 -> drawLiveOverlay(canvas, fetcher, generationId, grid, now)
            else -> {
                when (val overlay = fetchHrrrOverlay(fetcher, generationId, grid, now, step)) {
                    is HrrrOverlay.Ready -> {
                        for ((x, y, tile) in overlay.tiles) {
                            tile?.let {
                                canvas.drawBitmap(
                                    it,
                                    grid.tileDrawX(x).toFloat(),
                                    grid.tileDrawY(y).toFloat(),
                                    null
                                )
                                it.recycle()
                            }
                        }
                        OverlayResult.Drawn(overlay.frameTimeMs, step, stale = false)
                    }
                    is HrrrOverlay.Failed -> {
                        Log.d(
                            TAG,
                            "radar bake: HRRR step $step unavailable " +
                                "(${overlay.reason}); falling back to live MRMS"
                        )
                        drawLiveOverlay(
                            canvas,
                            fetcher,
                            generationId,
                            grid,
                            now,
                            displayStep = radarWidgetOverlayStep(step, hrrrFailed = true)
                        )
                    }
                }
            }
        }
        val (frameTimeMs, displayStep, stale) = when (drawn) {
            is OverlayResult.Failed -> {
                Log.d(TAG, "radar bake failed: ${drawn.reason} (step $step)")
                return RadarWidgetBakeResult.Failed(drawn.reason)
            }
            is OverlayResult.Drawn -> Triple(drawn.frameTimeMs, drawn.displayStep, drawn.stale)
        }
        bakeCrt(canvas)
        return RadarWidgetBakeResult.Success(
            bitmap = base,
            label = radarWidgetLabel(displayStep, Instant.ofEpochMilli(frameTimeMs), place.zoneId, stale = stale),
            frameTimeMs = frameTimeMs
        )
    }

    // -- Overlays ----------------------------------------------------------

    /**
     * One step's overlay drawn onto [canvas]: the frame's valid time,
     * the step its label shows ([displayStep] — 0 for the live frame,
     * including the HRRR fallback), and whether the frame is older
     * than the newest (labelled STALE). A failure carries the reason
     * so the caller can log it instead of swallowing it.
     */
    private sealed class OverlayResult {
        data class Drawn(val frameTimeMs: Long, val displayStep: Int, val stale: Boolean) : OverlayResult()
        data class Failed(val reason: RadarWidgetBakeFailure) : OverlayResult()
    }

    /**
     * Draw the live MRMS overlay for [now]: try the candidate frame
     * times newest-first ([fetchMrmsOverlay]) and draw whichever
     * serves. [displayStep] is the label step — 0 for the live step,
     * and 0 for the HRRR fallback (the fallback is a live frame,
     * labelled LIVE NOW).
     */
    private suspend fun drawLiveOverlay(
        canvas: Canvas,
        fetcher: CoalescingFetcher,
        generationId: Long,
        grid: RadarWidgetTileGrid,
        now: Instant,
        displayStep: Int = radarWidgetOverlayStep(0, hrrrFailed = false)
    ): OverlayResult {
        return when (val overlay = fetchMrmsOverlay(fetcher, generationId, grid, now)) {
            is MrmsOverlay.Ready -> {
                canvas.drawBitmap(overlay.bitmap, 0f, 0f, null)
                overlay.bitmap.recycle()
                OverlayResult.Drawn(overlay.frameTimeMs, displayStep, overlay.stale)
            }
            is MrmsOverlay.ClearSky -> {
                // Genuinely clear (or unverified-blank, see below):
                // the basemap bakes alone.
                OverlayResult.Drawn(overlay.frameTimeMs, displayStep, overlay.stale)
            }
            is MrmsOverlay.Failed -> {
                Log.d(TAG, "radar bake: live MRMS failed (${overlay.reason})")
                OverlayResult.Failed(overlay.reason)
            }
        }
    }

    private sealed class MrmsOverlay {
        data class Ready(val bitmap: Bitmap, val frameTimeMs: Long, val stale: Boolean) : MrmsOverlay()
        data class ClearSky(val frameTimeMs: Long, val stale: Boolean) : MrmsOverlay()
        data class Failed(val reason: RadarWidgetBakeFailure) : MrmsOverlay()
    }

    private sealed class HrrrOverlay {
        data class Ready(
            val tiles: List<TileBitmap>,
            val frameTimeMs: Long
        ) : HrrrOverlay()

        data class Failed(val reason: RadarWidgetBakeFailure) : HrrrOverlay()
    }

    private data class TileBitmap(val tileX: Int, val tileY: Int, val bitmap: Bitmap?)

    /**
     * The live MRMS overlay: try the candidate frame times
     * newest-first ([radarWidgetMrmsCandidateTimes]) and serve the
     * first that works. A candidate fails two ways — the fetch/decode
     * fails (try the next, 5 min older), or the image is blank:
     * blank + time inside the extent is clear sky (the basemap bakes
     * alone, done — a clear sky needs no older frame); blank + time
     * outside the extent, or with the extent unread, tries the next
     * candidate. A served frame older than the newest is [stale] and
     * its label says so. When the service answers but every
     * candidate is blank and the extent was never read, the sky is
     * almost certainly clear — the basemap bakes alone, labelled
     * with the newest attempted time and STALE (freshness
     * unverified), rather than blanking the widget on a clear night
     * for a failed service-info fetch (the 23:41 failure mode).
     * Only when no candidate serves an image at all does this fail.
     */
    private suspend fun fetchMrmsOverlay(
        fetcher: CoalescingFetcher,
        generationId: Long,
        grid: RadarWidgetTileGrid,
        now: Instant
    ): MrmsOverlay {
        val serviceInfo = fetcher.fetchText(MRMS_SERVICE_INFO_URL, generationId)
        val newestMs = radarWidgetNewestMrmsTimeMs(serviceInfo)
        val withinExtent = newestMs != null
        if (serviceInfo == null) {
            Log.d(TAG, "radar bake: MRMS service info unreadable; extent unknown")
        }
        val candidates = radarWidgetMrmsCandidateTimes(newestMs, now)
        val bbox = LonLatBbox(grid.westLon, grid.southLat, grid.eastLon, grid.northLat)
        var sawBlank = false
        for ((index, frameTimeMs) in candidates.withIndex()) {
            val url = mrmsExportImageUrl(
                bbox,
                frameTimeMs,
                RADAR_WIDGET_WIDTH_PX,
                RADAR_WIDGET_HEIGHT_PX
            )
            val result = fetcher.fetch(url, generationId)
            val bytes = (result as? FetchResult.Success)?.bytes
            if (bytes == null) {
                Log.d(TAG, "radar bake: MRMS frame $frameTimeMs fetch failed ($result); stepping back")
                continue
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (decoded == null) {
                Log.d(TAG, "radar bake: MRMS frame $frameTimeMs undecodable; stepping back")
                continue
            }
            if (!hasNonTransparentPixel(decoded)) {
                decoded.recycle()
                sawBlank = true
                if (withinExtent) {
                    // Blank + time inside the extent = clear sky (the
                    // control the store relies on).
                    return MrmsOverlay.ClearSky(frameTimeMs, stale = index > 0)
                }
                Log.d(
                    TAG,
                    "radar bake: MRMS frame $frameTimeMs blank, extent unknown; stepping back"
                )
                continue
            }
            // The MRMS image covers the grid's exact bbox, so it draws
            // stretched over the whole bitmap.
            val stretched = Bitmap.createScaledBitmap(
                decoded,
                RADAR_WIDGET_WIDTH_PX,
                RADAR_WIDGET_HEIGHT_PX,
                true
            )
            decoded.recycle()
            return MrmsOverlay.Ready(stretched, frameTimeMs, stale = index > 0)
        }
        if (sawBlank && !withinExtent) {
            // Every frame the service answered was blank and the
            // extent was never read: bake the basemap alone, labelled
            // with the newest attempted time and STALE.
            Log.d(
                TAG,
                "radar bake: all ${candidates.size} MRMS candidates blank, extent unknown; " +
                    "basemap-only stale bake at ${candidates.first()}"
            )
            return MrmsOverlay.ClearSky(candidates.first(), stale = true)
        }
        return MrmsOverlay.Failed(RadarWidgetBakeFailure.NETWORK)
    }

    private suspend fun fetchHrrrOverlay(
        fetcher: CoalescingFetcher,
        generationId: Long,
        grid: RadarWidgetTileGrid,
        now: Instant,
        step: Int
    ): HrrrOverlay {
        val frame = radarWidgetFutureFrame(now, radarWidgetStepOffsetMinutes(step))
            ?: return HrrrOverlay.Failed(RadarWidgetBakeFailure.FRAME_NOT_PUBLISHED)
        val tiles = fetchTiles(fetcher, generationId, grid) { x, y ->
            iemTileUrl(frame.layerName, RADAR_WIDGET_ZOOM, x, y)
        }
        val centre = tiles.firstOrNull { it.tileX == grid.centreTileX && it.tileY == grid.centreTileY }
        if (centre?.bitmap == null) {
            tiles.forEach { it.bitmap?.recycle() }
            return HrrrOverlay.Failed(RadarWidgetBakeFailure.FRAME_NOT_PUBLISHED)
        }
        return HrrrOverlay.Ready(
            tiles.filter { it.bitmap != null },
            frame.validTime.toEpochMilli()
        )
    }

    /** Fetch + decode every tile of [grid] in parallel. */
    private suspend fun fetchTiles(
        fetcher: CoalescingFetcher,
        generationId: Long,
        grid: RadarWidgetTileGrid,
        urlFor: (tileX: Int, tileY: Int) -> String
    ): List<TileBitmap> = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<TileBitmap>>()
        for (tileX in grid.tileX0..grid.tileX1) {
            for (tileY in grid.tileY0..grid.tileY1) {
                jobs.add(async {
                    val result = fetcher.fetch(urlFor(tileX, tileY), generationId)
                    val bytes = (result as? FetchResult.Success)?.bytes
                    val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    TileBitmap(tileX, tileY, decoded)
                })
            }
        }
        jobs.awaitAll()
    }

    /** True when any pixel of [bitmap] is non-transparent (has echo). */
    private fun hasNonTransparentPixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            if ((pixel ushr 24) != 0) return true
        }
        return false
    }

    // -- CRT bake (Canvas re-implementation of CrtFilterLayer) ----------

    /**
     * The CRT, baked onto [canvas] in the same order and with the
     * same numbers as [CrtFilterLayer] draws it in-app: warm
     * phosphor tint, horizontal scanlines, vertical aperture-grille
     * stripes, then the vignette — the FIXED multi-stop radial
     * gradient from [CrtFilter.vignetteStops()] on the full
     * centre-to-corner radius. The density is the widget bitmap's
     * assumed ~2.6, so a 3dp pitch is ~7.8 px here.
     */
    fun bakeCrt(canvas: Canvas) {
        val width = RADAR_WIDGET_WIDTH_PX.toFloat()
        val height = RADAR_WIDGET_HEIGHT_PX.toFloat()
        val density = RADAR_WIDGET_DENSITY

        // Warm phosphor glass over the whole image.
        val tintPaint = Paint().apply {
            color = Color.rgb(0xFF, 0xF3, 0xD6)
            alpha = (CrtFilter.PHOSPHOR_TINT_ALPHA * 255).roundToInt()
        }
        canvas.drawRect(0f, 0f, width, height, tintPaint)

        // Horizontal scanlines, top to bottom.
        val scanPaint = Paint().apply {
            color = Color.BLACK
            alpha = (CrtFilter.SCANLINE_ALPHA * 255).roundToInt()
            strokeWidth = CrtFilter.scanlineThicknessPx(density)
        }
        var y = 0f
        val scanSpacing = CrtFilter.scanlineSpacingPx(density)
        while (y < height) {
            canvas.drawLine(0f, y, width, y, scanPaint)
            y += scanSpacing
        }

        // Vertical aperture-grille stripes, left to right.
        val grillePaint = Paint().apply {
            color = Color.BLACK
            alpha = (CrtFilter.GRILLE_ALPHA * 255).roundToInt()
            strokeWidth = CrtFilter.grilleThicknessPx(density)
        }
        var x = 0f
        val grilleSpacing = CrtFilter.grilleSpacingPx(density)
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, grillePaint)
            x += grilleSpacing
        }

        // Vignette: the FIXED stops — transparent until 55% of
        // centre-to-corner, then a ramp to 0.28 black at the corner —
        // on a radial gradient whose radius IS the full
        // centre-to-corner distance, so the stop fractions are
        // centre-to-corner fractions. (The old two-stop gradient on
        // a 0.75x radius darkened from the centre.)
        val stops = CrtFilter.vignetteStops()
        val vignettePaint = Paint().apply {
            shader = RadialGradient(
                width / 2f,
                height / 2f,
                CrtFilter.cornerDistancePx(width, height),
                stops.map { (_, alpha) ->
                    Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
                }.toIntArray(),
                stops.map { (fraction, _) -> fraction }.toFloatArray(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, vignettePaint)
    }
}
