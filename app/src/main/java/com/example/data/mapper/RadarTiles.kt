package com.example.data.mapper

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.ln
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
// Radar ranges (Build B): past 24/48/72h from the archived NEXRAD mosaic
// and future 24/48h from HRRR simulated reflectivity, both served by the
// same IEM tile service the live loop already uses.
// ---------------------------------------------------------------------------

/** A radar time range the loop can show. Forecast ranges are model output. */
enum class RadarRange(val title: String, val isForecast: Boolean) {
    Live("LIVE", false),
    Past24h("PAST 24H", false),
    Past48h("PAST 48H", false),
    Past72h("PAST 72H", false),
    Future24h("FUTURE 24H", true),
    Future48h("FUTURE 48H", true);

    /** Short label for the segmented control: six options share one row,
     * so the full titles ("FUTURE 24H") would clip. "24H AGO" / "IN 24H"
     * still say which direction and how far. */
    val shortTitle: String
        get() = when (this) {
            Live -> "LIVE"
            Past24h -> "24H AGO"
            Past48h -> "48H AGO"
            Past72h -> "72H AGO"
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
 * Archived NEXRAD mosaic layer for a valid time. The mosaic is produced
 * every 5 minutes, so the time must already be on a 5-minute boundary
 * (pastFrames rounds the reference down to one). Verified live: a tile
 * for ridge::USCOMP-N0Q-202611100040 returned HTTP 200, a valid
 * 256x256 PNG (368 bytes, clear sky over Crittenden KY at zoom 8).
 */
fun pastMosaicLayerName(validTime: Instant): String =
    "ridge::USCOMP-N0Q-${iemTimestamp(validTime)}"

/**
 * HRRR simulated-reflectivity layer. FXXXX is the FORECAST MINUTE
 * (not hour): F000 is the analysis, F360 is 6 hours out, F2880 is
 * 48 hours out. The run time is the HRRR initialisation (hourly).
 *
 * VERIFICATION CAVEAT (2026-11-09): the layer name parses correctly
 * (the tile service builds the right WMS request), but the IEM HRRR
 * backend returns HTTP 503 for every run/forecast-minute tried
 * (latest + archived runs back to 3 days, F000/F006/F060/F360) — the
 * WMS reports the raster file "corrupt, empty or missing". So the
 * future ranges are built on the documented format but their tiles
 * could NOT be confirmed live; they may render blank until the IEM
 * HRRR backend recovers.
 */
fun hrrrLayerName(runTime: Instant, forecastMinutes: Int): String {
    val f = forecastMinutes.toString().padStart(4, '0')
    return "hrrr::REFD-F$f-${iemTimestamp(runTime)}"
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
 * Sampling (per the audit brief): past 24h every 30 min (49 frames),
 * past 48/72h every 60 min (49 / 73 frames). Future: HRRR steps every
 * 15 min for the first 18h from the run, then hourly — so Future 24h
 * is ~75 frames and Future 48h ~99. The loop is a scrubber, not a
 * prefetch-everything list: only the current frame's tiles are
 * prefetched for ranges (see radarFrameUrls), the rest load on demand.
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
        RadarRange.Past24h -> pastFrames(now, hours = 24, stepMinutes = 30)
        RadarRange.Past48h -> pastFrames(now, hours = 48, stepMinutes = 60)
        RadarRange.Past72h -> pastFrames(now, hours = 72, stepMinutes = 60)
        RadarRange.Future24h -> futureFrames(now, hours = 24)
        RadarRange.Future48h -> futureFrames(now, hours = 48)
    }
}

private fun pastFrames(now: Instant, hours: Int, stepMinutes: Int): List<RadarFrame> {
    // Reference = now rounded down to the 5-minute mosaic grid.
    val refMin = (now.epochSecond / 60 / 5) * 5
    val ref = Instant.ofEpochSecond(refMin * 60)
    val totalMin = hours * 60
    val count = totalMin / stepMinutes + 1
    return (0 until count).map { k ->
        val validTime = ref.minusSeconds((totalMin - k * stepMinutes) * 60L)
        val label = if (k == count - 1) "NOW" else radarFrameLabel(validTime, ref)
        RadarFrame(validTime, pastMosaicLayerName(validTime), label)
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
        frames.add(RadarFrame(t, hrrrLayerName(run, forecastMin), radarFrameLabel(t, now)))
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
