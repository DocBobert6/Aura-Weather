package com.example.ui.widget

import com.example.data.mapper.MRMS_FRAME_STEP_MS
import com.example.data.mapper.RadarFrame
import com.example.data.mapper.RadarRange
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.radarRangeFrames
import com.example.ui.radar.engine.RADAR_ENGINE_BASEMAP_TILE_TEMPLATE
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Radar widget (Item 4) — the pure model: frame stepping, label text,
 * bitmap budget and the tile-grid math the compositor draws from.
 *
 * Pure Kotlin — no Android, no Compose, no OkHttp — so the stepping,
 * labels and grid are unit-testable locally ([RadarWidgetModelTest]);
 * the Canvas compositing itself needs the Android framework and is
 * CI-only ([RadarWidgetCompositor]).
 *
 * Shape (4x2, classic RemoteViews): a static composited bitmap —
 * newest MRMS frame over the Esri dark-gray basemap, CRT baked in —
 * plus a small Star4000 label line. Tap opens the radar engine.
 *
 * Stepping: the refresh cycle steps now / +15 / +30 on successive
 * 15-min refreshes. The step is derived from the wall clock
 * ([radarWidgetStepIndex]) instead of a persisted counter, so it
 * survives process death and a missed refresh: two refreshes 15 min
 * apart always advance exactly one step (a 15-min span crosses one
 * 15-min boundary), and a late run simply shows the step its time
 * falls in. The label always states which frame is shown, so a
 * skipped step is visible, never silent.
 *
 * Fallbacks (Item 4 refinement, 2026-11-16): a forecast step whose
 * HRRR lead has not published falls back to the live MRMS frame
 * ([radarWidgetOverlayStep]); a live frame that fails or is blank
 * steps back 5 min at a time ([radarWidgetMrmsCandidateTimes]) and
 * bakes the most recent frame that works, labelled with its real
 * valid time (and STALE when it is not the newest).
 */

// ---------------------------------------------------------------------------
// Bitmap / refresh shape
// ---------------------------------------------------------------------------

/** Composited bitmap size: a 4x2 widget is ~830x290 px at ~2.6 density. */
const val RADAR_WIDGET_WIDTH_PX: Int = 830
const val RADAR_WIDGET_HEIGHT_PX: Int = 290

/** Basemap zoom: the engine's opening zoom (7). */
const val RADAR_WIDGET_ZOOM: Int = 7

/** Density the CRT bake assumes: the bitmap is device px at ~2.6 density. */
const val RADAR_WIDGET_DENSITY: Float = 2.6f

/** One step per refresh; the refresh cadence is 15 min. */
const val RADAR_WIDGET_STEP_MINUTES: Int = 15

/** Steps in the cycle: now, +15, +30. */
const val RADAR_WIDGET_STEP_COUNT: Int = 3

/**
 * Live-frame step-back (Item 4 refinement, 2026-11-16): how many
 * MRMS candidates the compositor tries, newest first, before giving
 * up — the newest frame plus three 5-min step-backs (15 min of
 * history). Robert's principle: use the most recent frame that
 * works, even if it is not the newest.
 */
const val RADAR_WIDGET_MRMS_FALLBACK_ATTEMPTS: Int = 4

/** Delay before the one-off retry after a bake that showed nothing. */
const val RADAR_WIDGET_RETRY_DELAY_MINUTES: Long = 2L

/**
 * Empty-state wording (Item 5): the first failure with no image —
 * a retry is pending, so the widget is waiting, not dead.
 */
const val RADAR_WIDGET_WAITING_LABEL: String = "WAITING FOR RADAR"

/**
 * Empty-state wording (Item 5): bakes were attempted and failed
 * with no cached image — nothing has ever been shown.
 */
const val RADAR_WIDGET_UNAVAILABLE_LABEL: String = "RADAR UNAVAILABLE"

/** Tile size in px (Web Mercator 256 px tiles, basemap and HRRR alike). */
const val RADAR_WIDGET_TILE_PX: Int = 256

/**
 * Which frame a refresh at [now] shows: 0 = now (MRMS), 1 = +15
 * (HRRR), 2 = +30 (HRRR). Derived from the 15-min wall-clock grid,
 * so successive 15-min refreshes step 0 -> 1 -> 2 -> 0.
 */
fun radarWidgetStepIndex(now: Instant): Int =
    ((now.epochSecond / (RADAR_WIDGET_STEP_MINUTES * 60L)) % RADAR_WIDGET_STEP_COUNT).toInt()

/** Forecast offset in minutes for [stepIndex]: 0, 15, 30. */
fun radarWidgetStepOffsetMinutes(stepIndex: Int): Int =
    stepIndex.coerceIn(0, RADAR_WIDGET_STEP_COUNT - 1) * RADAR_WIDGET_STEP_MINUTES

/**
 * The widget's label line for the frame at [stepIndex] with valid
 * time [frameTime] in the place's [zoneId]:
 * "LIVE NOW · HH:MM" for the now-frame, "IN 15M · HH:MM" /
 * "IN 30M · HH:MM" for the forecast frames (the step states which
 * frame it is; the time is the frame's real valid time, which for
 * HRRR sits on the run's 15-min marks and may be a few minutes past
 * the step). A stale re-show of the last baked image appends
 * " · STALE" — never a fresh-looking label on old pixels.
 * HH:MM is 24-hour, in the place's zone (as the widget's other
 * times are in the place's zone).
 */
fun radarWidgetLabel(
    stepIndex: Int,
    frameTime: Instant,
    zoneId: String,
    stale: Boolean = false
): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone).format(frameTime)
    val base = when (stepIndex.coerceIn(0, RADAR_WIDGET_STEP_COUNT - 1)) {
        0 -> "LIVE NOW · $time"
        1 -> "IN 15M · $time"
        else -> "IN 30M · $time"
    }
    return if (stale) "$base · STALE" else base
}

/**
 * Serialized size of the composited bitmap: RGB_565 is 2 bytes/px,
 * so 830x290 is 481,400 B (~0.46 MB) — well inside Android's
 * serialized RemoteViews image limit (the ~1 MB Binder transaction
 * budget the whole RemoteViews parcel shares).
 */
fun radarWidgetBitmapByteCount(
    widthPx: Int = RADAR_WIDGET_WIDTH_PX,
    heightPx: Int = RADAR_WIDGET_HEIGHT_PX
): Long = widthPx.toLong() * heightPx * 2L

// ---------------------------------------------------------------------------
// Tile grid — Web Mercator, pure
// ---------------------------------------------------------------------------

/** World size in px at [zoom] (256 px tiles, 2^zoom per side). */
fun radarWidgetWorldSizePx(zoom: Int): Double = 256.0 * (1 shl zoom)

/** World-pixel x of [lon] at [zoom]. */
fun worldPixelX(lon: Double, zoom: Int): Double =
    (lon + 180.0) / 360.0 * radarWidgetWorldSizePx(zoom)

/** World-pixel y of [lat] at [zoom] (clamped to the Mercator limit). */
fun worldPixelY(lat: Double, zoom: Int): Double {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    return (1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI)) / 2.0 *
        radarWidgetWorldSizePx(zoom)
}

/** Longitude of world-pixel [x] at [zoom]. */
fun lonFromWorldPixelX(x: Double, zoom: Int): Double =
    x / radarWidgetWorldSizePx(zoom) * 360.0 - 180.0

/** Latitude of world-pixel [y] at [zoom]. */
fun latFromWorldPixelY(y: Double, zoom: Int): Double {
    val n = Math.PI * (1.0 - 2.0 * y / radarWidgetWorldSizePx(zoom))
    return Math.toDegrees(atan(sinh(n)))
}

/**
 * The tile grid covering the widget bitmap centred on
 * ([centreLat], [centreLon]): the tile ranges, the centre tile (the
 * HRRR fetch requires it — a forecast lead that has not published
 * 503s everywhere, so the centre tile is the coverage signal), the
 * geographic bbox of the bitmap (the MRMS `exportImage` bbox), and
 * the bitmap's world-pixel origin (for per-tile draw offsets).
 * At zoom 7 an 830x290 bitmap needs 4x2 = 8 tiles.
 */
data class RadarWidgetTileGrid(
    val tileX0: Int,
    val tileX1: Int,
    val tileY0: Int,
    val tileY1: Int,
    val centreTileX: Int,
    val centreTileY: Int,
    val westLon: Double,
    val southLat: Double,
    val eastLon: Double,
    val northLat: Double,
    /** World-pixel x of the bitmap's left edge. */
    val leftWorldPx: Double,
    /** World-pixel y of the bitmap's top edge. */
    val topWorldPx: Double
) {
    /** Tiles in the grid (8 at zoom 7 for 830x290). */
    val tileCount: Int get() = (tileX1 - tileX0 + 1) * (tileY1 - tileY0 + 1)

    /** Draw x (bitmap px) of tile [tileX]'s left edge. */
    fun tileDrawX(tileX: Int): Double = tileX * RADAR_WIDGET_TILE_PX - leftWorldPx

    /** Draw y (bitmap px) of tile [tileY]'s top edge. */
    fun tileDrawY(tileY: Int): Double = tileY * RADAR_WIDGET_TILE_PX - topWorldPx
}

fun radarWidgetTileGrid(
    centreLat: Double,
    centreLon: Double,
    widthPx: Int = RADAR_WIDGET_WIDTH_PX,
    heightPx: Int = RADAR_WIDGET_HEIGHT_PX,
    zoom: Int = RADAR_WIDGET_ZOOM
): RadarWidgetTileGrid {
    val cx = worldPixelX(centreLon, zoom)
    val cy = worldPixelY(centreLat, zoom)
    val left = cx - widthPx / 2.0
    val top = cy - heightPx / 2.0
    return RadarWidgetTileGrid(
        tileX0 = floor(left / RADAR_WIDGET_TILE_PX).toInt(),
        tileX1 = floor((left + widthPx - 1.0) / RADAR_WIDGET_TILE_PX).toInt(),
        tileY0 = floor(top / RADAR_WIDGET_TILE_PX).toInt(),
        tileY1 = floor((top + heightPx - 1.0) / RADAR_WIDGET_TILE_PX).toInt(),
        centreTileX = floor(cx / RADAR_WIDGET_TILE_PX).toInt(),
        centreTileY = floor(cy / RADAR_WIDGET_TILE_PX).toInt(),
        westLon = lonFromWorldPixelX(left, zoom),
        southLat = latFromWorldPixelY(top + heightPx, zoom),
        eastLon = lonFromWorldPixelX(left + widthPx, zoom),
        northLat = latFromWorldPixelY(top, zoom),
        leftWorldPx = left,
        topWorldPx = top
    )
}

/**
 * One Esri dark-gray basemap tile URL. The template is the engine's
 * own ([RADAR_ENGINE_BASEMAP_TILE_TEMPLATE], Esri `{z}/{y}/{x}`
 * order — y before x); this only substitutes the three placeholders,
 * never a second copy of the host.
 */
fun esriBasemapTileUrl(zoom: Int, tileX: Int, tileY: Int): String =
    RADAR_ENGINE_BASEMAP_TILE_TEMPLATE
        .replace("{z}", zoom.toString())
        .replace("{y}", tileY.toString())
        .replace("{x}", tileX.toString())

// ---------------------------------------------------------------------------
// Place blip (0.4.0) — the still dot baked into the widget picture
// ---------------------------------------------------------------------------

/** Still blip: filled white dot radius on the 830x290 bitmap (px). */
const val RADAR_WIDGET_BLIP_DOT_RADIUS_PX: Float = 5f

/** Still blip: thin white ring radius around the dot (px). */
const val RADAR_WIDGET_BLIP_RING_RADIUS_PX: Float = 10f

/** Still blip: thin white ring stroke width (px). */
const val RADAR_WIDGET_BLIP_RING_STROKE_PX: Float = 2f

/** Bitmap pixel of a place on the widget image. */
data class RadarWidgetPlaceDot(val xPx: Double, val yPx: Double)

/**
 * Bitmap pixel of ([lat], [lon]) on the widget image for [grid]:
 * the place's world pixel minus the bitmap's world-pixel origin.
 * The grid is built centred on the selected place, so the place's
 * dot lands at the bitmap centre (415, 145) — computed from the
 * coordinates, not assumed, so the dot stays on the place if the
 * grid's centre ever moves.
 */
fun radarWidgetPlaceDotPx(grid: RadarWidgetTileGrid, lat: Double, lon: Double): RadarWidgetPlaceDot =
    RadarWidgetPlaceDot(
        worldPixelX(lon, RADAR_WIDGET_ZOOM) - grid.leftWorldPx,
        worldPixelY(lat, RADAR_WIDGET_ZOOM) - grid.topWorldPx
    )

/**
 * The still blip's exact draw parameters on the widget bitmap: the
 * dot centre plus the dot/ring sizes. The compositor draws exactly
 * this spec (two `drawCircle` calls, dot then ring) — nothing in the
 * compositor re-derives the geometry, so the pure test below pins
 * the pixels the baked image will carry, including a pixel-coverage
 * check over this spec (see `RadarWidgetBlipTest`).
 */
data class RadarWidgetBlipDraw(
    val centerXPx: Double,
    val centerYPx: Double,
    val dotRadiusPx: Float,
    val ringRadiusPx: Float,
    val ringStrokePx: Float
)

/**
 * The still blip's draw spec for the place at ([lat], [lon]) on
 * [grid]: the projected centre plus the fixed dot/ring sizes.
 */
fun radarWidgetBlipDrawSpec(
    grid: RadarWidgetTileGrid,
    lat: Double,
    lon: Double
): RadarWidgetBlipDraw {
    val dot = radarWidgetPlaceDotPx(grid, lat, lon)
    return RadarWidgetBlipDraw(
        centerXPx = dot.xPx,
        centerYPx = dot.yPx,
        dotRadiusPx = RADAR_WIDGET_BLIP_DOT_RADIUS_PX,
        ringRadiusPx = RADAR_WIDGET_BLIP_RING_RADIUS_PX,
        ringStrokePx = RADAR_WIDGET_BLIP_RING_STROKE_PX
    )
}

// ---------------------------------------------------------------------------
// Frame selection
// ---------------------------------------------------------------------------

/**
 * The newest MRMS frame time (epoch ms) from a service-info JSON
 * document, or null when the document has no usable extent (the
 * caller then falls back to "now, truncated to the 5-min cadence"
 * and treats a blank image at that time as stale — see
 * [radarWidgetFallbackFrameTimeMs]).
 */
fun radarWidgetNewestMrmsTimeMs(serviceInfoJson: String?): Long? =
    serviceInfoJson?.let { mrmsFrameTimesFromServiceInfoJson(it) }?.lastOrNull()

/**
 * Fallback now-frame time when the MRMS service info cannot be
 * read: now truncated down to the 5-min cadence (MRMS serves one
 * frame every 5 min, so the newest servable time is at most one
 * step behind now).
 */
fun radarWidgetFallbackFrameTimeMs(now: Instant): Long =
    (now.toEpochMilli() / 300_000L) * 300_000L

/**
 * The HRRR frame for a forecast step: the first frame of the 6h
 * future range ([RadarRange.Future6h], Item 1) at or after
 * now + [offsetMinutes], else the last frame of the range when the
 * whole range is earlier (the range ends at the HRRR cap, never
 * past it). Null when the range itself is empty.
 */
fun radarWidgetFutureFrame(now: Instant, offsetMinutes: Int): RadarFrame? {
    val frames = radarRangeFrames(RadarRange.Future6h, now)
    if (frames.isEmpty()) return null
    val target = now.plusSeconds(offsetMinutes * 60L)
    return frames.firstOrNull { !it.validTime.isBefore(target) } ?: frames.last()
}

/**
 * Which step's label a bake shows when the HRRR fetch for
 * [stepIndex] fails: the live frame (step 0), honestly labelled
 * "LIVE NOW · HH:MM". A forecast step that cannot be served never
 * blanks the widget. The step cycle itself is unchanged — the next
 * refresh still shows its own step.
 */
fun radarWidgetOverlayStep(stepIndex: Int, hrrrFailed: Boolean): Int =
    if (hrrrFailed) 0 else stepIndex.coerceIn(0, RADAR_WIDGET_STEP_COUNT - 1)

/**
 * MRMS candidate frame times (epoch ms), newest first: the newest
 * frame from the service extent when [newestMs] is known, else now
 * truncated to the 5-min cadence ([radarWidgetFallbackFrameTimeMs]),
 * stepping back one [MRMS_FRAME_STEP_MS] per attempt, [attempts]
 * candidates in all. The compositor tries them in order and bakes
 * the first that serves — the most recent frame that works, even
 * when it is not the newest.
 */
fun radarWidgetMrmsCandidateTimes(
    newestMs: Long?,
    now: Instant,
    attempts: Int = RADAR_WIDGET_MRMS_FALLBACK_ATTEMPTS
): List<Long> {
    val first = newestMs ?: radarWidgetFallbackFrameTimeMs(now)
    return (0 until attempts.coerceAtLeast(1)).map { first - it * MRMS_FRAME_STEP_MS }
}

/**
 * The failed-bake decision (Item 4): a cached image re-shows
 * labelled stale, else there is nothing to show. Pure so the
 * updater's branch is unit-testable locally; the updater maps this
 * onto its own [RadarWidgetUpdater.Outcome].
 */
enum class RadarWidgetFailedBakeOutcome {
    STALE_SHOWN,
    UNAVAILABLE
}

fun radarWidgetFailedBakeOutcome(
    hasCachedImage: Boolean
): RadarWidgetFailedBakeOutcome =
    if (hasCachedImage) RadarWidgetFailedBakeOutcome.STALE_SHOWN
    else RadarWidgetFailedBakeOutcome.UNAVAILABLE

/**
 * Empty-state wording (Item 5): "WAITING FOR RADAR" on the first
 * failure with no image (a retry is pending), "RADAR UNAVAILABLE"
 * once a bake has already failed and left nothing to show. The
 * strings live here so Worker B's bitmap-text rendering can share
 * them; the updater passes them as the label until the layout can
 * carry them centred (see the Worker C report — the centred empty
 * view's text is fixed in XML, so the label bar is the only
 * wording channel the updater owns).
 */
fun radarWidgetEmptyStateLabel(hasFailedBefore: Boolean): String =
    if (hasFailedBefore) RADAR_WIDGET_UNAVAILABLE_LABEL else RADAR_WIDGET_WAITING_LABEL
