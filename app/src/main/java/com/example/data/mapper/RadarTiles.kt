package com.example.data.mapper

import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Pure radar helpers kept for the MapLibre engine (Phase 5 cutover).
 * No Android, no Coil — plain JUnit tests cover them
 * (RadarTilesTest). The engine uses these for frame lists
 * ([radarRangeFrames], [mrmsLiveFrames]), frame labels
 * ([radarFrameLabel]), tile coordinates ([lonToTileX], [latToTileY]),
 * IEM layer names / tile URLs ([iemLayerNameFor], [iemTileUrl]),
 * proving grids ([centreFirstTileOffsets]) and loop timing
 * ([loopNextIndex], [loopDwellMs]). The old tile-grid builders,
 * prefetch URL builders, settlement gates and cost estimates for the
 * retired Compose tile screen were deleted with it.
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

// One IEM NEXRAD composite tile URL. The engine builds proving URLs
// from this, and the MapLibre raster source requests the same shape.
fun iemTileUrl(layerName: String, zoom: Int, tileX: Int, tileY: Int): String =
    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layerName/$zoom/$tileX/$tileY.png"

/**
 * Tile offsets (dx, dy) for a centre-based grid of [tilesX] x [tilesY]
 * (dx in -halfX..halfX, dy in -halfY..halfY), ordered CENTRE FIRST — rings outward by Chebyshev
 * distance, then by Manhattan distance, then dx, then dy — so a
 * fetcher that launches requests in this order proves the visible
 * centre of a frame before its margin ring (research §3.2.7:
 * centre-first fetch order). Deterministic: the same grid always
 * yields the same order. (0, 0) is always first; the last entries are
 * the grid's corners.
 */
fun centreFirstTileOffsets(tilesX: Int, tilesY: Int): List<Pair<Int, Int>> {
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    return buildList {
        for (dx in -halfX..halfX) {
            for (dy in -halfY..halfY) {
                add(dx to dy)
            }
        }
    }.sortedWith(
        compareBy<Pair<Int, Int>> { maxOf(kotlin.math.abs(it.first), kotlin.math.abs(it.second)) }
            .thenBy { kotlin.math.abs(it.first) + kotlin.math.abs(it.second) }
            .thenBy { it.first }
            .thenBy { it.second }
    )
}

/** A radar time range the loop can show. Forecast ranges are model output. */
enum class RadarRange(val title: String, val isForecast: Boolean) {
    Live("LIVE", false),
    Future6h("FUTURE 6H", true),
    Future24h("FUTURE 24H", true),
    Future48h("FUTURE 48H", true);

    /** Short label for the segmented control: four options share one
     * row, so the full titles ("FUTURE 24H") would clip. "IN 6H" /
     * "IN 24H" / "IN 48H" still say how far ahead. (Measured against
     * the Star4000Small advances at 12sp: the widest label "IN 48H"
     * is 34.2dp + 8dp padding against a ~70dp cell even on a 320dp
     * screen, so the four-pill row does not clip.) */
    val shortTitle: String
        get() = when (this) {
            Live -> "LIVE"
            Future6h -> "IN 6H"
            Future24h -> "IN 24H"
            Future48h -> "IN 48H"
        }
}

/** One frame of a radar range: its valid time, the IEM layer name, and a short label. */
data class RadarFrame(val validTime: Instant, val layerName: String, val label: String)

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
 * then hourly — so Future 6h is 24 frames, Future 24h ~75 frames
 * and Future 48h ~99. The loop is a scrubber, not a
 * prefetch-everything list: only the current frame's tiles are
 * fetched for a range; the rest load on demand.
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
        RadarRange.Future6h -> futureFrames(now, hours = 6)
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

/** Dwell on each frame once shown: 750 ms is the loop's normal step
 * (in the band the best radar apps use — research §3: 800 ms). */
const val RADAR_LOOP_DWELL_MS = 750L

/** Dwell on the NEWEST frame before the loop wraps (end pause,
 * research §5). No published standard duration was found, so this is
 * a judgment call: 3x the normal dwell, long enough to read the
 * newest frame, short enough not to feel stalled. */
const val RADAR_LOOP_END_PAUSE_MS = 2250L

/** The next frame index after [currentFrameIndex], wrapping to 0 at
 * the end of the loop — the same wrap the loop itself advances with,
 * so the prefetch window and the playhead never disagree. Out-of-range
 * input is coerced; an empty loop has no next frame (0). */
fun loopNextIndex(currentFrameIndex: Int, frameCount: Int): Int {
    if (frameCount <= 0) return 0
    return (currentFrameIndex.coerceIn(0, frameCount - 1) + 1) % frameCount
}

/** How long the loop dwells on [displayedIndex] once shown: the normal
 * dwell, except the newest frame (last index) gets the end pause
 * before the loop wraps. */
fun loopDwellMs(displayedIndex: Int, frameCount: Int): Long =
    if (frameCount > 0 && displayedIndex == frameCount - 1) RADAR_LOOP_END_PAUSE_MS else RADAR_LOOP_DWELL_MS

/** The LIVE loop keeps its 10-frame shape at MRMS cadence: 10 frames
 * x 5 min = a 50-min span (vs 45 min on the old IEM loop). The MRMS
 * service window itself is wider (~112 min / 22 frames live) — the
 * loop plays only the newest 10, so the finer cadence is the point
 * (less flicker), not a longer span. */
const val MRMS_LOOP_FRAME_COUNT: Int = 10

/**
 * The LIVE loop frames from MRMS extent times: the newest
 * [MRMS_LOOP_FRAME_COUNT] times, oldest first, each with its true
 * valid time and a minute-granular label. The newest frame is labelled
 * "LIVE NOW" (as the old IEM skeleton did). The IEM layer name is kept
 * per frame — mapped from the END, so the newest frame always carries
 * the live layer name even when the extent holds fewer than 10
 * frames — because it is the IEM-fallback identity and the crossfade
 * key for that frame. Empty when the extent holds no times: the
 * caller then keeps the IEM skeleton instead of synthesising frames.
 */
fun mrmsLiveFrames(timesMs: List<Long>, now: Instant): List<RadarFrame> {
    val recent = timesMs.sorted().takeLast(MRMS_LOOP_FRAME_COUNT)
    if (recent.isEmpty()) return emptyList()
    return recent.mapIndexed { index, timeMs ->
        val validTime = Instant.ofEpochMilli(timeMs)
        val label = if (index == recent.lastIndex) "LIVE NOW" else radarFrameLabel(validTime, now)
        val layerIndex = MRMS_LOOP_FRAME_COUNT - 1 - (recent.lastIndex - index)
        RadarFrame(validTime, iemLayerNameFor(layerIndex), label)
    }
}
