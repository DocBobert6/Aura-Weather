package com.example.ui.radar.engine

/**
 * Phase 1 — MapLibre map engine: production style + source helpers.
 *
 * Pure Kotlin: no Android, no MapLibre, no Compose imports — compiled
 * and unit-tested locally with kotlinc + JUnit (see
 * MapLibreRadarStyleTest); the engine activity
 * ([RadarEngineActivity]) consumes these builders.
 *
 * Shape (from the Phase 0 spikes, RADAR_PHASE0_MAPLIBRE.md):
 * - Raster-only style JSON: version 8, navy background, Esri basemap,
 *   GIBS satellite (visible + IR) sources. No vector sources, no
 *   glyphs, no sprite — only `version`, `sources`, `layers` required.
 * - Radar sources are deliberately NOT in this JSON: the activity adds
 *   them programmatically so the Kotlin `Source.isVolatile` /
 *   `Source.prefetchZoomDelta` API — the exact API the rebuild depends
 *   on — is exercised, instead of the `"volatile": true` JSON flag
 *   (whose parse path into the renderer is unproven; the flag stays
 *   unused). Live = 10 per-frame volatile sources (shape (a));
 *   future = a bounded window of HRRR sources (Phase 4 owns the full
 *   49–99-frame ranges through the frame store); MRMS = 2 ImageSource
 *   slots retargeted with `setUri`.
 * - Layers draw, bottom to top: background, basemap, satellite,
 *   radar-live, radar-future, MRMS. See [layerDrawOrder].
 *
 * The GIBS timestamp is a PARAMETER ([gibsTimestamp] values are
 * computed by the caller with the canonical `gibsTimestamp` helper):
 * this file stays free of the mapper that owns that helper so the pure
 * compile set stays one file. IEM/HRRR layer names are likewise passed
 * in ([iemFrameTileTemplate] / [hrrrFrameTileTemplate] take the layer
 * name) — the canonical names live in `RadarTiles.kt`
 * (`iemLayerNameFor` / `hrrrLayerName`) and are never duplicated here.
 */

// ---------------------------------------------------------------------------
// Constants — the engine's shape, in one place
// ---------------------------------------------------------------------------

/** Loop shape (a): one raster source per live loop frame. */
const val RADAR_ENGINE_FRAME_COUNT: Int = 10

/**
 * Phase 1 bound on the future ranges: the first 16 HRRR frames of the
 * range (15-min steps, so ~4 h of forecast). The full ranges hold
 * 49–99 frames; creating a volatile source + layer per frame up front
 * is Phase 4's frame-store work, not Phase 1's. The first 16 frames
 * of the 24 h and 48 h ranges are identical (same HRRR run, same
 * steps), so one window serves both range pills.
 */
const val RADAR_ENGINE_FUTURE_WINDOW: Int = 16

/** Two-slot crossfade duration (ms), matching the rebuild plan §3.2. */
const val RADAR_ENGINE_CROSSFADE_MS: Long = 150L

/** Navy background of the raster-only style (the app's retro navy). */
const val RADAR_ENGINE_NAVY_BG: String = "#0a1a33"

/** Style-JSON ids for the basemap source/layer. */
const val RADAR_ENGINE_BASEMAP_SOURCE_ID: String = "radar-basemap"
const val RADAR_ENGINE_BASEMAP_LAYER_ID: String = "radar-basemap-layer"

/** Style-JSON ids for the satellite sources/layers. */
const val RADAR_ENGINE_SAT_VIS_SOURCE_ID: String = "radar-sat-vis"
const val RADAR_ENGINE_SAT_IR_SOURCE_ID: String = "radar-sat-ir"
const val RADAR_ENGINE_SAT_VIS_LAYER_ID: String = "radar-sat-vis-layer"
const val RADAR_ENGINE_SAT_IR_LAYER_ID: String = "radar-sat-ir-layer"

/** MRMS ImageSource slot ids (two-slot crossfade). */
const val RADAR_ENGINE_MRMS_SLOT_A: String = "radar-mrms-a"
const val RADAR_ENGINE_MRMS_SLOT_B: String = "radar-mrms-b"

/**
 * Esri dark-gray basemap — the app's own basemap
 * (`RadarScreen.kt:1313`). Esri tile order is `{z}/{y}/{x}` (not
 * `{z}/{x}/{y}`); the template keeps Esri's order and MapLibre
 * substitutes the `{x}` / `{y}` / `{z}` placeholders in place.
 */
const val RADAR_ENGINE_BASEMAP_TILE_TEMPLATE: String =
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"

/** Camera default: Cincinnati, OH (Robert's area), zoom 7 — as the spike. */
const val RADAR_ENGINE_CENTER_LAT: Double = 39.1
const val RADAR_ENGINE_CENTER_LON: Double = -84.5
const val RADAR_ENGINE_ZOOM: Double = 7.0

/** Zoom-button range on radar layers (the old screen's 7..9). */
const val RADAR_ENGINE_ZOOM_MIN: Int = 7
const val RADAR_ENGINE_ZOOM_MAX: Int = 9

/** MRMS image size (px): the viewport size, not a fixed 512. */
const val RADAR_ENGINE_MRMS_IMAGE_WIDTH_PX: Int = 1080
const val RADAR_ENGINE_MRMS_IMAGE_HEIGHT_PX: Int = 1000

/** 1.0x MRMS bbox around the centre; the request uses 1.5x (below). */
val RADAR_ENGINE_MRMS_BBOX_1X: RadarBbox = RadarBbox(
    westLon = -87.5, southLat = 36.6, eastLon = -81.5, northLat = 41.6
)

/** Wide-margin ratio: small pans stay inside the fetched image. */
const val RADAR_ENGINE_MRMS_BBOX_FACTOR: Double = 1.5

// ---------------------------------------------------------------------------
// Frame slots — shape (a): one raster source per frame
// ---------------------------------------------------------------------------

/** Raster source id for live loop frame [index] (0..9). */
fun radarLiveFrameSourceId(index: Int): String = "radar-live-frame-$index"

/** Raster layer id for live loop frame [index] (0..9). */
fun radarLiveFrameLayerId(index: Int): String = "radar-live-frame-layer-$index"

/** Raster source id for future frame [index] (0..15 in the window). */
fun radarFutureFrameSourceId(index: Int): String = "radar-future-frame-$index"

/** Raster layer id for future frame [index] (0..15 in the window). */
fun radarFutureFrameLayerId(index: Int): String = "radar-future-frame-layer-$index"

/** Raster layer id for an MRMS ImageSource slot. */
fun radarMrmsLayerId(slotId: String): String = "radar-mrms-layer-$slotId"

/**
 * MapLibre tile-URL template for one IEM radar frame. MapLibre's own
 * placeholder order is `{z}/{x}/{y}` and IEM serves that order.
 * [layerName] is the canonical name (`iemLayerNameFor` in RadarTiles.kt).
 */
fun iemFrameTileTemplate(layerName: String): String =
    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/" +
        "$layerName/{z}/{x}/{y}.png"

/**
 * MapLibre tile-URL template for one HRRR future frame. HRRR layers
 * are served by the same IEM tile endpoint in the same `{z}/{x}/{y}`
 * order. [layerName] is the canonical name (`hrrrLayerName`).
 */
fun hrrrFrameTileTemplate(layerName: String): String =
    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/" +
        "$layerName/{z}/{x}/{y}.png"

/**
 * GIBS WMTS tile-URL template for one satellite layer. GIBS order is
 * `{z}/{y}/{x}` (as in the canonical `gibsTileUrl`); [gibsTime] is the
 * full UTC timestamp from `gibsTimestamp` (Phase 3 owns per-path
 * probing and refresh — Phase 1 freezes it at style build).
 */
fun gibsTileTemplate(isVisible: Boolean, gibsTime: String): String =
    if (isVisible) {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
            "GOES-East_ABI_GeoColor/default/$gibsTime/" +
            "GoogleMapsCompatible_Level7/{z}/{y}/{x}.png"
    } else {
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
            "GOES-East_ABI_Band13_Clean_Infrared/default/$gibsTime/" +
            "GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"
    }

/**
 * GIBS native max zoom per layer (the set's max MATRIX: Level7 holds
 * matrices 0..6, Level6 holds 0..5). Past it, MapLibre overzooms the
 * native tiles instead of refetching — the research §3.4 rule.
 */
fun satelliteMaxZoom(isVisible: Boolean): Int = if (isVisible) 6 else 5

// ---------------------------------------------------------------------------
// Raster-only style JSON (background + basemap + satellite)
//
// Radar sources are deliberately NOT in this JSON (see the file header):
// the activity adds them programmatically with volatile + prefetch set
// in Kotlin at creation. Satellite layers start at opacity 0 — the
// radar layers the activity adds on top are the default view.
// ---------------------------------------------------------------------------

/**
 * Raster-only style: version 8, background navy + Esri basemap +
 * GIBS satellite (visible + IR) raster sources and layers. No vector
 * sources, no glyphs, no sprite.
 */
fun buildRadarStyleJson(gibsVisTime: String, gibsIrTime: String): String = "{" +
    "\"version\":8," +
    "\"name\":\"aura-radar-engine\"," +
    "\"sources\":{" +
    "\"$RADAR_ENGINE_BASEMAP_SOURCE_ID\":{" +
    "\"type\":\"raster\"," +
    "\"tiles\":[\"$RADAR_ENGINE_BASEMAP_TILE_TEMPLATE\"]," +
    "\"tileSize\":256,\"minzoom\":0,\"maxzoom\":19," +
    "\"attribution\":\"Esri\"" +
    "}," +
    "\"$RADAR_ENGINE_SAT_VIS_SOURCE_ID\":{" +
    "\"type\":\"raster\"," +
    "\"tiles\":[\"${gibsTileTemplate(true, gibsVisTime)}\"]," +
    "\"tileSize\":256,\"minzoom\":0,\"maxzoom\":${satelliteMaxZoom(true)}," +
    "\"attribution\":\"NASA GIBS\"" +
    "}," +
    "\"$RADAR_ENGINE_SAT_IR_SOURCE_ID\":{" +
    "\"type\":\"raster\"," +
    "\"tiles\":[\"${gibsTileTemplate(false, gibsIrTime)}\"]," +
    "\"tileSize\":256,\"minzoom\":0,\"maxzoom\":${satelliteMaxZoom(false)}," +
    "\"attribution\":\"NASA GIBS\"" +
    "}" +
    "}," +
    "\"layers\":[" +
    "{\"id\":\"radar-background\",\"type\":\"background\"," +
    "\"paint\":{\"background-color\":\"$RADAR_ENGINE_NAVY_BG\"}}," +
    "{\"id\":\"$RADAR_ENGINE_BASEMAP_LAYER_ID\",\"type\":\"raster\"," +
    "\"source\":\"$RADAR_ENGINE_BASEMAP_SOURCE_ID\"}," +
    "{\"id\":\"$RADAR_ENGINE_SAT_VIS_LAYER_ID\",\"type\":\"raster\"," +
    "\"source\":\"$RADAR_ENGINE_SAT_VIS_SOURCE_ID\"," +
    "\"paint\":{\"raster-opacity\":0}}," +
    "{\"id\":\"$RADAR_ENGINE_SAT_IR_LAYER_ID\",\"type\":\"raster\"," +
    "\"source\":\"$RADAR_ENGINE_SAT_IR_SOURCE_ID\"," +
    "\"paint\":{\"raster-opacity\":0}}" +
    "]" +
    "}"

/**
 * Full layer draw order, bottom to top, as the activity builds it:
 * the JSON layers first, then the programmatically added live frame
 * layers (newest last among themselves — opacity decides which shows),
 * then the future frame layers, then the MRMS slots on top. The
 * activity adds layers in exactly this order (`addLayer` appends).
 */
fun layerDrawOrder(): List<String> {
    val order = mutableListOf<String>()
    order.add("radar-background")
    order.add(RADAR_ENGINE_BASEMAP_LAYER_ID)
    order.add(RADAR_ENGINE_SAT_VIS_LAYER_ID)
    order.add(RADAR_ENGINE_SAT_IR_LAYER_ID)
    for (i in 0 until RADAR_ENGINE_FRAME_COUNT) order.add(radarLiveFrameLayerId(i))
    for (i in 0 until RADAR_ENGINE_FUTURE_WINDOW) order.add(radarFutureFrameLayerId(i))
    order.add(radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_A))
    order.add(radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_B))
    return order
}

// ---------------------------------------------------------------------------
// Bbox math — wide-margin MRMS bbox (rebuild §3.3 adapter fix 2)
// ---------------------------------------------------------------------------

/** Lon/lat bbox in degrees (west, south, east, north). */
data class RadarBbox(
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
fun expandBbox(bbox: RadarBbox, factor: Double): RadarBbox {
    val centreLon = (bbox.westLon + bbox.eastLon) / 2.0
    val centreLat = (bbox.southLat + bbox.northLat) / 2.0
    val halfWidth = (bbox.eastLon - bbox.westLon) / 2.0 * factor
    val halfHeight = (bbox.northLat - bbox.southLat) / 2.0 * factor
    return RadarBbox(
        westLon = centreLon - halfWidth,
        southLat = centreLat - halfHeight,
        eastLon = centreLon + halfWidth,
        northLat = centreLat + halfHeight
    )
}

/** `bbox` query-param form for MRMS `exportImage` (`bboxSR=4326`). */
fun radarBboxToParam(bbox: RadarBbox): String =
    "${bbox.westLon},${bbox.southLat},${bbox.eastLon},${bbox.northLat}"
