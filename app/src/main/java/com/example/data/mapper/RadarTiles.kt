package com.example.data.mapper

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Pure radar tile math + prefetch URL building (3.3). No Android, no
 * Coil — plain JUnit tests cover it (RadarTilesTest), and the radar
 * screen calls it for both rendering and prefetching, so the prefetch
 * covers exactly the grid the screen draws.
 *
 * The tile grid is the one the screen renders: for a grid of tilesX x
 * tilesY the loop runs dx in -halfX..halfX, dy in -halfY..halfY with
 * halfX = tilesX / 2, halfY = tilesY / 2, so the drawn width is
 * 2 * halfX + 1 (not tilesX). The prefetch uses the same bounds.
 *
 * Viewport-driven grid (AUDIT6 §9 Fix 4): the screen no longer draws
 * that fixed centre-anchored grid. It computes a [TileRange] from the
 * panned viewport ([viewportTileRange]) — the visible tiles plus a
 * 1-tile margin (and +1 in the pan-velocity direction for prefetch) —
 * and both rendering and prefetch use the range-based builders below,
 * so a pan actually requests the newly visible tiles. The centre-based
 * builders delegate to the range versions with identical output.
 */

// IEM NEXRAD composite layer name for frame 0..9 (0 = 45m ago, 9 = LIVE).
fun iemLayerNameFor(frameIndex: Int): String = when (frameIndex) {
    9 -> "nexrad-n0q-900913"
    8 -> "nexrad-n0q-900913-m05m"
    7 -> "nexrad-n0q-900913-m10m"
    6 -> "nexrad-n0q-900913-m15m"
    5 -> "nexrad-n0q-900913-m20m"
    4 -> "nexrad-n0q-900913-m25m"
    3 -> "nexrad-n0q-900913-m30m"
    2 -> "nexrad-n0q-900913-m35m"
    1 -> "nexrad-n0q-900913-m40m"
    else -> "nexrad-n0q-900913-m45m"
}

// Spherical Mercator tile coordinates for a lon/lat at a zoom level.
fun lonToTileX(lon: Double, zoom: Int): Int {
    val x = ((lon + 180.0) / 360.0) * (1 shl zoom)
    return x.toInt()
}

fun latToTileY(lat: Double, zoom: Int): Int {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = (1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI)) / 2.0 * (1 shl zoom)
    return y.toInt()
}

/**
 * Effective zoom for a GIBS satellite layer. The matrix-set names count
 * levels, not the max matrix: GoogleMapsCompatible_Level7 holds
 * matrices 0..6 (visible/GeoColor) and Level6 holds 0..5 (infrared/
 * Band13). The radar zoom runs 7..9, so clamping to the set's level
 * count (7 / 6) requests a matrix that does not exist - GeoColor 404s,
 * Band13 400s - and both layers render blank. [isVisible] selects the
 * visible (GeoColor) layer; false is IR.
 */
fun satelliteZoomLevel(isVisible: Boolean, zoom: Int): Int =
    minOf(zoom, if (isVisible) 6 else 5)

// One IEM NEXRAD composite tile URL. This is the exact URL the radar
// screen's AsyncImage loads, so a prefetched tile is a cache hit for it.
fun iemTileUrl(layerName: String, zoom: Int, tileX: Int, tileY: Int): String =
    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layerName/$zoom/$tileX/$tileY.png"

/**
 * The tile range for a centre-based grid: the range the old code drew
 * (centre tile +/- half tiles). The centre-based URL builders below
 * delegate through it to the range-based builders, with identical
 * output — it exists so the old call sites and tests keep passing.
 */
fun centreTileRange(centerTileX: Int, centerTileY: Int, tilesX: Int, tilesY: Int): TileRange {
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    return TileRange(centerTileX - halfX, centerTileX + halfX, centerTileY - halfY, centerTileY + halfY)
}

/**
 * The radar tile URLs to prefetch for a scrub: one list per loop frame
 * (index 0..9), each covering the visible tile grid centred on the
 * place. The caller enqueues the current frame's list first so the
 * visible tiles are not queued behind the other nine frames, then the
 * rest. Satellite layers are a single image and need no prefetch, so
 * this only ever builds IEM composite URLs.
 */
fun radarPrefetchUrls(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    tilesX: Int,
    tilesY: Int
): List<List<String>> {
    val range = centreTileRange(lonToTileX(centerLon, zoom), latToTileY(centerLat, zoom), tilesX, tilesY)
    return radarPrefetchUrlsForRange(zoom, range)
}

// ---------------------------------------------------------------------------
// Radar ranges (Build B): future 24/48h from HRRR simulated reflectivity,
// served by the same IEM tile service the live loop already uses.
// The past 24/48/72h ranges were removed at Robert's direction
// (2026-11-09); only LIVE and the two future ranges remain.
// ---------------------------------------------------------------------------

/** A radar time range the loop can show. Forecast ranges are model output. */
enum class RadarRange(val title: String, val isForecast: Boolean) {
    Live("LIVE", false),
    Future24h("FUTURE 24H", true),
    Future48h("FUTURE 48H", true);

    /** Short label for the segmented control: three options share one
     * row, so the full titles ("FUTURE 24H") would clip. "IN 24H" /
     * "IN 48H" still say how far ahead. */
    val shortTitle: String
        get() = when (this) {
            Live -> "LIVE"
            Future24h -> "IN 24H"
            Future48h -> "IN 48H"
        }
}

/** One frame of a radar range: its valid time, the IEM layer name, and a short label. */
data class RadarFrame(val validTime: Instant, val layerName: String, val label: String)

// IEM layer timestamps are UTC, YYYYMMDDHHMI.
private val iemTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC)

fun iemTimestamp(instant: Instant): String = iemTimeFormat.format(instant)


/**
 * HRRR simulated-reflectivity layer. FXXXX is the FORECAST MINUTE
 * (not hour): F000 is the analysis, F360 is 6 hours out, F2880 is
 * 48 hours out.
 *
 * The layer uses the LIVE form "hrrr::REFD-FXXXX-0" (-0 = the latest
 * run). The timestamped form "hrrr::REFD-FXXXX-YYYYMMDDHHMI" is
 * IEM's ARCHIVED form: the latest run (~1h old) has not been archived
 * yet (the archive lags ~3h), so IEM answers HTTP 503 for it and the
 * future radar renders blank. Probed live 2026-11-10: the timestamped
 * form 503s for runs 1-2h old but works for a 3h-old run, while the
 * -0 form returns a valid PNG for F0075 and F1440 right now. The
 * forecast-minute math (computed from the run in futureFrames) is
 * unchanged; only the layer name drops the timestamp.
 */
fun hrrrLayerName(forecastMinutes: Int): String {
    val f = forecastMinutes.toString().padStart(4, '0')
    return "hrrr::REFD-F$f-0"
}

/**
 * Short relative label for a frame ("24H AGO", "NOW", "IN 6H"), in the
 * same style as the live loop's "45M AGO" / "LIVE NOW".
 *
 * Minute-granular (AUDIT6 §9 Fix 11): an hourly label covered four
 * consecutive 15-min frames, so the label sat still for ~9 s while the
 * loop advanced. Hours now carry the remainder minutes ("IN 2H 15M"),
 * so every frame gets its own label; exact hours stay short ("IN 2H").
 */
fun radarFrameLabel(validTime: Instant, now: Instant): String {
    val diffMin = Duration.between(now, validTime).toMinutes()
    return when {
        diffMin == 0L -> "NOW"
        diffMin < 0 -> {
            val ago = -diffMin
            val hours = ago / 60
            val mins = ago % 60
            if (ago < 60) "${ago}M AGO"
            else if (mins == 0L) "${hours}H AGO"
            else "${hours}H ${mins}M AGO"
        }
        else -> {
            val hours = diffMin / 60
            val mins = diffMin % 60
            if (diffMin < 60) "IN ${diffMin}M"
            else if (mins == 0L) "IN ${hours}H"
            else "IN ${hours}H ${mins}M"
        }
    }
}

/**
 * The frames for a range, oldest first, ending at (past/live) or
 * starting from (future) the present.
 *
 * Future: HRRR steps every 15 min for the first 18h from the run,
 * then hourly — so Future 24h is ~75 frames and Future 48h ~99.
 * The loop is a scrubber, not a prefetch-everything list: only the
 * current frame's tiles are prefetched for ranges (see
 * radarFrameUrls), the rest load on demand.
 *
 * Future 72h is NOT offered: HRRR caps at 48h from the run.
 */
fun radarRangeFrames(range: RadarRange, now: Instant): List<RadarFrame> {
    return when (range) {
        RadarRange.Live -> {
            (0..9).map { i ->
                val minutesAgo = (9 - i) * 5
                val validTime = now.minusSeconds(minutesAgo * 60L)
                val label = if (i == 9) "LIVE NOW" else "${minutesAgo}M AGO"
                RadarFrame(validTime, iemLayerNameFor(i), label)
            }
        }
        RadarRange.Future24h -> futureFrames(now, hours = 24)
        RadarRange.Future48h -> futureFrames(now, hours = 48)
    }
}


private fun futureFrames(now: Instant, hours: Int): List<RadarFrame> {
    // HRRR run: the latest hourly run, allowing ~1h for publish.
    val run = Instant.ofEpochSecond(((now.epochSecond - 3600) / 3600) * 3600)
    // HRRR caps at 48h from the run, so the range ends there even if
    // the requested horizon is further out.
    val end = minOf(now.plusSeconds(hours * 3600L), run.plusSeconds(48 * 3600L))
    // First frame: the next 15-minute mark strictly after now (so the
    // first frame is always a forecast, never the present instant).
    val firstMin = ((now.epochSecond / 60 / 15) + 1) * 15
    var t = Instant.ofEpochSecond(firstMin * 60)
    val frames = mutableListOf<RadarFrame>()
    while (!t.isAfter(end)) {
        val forecastMin = Duration.between(run, t).toMinutes().toInt()
        frames.add(RadarFrame(t, hrrrLayerName(forecastMin), radarFrameLabel(t, now)))
        // 15-min steps while within 18h of the run, then hourly.
        val stepMin = if (Duration.between(run, t).toMinutes() < 18 * 60) 15L else 60L
        t = t.plusSeconds(stepMin * 60)
    }
    return frames
}

/**
 * The tile URLs for ONE frame's layer across the visible grid. Ranges
 * prefetch only the current frame (not all 49-99 frames), so a range
 * switch is a bounded burst and the rest load on demand via Coil.
 */
fun radarFrameUrls(
    layerName: String,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    tilesX: Int,
    tilesY: Int
): List<String> {
    val range = centreTileRange(lonToTileX(centerLon, zoom), latToTileY(centerLat, zoom), tilesX, tilesY)
    return radarFrameUrlsForRange(layerName, zoom, range)
}

/**
 * Rolling look-ahead indices for the radar loop (AUDIT5 L-4): the
 * next [ahead] frame indices after [currentFrameIndex], wrapping
 * around the end of the loop so the last frames prefetch frame 0, 1,
 * ... instead of nothing. The loop itself advances with
 * `(index + 1) % frameCount`, so the prefetch must wrap the same way
 * or every wrap stutters on an on-demand download.
 *
 * Default [ahead] is 5 (radar smoothness fix, 2026-11-14): 3 was not
 * enough — with ~77 tiles per frame on a phone grid and only a
 * handful of concurrent IEM requests, the loop outran a 3-frame
 * buffer and arrived at blank frames. 5 frames is the rolling
 * window; the range effect also prefetches the current frame plus
 * this window on a range switch. Whole ranges (49-99 frames) are
 * never prefetched: thousands of requests.
 *
 * The current frame itself is never returned (it is already the
 * visible frame, prefetched by the range effect), and duplicates are
 * dropped, so a 1-frame loop returns an empty list and a 2-frame loop
 * returns just the other frame.
 */
fun lookAheadFrameIndices(currentFrameIndex: Int, frameCount: Int, ahead: Int = 5): List<Int> {
    if (frameCount <= 0 || ahead <= 0) return emptyList()
    val current = currentFrameIndex.coerceIn(0, frameCount - 1)
    return (1..ahead)
        .map { (current + it) % frameCount }
        .filter { it != current }
        .distinct()
}

// ---------------------------------------------------------------------------
// Radar loop gating (AUDIT6 §9 Fixes 2-3): hold the previous frame until
// the next is ready, preload the loop, dwell on the newest frame.
// Pure Kotlin — covered by RadarTilesTest, no Compose, no Android.
// ---------------------------------------------------------------------------

/** Dwell on each frame once shown: 750 ms is the loop's normal step
 * (in the band the best radar apps use — research §3: 800 ms). */
const val RADAR_LOOP_DWELL_MS = 750L

/** Dwell on the NEWEST frame before the loop wraps (end pause,
 * research §5). No published standard duration was found, so this is
 * a judgment call: 3x the normal dwell, long enough to read the
 * newest frame, short enough not to feel stalled. */
const val RADAR_LOOP_END_PAUSE_MS = 2250L

/** How long the loop waits for a frame to become ready before it
 * advances anyway. UNCHANGED from PR #14 and deliberately not raised:
 * the audit shows the timeout is not the bottleneck (a cold frame
 * needs ~16 s; a longer timeout just shows nothing for longer), and
 * it is the bound that keeps a dead frame from freezing the loop. */
const val RADAR_LOOP_WAIT_TIMEOUT_MS = 2000L

/** The next frame index after [currentFrameIndex], wrapping to 0 at
 * the end of the loop — the same wrap the loop itself advances with,
 * so the prefetch window and the playhead never disagree. Out-of-range
 * input is coerced; an empty loop has no next frame (0). */
fun loopNextIndex(currentFrameIndex: Int, frameCount: Int): Int {
    if (frameCount <= 0) return 0
    return (currentFrameIndex.coerceIn(0, frameCount - 1) + 1) % frameCount
}

/** Is a frame ready to show? [settledTiles] tiles (loaded or errored —
 * a settled error is a known-missing tile, not a tile still in flight)
 * out of [totalTiles]. A frame with no tiles drawn yet is never ready. */
fun isFrameSettled(settledTiles: Int, totalTiles: Int): Boolean =
    totalTiles > 0 && settledTiles >= totalTiles

/** How long the loop dwells on [displayedIndex] once shown: the normal
 * dwell, except the newest frame (last index) gets the end pause
 * before the loop wraps. */
fun loopDwellMs(displayedIndex: Int, frameCount: Int): Long =
    if (frameCount > 0 && displayedIndex == frameCount - 1) RADAR_LOOP_END_PAUSE_MS else RADAR_LOOP_DWELL_MS

/**
 * Honest first-load data cost of preloading the loop, for the
 * "LOADING LOOP..." state (AUDIT6 §9 Fix 3). From the audit's probe
 * (§9 Fix 3 / appendix): LIVE downloads ~25 MB of speckle-heavy
 * tiles the first time, then reads them from the disk cache; future
 * ranges are mostly transparent PNGs (~3.5 MB for 24H, ~5 MB for
 * 48H). Star4000-safe: capitals, digits, spaces and periods only.
 */
fun loopPreloadCostText(range: RadarRange): String = when (range) {
    RadarRange.Live -> "FIRST LOAD ABOUT 25 MB THEN CACHED"
    RadarRange.Future24h -> "FIRST LOAD ABOUT 3.5 MB THEN CACHED"
    RadarRange.Future48h -> "FIRST LOAD ABOUT 5 MB THEN CACHED"
}

// ---------------------------------------------------------------------------
// GIBS satellite tiles (radar smoothness fix, 2026-11-14): the satellite
// layers are a tile grid like radar, not a single image — the screen
// used to prefetch nothing for them, so satellite loaded tile-by-tile
// behind the LOADING overlay. These are the exact URLs the screen's
// AsyncImage loads, so a prefetched tile is a cache hit for it.
// ---------------------------------------------------------------------------

/** One GIBS satellite tile URL. [isVisible] selects the GeoColor
 * visible layer (matrix set Level7, matrices 0..6); false is the
 * Band13 clean-infrared layer (matrix set Level6, matrices 0..5).
 * [gibsTime] is the full UTC timestamp from gibsTimestamp; [zoom]
 * must already be the layer-clamped effective zoom (see
 * satelliteZoomLevel) — an unclamped zoom 404s/400s.
 * Tile size is 256 px, not 512: the GIBS GetCapabilities (checked
 * 2026-11-15) defines every TileMatrixSet at 256x256 — no 512 px
 * matrix set exists for either GOES layer — and a live tile fetch
 * returns a 256x256 PNG, so there is no 512 px variant to switch
 * to. The seam defence stays the parent underlay + whole-frame
 * fade-in (RadarScreen, Fix 9), not larger tiles. */
fun gibsTileUrl(isVisible: Boolean, gibsTime: String, zoom: Int, tileX: Int, tileY: Int): String =
    if (isVisible) {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/$gibsTime/GoogleMapsCompatible_Level7/$zoom/$tileY/$tileX.png"
    } else {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/$gibsTime/GoogleMapsCompatible_Level6/$zoom/$tileY/$tileX.png"
    }

// ---------------------------------------------------------------------------
// Satellite loading (AUDIT6 §9 Fix 9, 2026-11-15): a layer switch used
// to invalidate everything (the zoom clamp changes every URL), the
// 12 s overlay cap restarted on every switch, and the frame assembled
// tile by tile behind the overlay. The screen now holds the satellite
// layer at alpha 0 until the whole frame has settled (or the per-visit
// cap below fires) and fades it in whole; the Fix 4 parent underlay
// shows low-res imagery underneath meanwhile. Pure Kotlin — covered
// by RadarTilesTest, no Compose, no Android.
// ---------------------------------------------------------------------------

/** Overlay cap for satellite: 12 s per satellite VISIT, not per
 * layer switch. The visit starts when the user enters satellite
 * (either layer) and ends when they leave it; switching layers or
 * zooming within the visit must not restart the timer, or the
 * overlay can stay up indefinitely. Unchanged from the old cap's
 * 12 s — only the restart is fixed. */
const val SATELLITE_OVERLAY_CAP_MS = 12000L

/** Milliseconds left before the per-visit satellite overlay cap
 * fires, from a visit that started at [visitStartMs] (uptime ms)
 * as of [nowMs]. Zero when the visit never started (0) or the cap
 * has already elapsed — the screen delays by this, never longer,
 * so a recomposition cannot stretch the cap. */
fun satelliteCapRemainingMs(visitStartMs: Long, nowMs: Long, capMs: Long = SATELLITE_OVERLAY_CAP_MS): Long {
    if (visitStartMs <= 0L) return 0L
    return (capMs - (nowMs - visitStartMs)).coerceAtLeast(0L)
}

/** Is the satellite frame ready to show whole? [settledTiles] tiles
 * (loaded or errored — a settled error is a known-missing tile, not
 * a tile still in flight) out of [totalTiles] drawn. A frame with no
 * tiles drawn yet is never ready. Same shape as [isFrameSettled];
 * kept separate so the satellite gate reads as a satellite gate. */
fun isSatelliteFrameSettled(settledTiles: Int, totalTiles: Int): Boolean =
    totalTiles > 0 && settledTiles >= totalTiles

/**
 * Honest data cost of one satellite layer switch, for the loading
 * state (AUDIT6 §9 Fix 9). From the audit's probe (appendix): the
 * visible grid downloads ~6.0 MB and the IR grid ~5.2 MB, re-fetched
 * on every layer switch and on every new GOES timestamp. Star4000-safe:
 * capitals, digits, spaces and periods only. No Wi-Fi-only gate —
 * the rule is honest cost, not a gate.
 */
fun satelliteCostText(isVisible: Boolean): String = if (isVisible) {
    "SATELLITE USES MORE DATA. ABOUT 6 MB PER SWITCH."
} else {
    "SATELLITE USES MORE DATA. ABOUT 5 MB PER SWITCH."
}

/**
 * The GIBS satellite tile URLs for the visible grid centred on the
 * place — the same grid the screen renders (see the file header
 * note on tilesX/tilesY vs the drawn 2 * half + 1 span). One bounded
 * burst per layer/zoom/centre change, exactly like the radar
 * current-frame prefetch.
 */
fun satelliteTileUrls(
    isVisible: Boolean,
    gibsTime: String,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    tilesX: Int,
    tilesY: Int
): List<String> {
    val range = centreTileRange(lonToTileX(centerLon, zoom), latToTileY(centerLat, zoom), tilesX, tilesY)
    return satelliteTileUrlsForRange(isVisible, gibsTime, zoom, range)
}

/**
 * Snapped tile span (satellite seam fix, 2026-11-14): the screen span
 * of tile [tileIndex] at [stepPx] pixels per tile from a common
 * [originPx] (the screen position of world x/y = 0). The left edge
 * is rounded, and the width is derived from the NEXT tile's left
 * edge — never from a separately rounded size — so adjacent tiles
 * share edges exactly and no 1px seams open up when the step is
 * fractional (pinch zoom) or the size round-trips through dp.
 * Returns (leftPx, widthPx); width is always stepPx rounded to the
 * nearest integer, i.e. floor or ceil of the step, never 0 for a
 * step >= 1px.
 */
fun snappedTileSpan(originPx: Float, tileIndex: Int, stepPx: Float): Pair<Int, Int> {
    val left = (originPx + tileIndex * stepPx).roundToInt()
    val right = (originPx + (tileIndex + 1) * stepPx).roundToInt()
    return left to (right - left)
}

// ---------------------------------------------------------------------------
// Viewport-driven tile manager (AUDIT6 §9 Fix 4, 2026-11-15): the tile
// set used to be FIXED, centred on the place — panning only shifted
// positions and never requested new tiles, so the newly visible area
// stayed solid navy for ~10 s. The grid is now computed from the
// PANNED VIEWPORT: the visible tiles plus a margin ring, recomputed
// only when the pan settles (the screen transforms the existing tiles
// during the drag and reconciles on gesture end). Pure Kotlin —
// covered by RadarTilesTest, no Compose, no Android.
// ---------------------------------------------------------------------------

/** Margin ring around the viewport, in tiles (research §4: Leaflet
 * keepBuffer 2, Slippery 128 px expansion — 1 tile here, because the
 * grid cap below bounds the cost; the pan-velocity direction gets +1
 * more for prefetch). A 1-tile ring covers any pan up to a full tile
 * (256 px) with no gap at all during the drag. */
const val VIEWPORT_TILE_MARGIN = 1

/** Grid cap, unchanged from the old centre-based grid: at most 9 tiles
 * wide and 11 tall, so a very large screen cannot fire hundreds of
 * tile requests. */
const val VIEWPORT_MAX_TILES_X = 9
const val VIEWPORT_MAX_TILES_Y = 11

/** How long after the last pan/zoom move the viewport counts as
 * settled: the screen reconciles (recomputes the tile set and
 * prefetches the new area) when the gesture has been still this long.
 * Leaflet throttles tile updates to 200 ms while panning and loads on
 * idle on mobile; 300 ms is the same idea with a little more settle. */
const val PAN_SETTLE_DELAY_MS = 300L

/** A pan smaller than this does not count as a pan direction: jitter
 * and rounding noise must not add a velocity tile. */
const val PAN_DIRECTION_THRESHOLD_PX = 8f

/** The inclusive tile-coordinate rectangle the screen draws and
 * prefetches. [width]/[height] are tile counts, not spans. */
data class TileRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val tileCount: Int get() = width * height
    fun contains(tileX: Int, tileY: Int): Boolean =
        tileX in minX..maxX && tileY in minY..maxY
}

/**
 * The tile set for a viewport, from the map origin (the screen
 * position of world x/y = 0 — the same origin the screen derives every
 * tile's snapped edges from) and the screen size. A pan moves the
 * origin, so the visible tiles move with it — unlike the old
 * centre-anchored grid, which never moved at all.
 *
 * [marginTiles] extra tiles are loaded on every side; the pan-velocity
 * direction ([velocityDx]/[velocityDy], -1/0/1 from [panDirection])
 * gets one more tile, so tiles in the direction the user is moving are
 * already loading when the gesture settles. The span is capped at
 * [maxTilesX] x [maxTilesY] around the viewport centre tile and
 * clamped to the valid tile coordinates for [zoom].
 */
fun viewportTileRange(
    mapOriginXPx: Float,
    mapOriginYPx: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    tileSizePx: Float,
    zoom: Int,
    marginTiles: Int = VIEWPORT_TILE_MARGIN,
    velocityDx: Int = 0,
    velocityDy: Int = 0,
    maxTilesX: Int = VIEWPORT_MAX_TILES_X,
    maxTilesY: Int = VIEWPORT_MAX_TILES_Y
): TileRange {
    val maxCoord = (1 shl zoom) - 1
    val step = tileSizePx.coerceAtLeast(1f)
    // First/last tile intersecting the viewport: floor, not truncation
    // — a negative origin must round toward -infinity, not toward
    // zero, or the tile left of the origin is dropped.
    val firstX = floor((-mapOriginXPx) / step).toInt()
    val lastX = floor((screenWidthPx - 1f - mapOriginXPx) / step).toInt()
    val firstY = floor((-mapOriginYPx) / step).toInt()
    val lastY = floor((screenHeightPx - 1f - mapOriginYPx) / step).toInt()
    val centreX = Math.floorDiv(firstX + lastX, 2)
    val centreY = Math.floorDiv(firstY + lastY, 2)
    var minX = firstX - marginTiles - if (velocityDx < 0) 1 else 0
    var maxX = lastX + marginTiles + if (velocityDx > 0) 1 else 0
    var minY = firstY - marginTiles - if (velocityDy < 0) 1 else 0
    var maxY = lastY + marginTiles + if (velocityDy > 0) 1 else 0
    // Cap the span around the viewport centre tile, then clamp to the
    // valid tile coordinates for this zoom.
    if (maxX - minX + 1 > maxTilesX) {
        minX = centreX - maxTilesX / 2
        maxX = minX + maxTilesX - 1
    }
    if (maxY - minY + 1 > maxTilesY) {
        minY = centreY - maxTilesY / 2
        maxY = minY + maxTilesY - 1
    }
    return TileRange(
        minX.coerceIn(0, maxCoord),
        maxX.coerceIn(0, maxCoord),
        minY.coerceIn(0, maxCoord),
        maxY.coerceIn(0, maxCoord)
    )
}

/** Pan direction from a cumulative pan delta: -1, 0 or 1 per axis.
 * The screen passes the pan since the last settle (not the per-event
 * delta, which is only a few pixels per pointer event), so a real
 * drag always clears the threshold and jitter never does. */
fun panDirection(deltaPx: Float): Int = when {
    deltaPx > PAN_DIRECTION_THRESHOLD_PX -> 1
    deltaPx < -PAN_DIRECTION_THRESHOLD_PX -> -1
    else -> 0
}

/**
 * The lower-zoom parent tile for a tile: the tile at [zoom] - 1 that
 * contains it, plus which quadrant of the parent the child sits in
 * ([offsetX]/[offsetY], 0 or 1). The screen draws the parent scaled
 * 2x, shifted by -offset * tileSize and clipped to the child's box,
 * underneath the child — so a tile still loading shows its parent's
 * imagery instead of a hole, and a pan into unloaded area shows
 * low-res map instead of navy void. Null at zoom 0 (no parent).
 */
data class ParentTile(val tileX: Int, val tileY: Int, val zoom: Int, val offsetX: Int, val offsetY: Int)

fun parentTileFor(tileX: Int, tileY: Int, zoom: Int): ParentTile? {
    if (zoom <= 0) return null
    return ParentTile(
        tileX = Math.floorDiv(tileX, 2),
        tileY = Math.floorDiv(tileY, 2),
        zoom = zoom - 1,
        offsetX = Math.floorMod(tileX, 2),
        offsetY = Math.floorMod(tileY, 2)
    )
}

/** URL-set readiness (Fix 4): is every tile URL in [urls] settled
 * (loaded or errored, in [settledUrls])? The loop gates on this, not
 * on a per-frame count — a count cannot survive a pan, because tiles
 * that left the viewport stay counted while newly visible tiles are
 * still in flight. A set is exact across pans: tiles still visible
 * keep their settled status, new tiles are not ready until they
 * settle, departed tiles simply stop being asked about. */
fun isTileSettled(urls: List<String>, settledUrls: Set<String>): Boolean =
    urls.isNotEmpty() && urls.all { it in settledUrls }

/** The tile URLs for ONE frame's layer across [range] — the
 * viewport-driven form of [radarFrameUrls]. */
fun radarFrameUrlsForRange(layerName: String, zoom: Int, range: TileRange): List<String> =
    buildList {
        for (tileX in range.minX..range.maxX) {
            for (tileY in range.minY..range.maxY) {
                add(iemTileUrl(layerName, zoom, tileX, tileY))
            }
        }
    }

/** The radar tile URLs to prefetch for a scrub across [range]: one
 * list per loop frame (index 0..9). The viewport-driven form of
 * [radarPrefetchUrls]. */
fun radarPrefetchUrlsForRange(zoom: Int, range: TileRange): List<List<String>> =
    (0..9).map { frameIndex ->
        radarFrameUrlsForRange(iemLayerNameFor(frameIndex), zoom, range)
    }

/** The GIBS satellite tile URLs for [range] — the viewport-driven
 * form of [satelliteTileUrls]. */
fun satelliteTileUrlsForRange(
    isVisible: Boolean,
    gibsTime: String,
    zoom: Int,
    range: TileRange
): List<String> =
    buildList {
        for (tileX in range.minX..range.maxX) {
            for (tileY in range.minY..range.maxY) {
                add(gibsTileUrl(isVisible, gibsTime, zoom, tileX, tileY))
            }
        }
    }
