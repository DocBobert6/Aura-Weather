package com.example.ui.radar.engine

/**
 * Phase 1 — MapLibre map engine: production style + source helpers.
 *
 * Pure Kotlin: no Android, no MapLibre, no Compose imports — compiled
 * and unit-tested locally with kotlinc + JUnit (see
 * MapLibreRadarStyleTest); the engine controller
 * ([RadarEngineController]) consumes these builders.
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
 *   future = one volatile source per HRRR frame of the full range
 *   (Phase 4: ~74 for 24 h, ~97 for 48 h), built lazily on the
 *   first future visit; MRMS = 2 ImageSource slots retargeted with
 *   `setUri`.
 * - Layers draw, bottom to top: background, basemap, satellite,
 *   radar-live, radar-future, MRMS. See [layerDrawOrder].
 *
 * The GIBS timestamp is a PARAMETER: the caller passes the INITIAL
 * fallback timestamps (computed with the canonical `gibsTimestamp`
 * helper) — the activity rebuilds the satellite sources with a
 * probed timestamp before first satellite display (Phase 3), so the
 * JSON value is never the displayed time.
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
 * Phase 4: there is NO bound on the future ranges any more. The
 * full 24 h (~73 frames) and 48 h (~97 frames) HRRR windows each
 * get one volatile source + layer per frame, built lazily on the
 * first future visit (the activity's `rebuildFutureSources`); the
 * 24 h list is a prefix of the 48 h list, so a range switch only
 * extends the sources instead of rebuilding them. Phase 1's
 * 16-frame window (`RADAR_ENGINE_FUTURE_WINDOW`) is gone: it cut
 * both ranges to ~4 h of forecast. A future layer only loads its
 * tiles once its frame is proved ready (the activity flips its
 * `visibility` from `none` to `visible` then) — creating 99
 * sources does not fire a 99-frame tile burst on range entry.
 */

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

/** Satellite WMS ImageSource slot ids (two-slot crossfade, Phase 3). */
const val RADAR_ENGINE_SAT_WMS_SLOT_A: String = "radar-sat-wms-a"
const val RADAR_ENGINE_SAT_WMS_SLOT_B: String = "radar-sat-wms-b"

/**
 * Per-request timeout for satellite WMS fetches (Phase 3): a hung
 * GetMap or probe must move the UI on inside the 20 s loop slow
 * budget — the fetcher's own 20 s read bound is too long for the
 * satellite 10 s single-request budget, so the wiring applies this
 * around the await instead of changing the client.
 */
const val RADAR_ENGINE_SAT_REQUEST_TIMEOUT_MS: Long = 10_000L

/**
 * Pan margin trigger (Phase 2): the camera target has moved this far
 * (degrees) from the generation centre when the loop refetches with a
 * new bbox. 0.5° ≈ 55 km ≈ 1/9 of the visible viewport height at zoom
 * 7; it sits inside the tightest slack the 1.5× image gives over the
 * chrome-adjusted viewport (~0.65° vertically — the 7.5°-tall image
 * over a ~6.2°-tall visible map), so a refetch always lands before a
 * visible gap — and a small pan never refetches.
 */
const val RADAR_ENGINE_PAN_MARGIN_DEGREES: Double = 0.5

/**
 * Live slow/unavailable budget (Phase 2): a generation with NO ready
 * frame after this long counts as stalled. A healthy cold MRMS loop
 * primes in ~3 s (first frame) and completes in ~6 s wall (10 images
 * in parallel at ~2.8 s median server render); 20 s is 3× past a
 * healthy full load, and matches the old forecast trigger's 20 s
 * precedent — the same user-visible patience for both loops.
 */
const val RADAR_ENGINE_LIVE_SLOW_BUDGET_MS: Long = 20_000L

/** Loop refresh cadence (Phase 2): frame list + MRMS extent re-resolve. */
const val RADAR_ENGINE_LOOP_REFRESH_MS: Long = 5 * 60 * 1000L

/**
 * Per-metadata-fetch timeout (Phase 2): a hung MRMS service-info or
 * LibreWXR maps call must not stall the loop behind it — the two
 * metadata fetches run in parallel and each is bounded by this.
 * 12 s is past a healthy metadata round-trip (~1 s) and inside the
 * 20 s slow budget, so a hung metadata call still resolves to a
 * fallback (or the unavailable state) inside the budget.
 */
const val RADAR_ENGINE_METADATA_TIMEOUT_MS: Long = 12_000L

/**
 * Has the camera target moved past the pan margin from the generation
 * centre ([centreLat], [centreLon])? Either axis past
 * [marginDegrees] counts — a diagonal pan clears the margin sooner,
 * which is the safe direction (refetch early, never show a gap).
 */
fun panPastMargin(
    centreLat: Double,
    centreLon: Double,
    targetLat: Double,
    targetLon: Double,
    marginDegrees: Double = RADAR_ENGINE_PAN_MARGIN_DEGREES
): Boolean =
    kotlin.math.abs(targetLat - centreLat) > marginDegrees ||
        kotlin.math.abs(targetLon - centreLon) > marginDegrees

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

/**
 * Zoom range on every layer, radar and satellite alike (5..9). The
 * radar floor used to be 7 (the old screen's 7..9) — but 7 is also
 * the opening zoom, so a pinch-out on radar could never move: every
 * settle snapped the camera back to 7 (the controller enforced it
 * in onMapIdle). The floor is now 5, the satellite floor: the tiled
 * sources render at any zoom, and the MRMS single image simply
 * covers a smaller fraction of a wider viewport below 7 — the loop
 * keeps playing inside it. The bound is enforced by the map's own
 * min/max zoom preferences (set in [RadarEngineController.start]),
 * so a pinch-out stops at the floor during the gesture itself —
 * never a post-settle snap-back.
 */
const val RADAR_ENGINE_ZOOM_MIN: Int = 5
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

/**
 * Raster source id for LibreWXR fallback loop frame [index] (0..9).
 * LibreWXR sources are built at fallback time (their tile templates
 * embed the per-frame `path` from the maps JSON, known only after
 * metadata arrives) — unlike the IEM sources, whose layer names are
 * static. See `RadarEngineController` for the build/teardown order
 * (layer first, then source — a source still referenced by a layer
 * cannot simply be removed).
 */
fun radarLibreWxrFrameSourceId(index: Int): String = "radar-librewxr-frame-$index"

/** Raster layer id for LibreWXR fallback loop frame [index] (0..9). */
fun radarLibreWxrFrameLayerId(index: Int): String = "radar-librewxr-frame-layer-$index"

/** Raster layer id for an MRMS ImageSource slot. */
fun radarMrmsLayerId(slotId: String): String = "radar-mrms-layer-$slotId"

/** Raster layer id for a satellite WMS ImageSource slot (Phase 3). */
fun radarSatWmsLayerId(slotId: String): String = "radar-sat-wms-layer-$slotId"

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
 * `{z}/{y}/{x}` (y before x, unlike XYZ); [gibsTime] is the
 * full UTC timestamp — the initial fallback value at style build, or
 * the probed WMS scan time when the activity rebuilds the source for
 * the WMTS fallback (Phase 3).
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
 * then the future frame layers ([futureFrameCount] of them — the
 * full range the activity built, ~74 for 24 h, ~97 for 48 h), then
 * the MRMS slots on top. The activity adds layers in exactly this
 * order (`addLayer` appends).
 */
fun layerDrawOrder(futureFrameCount: Int): List<String> {
    val order = mutableListOf<String>()
    order.add("radar-background")
    order.add(RADAR_ENGINE_BASEMAP_LAYER_ID)
    order.add(RADAR_ENGINE_SAT_VIS_LAYER_ID)
    order.add(RADAR_ENGINE_SAT_IR_LAYER_ID)
    for (i in 0 until RADAR_ENGINE_FRAME_COUNT) order.add(radarLiveFrameLayerId(i))
    for (i in 0 until futureFrameCount) order.add(radarFutureFrameLayerId(i))
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

/**
 * The 1.0× MRMS bbox around a centre: 6° lon × 5° lat — the viewport
 * at zoom 8, and exactly [RADAR_ENGINE_MRMS_BBOX_1X] around Cincinnati
 * ([RADAR_ENGINE_CENTER_LAT], [RADAR_ENGINE_CENTER_LON]). The activity
 * expands it by [RADAR_ENGINE_MRMS_BBOX_FACTOR] for the request, and
 * re-derives it around the new camera target on a pan past the margin
 * (a bbox change is a new generation: every MRMS URL changes).
 */
fun mrmsBboxForCentre(centreLat: Double, centreLon: Double): RadarBbox = RadarBbox(
    westLon = centreLon - 3.0,
    southLat = centreLat - 2.5,
    eastLon = centreLon + 3.0,
    northLat = centreLat + 2.5
)

/** `bbox` query-param form for MRMS `exportImage` (`bboxSR=4326`). */
fun radarBboxToParam(bbox: RadarBbox): String =
    "${bbox.westLon},${bbox.southLat},${bbox.eastLon},${bbox.northLat}"
