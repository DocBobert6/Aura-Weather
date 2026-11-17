package com.example.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
 *    URL for its HRRR layer name) for steps 1/2. A forecast lead that has not published yet 503s — the
 *    centre tile is the coverage signal, so a failed centre tile
 *    fails the frame instead of drawing a basemap with no radar.
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
 * does: blank + time outside the extent = stale (the bake fails and
 * the updater shows the last baked image labelled stale); blank +
 * time inside the extent = a ready, empty frame (clear sky — the
 * basemap bakes alone). The known-blank hash set cannot be used
 * here: it covers only the probed sizes (2048x2048, 1080x1000),
 * and the widget requests 830x290.
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
    /** The MRMS image was blank for a time outside the extent (stale). */
    MRMS_STALE_BLANK,

    /** The forecast frame has no HRRR lead yet (centre tile 503'd). */
    FRAME_NOT_PUBLISHED,

    /** A fetch or decode failed (network, service, or bad bytes). */
    NETWORK
}

object RadarWidgetCompositor {

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

        // Overlay for this step, then the CRT bake.
        val frameTimeMs = when (step) {
            0 -> {
                when (val overlay = fetchMrmsOverlay(fetcher, generationId, grid, now)) {
                    is MrmsOverlay.Ready -> {
                        canvas.drawBitmap(overlay.bitmap, 0f, 0f, null)
                        overlay.bitmap.recycle()
                        overlay.frameTimeMs
                    }
                    is MrmsOverlay.ClearSky -> {
                        // Genuinely clear: the basemap bakes alone.
                        overlay.frameTimeMs
                    }
                    is MrmsOverlay.Failed -> return RadarWidgetBakeResult.Failed(overlay.reason)
                }
            }
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
                        overlay.frameTimeMs
                    }
                    is HrrrOverlay.Failed -> return RadarWidgetBakeResult.Failed(overlay.reason)
                }
            }
        }
        bakeCrt(canvas)
        return RadarWidgetBakeResult.Success(
            bitmap = base,
            label = radarWidgetLabel(step, Instant.ofEpochMilli(frameTimeMs), place.zoneId),
            frameTimeMs = frameTimeMs
        )
    }

    // -- Overlays ----------------------------------------------------------

    private sealed class MrmsOverlay {
        data class Ready(val bitmap: Bitmap, val frameTimeMs: Long) : MrmsOverlay()
        data class ClearSky(val frameTimeMs: Long) : MrmsOverlay()
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

    private suspend fun fetchMrmsOverlay(
        fetcher: CoalescingFetcher,
        generationId: Long,
        grid: RadarWidgetTileGrid,
        now: Instant
    ): MrmsOverlay {
        val serviceInfo = fetcher.fetchText(MRMS_SERVICE_INFO_URL, generationId)
        val newestMs = radarWidgetNewestMrmsTimeMs(serviceInfo)
        val withinExtent = newestMs != null
        val frameTimeMs = newestMs ?: radarWidgetFallbackFrameTimeMs(now)
        val bbox = LonLatBbox(grid.westLon, grid.southLat, grid.eastLon, grid.northLat)
        val url = mrmsExportImageUrl(
            bbox,
            frameTimeMs,
            RADAR_WIDGET_WIDTH_PX,
            RADAR_WIDGET_HEIGHT_PX
        )
        val result = fetcher.fetch(url, generationId)
        val bytes = (result as? FetchResult.Success)?.bytes
            ?: return MrmsOverlay.Failed(RadarWidgetBakeFailure.NETWORK)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return MrmsOverlay.Failed(RadarWidgetBakeFailure.NETWORK)
        if (!hasNonTransparentPixel(decoded)) {
            decoded.recycle()
            // Blank + time outside the extent = stale; blank + time
            // inside = clear sky (the control the store relies on).
            return if (!withinExtent) {
                MrmsOverlay.Failed(RadarWidgetBakeFailure.MRMS_STALE_BLANK)
            } else {
                MrmsOverlay.ClearSky(frameTimeMs)
            }
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
        return MrmsOverlay.Ready(stretched, frameTimeMs)
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
