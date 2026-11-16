package com.example.data.mapper

/**
 * Shared types for the AUDIT6 §9 fix 6 / fix 7 radar sources (MRMS
 * primary, LibreWXR paired, IEM fallback). Pure Kotlin — no Android,
 * no Compose, no Moshi, no Retrofit — so the pure-test script can
 * compile it.
 *
 * The two new sources have DIFFERENT shapes, and the engine
 * must respect that:
 * - MRMS is a REST/WMS bbox service, NOT XYZ tiles: one
 *   `exportImage` call returns ONE image for the whole viewport.
 *   The loop plays one image per frame, composited over the basemap
 *   — it never builds a tile grid.
 * - LibreWXR is an XYZ tile pyramid (RainViewer-v2-compatible): one
 *   frame is a grid of 256 px tiles, exactly like the old IEM path,
 *   so the engine serves it through a MapLibre raster source.
 * - IEM stays as the third fallback on its existing path
 *   (RadarTiles.kt); no new code is needed for it here.
 */

// ---------------------------------------------------------------------------
// Source identity + fallback ordering (fix 7: LibreWXR paired, never sole)
// ---------------------------------------------------------------------------

/** An observed-radar source the loop can draw from. */
enum class RadarSourceKind {
    /** NWS ArcGIS MRMS ImageServer — primary. Official, public domain. */
    MRMS,

    /** LibreWXR public instance — paired second source, never sole:
     *  single operator, no SLA, commercial-scale traffic not allowed. */
    LIBRE_WXR,

    /** IEM NEXRAD composite — existing path, demoted to fallback. */
    IEM
}

/**
 * The fallback order the loop tries, in priority order. LibreWXR is
 * second, never first: if its public instance is down, rate-limited
 * or pruned, the loop falls through to IEM instead of going blank.
 */
val DEFAULT_RADAR_SOURCE_ORDER: List<RadarSourceKind> =
    listOf(RadarSourceKind.MRMS, RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM)

/** The next source to try after [failed] in [order], or null if it was last. */
fun fallbackAfter(
    failed: RadarSourceKind,
    order: List<RadarSourceKind> = DEFAULT_RADAR_SOURCE_ORDER
): RadarSourceKind? {
    val index = order.indexOf(failed)
    if (index < 0) return order.firstOrNull()
    return order.getOrNull(index + 1)
}

/** A lon/lat bounding box in degrees (EPSG:4326), west/south/east/north. */
data class LonLatBbox(
    val westLon: Double,
    val southLat: Double,
    val eastLon: Double,
    val northLat: Double
) {
    fun contains(lon: Double, lat: Double): Boolean =
        lon in westLon..eastLon && lat in southLat..northLat
}
