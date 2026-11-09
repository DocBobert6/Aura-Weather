package com.example.data.mapper

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
