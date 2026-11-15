package com.example.data.mapper

import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/**
 * Shared types for the AUDIT6 §9 fix 6 / fix 7 radar sources (MRMS
 * primary, LibreWXR paired, IEM fallback). Pure Kotlin — no Android,
 * no Compose, no Moshi, no Retrofit — so the pure-test script can
 * compile it. Network fetching stays behind [RadarTextFetcher]; the
 * wiring step implements that seam with OkHttp/Retrofit and plugs the
 * adapters below into the radar screen.
 *
 * The two new sources have DIFFERENT shapes, and the wiring agent
 * must respect that:
 * - MRMS is a REST/WMS bbox service, NOT XYZ tiles: one
 *   `exportImage` call returns ONE image for the whole viewport
 *   ([RadarFrameContent.SingleImage]). The loop plays one image per
 *   frame, composited over the basemap — it never builds a tile grid.
 * - LibreWXR is an XYZ tile pyramid (RainViewer-v2-compatible): one
 *   frame is a grid of 256 px tiles
 *   ([RadarFrameContent.Tiled]), exactly like the existing IEM path,
 *   so it can reuse the tile grid, prefetch and cache machinery.
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

/** [order] with every kind in [unavailable] removed, highest priority first. */
fun availableSources(
    unavailable: Set<RadarSourceKind>,
    order: List<RadarSourceKind> = DEFAULT_RADAR_SOURCE_ORDER
): List<RadarSourceKind> = order.filter { it !in unavailable }

// ---------------------------------------------------------------------------
// Viewport bbox (Web Mercator inverse of RadarTiles.kt's tile math)
// ---------------------------------------------------------------------------

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

/** Longitude of the west edge of tile [tileX] at [zoom]. Inverse of [lonToTileX]. */
fun tileXToLon(tileX: Int, zoom: Int): Double =
    tileX.toDouble() / (1 shl zoom) * 360.0 - 180.0

/** Latitude of the north edge of tile [tileY] at [zoom]. Inverse of [latToTileY]. */
fun tileYToLat(tileY: Int, zoom: Int): Double {
    val n = PI * (1.0 - 2.0 * tileY.toDouble() / (1 shl zoom))
    return Math.toDegrees(atan(sinh(n)))
}

/**
 * The lon/lat bbox covering the tile grid the screen draws — the same
 * grid as [radarPrefetchUrls]/[radarFrameUrls] (centre tile ± half
 * tiles), expanded by [marginTiles] tiles on every side so a pan does
 * not immediately leave the MRMS image behind (AUDIT6 §4 margin
 * principle, applied to the single-image path).
 *
 * MRMS `exportImage` takes this bbox with `bboxSR=4326`; the image
 * itself is projected to Web Mercator (`imageSR=3857`) for display.
 */
fun viewportBboxForTileGrid(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    tilesX: Int,
    tilesY: Int,
    marginTiles: Int = 1
): LonLatBbox {
    val centerTileX = lonToTileX(centerLon, zoom)
    val centerTileY = latToTileY(centerLat, zoom)
    val halfX = tilesX / 2
    val halfY = tilesY / 2
    val westTile = centerTileX - halfX - marginTiles
    val eastTile = centerTileX + halfX + marginTiles + 1 // east edge of last tile
    val northTile = centerTileY - halfY - marginTiles
    val southTile = centerTileY + halfY + marginTiles + 1 // south edge of last tile
    return LonLatBbox(
        westLon = tileXToLon(westTile, zoom),
        southLat = tileYToLat(southTile, zoom),
        eastLon = tileXToLon(eastTile, zoom),
        northLat = tileYToLat(northTile, zoom)
    )
}

// ---------------------------------------------------------------------------
// Frame content: one image per frame (MRMS) vs one tile grid (XYZ sources)
// ---------------------------------------------------------------------------

/**
 * What the loop draws for one frame. The wiring agent renders a
 * [SingleImage] as ONE image stretched over its [bbox] (drawn once,
 * no tile grid, no per-tile Coil requests) and a [Tiled] frame through
 * the existing per-tile grid — the two shapes must not be conflated.
 */
sealed class RadarFrameContent {
    /** One image covering [bbox], [widthPx] x [heightPx] pixels (MRMS). */
    data class SingleImage(
        val url: String,
        val bbox: LonLatBbox,
        val widthPx: Int,
        val heightPx: Int
    ) : RadarFrameContent()

    /** One 256 px XYZ tile URL per grid cell (LibreWXR, IEM). */
    data class Tiled(
        val urls: List<String>,
        val zoom: Int
    ) : RadarFrameContent()
}

/** One playable loop frame from one source. */
data class RadarSourceFrame(
    val validTime: Instant,
    val source: RadarSourceKind,
    val label: String,
    val content: RadarFrameContent
)

// ---------------------------------------------------------------------------
// Network seam: pure helpers take raw text; the wiring step fetches it.
// ---------------------------------------------------------------------------

/**
 * The only network seam the pure adapters need: fetch a small JSON
 * document (MRMS service info, LibreWXR weather-maps) as text, or
 * null on any failure. The wiring agent implements this with the
 * app's OkHttp client (blocking call, or a Retrofit suspend call
 * adapted to it) — the pure URL/time/bbox helpers never touch the
 * network themselves, so they stay unit-testable without coroutines.
 *
 * Image bytes are NOT fetched through this interface: MRMS
 * exportImage URLs and LibreWXR tile URLs are handed to Coil (or the
 * tile client) exactly like the existing IEM tile URLs.
 */
fun interface RadarTextFetcher {
    fun fetchText(url: String): String?
}
