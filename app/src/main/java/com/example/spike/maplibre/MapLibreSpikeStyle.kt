package com.example.spike.maplibre

/**
 * Phase 0 spike helpers — MapLibre direct (13.7.1) + loop-source retarget.
 *
 * Pure Kotlin: no Android, no MapLibre, no Compose. Compiled and unit-tested
 * locally with kotlinc + JUnit (see MapLibreSpikeStyleTest); the Android
 * spike activity ([MapLibreSpikeActivity]) consumes these builders.
 *
 * Throwaway code for the spike branch only (`fix/radar-phase0-spikes`).
 * Nothing here lands on main; Phase 1 re-derives the real builders from
 * the canonical adapters (not from this file).
 */

// ---------------------------------------------------------------------------
// Constants — the spike's shape, in one place
// ---------------------------------------------------------------------------

/** Loop shape (a): one raster source per loop frame. */
const val SPIKE_FRAME_COUNT: Int = 10

/** Two-slot crossfade duration (ms), matching the rebuild plan §3.2. */
const val SPIKE_CROSSFADE_MS: Long = 150L

/** Navy background of the raster-only style (the app's retro navy). */
const val SPIKE_NAVY_BG: String = "#0a1a33"

/** Style-JSON ids for the basemap source/layer. */
const val SPIKE_BASEMAP_SOURCE_ID: String = "spike-basemap"
const val SPIKE_BASEMAP_LAYER_ID: String = "spike-basemap-layer"

/** MRMS ImageSource slot ids (two-slot crossfade, shape from §3.2). */
const val SPIKE_MRMS_SLOT_A: String = "spike-mrms-a"
const val SPIKE_MRMS_SLOT_B: String = "spike-mrms-b"

/**
 * Esri dark-gray basemap — the app's own basemap
 * (`RadarScreen.kt:1313`). Esri tile order is `{z}/{y}/{x}` (not
 * `{z}/{x}/{y}`); the template keeps Esri's order and MapLibre
 * substitutes the `{x}` / `{y}` / `{z}` placeholders in place.
 */
const val SPIKE_BASEMAP_TILE_TEMPLATE: String =
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"

// ---------------------------------------------------------------------------
// Frame slots — shape (a): 10 per-frame raster sources
// ---------------------------------------------------------------------------

/** Raster source id for loop frame [index] (0..9). */
fun spikeFrameSourceId(index: Int): String = "spike-radar-frame-$index"

/** Raster layer id for loop frame [index] (0..9). */
fun spikeFrameLayerId(index: Int): String = "spike-radar-frame-layer-$index"

/** Raster layer id for an MRMS ImageSource slot. */
fun spikeMrmsLayerId(slotId: String): String = "spike-mrms-layer-$slotId"

/**
 * IEM NEXRAD composite layer name for loop frame [index], copied from
 * the canonical [com.example.data.mapper.iemLayerNameFor] in RadarTiles.kt
 * (duplicated here so this file stays self-contained and pure-testable;
 * the test asserts the values match). 9 = LIVE, 0 = 45 min ago.
 */
fun spikeIemLayerNameFor(frameIndex: Int): String = when (frameIndex) {
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

/**
 * MapLibre tile-URL template for one IEM loop frame. MapLibre's own
 * placeholder order is `{z}/{x}/{y}` and IEM serves that order.
 */
fun spikeIemFrameTileTemplate(frameIndex: Int): String =
    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/" +
        "${spikeIemLayerNameFor(frameIndex)}/{z}/{x}/{y}.png"

// ---------------------------------------------------------------------------
// Raster-only style JSON (background + basemap only)
//
// Radar sources are deliberately NOT in this JSON: the spike adds them
// programmatically so the Kotlin `Source.isVolatile` /
// `Source.prefetchZoomDelta` API — the exact API the rebuild depends on —
// is exercised, instead of the `"volatile": true` JSON flag (whose parse
// path into the renderer is the wrapper's open question, and moot for
// the direct route once the Kotlin setter is proven).
// ---------------------------------------------------------------------------

/**
 * Minimal raster-only style: version 8, one Esri basemap raster source,
 * background navy + basemap raster layer. No vector sources, no glyphs,
 * no sprite — only `version`, `sources`, `layers` are required.
 */
fun buildSpikeStyleJson(): String = "{" +
    "\"version\":8," +
    "\"name\":\"aura-radar-spike\"," +
    "\"sources\":{" +
    "\"$SPIKE_BASEMAP_SOURCE_ID\":{" +
    "\"type\":\"raster\"," +
    "\"tiles\":[\"$SPIKE_BASEMAP_TILE_TEMPLATE\"]," +
    "\"tileSize\":256,\"minzoom\":0,\"maxzoom\":19," +
    "\"attribution\":\"Esri\"" +
    "}" +
    "}," +
    "\"layers\":[" +
    "{\"id\":\"spike-background\",\"type\":\"background\"," +
    "\"paint\":{\"background-color\":\"$SPIKE_NAVY_BG\"}}," +
    "{\"id\":\"$SPIKE_BASEMAP_LAYER_ID\",\"type\":\"raster\"," +
    "\"source\":\"$SPIKE_BASEMAP_SOURCE_ID\"}" +
    "]" +
    "}"

// ---------------------------------------------------------------------------
// Bbox math — wide-margin MRMS bbox (rebuild §3.3 adapter fix 2)
// ---------------------------------------------------------------------------

/** Lon/lat bbox in degrees (west, south, east, north). */
data class SpikeBbox(
    val westLon: Double,
    val southLat: Double,
    val eastLon: Double,
    val northLat: Double
)

/**
 * Expand [bbox] by [factor] around its centre (1.5 = the rebuild's
 * wide-margin ratio: small pans stay inside the already-fetched image
 * and cost no refetch). Centre is preserved exactly.
 */
fun expandBbox(bbox: SpikeBbox, factor: Double): SpikeBbox {
    val centreLon = (bbox.westLon + bbox.eastLon) / 2.0
    val centreLat = (bbox.southLat + bbox.northLat) / 2.0
    val halfWidth = (bbox.eastLon - bbox.westLon) / 2.0 * factor
    val halfHeight = (bbox.northLat - bbox.southLat) / 2.0 * factor
    return SpikeBbox(
        westLon = centreLon - halfWidth,
        southLat = centreLat - halfHeight,
        eastLon = centreLon + halfWidth,
        northLat = centreLat + halfHeight
    )
}

/** `bbox` query-param form for MRMS `exportImage` (`bboxSR=4326`). */
fun spikeBboxToParam(bbox: SpikeBbox): String =
    "${bbox.westLon},${bbox.southLat},${bbox.eastLon},${bbox.northLat}"
