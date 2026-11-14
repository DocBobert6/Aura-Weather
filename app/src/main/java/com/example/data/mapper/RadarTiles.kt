package com.example.data.mapper

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.cos
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
    val centerTileX = lonToTileX(centerLon, zoom)
    val centerTileY = latToTileY(centerLat, zoom)
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    return (0..9).map { frameIndex ->
        val layerName = iemLayerNameFor(frameIndex)
        buildList {
            for (dx in -halfX..halfX) {
                for (dy in -halfY..halfY) {
                    add(iemTileUrl(layerName, zoom, centerTileX + dx, centerTileY + dy))
                }
            }
        }
    }
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
 */
fun radarFrameLabel(validTime: Instant, now: Instant): String {
    val diffMin = Duration.between(now, validTime).toMinutes()
    return when {
        diffMin == 0L -> "NOW"
        diffMin < 0 -> {
            val ago = -diffMin
            if (ago < 60) "${ago}M AGO" else "${ago / 60}H AGO"
        }
        else -> {
            if (diffMin < 60) "IN ${diffMin}M" else "IN ${diffMin / 60}H"
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
    val centerTileX = lonToTileX(centerLon, zoom)
    val centerTileY = latToTileY(centerLat, zoom)
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    return buildList {
        for (dx in -halfX..halfX) {
            for (dy in -halfY..halfY) {
                add(iemTileUrl(layerName, zoom, centerTileX + dx, centerTileY + dy))
            }
        }
    }
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
 * satelliteZoomLevel) — an unclamped zoom 404s/400s. */
fun gibsTileUrl(isVisible: Boolean, gibsTime: String, zoom: Int, tileX: Int, tileY: Int): String =
    if (isVisible) {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/$gibsTime/GoogleMapsCompatible_Level7/$zoom/$tileY/$tileX.png"
    } else {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/$gibsTime/GoogleMapsCompatible_Level6/$zoom/$tileY/$tileX.png"
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
    val centerTileX = lonToTileX(centerLon, zoom)
    val centerTileY = latToTileY(centerLat, zoom)
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    return buildList {
        for (dx in -halfX..halfX) {
            for (dy in -halfY..halfY) {
                add(gibsTileUrl(isVisible, gibsTime, zoom, centerTileX + dx, centerTileY + dy))
            }
        }
    }
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
