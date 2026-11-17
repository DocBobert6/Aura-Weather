package com.example.ui.radar.engine

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.domain.model.Place
import com.example.data.mapper.FutureFrameAvailability
import com.example.data.mapper.FutureTailResult
import com.example.data.mapper.capFutureFrames
import com.example.data.mapper.futureCostTextFromBytes
import com.example.data.mapper.LIBRE_WXR_MAPS_URL
import com.example.data.mapper.LIBRE_WXR_TILE_SIZE_512
import com.example.data.mapper.LibreWxrFrame
import com.example.data.mapper.LibreWxrMaps
import com.example.data.mapper.LonLatBbox
import com.example.data.mapper.MRMS_SERVICE_INFO_URL
import com.example.data.mapper.RadarFrame
import com.example.data.mapper.RadarRange
import com.example.data.mapper.RadarSourceKind
import com.example.data.mapper.centreFirstTileOffsets
import com.example.data.mapper.fallbackAfter
import com.example.data.mapper.gibsTimestamp
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.latToTileY
import com.example.data.mapper.libreWxrLoopFrames
import com.example.data.mapper.libreWxrTileTemplate
import com.example.data.mapper.libreWxrTileUrl
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.mrmsExportImageUrl
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.mrmsLiveFrames
import com.example.data.mapper.parseLibreWxrMaps
import com.example.data.mapper.parseMrmsTimeExtent
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarRangeFrames
import java.time.Instant
import java.util.Locale
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.PI
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/**
 * Phase 5 — the MapLibre map engine + the rebuilt radar loop, now
 * the Radar tab itself (it was the standalone debug activity until
 * the Phase 5 cutover; [RadarEngineTab] hosts it in the nav shell).
 * The controller owns the machinery; the tab owns the MapView, the
 * lifecycle forwarding and the place wiring.
 *
 * What this controller owns (the screen file is chrome only):
 * - `MapView` in `AndroidView`, full lifecycle forwarding from the
 *   host activity (the tab creates the view, after
 *   `MapLibre.getInstance`).
 * - The raster-only style (JSON from [buildRadarStyleJson]) + the
 *   programmatically added sources, in draw order: 10 volatile live
 *   IEM frame sources (shape (a), prefetch OFF), the full future
 *   HRRR ranges as volatile sources (Phase 4, one per frame),
 *   2 MRMS ImageSource
 *   slots (bitmaps via `setImage`, 150 ms opacity crossfade), and —
 *   built at fallback time — 10 volatile LibreWXR 512 sources.
 *   Volatile/prefetch are set in Kotlin at source creation — never
 *   the `"volatile": true` JSON flag.
 * - The loop itself, in two [RadarLoopStore]s (live + future),
 *   driven by ONE [CoalescingFetcher] and a dwell ticker:
 *   metadata (MRMS + LibreWXR, in parallel) BEFORE the first
 *   prefetch burst; source lock for the whole loop; no-timeout
 *   advance (hold N and say so); 5-min refresh + on resume; a Live
 *   slow/unavailable state with a retry that re-requests in-flight
 *   work AND re-resolves metadata. The store is the source of truth
 *   for loop state — this activity never keeps its own copy.
 * - MRMS primary as wide-margin (1.5×) single images, decoded ON
 *   DEMAND from fetched bytes (at most 2 bitmaps alive — the two
 *   slots); `setUri` is never used for loop frames (it would
 *   double-fetch and reintroduce the cache question).
 * - Satellite (Phase 3): one WMS single-image JPEG per layer visit
 *   (viewport x 1.5, ~0.85 MB VIS / ~0.33 MB IR), drawn through two
 *   `ImageSource` slots with the same 150 ms crossfade as MRMS; the
 *   scan time is probed per layer on the WMS path itself (direct
 *   256x256 GetMaps, newest non-blank of 5 per round, 2 rounds max,
 *   10-min TTL cache), blank-checked by pixel sampling before display,
 *   held across refresh (the old image stays visible until the new
 *   one decodes), and the WMTS tile grid — rebuilt with the resolved
 *   scan time — is the fallback when WMS fails. Chrome is honest:
 *   "LOADING SATELLITE…" while resolving, "SATELLITE UNAVAILABLE" +
 *   RETRY only when there is no image to hold, and "GIBS WMS" /
 *   "GIBS TILES" + actual fetched bytes per switch.
 * - Pan/zoom/rotate are MapLibre's own gestures (on by default);
 *   the zoom/recenter buttons drive the camera directly. A pan past
 *   the 0.5° margin re-resolves the MRMS loop with a new bbox; small
 *   pans are free. Zoom is bounded 5..12 by the map's own min/max zoom
 *   preferences — a pinch-out stops at the floor during the gesture
 *   and stays where the fingers left it (no post-settle snap-back).
 *   On satellite the same settle retargets the WMS image (pan past
 *   the margin, or a zoom change); in tiles-fallback mode a pan
 *   needs nothing — MapLibre fetches the new area itself.
 * Centre is the selected place ([onPlaceChanged] moves it on every
 * place change); the tab only hosts the engine once a place exists,
 * so there is no hardcoded default centre.
 *
 * What Phase 2 deliberately does NOT do (later phases own it):
 * - The loop never re-promotes to a higher source on refresh: a
 *   fallback stays fallen-back until the user hits RETRY, which
 *   re-resolves metadata and takes the best source available then.
 *   (Re-promoting mid-session would swap palettes under a playing
 *   loop — the source lock exists to prevent exactly that.)
 *
 * Phase 4 — the full future ranges (HRRR) through the same frame
 * store: the 24 h (~74 frames) and 48 h (~97 frames) ranges, no
 * window cap, one volatile source per frame built lazily on the
 * first future visit. The 24 h list is a prefix of the 48 h list
 * (same HRRR run), so a range switch only extends the sources.
 * A future layer starts with `visibility: none` — its tiles only
 * load once its frame is proved ready, so creating 97 sources
 * does not fire a 97-frame tile burst on range entry (a hidden
 * layer produces no render data, so its source gets no tile
 * updates; `Source::update()` takes the layer properties).
 * The tail cap: every future frame's centre 3x3 proving fetch is
 * also its served/failed signal (bytes seen = SERVED, all failed
 * = FAILED, unprobed = UNKNOWN); [capFutureFrames] runs on every
 * settle and the store's list is capped to the longest leading
 * run before the first failure, with the end note
 * ("FORECAST ENDS ABOUT 11:00 UTC") in the chrome while capped,
 * and the unavailable state + RETRY when nothing serves. Cost is
 * the range's actual fetched bytes via [futureCostTextFromBytes]
 * — no "THEN CACHED": nothing caches a future visit (no fetcher
 * disk cache, volatile sources, IEM max-age=300).
 *
 * Readiness, and what proves it (the store never trusts the map):
 * - MRMS: the fetcher's bytes in hand, blank-checked by the store
 *   against the metadata extent (blank + in-extent = a ready, empty
 *   clear-sky frame; blank + out-of-extent = a stale failure).
 * - Tiled (LibreWXR / IEM / HRRR): the fetcher's tile bodies for the
 *   viewport, centre-first. A frame is ready when every tile in its
 *   proving set has settled AND at least one tile returned bytes; a
 *   frame whose tiles ALL failed (or 404'd with none returned) is
 *   failed, not ready. MapLibre's `addOnDidBecomeIdleListener` is
 *   used ONLY as the pan-settle signal, and never marks a frame
 *   ready — there is no public per-source load API in 13.7.1.
 */
class RadarEngineController(
    private val context: Context,
    initialPlace: Place
) {

    companion object {
        private const val TAG = "RadarEngine"

        /** IEM/HRRR proving grid (256 px tiles): 7 x 11 = 77 tiles — the
         *  old grid cap. Covers the viewport + margin at zoom 9, the
         *  old ceiling; higher zooms cover less ground, so the same
         *  grid covers them too, under either reading of the viewport
         *  size (dp or physical px). */
        private const val PROVING_TILES_X: Int = 7
        private const val PROVING_TILES_Y: Int = 11

        /** LibreWXR 512 proving grid at zoom−1: 5 x 7 = 35 tiles — the
         *  same ground as the 7x11 256-grid, halved per axis (one 512
         *  tile at z−1 covers the 2x2 256 tiles at z). */
        private const val LIBRE_WXR_PROVING_TILES_X: Int = 4
        private const val LIBRE_WXR_PROVING_TILES_Y: Int = 6

        /** Future-range (HRRR) proving grid: centre 3x3 only (9 tiles)
         *  — see the file header for why the future loop proves less. */
        private const val FUTURE_PROVING_TILES_X: Int = 3
        private const val FUTURE_PROVING_TILES_Y: Int = 3

        /** Tiled look-ahead window: the current frame + this many ahead. */
        private const val TILED_LOOK_AHEAD: Int = 5
    }

    /** The controller's own scope (Main.immediate, like the
     *  activity's lifecycleScope was): the ticker, refresh timer,
     *  decodes and resolutions all run here; [destroy] cancels it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The place the engine is centred on (never a hardcoded
     *  default): the tab only hosts the engine once a place exists,
     *  and [onPlaceChanged] moves this on every place change. */
    private var placeCentre: LatLng = LatLng(initialPlace.point.lat, initialPlace.point.lon)
    private var placeId: String = initialPlace.id

    /** Low-memory forwarding (was the activity's onLowMemory). */
    private val lowMemoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() {
            mapView?.onLowMemory()
        }
        override fun onTrimMemory(level: Int) = Unit
    }

    /** Guard: [start] runs once per controller lifetime. */
    private var started: Boolean = false

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null

    private val liveFrameLayers = mutableListOf<RasterLayer>()
    private val futureFrameLayers = mutableListOf<RasterLayer>()
    private val libreWxrFrameLayers = mutableListOf<RasterLayer>()
    private var satVisLayer: RasterLayer? = null
    private var satIrLayer: RasterLayer? = null
    private var mrmsSourceA: ImageSource? = null
    private var mrmsSourceB: ImageSource? = null
    private var mrmsLayerA: RasterLayer? = null
    private var mrmsLayerB: RasterLayer? = null
    private var satSourceA: ImageSource? = null
    private var satSourceB: ImageSource? = null
    private var satLayerA: RasterLayer? = null
    private var satLayerB: RasterLayer? = null

    /** Era-accurate radar blip (0.4.0): the SymbolLayer drawing the
     *  dot-plus-ring icon at the selected place (see [RadarBlip.kt]
     *  for the blink rule). Null until the style loads. */
    private var blipLayer: SymbolLayer? = null

    /** Esri reference (city labels) layer (0.4.1): the RasterLayer
     *  drawing the `Reference/World_Boundaries_and_Places` tiles
     *  above every radar family, below the blip. Null until the
     *  style loads (or when adding it failed). */
    private var referenceLayer: RasterLayer? = null

    /** Is the reference layer currently visible ([setReferenceLayerVisible]
     *  is a no-op when nothing changed — it runs on every
     *  [refreshDisplay]). Starts false: the layer is added visible,
     *  and the add site sets this true with it. */
    private var referenceLayerVisible: Boolean = false

    /** Last blip display state applied to the layer (updateBlip is a
     *  no-op when nothing changed — it runs on every chrome refresh). */
    private var lastBlipDisplay: RadarBlipDisplay? = null

    // -- Satellite (Phase 3) -------------------------------------------------

    /** Satellite display path: WMS single image, or the WMTS tile grid. */
    private enum class SatMode { WMS, TILES, NONE }

    /** Satellite chrome state (drives the status / unavailable panels). */
    private enum class SatState { LOADING, SHOWING, UNAVAILABLE }

    /** Why a satellite resolution is running. */
    private enum class SatResolveReason { LAYER_SWITCH, RETARGET, REFRESH, RETRY }

    /** Own generation counter for satellite fetches (never the loop's):
     *  byte accounting and the negative cache are satellite-scoped, so
     *  the cost text reports this visit's bytes, probes included. */
    private var satGenerationId: Long = 0L

    /** Current satellite mode / chrome state (per layer-visit). */
    private var satMode: SatMode = SatMode.NONE
    private var satState: SatState = SatState.LOADING

    /** Resolved WMS scan times, one entry per layer, 10-min TTL. */
    private val satTimestampCache = WmsTimestampCache()

    /** Fetched WMS JPEG bytes, keyed by layer + scan time + bbox
     *  ([satKey]) — a switch-back to a layer whose timestamp is still
     *  fresh is a decode-only operation (no network). Capped at the 2
     *  most recent entries (one per layer); a bbox change clears the
     *  entries for that layer. */
    private val satBytes = LinkedHashMap<String, ByteArray>()

    /** The bbox / quad the current satellite image was fetched for. */
    private var satBbox: WmsBbox3857? = null
    private var satQuad: LatLngQuad? = null

    /** Camera the current satellite image's bbox was built on
     *  (pan-margin origin) and the Int zoom it was computed at. */
    private var satBboxCentre: LatLng? = null
    private var satBboxZoom: Int = -1

    /** Which layer the currently displayed satellite image belongs to
     *  (null when the tiles fallback or nothing is showing). */
    private var satShowingLayer: WmsSatelliteLayer? = null

    /** Which layer+time+bbox key each WMS slot's bitmap belongs to
     *  (null = no bitmap yet), and which key is decoding into it. */
    private val satSlotKey = arrayOf<String?>(null, null)
    private val satDecodingKey = arrayOf<String?>(null, null)

    /** The bitmaps currently held by the WMS slots (for recycling on
     *  supersede — safe: `ImageSource.setImage` copies the pixels
     *  into native storage, verified against the 13.7.1 SDK source). */
    private val satSlotBitmaps = arrayOf<Bitmap?>(null, null)

    /** Which WMS slot's layer is currently at opacity 1 (0 or 1). */
    private var satVisibleSlot: Int = 0

    /** Is a WMS slot currently visible (either layer at opacity 1)? */
    private var satSlotsVisible: Boolean = false

    /** The in-flight satellite resolution (cancelled by a newer one). */
    private var satResolveJob: Job? = null

    /** Sequence guard for satellite resolutions (a superseded decode
     *  never places its bitmap). */
    private var satResolveSeq: Long = 0L

    // -- The loop ----------------------------------------------------------

    private val fetcher = CoalescingFetcher()
    private val liveStore = RadarLoopStore()
    private val futureStore = RadarLoopStore()

    /** The frames the style's future sources were built from (with
     *  their HRRR layer names). The store's frames carry no layer
     *  names, so this list is the source of truth for rebuilds —
     *  and, for the tail cap, the full range list the availability
     *  signals are indexed against (the store's list is a prefix of
     *  it once capped). */
    private lateinit var futureSourceFrames: List<RadarFrame>
    private var futureSourcesBuiltFor: List<RadarFrame>? = null

    /** Per-frame served/failed signal for the future range, parallel
     *  to [futureSourceFrames] (the FULL range list, not the store's
     *  possibly-capped list): SERVED when the frame's centre-3x3
     *  proving fetch returned bytes, FAILED when every tile failed,
     *  UNKNOWN until probed. Re-run [capFutureFrames] on every
     *  change; reset to all-UNKNOWN on every new generation. */
    private var futureAvailability = mutableListOf<FutureFrameAvailability>()

    /** Last tail-cap result for the future range (null before the
     *  first probe settles). Its [FutureTailResult.endNote] is the
     *  chrome's end note while capped. */
    private var futureTail: FutureTailResult? = null

    /** Which future range the future store's generation was resolved
     *  for (null before the first future resolution). A range switch
     *  re-resolves even when a generation exists. */
    private var futureResolvedRange: EngineRange? = null

    /** The LibreWXR frames of the current live generation (for the
     *  source templates + proving URLs), in loop order. Empty unless
     *  the live source is LIBRE_WXR. */
    private var libreWxrLoopFrames: List<LibreWxrFrame> = emptyList()
    private var libreWxrSourcesBuiltFor: List<LibreWxrFrame>? = null

    /** Last good LibreWXR maps document (kept for a later fallback). */
    private var libreWxrMaps: LibreWxrMaps? = null

    /** Current MRMS metadata extent (epoch ms), or null before the
     *  first successful metadata fetch. Drives `timeWithinExtent`. */
    private var mrmsExtent: Pair<Long, Long>? = null

    /** Fetched MRMS image bytes, keyed by frame valid time (epoch ms)
     *  — NOT by index, so a refresh that carries readiness by valid
     *  time keeps its bytes. [mrmsBytesBbox] is the bbox those bytes
     *  were fetched for; a bbox change clears the map. */
    private val mrmsBytes = mutableMapOf<Long, ByteArray>()
    private var mrmsBytesBbox: RadarBbox? = null

    /** The 1.5x MRMS bbox of the current live generation. */
    private var mrmsBbox: RadarBbox =
        expandBbox(RADAR_ENGINE_MRMS_BBOX_1X, RADAR_ENGINE_MRMS_BBOX_FACTOR)
    private var mrmsQuad: LatLngQuad = quadFor(mrmsBbox)

    /** Which loop frame index each MRMS slot's bitmap belongs to
     *  (-1 = no bitmap yet). Bookkeeping only: the visible slot keeps
     *  showing its bitmap across a generation change (hold) until a
     *  new image is placed in a slot. */
    private val slotFrameIndex = intArrayOf(-1, -1)

    /** Which frame index is being decoded into each slot (-1 = idle). */
    private val decodingFrame = intArrayOf(-1, -1)
    private val decodingJobs = mutableMapOf<Int, Job>()

    /** Which slot's layer is currently at opacity 1 (0 or 1). */
    private var visibleSlot: Int = 0

    /** Is an MRMS slot currently visible (either layer at opacity 1)? */
    private var mrmsSlotsVisible: Boolean = false

    /** Visible layer index per tiled family (-1 = none showing). */
    private var visibleLiveIemIndex: Int = -1
    private var visibleLibreWxrIndex: Int = -1
    private var visibleFutureIndex: Int = -1

    /** Camera the live generation's bbox was built on (pan-margin origin). */
    private var liveGenerationCentre: LatLng? = null

    /** Uptime ms each store's generation started (slow-budget origin). */
    private var liveGenerationStartedAtMs: Long = 0L
    private var futureGenerationStartedAtMs: Long = 0L

    /** Generation id the slow budget already fired for, per store
     *  (-1 = not fired): the budget action runs once per generation. */
    private var liveSlowBudgetFiredGen: Long = -1L
    private var futureSlowBudgetFiredGen: Long = -1L

    /** Uptime ms the visible frame was shown (dwell origin). */
    private var frameShownAtMs: Long = 0L

    /** Uptime ms the last refresh completed (resume-refresh guard). */
    private var lastRefreshMs: Long = 0L
    private var refreshInFlight: Boolean = false
    private var activityResumed: Boolean = false

    /** Was the loop playing before the current scrub drag started? */
    private var wasPlayingBeforeScrub: Boolean = false

    /** Poke channel: readiness / play / scrub / range changes wake the ticker. */
    private val tickerPoke = Channel<Unit>(Channel.CONFLATED)

    // UI state (the screen file is chrome only; these are its inputs).
    var selectedLayer by mutableStateOf(EngineLayer.Radar)
        private set
    var selectedRange by mutableStateOf(EngineRange.Live)
        private set
    var uiFrames by mutableStateOf<List<RadarFrame>>(emptyList())
        private set
    var uiCurrentIndex by mutableStateOf(0)
        private set
    var uiIsPlaying by mutableStateOf(false)
        private set
    var uiPlayEnabled by mutableStateOf(false)
        private set
    var uiStatusText by mutableStateOf<String?>(null)
        private set
    var uiUnavailableText by mutableStateOf<String?>(null)
        private set
    var uiSourceLabel by mutableStateOf<String?>(null)
        private set
    var uiCostText by mutableStateOf<String?>(null)
        private set
    var uiEndNote by mutableStateOf<String?>(null)
        private set

    private fun activeStore(): RadarLoopStore =
        if (selectedRange == EngineRange.Live) liveStore else futureStore

    private fun currentTarget(): LatLng =
        mapLibreMap?.cameraPosition?.target ?: placeCentre

    private fun currentZoom(): Double =
        mapLibreMap?.cameraPosition?.zoom ?: RADAR_ENGINE_ZOOM

    private fun mrmsUrl(timeMs: Long): String = mrmsExportImageUrl(
        LonLatBbox(mrmsBbox.westLon, mrmsBbox.southLat, mrmsBbox.eastLon, mrmsBbox.northLat),
        timeMs,
        RADAR_ENGINE_MRMS_IMAGE_WIDTH_PX,
        RADAR_ENGINE_MRMS_IMAGE_HEIGHT_PX
    )

    private fun quadFor(bbox: RadarBbox): LatLngQuad = LatLngQuad(
        LatLng(bbox.northLat, bbox.westLon),
        LatLng(bbox.northLat, bbox.eastLon),
        LatLng(bbox.southLat, bbox.eastLon),
        LatLng(bbox.southLat, bbox.westLon)
    )

    /**
     * Start the engine on the tab's [MapView] (created by
     * [RadarEngineTab] after `MapLibre.getInstance`). The camera opens
     * on the place; the loop resolves and the ticker + refresh timer
     * start. Runs once per controller lifetime; [destroy] undoes it.
     */
    fun start(view: MapView) {
        if (started) return
        started = true
        mapView = view
        context.registerComponentCallbacks(lowMemoryCallbacks)
        val now = Instant.now()
        // Future sources are built lazily on the first future visit
        // (Phase 4: ~74/97 sources for the full ranges — not at
        // startup for a range the user may never open).
        futureSourceFrames = emptyList()
        frameShownAtMs = SystemClock.uptimeMillis()

        view.addOnDidBecomeIdleListener { onMapIdle() }
        view.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder()
                .target(placeCentre).zoom(RADAR_ENGINE_ZOOM).build()
            // Zoom bounds live on the map itself, so a pinch-out
            // stops at the floor DURING the gesture — the controller
            // never moves the camera after a user gesture (the
            // auto-zoom bug, 2026-11-15: onMapIdle used to snap any
            // zoom below 7 back to 7 on settle, and 7 is the opening
            // zoom, so a radar pinch-out could never move at all).
            map.setMinZoomPreference(RADAR_ENGINE_ZOOM_MIN.toDouble())
            map.setMaxZoomPreference(RADAR_ENGINE_ZOOM_MAX.toDouble())
            // GIBS time here is ONLY the initial value baked into the
            // style JSON — it is never displayed. Phase 3 resolves the
            // real per-layer scan time by probing (WmsSatellite)
            // before first satellite display, and rebuilds the tile
            // sources with the probed time on fallback.
            val gibsTime = gibsTimestamp(now)
            map.setStyle(
                Style.Builder().fromJson(buildRadarStyleJson(gibsTime, gibsTime)),
                Style.OnStyleLoaded { style ->
                    loadedStyle = style
                    Log.i(TAG, "style loaded: raster-only JSON OK")
                    addEngineSourcesAndLayers(style)
                    syncSourcesForCurrentGeneration()
                    applyLayerAndFrame()
                    refreshUiState()
                    if (selectedLayer != EngineLayer.Radar) {
                        wmsLayerFor(selectedLayer)?.let {
                            resolveSatellite(it, SatResolveReason.LAYER_SWITCH)
                        }
                    }
                }
            )
        }
        resolveLiveLoop()
        startTicker()
        startRefreshTimer()
    }

    // -- MapView lifecycle (forwarded by RadarEngineTab from the host
    //    activity's lifecycle; the tab calls [destroy] when it leaves
    //    composition) -------------------------------------------------------

    fun onStart() {
        mapView?.onStart()
    }

    fun onResume() {
        mapView?.onResume()
        activityResumed = true
        pokeTicker()
        // Refresh on resume when the loop is stale (5-min cadence);
        // the entry resolution counts as a refresh, so the first
        // onResume after start does not refetch.
        if (liveStore.generationId > 0L &&
            SystemClock.uptimeMillis() - lastRefreshMs > RADAR_ENGINE_LOOP_REFRESH_MS
        ) {
            scope.launch { refreshLoops() }
        }
    }

    fun onPause() {
        activityResumed = false
        mapView?.onPause()
    }

    fun onStop() {
        mapView?.onStop()
    }

    /**
     * Teardown: stop the fetcher, destroy the MapView, cancel the
     * controller scope (ticker, refresh timer, decodes, resolutions).
     * The tab calls this exactly once, from onDispose. (MapView
     * instance state is not saved: the camera is restored from the
     * place on the next start.)
     */
    fun destroy() {
        fetcher.close()
        try {
            context.unregisterComponentCallbacks(lowMemoryCallbacks)
        } catch (e: Exception) {
            Log.w(TAG, "unregistering low-memory callbacks failed: ${e.javaClass.simpleName}")
        }
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
        scope.cancel()
    }

    /**
     * Retarget the engine to a new place: the camera recentres (the
     * old screen recentred on a place switch too), the MRMS loop
     * re-resolves for the new bbox when it is live, and the
     * pan-margin origin moves so a tiled loop does not refetch on
     * the next idle. The satellite retarget rides the camera move's
     * idle settle. No-op for the place already centred on. Called by
     * RadarEngineTab on every place change; the MapView and the loop
     * survive it.
     */
    fun onPlaceChanged(place: Place) {
        if (place.id == placeId &&
            place.point.lat == placeCentre.latitude &&
            place.point.lon == placeCentre.longitude
        ) return
        placeId = place.id
        placeCentre = LatLng(place.point.lat, place.point.lon)
        // The blip is geo-anchored to the place: move its point with
        // it (no-op before the style loads — the source is created at
        // the current place then).
        updateBlipPosition()
        // No map yet: the camera + loop follow when it is ready
        // (start uses placeCentre).
        val map = mapLibreMap ?: return
        map.cameraPosition = CameraPosition.Builder()
            .target(placeCentre).zoom(RADAR_ENGINE_ZOOM)
            .bearing(0.0).tilt(0.0).build()
        if (selectedLayer == EngineLayer.Radar &&
            selectedRange == EngineRange.Live &&
            liveStore.generationId != 0L &&
            liveStore.source == RadarSourceKind.MRMS
        ) {
            reresolveLiveForBbox()
        } else if (liveStore.generationId != 0L) {
            // Tiled sources follow the camera themselves; only the
            // pan-margin origin needs to move to the new place.
            liveGenerationCentre = placeCentre
        }
    }

    // -- Style setup ---------------------------------------------------------

    private fun addEngineSourcesAndLayers(style: Style) {
        // Shape (a): 10 per-frame volatile live sources + layers.
        // Volatile = never persist to the ambient cache; prefetch
        // delta 0 = no low-res parent prefetch on radar sources
        // (Robert's data budget). Newest frame visible once ready.
        try {
            for (i in 0 until RADAR_ENGINE_FRAME_COUNT) {
                val source = RasterSource(
                    radarLiveFrameSourceId(i),
                    TileSet("2.2.0", iemFrameTileTemplate(iemLayerNameFor(i))),
                    256
                )
                source.isVolatile = true
                source.prefetchZoomDelta = 0
                style.addSource(source)
                val layer = RasterLayer(radarLiveFrameLayerId(i), radarLiveFrameSourceId(i))
                    .withProperties(
                        PropertyFactory.rasterOpacity(0f)
                    )
                style.addLayer(layer)
                liveFrameLayers.add(layer)
            }
            Log.i(TAG, "added $RADAR_ENGINE_FRAME_COUNT volatile live (IEM) frame sources")
        } catch (e: Exception) {
            Log.w(TAG, "adding live frame sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Future HRRR sources are NOT built here: they are built
        // lazily on the first future visit (Phase 4 — ~74/97 sources
        // for the full ranges, via [rebuildFutureSources], which the
        // style-loaded callback re-runs through
        // [syncSourcesForCurrentGeneration] when a resolution raced
        // ahead of the style load). The 24 h and 48 h ranges share
        // the HRRR run prefix, so one built set serves both pills.
        // NOTE: no `rebuildFutureSources` call here on purpose.

        // MRMS two-slot ImageSources (wide-margin bbox, png32
        // exportImage). Built with a 1x1 transparent bitmap — never a
        // placeholder URI (setUri would fetch a throwaway image and
        // show a stale frame); real bitmaps arrive via setImage once
        // the loop resolves and the first frame's bytes land.
        try {
            val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            mrmsSourceA = ImageSource(
                RADAR_ENGINE_MRMS_SLOT_A, mrmsQuad, placeholder
            )
            mrmsSourceB = ImageSource(
                RADAR_ENGINE_MRMS_SLOT_B, mrmsQuad, placeholder
            )
            style.addSource(mrmsSourceA!!)
            style.addSource(mrmsSourceB!!)
            mrmsLayerA = RasterLayer(
                radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_A), RADAR_ENGINE_MRMS_SLOT_A
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            mrmsLayerB = RasterLayer(
                radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_B), RADAR_ENGINE_MRMS_SLOT_B
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            style.addLayer(mrmsLayerA!!)
            style.addLayer(mrmsLayerB!!)
            Log.i(TAG, "added MRMS ImageSources A/B, bbox ${radarBboxToParam(mrmsBbox)} (1.5x)")
        } catch (e: Exception) {
            Log.w(TAG, "adding MRMS sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Satellite WMS two-slot ImageSources (Phase 3): one pair
        // serves both layers — VIS and IR are never visible at once,
        // so a layer switch is a new fetch into the idle slot. Same
        // 1x1 transparent placeholder as MRMS, never a placeholder URI.
        // Added after the MRMS slots, so plain addLayer puts them on
        // top (the topmost satellite content; the radar layers sit
        // above them only via the fallback's addLayerBelow).
        try {
            val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            satSourceA = ImageSource(
                RADAR_ENGINE_SAT_WMS_SLOT_A, mrmsQuad, placeholder
            )
            satSourceB = ImageSource(
                RADAR_ENGINE_SAT_WMS_SLOT_B, mrmsQuad, placeholder
            )
            style.addSource(satSourceA!!)
            style.addSource(satSourceB!!)
            satLayerA = RasterLayer(
                radarSatWmsLayerId(RADAR_ENGINE_SAT_WMS_SLOT_A), RADAR_ENGINE_SAT_WMS_SLOT_A
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            satLayerB = RasterLayer(
                radarSatWmsLayerId(RADAR_ENGINE_SAT_WMS_SLOT_B), RADAR_ENGINE_SAT_WMS_SLOT_B
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            style.addLayer(satLayerA!!)
            style.addLayer(satLayerB!!)
            Log.i(TAG, "added satellite WMS ImageSources A/B")
        } catch (e: Exception) {
            Log.w(TAG, "adding satellite WMS sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Basemap read-back (JSON-declared source, prefetch default ON).
        try {
            val basemap = style.getSourceAs<RasterSource>(RADAR_ENGINE_BASEMAP_SOURCE_ID)
            satVisLayer = style.getLayer(RADAR_ENGINE_SAT_VIS_LAYER_ID) as? RasterLayer
            satIrLayer = style.getLayer(RADAR_ENGINE_SAT_IR_LAYER_ID) as? RasterLayer
            Log.i(
                TAG,
                "basemap read-back: isVolatile=${basemap?.isVolatile} " +
                    "prefetchZoomDelta=${basemap?.prefetchZoomDelta}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "basemap read-back failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Esri reference (city labels + boundaries, 0.4.1): restored
        // from the pre-rebuild screen — the rebuild's style JSON never
        // carried it over, and it cannot go in the JSON (JSON layers
        // draw first, so it would sit UNDER the radar). Added here,
        // after the radar layers and the satellite WMS pair, so it
        // draws above every radar family; the blip goes on top of it
        // (addBlipSourceAndLayer, below). NOT volatile: it is a static
        // basemap-style layer and SHOULD cache (the 24 h TTL for
        // server.arcgisonline.com already covers it). Visibility
        // follows the pure rule [referenceLayerVisibleFor]: shown on
        // radar ([refreshDisplay]), hidden on satellite
        // ([applyLayerAndFrame]).
        try {
            val referenceSource = RasterSource(
                RADAR_ENGINE_REFERENCE_SOURCE_ID,
                TileSet("2.2.0", RADAR_ENGINE_REFERENCE_TILE_TEMPLATE),
                256
            )
            style.addSource(referenceSource)
            referenceLayer = RasterLayer(
                RADAR_ENGINE_REFERENCE_LAYER_ID, RADAR_ENGINE_REFERENCE_SOURCE_ID
            ).withProperties(PropertyFactory.visibility(Property.VISIBLE))
            style.addLayer(referenceLayer!!)
            referenceLayerVisible = true
            Log.i(TAG, "added Esri reference (city labels) layer")
        } catch (e: Exception) {
            Log.w(TAG, "adding reference layer failed: ${e.javaClass.simpleName}: ${e.message}")
            referenceLayer = null
        }

        // Era-accurate radar blip (0.4.0): added last, so it starts
        // on top of every radar/satellite layer — the marker draws
        // over the radar, under the CRT filter. Sources built later
        // (future HRRR, LibreWXR fallback) land above it via plain
        // addLayer, so they call [ensureBlipOnTop] after adding.
        addBlipSourceAndLayer(style)
    }

    /**
     * Rebuild the future HRRR sources for [frames] (their layer names
     * are the HRRR run's, so a new run means new sources). Deferred
     * when the style is not loaded yet — the style-loaded callback
     * re-runs this via [syncSourcesForCurrentGeneration]. Incremental:
     * - same list as built: no-op;
     * - [frames] a prefix of the built list (48 h -> 24 h): no-op —
     *   the built sources already cover it, the extras just sit
     *   unused (never proved, never visible);
     * - [frames] extends the built list (24 h -> 48 h, same run):
     *   only the new tail's sources are added;
     * - anything else (a new HRRR run, or a list that is not a
     *   prefix of the built one): full remove + re-add.
     * Every new layer starts with `visibility: none` + opacity 0:
     * its tiles only load once its frame is proved ready (see
     * [setFutureLayerVisible]) — creating ~97 sources must not fire
     * a ~97-frame tile burst on range entry.
     */
    private fun rebuildFutureSources(frames: List<RadarFrame>) {
        futureSourceFrames = frames
        val style = loadedStyle ?: return
        val built = futureSourcesBuiltFor
        if (frames == built) return
        if (built != null && frames.size <= built.size &&
            built.subList(0, frames.size) == frames
        ) return // already covered by the built superset
        try {
            if (built != null && frames.size > built.size &&
                frames.subList(0, built.size) == built
            ) {
                // Prefix extension: only add the new tail.
                for (i in built.size until frames.size) {
                    addFutureSource(style, i, frames[i])
                }
                futureSourcesBuiltFor = frames
                syncFutureLayerVisibility()
                // New layers land above the reference layer and the
                // blip — move both back on top, reference first.
                ensureReferenceLayerAboveRadar()
                ensureBlipOnTop()
                Log.i(
                    TAG,
                    "extended future (HRRR) sources: ${built.size} -> ${frames.size}"
                )
                return
            }
            for ((i, layer) in futureFrameLayers.withIndex()) {
                try {
                    style.removeLayer(layer.id)
                } catch (e: Exception) {
                    Log.w(TAG, "removing future layer $i failed: ${e.javaClass.simpleName}")
                }
                try {
                    style.removeSource(radarFutureFrameSourceId(i))
                } catch (e: Exception) {
                    Log.w(TAG, "removing future source $i failed: ${e.javaClass.simpleName}")
                }
            }
            futureFrameLayers.clear()
            visibleFutureIndex = -1
            for ((i, frame) in frames.withIndex()) {
                addFutureSource(style, i, frame)
            }
            futureSourcesBuiltFor = frames
            syncFutureLayerVisibility()
            // New layers land above the reference layer and the
            // blip — move both back on top, reference first.
            ensureReferenceLayerAboveRadar()
            ensureBlipOnTop()
            Log.i(TAG, "added ${frames.size} volatile future (HRRR) frame sources")
        } catch (e: Exception) {
            Log.w(TAG, "adding future frame sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Add one future HRRR source + layer at [index], hidden until
     *  its frame is proved ready ([setFutureLayerVisible]). */
    private fun addFutureSource(style: Style, index: Int, frame: RadarFrame) {
        val source = RasterSource(
            radarFutureFrameSourceId(index),
            TileSet("2.2.0", hrrrFrameTileTemplate(frame.layerName)),
            256
        )
        source.isVolatile = true
        source.prefetchZoomDelta = 0
        style.addSource(source)
        val layer = RasterLayer(radarFutureFrameLayerId(index), radarFutureFrameSourceId(index))
            .withProperties(
                PropertyFactory.rasterOpacity(0f),
                PropertyFactory.visibility(Property.NONE)
            )
        style.addLayer(layer)
        // Index-aligned with the frame list: the append path adds in
        // order from the built size, the rebuild path clears first.
        // Out-of-order would corrupt show/hide by index, so fail
        // loudly (the caller logs it) instead of filling a gap.
        check(index == futureFrameLayers.size) {
            "future layer index $index out of order (have ${futureFrameLayers.size})"
        }
        futureFrameLayers.add(layer)
    }

    /**
     * Make future frame [index]'s layer visible so MapLibre starts
     * loading its tiles. Called when the frame is proved ready —
     * never before: a hidden layer produces no render data, so its
     * source loads nothing, which is what keeps the 97-source range
     * from firing a 97-frame tile burst on entry. Opacity stays 0
     * until the frame is the current one ([showTiledFrame]).
     */
    private fun setFutureLayerVisible(index: Int) {
        if (index !in futureFrameLayers.indices) return
        try {
            futureFrameLayers[index]
                .setProperties(PropertyFactory.visibility(Property.VISIBLE))
        } catch (e: Exception) {
            Log.w(TAG, "unhiding future layer $index failed: ${e.javaClass.simpleName}")
        }
    }

    /** Unhide every future layer whose frame is already ready (a
     *  rebuild after the style loaded late, or a refresh that
     *  carried readiness): without this a ready frame's layer could
     *  stay hidden and the loop would show nothing. */
    private fun syncFutureLayerVisibility() {
        if (loadedStyle == null) return
        for (i in 0 until futureStore.frameCount()) {
            if (futureStore.isReady(i)) setFutureLayerVisible(i)
        }
    }

    /**
     * LibreWXR fallback sources: 10 per-frame volatile raster sources
     * at 512 px, maxzoom 7 — one 512 tile at zoom−1 covers exactly the
     * 2x2 256 tiles at zoom (Phase 0 spike 5, MAD 0.0), so at map zoom
     * 8–12 MapLibre overzooms the z7 tiles instead of ever requesting
     * z8–z12 tiles from LibreWXR (~20 requests/frame instead of ~77).
     * Built at fallback time: the templates embed the per-frame `path`
     * from the maps JSON, known only after metadata arrives. Deferred
     * when the style is not loaded yet; no-op when already built for
     * these frames.
     */
    private fun buildLibreWxrSources(frames: List<LibreWxrFrame>) {
        val style = loadedStyle ?: return
        val maps = libreWxrMaps ?: return
        if (frames == libreWxrSourcesBuiltFor) return
        removeLibreWxrSources()
        try {
            for ((i, frame) in frames.withIndex()) {
                val tileSet = TileSet("2.2.0", libreWxrTileTemplate(maps.host, frame.path))
                    .apply { maxZoom = 7f }
                val source = RasterSource(
                    radarLibreWxrFrameSourceId(i),
                    tileSet,
                    LIBRE_WXR_TILE_SIZE_512
                )
                source.isVolatile = true
                source.prefetchZoomDelta = 0
                style.addSource(source)
                val layer = RasterLayer(
                    radarLibreWxrFrameLayerId(i), radarLibreWxrFrameSourceId(i)
                ).withProperties(PropertyFactory.rasterOpacity(0f))
                style.addLayer(layer)
                libreWxrFrameLayers.add(layer)
            }
            libreWxrSourcesBuiltFor = frames
            // New layers land above the reference layer and the
            // blip — move both back on top, reference first.
            ensureReferenceLayerAboveRadar()
            ensureBlipOnTop()
            Log.i(TAG, "added ${frames.size} volatile LibreWXR 512 sources (maxzoom 7)")
        } catch (e: Exception) {
            Log.w(TAG, "adding LibreWXR sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Remove the LibreWXR sources (layer first, then source — a source
     *  still referenced by a layer cannot simply be removed). */
    private fun removeLibreWxrSources() {
        val style = loadedStyle ?: return
        for ((i, layer) in libreWxrFrameLayers.withIndex()) {
            try {
                style.removeLayer(layer.id)
            } catch (e: Exception) {
                Log.w(TAG, "removing LibreWXR layer $i failed: ${e.javaClass.simpleName}")
            }
            try {
                style.removeSource(radarLibreWxrFrameSourceId(i))
            } catch (e: Exception) {
                Log.w(TAG, "removing LibreWXR source $i failed: ${e.javaClass.simpleName}")
            }
        }
        libreWxrFrameLayers.clear()
        visibleLibreWxrIndex = -1
        libreWxrSourcesBuiltFor = null
    }

    /** Build any generation-dependent sources the style load raced
     *  ahead of (entry metadata resolving before the style loads). */
    private fun syncSourcesForCurrentGeneration() {
        if (liveStore.source == RadarSourceKind.LIBRE_WXR) {
            buildLibreWxrSources(libreWxrLoopFrames)
        }
        // Future sources build lazily on the first future visit;
        // nothing to sync until a future resolution exists.
        if (futureSourceFrames.isNotEmpty()) {
            rebuildFutureSources(futureSourceFrames)
        }
    }

    // -- Layer / frame application (opacity only, no tile reload) -----------

    private fun setOpacity(layer: RasterLayer?, opacity: Float) {
        layer?.setRasterOpacityTransition(
            TransitionOptions(RADAR_ENGINE_CROSSFADE_MS, 0)
        )
        layer?.withProperties(PropertyFactory.rasterOpacity(opacity))
    }

    private fun applyLayerAndFrame() {
        if (loadedStyle == null) return
        when (selectedLayer) {
            EngineLayer.SatelliteVis, EngineLayer.SatelliteIr -> {
                // 0.4.1: the reference (city labels) layer hides on
                // satellite — the pure rule [referenceLayerVisibleFor].
                // GIBS carries its own embedded labels; a second set
                // would double-label. (Not inside [hideSatWmsPair]:
                // that runs from the radar branch too.)
                setReferenceLayerVisible(referenceLayerVisibleFor(RadarDisplayRange.SATELLITE))
                hideMrmsSlots()
                visibleLiveIemIndex = hideTiledFamily(liveFrameLayers, visibleLiveIemIndex)
                visibleLibreWxrIndex = hideTiledFamily(libreWxrFrameLayers, visibleLibreWxrIndex)
                visibleFutureIndex = hideTiledFamily(futureFrameLayers, visibleFutureIndex)
                if (satMode == SatMode.TILES) {
                    // Tiles fallback owns the display: hide the WMS
                    // pair and flip the tile layers (the fallback
                    // rebuild already set the opacities; this keeps a
                    // layer switch consistent).
                    hideSatWmsPair()
                    setOpacity(satVisLayer, if (selectedLayer == EngineLayer.SatelliteVis) 1f else 0f)
                    setOpacity(satIrLayer, if (selectedLayer == EngineLayer.SatelliteIr) 1f else 0f)
                } else {
                    // WMS owns the display: the tile layers stay
                    // hidden and the pair shows once its image is
                    // placed. A layer switch hides the previous
                    // layer's image (wrong layer to hold).
                    setOpacity(satVisLayer, 0f)
                    setOpacity(satIrLayer, 0f)
                    hideSatWmsPair()
                }
            }
            EngineLayer.Radar -> {
                // 0.3.5: hide the satellite WMS pair too. It sits
                // ABOVE the MRMS slots (added later), and nothing hid
                // it on the way back to radar — the tile layers were
                // zeroed here but the pair stayed at opacity 1, so a
                // satellite visit's image stayed on top of the live
                // radar. (No-op when no satellite image is showing.)
                hideSatWmsPair()
                setOpacity(satVisLayer, 0f)
                setOpacity(satIrLayer, 0f)
                refreshDisplay()
            }
        }
    }

    /** Turn off the one visible layer of a tiled family (-1 = none was
     *  showing). The family's other layers are always already 0: only
     *  one layer per family is ever at opacity 1. */
    private fun hideTiledFamily(layers: List<RasterLayer>, visibleIndex: Int): Int {
        if (visibleIndex in layers.indices) setOpacity(layers[visibleIndex], 0f)
        return -1
    }

    private fun hideMrmsSlots() {
        if (!mrmsSlotsVisible) return
        setOpacity(mrmsLayerA, 0f)
        setOpacity(mrmsLayerB, 0f)
        mrmsSlotsVisible = false
    }

    // -- Radar blip (0.4.0) -------------------------------------------------

    /** The blip icon bitmap: a white dot with a thin ring, drawn to
     *  the pixel spec in [RadarBlip.kt] (CI-only: android.graphics). */
    private fun makeBlipIconBitmap(): Bitmap {
        val size = RADAR_BLIP_ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val ringPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = RADAR_BLIP_RING_STROKE_PX
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, RADAR_BLIP_RING_RADIUS_PX, ringPaint)
        val dotPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, RADAR_BLIP_DOT_RADIUS_PX, dotPaint)
        return bitmap
    }

    /** A fresh blip SymbolLayer (re-created whenever the layer is
     *  moved to the top — a removed layer's native peer is gone). */
    private fun newBlipLayer(): SymbolLayer =
        SymbolLayer(RADAR_BLIP_LAYER_ID, RADAR_BLIP_SOURCE_ID)
            .withProperties(
                PropertyFactory.iconImage(RADAR_BLIP_IMAGE_ID),
                PropertyFactory.iconSize(1f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconOpacity(1f)
            )

    /**
     * Add the blip source + layer to [style]: the runtime icon, a
     * GeoJson point at the selected place, and the SymbolLayer on
     * top. The icon-opacity transition is 0/0 — a hard flip, not a
     * fade: the blink is a beat, and a crossfade would smear it.
     * Runs once, from [addEngineSourcesAndLayers].
     */
    private fun addBlipSourceAndLayer(style: Style) {
        try {
            style.addImage(RADAR_BLIP_IMAGE_ID, makeBlipIconBitmap())
            style.addSource(
                GeoJsonSource(
                    RADAR_BLIP_SOURCE_ID,
                    Feature.fromGeometry(
                        Point.fromLngLat(placeCentre.longitude, placeCentre.latitude)
                    )
                )
            )
            blipLayer = newBlipLayer()
            style.addLayer(blipLayer!!)
            blipLayer?.setIconOpacityTransition(TransitionOptions(0, 0))
            lastBlipDisplay = null
            updateBlip()
            Log.i(
                TAG,
                "added radar blip at ${placeCentre.latitude}, ${placeCentre.longitude}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "adding radar blip failed: ${e.javaClass.simpleName}: ${e.message}")
            blipLayer = null
        }
    }

    /**
     * Move the blip's GeoJson point to the selected place
     * (geo-anchored, so pan/zoom need nothing — the map moves the
     * point itself; only a place change moves it). Called from
     * [onPlaceChanged]; no-op before the style loads (the source is
     * created at the current place then).
     */
    private fun updateBlipPosition() {
        val style = loadedStyle ?: return
        try {
            style.getSourceAs<GeoJsonSource>(RADAR_BLIP_SOURCE_ID)?.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(placeCentre.longitude, placeCentre.latitude)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "moving radar blip failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Apply the blip's current display state ([radarBlipDisplay]):
     * the blink flips `iconOpacity` 1/0 with the playhead parity
     * while the loop plays, holds steady VISIBLE otherwise — the
     * same rule on the radar and satellite layers. No-op when
     * nothing changed — this runs on every chrome refresh
     * ([refreshUiState]).
     */
    private fun updateBlip() {
        val layer = blipLayer ?: return
        val store = activeStore()
        val playing = !store.isPaused &&
            (store.state == LiveLoopState.PLAYING ||
                store.state == LiveLoopState.WAITING_NEXT)
        val display = radarBlipDisplay(
            isLoopPlaying = playing,
            currentFrameIndex = store.currentIndex
        )
        if (display == lastBlipDisplay) return
        lastBlipDisplay = display
        try {
            layer.setProperties(
                PropertyFactory.iconOpacity(
                    if (display == RadarBlipDisplay.VISIBLE) 1f else 0f
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "setting radar blip opacity failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Show/hide the reference (city labels) layer, following the pure
     * rule [referenceLayerVisibleFor]: visible on any radar display,
     * hidden on satellite (GIBS carries its own embedded labels).
     * No-op when nothing changed — this runs on every
     * [refreshDisplay]. A hidden layer loads no tiles, so a satellite
     * visit costs no label traffic.
     */
    private fun setReferenceLayerVisible(visible: Boolean) {
        val layer = referenceLayer ?: return
        if (visible == referenceLayerVisible) return
        referenceLayerVisible = visible
        try {
            layer.setProperties(
                PropertyFactory.visibility(
                    if (visible) Property.VISIBLE else Property.NONE
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "setting reference layer visibility failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Keep the reference layer above the radar families and below the
     * blip: sources built after the style load (future HRRR rebuild,
     * LibreWXR fallback) use plain `addLayer`, which lands above the
     * reference layer — remove it and re-add a fresh one so the
     * labels always draw over the radar. The source survives (only
     * the layer is removed/re-added), and the current visibility is
     * re-applied to the fresh layer. [ensureBlipOnTop] must run
     * AFTER this at the same sites: the re-added reference lands on
     * top of everything, and the blip belongs above it. The
     * satellite tile rebuild needs no call here: it inserts BELOW
     * the first live frame layer, under the radar families and the
     * blip alike.
     */
    private fun ensureReferenceLayerAboveRadar() {
        val style = loadedStyle ?: return
        if (referenceLayer == null) return
        try {
            style.removeLayer(RADAR_ENGINE_REFERENCE_LAYER_ID)
        } catch (e: Exception) {
            Log.w(TAG, "removing reference layer failed: ${e.javaClass.simpleName}")
        }
        try {
            referenceLayer = RasterLayer(
                RADAR_ENGINE_REFERENCE_LAYER_ID, RADAR_ENGINE_REFERENCE_SOURCE_ID
            ).withProperties(
                PropertyFactory.visibility(
                    if (referenceLayerVisible) Property.VISIBLE else Property.NONE
                )
            )
            style.addLayer(referenceLayer!!)
        } catch (e: Exception) {
            Log.w(TAG, "re-adding reference layer failed: ${e.javaClass.simpleName}")
            referenceLayer = null
        }
    }

    /**
     * Keep the blip on top: sources built after the style load
     * (future HRRR, LibreWXR fallback) use plain `addLayer`, which
     * lands above the blip — remove the blip layer and re-add a
     * fresh one so the marker always draws over the radar. The
     * source + icon survive (only the layer is removed/re-added),
     * and the current display state is re-applied after the move.
     */
    private fun ensureBlipOnTop() {
        val style = loadedStyle ?: return
        if (blipLayer == null) return
        try {
            style.removeLayer(RADAR_BLIP_LAYER_ID)
        } catch (e: Exception) {
            Log.w(TAG, "removing radar blip layer failed: ${e.javaClass.simpleName}")
        }
        try {
            blipLayer = newBlipLayer()
            style.addLayer(blipLayer!!)
            blipLayer?.setIconOpacityTransition(TransitionOptions(0, 0))
            lastBlipDisplay = null
            updateBlip()
        } catch (e: Exception) {
            Log.w(TAG, "re-adding radar blip layer failed: ${e.javaClass.simpleName}")
            blipLayer = null
        }
    }

    /**
     * Show the active store's current frame, if it is ready. An unready
     * current frame is a HOLD: the previously visible frame stays
     * exactly where it is (no jump, no blank) and the chrome says
     * "waiting" — the store's state, read by [refreshUiState], is the
     * signal. The hold is WITHIN the active family only.
     *
     * 0.3.5: the families not in use are hidden FIRST, from the pure
     * rule [displayFamiliesFor] — the single authority for which
     * family owns the map for this (range, source) pair. The old code
     * hid the other live families per branch but never the future
     * family in a Live branch, so IN 6H -> Live left the sixth-hour
     * frame (appended above the MRMS slots) on top while the live
     * loop advanced underneath. A range switch now hides the outgoing
     * family unconditionally, even if the incoming frame is not ready
     * yet (basemap briefly, then the live frame). The active family
     * is never hidden here, so its crossfade and its within-family
     * hold are untouched.
     */
    private fun refreshDisplay() {
        if (loadedStyle == null) return
        if (selectedLayer != EngineLayer.Radar) return
        val store = activeStore()
        val range = displayRangeFor(selectedRange)
        // 0.4.1: the reference (city labels) layer shows on any radar
        // display — the pure rule [referenceLayerVisibleFor]. The
        // satellite branch of [applyLayerAndFrame] hides it.
        setReferenceLayerVisible(referenceLayerVisibleFor(range))
        val visible = displayFamiliesFor(range, store.source)
        if (RadarDisplayFamily.MRMS !in visible) hideMrmsSlots()
        if (RadarDisplayFamily.LIVE_IEM !in visible) {
            visibleLiveIemIndex = hideTiledFamily(liveFrameLayers, visibleLiveIemIndex)
        }
        if (RadarDisplayFamily.LIBRE_WXR !in visible) {
            visibleLibreWxrIndex = hideTiledFamily(libreWxrFrameLayers, visibleLibreWxrIndex)
        }
        if (RadarDisplayFamily.FUTURE !in visible) {
            visibleFutureIndex = hideTiledFamily(futureFrameLayers, visibleFutureIndex)
        }
        if (store.frameCount() == 0) return
        val index = store.currentIndex
        when (selectedRange) {
            EngineRange.Live -> when (store.source) {
                RadarSourceKind.MRMS -> {
                    if (store.isReady(index)) showMrmsFrame(index)
                }
                RadarSourceKind.LIBRE_WXR -> {
                    if (store.isReady(index)) {
                        visibleLibreWxrIndex = showTiledFrame(
                            libreWxrFrameLayers, visibleLibreWxrIndex, index
                        )
                    }
                }
                RadarSourceKind.IEM -> {
                    if (store.isReady(index)) {
                        visibleLiveIemIndex = showTiledFrame(
                            liveFrameLayers, visibleLiveIemIndex, index
                        )
                    }
                }
                null -> Unit
            }
            else -> {
                // A held future frame is only a valid hold while it is
                // a frame of the ACTIVE future list: after a
                // future->future switch (24 h -> 6 h) or a tail cap the
                // old visible index can sit past the end of the new
                // list, and holding it would show a frame the new
                // range never contains. Drop it (basemap briefly)
                // instead of holding another range's frame.
                if (visibleFutureIndex >= 0 &&
                    !isFutureVisibleIndexValid(visibleFutureIndex, store.frameCount())
                ) {
                    visibleFutureIndex = hideTiledFamily(futureFrameLayers, visibleFutureIndex)
                }
                if (store.isReady(index)) {
                    // Belt-and-braces: the layer must be visible
                    // before the opacity crossfade (a ready frame is
                    // unhidden at settle time, but a rebuild raced
                    // ahead of the style load must not leave it
                    // hidden here).
                    setFutureLayerVisible(index)
                    visibleFutureIndex = showTiledFrame(
                        futureFrameLayers, visibleFutureIndex, index
                    )
                }
            }
        }
    }

    /** The pure-rule range for the selected range ([displayFamiliesFor]). */
    private fun displayRangeFor(range: EngineRange): RadarDisplayRange = when (range) {
        EngineRange.Live -> RadarDisplayRange.LIVE
        EngineRange.Future6h -> RadarDisplayRange.FUTURE_6H
        EngineRange.Future24h -> RadarDisplayRange.FUTURE_24H
        EngineRange.Future48h -> RadarDisplayRange.FUTURE_48H
    }

    /** Crossfade a tiled family to [index]; returns the new visible index. */
    private fun showTiledFrame(
        layers: List<RasterLayer>,
        visibleIndex: Int,
        index: Int
    ): Int {
        if (index !in layers.indices) return visibleIndex
        if (visibleIndex == index) return visibleIndex
        if (visibleIndex in layers.indices) setOpacity(layers[visibleIndex], 0f)
        setOpacity(layers[index], 1f)
        frameShownAtMs = SystemClock.uptimeMillis()
        return index
    }

    // -- MRMS two-slot display (decode on demand, at most 2 bitmaps) ------

    /** Show MRMS frame [index] (it is ready — callers check). */
    private fun showMrmsFrame(index: Int) {
        if (!liveStore.isReady(index)) return
        if (slotFrameIndex[visibleSlot] == index) return // already showing
        val idle = 1 - visibleSlot
        if (slotFrameIndex[idle] == index) {
            crossfadeMrmsTo(idle)
            return
        }
        if (decodingFrame[idle] == index) return // decode in flight; its completion crossfades
        decodeMrmsFrameIntoSlot(index, idle)
    }

    private fun crossfadeMrmsTo(slot: Int) {
        val showLayer = if (slot == 0) mrmsLayerA else mrmsLayerB
        val hideLayer = if (slot == 0) mrmsLayerB else mrmsLayerA
        setOpacity(showLayer, 1f)
        setOpacity(hideLayer, 0f)
        visibleSlot = slot
        mrmsSlotsVisible = true
        frameShownAtMs = SystemClock.uptimeMillis()
    }

    /**
     * Decode frame [index]'s held bytes into [slot], off the main
     * thread. On completion the bitmap is placed with `setImage`
     * (never `setUri`) at the generation's quad, and the loop
     * crossfades to it if it is still the frame under the playhead.
     * A decode for a superseded frame is dropped, never shown: the
     * slot only ever receives the bitmap of the frame it was decoded
     * for, and a slot that became visible for another frame meanwhile
     * is left alone.
     */
    private fun decodeMrmsFrameIntoSlot(index: Int, slot: Int) {
        val timeMs = liveStore.frameValidTime(index)?.toEpochMilli() ?: return
        val bytes = mrmsBytes[timeMs] ?: return
        decodingJobs[slot]?.cancel()
        val generationId = liveStore.generationId
        decodingFrame[slot] = index
        decodingJobs[slot] = scope.launch(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@launch
                withContext(Dispatchers.Main) {
                    if (liveStore.generationId != generationId) return@withContext
                    val source = if (slot == 0) mrmsSourceA else mrmsSourceB
                    if (source == null) return@withContext
                    if (slot == visibleSlot && slotFrameIndex[slot] != index) {
                        // This slot became visible for another frame
                        // while decoding; do not swap the visible image.
                        return@withContext
                    }
                    source.setCoordinates(mrmsQuad)
                    source.setImage(bitmap)
                    slotFrameIndex[slot] = index
                    if (liveStore.currentIndex == index &&
                        liveStore.source == RadarSourceKind.MRMS
                    ) {
                        crossfadeMrmsTo(slot)
                    }
                }
            } finally {
                if (decodingFrame[slot] == index) decodingFrame[slot] = -1
            }
        }
    }

    /**
     * Eager-decode [index] into the idle slot (its bitmap is then
     * waiting when the playhead reaches it, so the advance is just a
     * crossfade). Only when the frame is ready and no slot holds it.
     */
    private fun maybeDecodeMrmsFrame(index: Int) {
        if (liveStore.source != RadarSourceKind.MRMS) return
        if (!liveStore.isReady(index)) return
        if (slotFrameIndex.contains(index)) return
        decodeMrmsFrameIntoSlot(index, 1 - visibleSlot)
    }

    // -- Satellite WMS (Phase 3) -------------------------------------------

    private data class SatTimeResolution(
        val scanTimeMs: Long,
        val candidates: List<Long>,
        val blankByTime: Map<Long, Boolean>,
        /** Rounds already probed (0 = from the cache, no probe map). */
        val roundsProbed: Int
    )

    private data class SatProbeRound(
        val candidates: List<Long>,
        val blankByTime: Map<Long, Boolean>
    )

    private data class SatDecoded(val bitmap: Bitmap, val blank: Boolean)

    private enum class SatPlaceOutcome { Placed, Blank, Dropped, DecodeFailed }

    private fun wmsLayerFor(layer: EngineLayer): WmsSatelliteLayer? = when (layer) {
        EngineLayer.SatelliteVis -> WmsSatelliteLayer.VISIBLE
        EngineLayer.SatelliteIr -> WmsSatelliteLayer.INFRARED
        EngineLayer.Radar -> null
    }

    private fun engineLayerFor(layer: WmsSatelliteLayer): EngineLayer = when (layer) {
        WmsSatelliteLayer.VISIBLE -> EngineLayer.SatelliteVis
        WmsSatelliteLayer.INFRARED -> EngineLayer.SatelliteIr
    }

    /** Viewport in physical pixels (the MapView size, or the display
     *  metrics before first layout — the map is full-screen, so the
     *  two agree; never a hardcoded size). */
    private fun satelliteViewportPx(): Pair<Int, Int> {
        val view = mapView
        val w = view?.width ?: 0
        val h = view?.height ?: 0
        if (w > 0 && h > 0) return w to h
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /** EPSG:3857 metres back to lon/lat degrees (Worker A provides no
     *  inverse — the pure core only goes lon/lat -> 3857). */
    private fun lonLatFrom3857(x: Double, y: Double): Pair<Double, Double> {
        val lon = Math.toDegrees(x / EPSG3857_EARTH_RADIUS_METRES)
        val lat = Math.toDegrees(
            2.0 * atan(exp(y / EPSG3857_EARTH_RADIUS_METRES)) - PI / 2.0
        )
        return lon to lat
    }

    /** The [LatLngQuad] for a WMS bbox (corners in lon/lat degrees). */
    private fun quadForBbox(bbox: WmsBbox3857): LatLngQuad {
        val (westLon, southLat) = lonLatFrom3857(bbox.minX, bbox.minY)
        val (eastLon, northLat) = lonLatFrom3857(bbox.maxX, bbox.maxY)
        return LatLngQuad(
            LatLng(northLat, westLon),
            LatLng(northLat, eastLon),
            LatLng(southLat, eastLon),
            LatLng(southLat, westLon)
        )
    }

    /** Bbox signature for the held-bytes key (2 decimals, as the URL). */
    private fun satBboxSignature(bbox: WmsBbox3857): String =
        listOf(bbox.minX, bbox.minY, bbox.maxX, bbox.maxY)
            .joinToString(",") { "%.2f".format(Locale.US, it) }

    /** Held-bytes key: layer + scan time + bbox. */
    private fun satKey(
        layer: WmsSatelliteLayer,
        scanTimeMs: Long,
        bbox: WmsBbox3857
    ): String = "${layer.name}|$scanTimeMs|${satBboxSignature(bbox)}"

    /** Hold WMS bytes, capped at the 2 most recent entries (one per
     *  layer); the eldest goes first. */
    private fun putSatBytes(key: String, bytes: ByteArray) {
        satBytes[key] = bytes
        while (satBytes.size > 2) {
            val eldest = satBytes.keys.firstOrNull() ?: break
            satBytes.remove(eldest)
        }
    }

    /** A new layer visit: fresh fetcher generation (byte accounting +
     *  negative cache are satellite-scoped), WMS attempted first, and
     *  the status pill shows LOADING until an image is placed. */
    private fun beginSatelliteVisit(layer: WmsSatelliteLayer) {
        fetcher.endGeneration(satGenerationId)
        satGenerationId++
        satMode = SatMode.WMS
        satState = SatState.LOADING
        Log.i(TAG, "satellite visit ($layer): generation $satGenerationId")
    }

    /**
     * Resolve satellite for [layer] (layer switch, pan/zoom retarget,
     * 10-min refresh, RETRY). Cancels any in-flight resolution — a
     * superseded resolve never places its bitmap ([satResolveSeq]).
     * The visible image is never cleared first: a refresh or retarget
     * holds the old image until the new one decodes and crossfades.
     */
    private fun resolveSatellite(layer: WmsSatelliteLayer, reason: SatResolveReason) {
        if (loadedStyle == null) return
        satResolveJob?.cancel()
        satResolveJob = scope.launch {
            resolveSatelliteInner(layer, reason)
        }
    }

    private suspend fun resolveSatelliteInner(
        layer: WmsSatelliteLayer,
        reason: SatResolveReason
    ) {
        val seq = ++satResolveSeq
        Log.i(TAG, "satellite resolve ($layer, $reason, seq $seq)")
        val target = currentTarget()
        val zoomInt = currentTileZoom()
        val (viewW, viewH) = satelliteViewportPx()
        val bbox = wmsBbox3857(target.longitude, target.latitude, viewW, viewH, zoomInt)
        val (imgW, imgH) = wmsImageSizeForViewport(viewW, viewH)
        val quad = quadForBbox(bbox)
        // A bbox change clears this layer's held bytes for other bboxes.
        val bboxSig = satBboxSignature(bbox)
        satBytes.keys.filter { it.startsWith("${layer.name}|") && !it.endsWith("|$bboxSig") }
            .forEach { satBytes.remove(it) }
        val resolution = resolveSatelliteTime(layer, bbox)
        if (resolution == null) {
            if (seq != satResolveSeq) return
            if (satShowingLayer == layer && satSlotsVisible) {
                // Probe exhausted but an image is held: stay SHOWING
                // on the old image; retry at the next 10-min tick.
                Log.i(TAG, "satellite probe exhausted — holding previous image")
                refreshUiState()
                return
            }
            enterSatelliteTilesFallback(layer, "probe-exhausted")
            return
        }
        val key = satKey(layer, resolution.scanTimeMs, bbox)
        val held = satBytes[key]
        if (held != null) {
            if (placeHeldSatelliteBytes(
                    layer, resolution.scanTimeMs, bbox, quad,
                    target, zoomInt, key, held, seq
                )
            ) return
            satBytes.remove(key)
        }
        fetchAndPlaceSatellite(
            layer, resolution, bbox, quad, target, zoomInt, imgW, imgH, key, seq
        )
    }

    /**
     * Resolve the WMS scan time for [layer] over [bbox]: the cached
     * time while the 10-min TTL holds, else 5 parallel direct
     * 256x256 GetMap probes (round 1), newest non-blank wins; round 2
     * only when round 1 is all blank. Never a fixed backoff, never
     * GetCapabilities, never a third round.
     */
    private suspend fun resolveSatelliteTime(
        layer: WmsSatelliteLayer,
        bbox: WmsBbox3857
    ): SatTimeResolution? {
        val nowMs = System.currentTimeMillis()
        if (!satTimestampCache.needsResolve(layer, nowMs)) {
            val cached = satTimestampCache.resolvedTime(layer)
            if (cached != null) {
                return SatTimeResolution(cached, emptyList(), emptyMap(), 0)
            }
        }
        val round1 = probeSatelliteRound(layer, bbox, 1, nowMs)
        val pick1 = newestNonBlank(round1.candidates, round1.blankByTime)
        if (pick1 != null) {
            satTimestampCache.recordResolved(layer, pick1, nowMs)
            return SatTimeResolution(pick1, round1.candidates, round1.blankByTime, 1)
        }
        val round2 = probeSatelliteRound(layer, bbox, 2, nowMs)
        val pick2 = newestNonBlank(round2.candidates, round2.blankByTime)
        if (pick2 != null) {
            satTimestampCache.recordResolved(layer, pick2, nowMs)
            return SatTimeResolution(pick2, round2.candidates, round2.blankByTime, 2)
        }
        Log.w(TAG, "satellite probe exhausted ($layer): no non-blank scan in 10 candidates")
        return null
    }

    /** One probe round: all 5 candidates in parallel through the same
     *  fetcher (single-flight collapses any duplicate URL). */
    private suspend fun probeSatelliteRound(
        layer: WmsSatelliteLayer,
        bbox: WmsBbox3857,
        round: Int,
        nowMs: Long
    ): SatProbeRound {
        val candidates = wmsTimeCandidates(nowMs, round)
        val blankByTime = coroutineScope {
            candidates.map { timeMs ->
                async(Dispatchers.Default) {
                    timeMs to probeSatelliteCandidate(layer, bbox, timeMs)
                }
            }.awaitAll().toMap()
        }
        Log.i(
            TAG,
            "satellite probe round $round ($layer): " +
                "${blankByTime.count { !it.value }} non-blank of ${candidates.size}"
        )
        return SatProbeRound(candidates, blankByTime)
    }

    /** One candidate probe: true = blank (or failed — unproven is not
     *  usable). XML is sniffed BEFORE decoding; a decode failure is
     *  blank too. The decoded probe bitmap is recycled here. */
    private suspend fun probeSatelliteCandidate(
        layer: WmsSatelliteLayer,
        bbox: WmsBbox3857,
        timeMs: Long
    ): Boolean {
        val url = wmsSmallProbeUrl(layer, bbox, timeMs)
        val result = withTimeoutOrNull(RADAR_ENGINE_SAT_REQUEST_TIMEOUT_MS) {
            fetcher.fetch(url, satGenerationId)
        } ?: return true
        val bytes = (result as? FetchResult.Success)?.bytes ?: return true
        if (isXmlErrorBody(bytes)) return true
        val decoded = decodeAndSampleSatellite(bytes) ?: return true
        val blank = decoded.blank
        decoded.bitmap.recycle()
        return blank
    }

    /**
     * Step back from a blank full image to the next older probed-good
     * candidate (probing the next round first when the rounds already
     * run hold none). Returns the older scan time, or null when no
     * probed-good candidate remains — the caller then falls back to
     * tiles.
     */
    private suspend fun stepBackSatelliteTime(
        layer: WmsSatelliteLayer,
        resolution: SatTimeResolution,
        bbox: WmsBbox3857
    ): Long? {
        val nowMs = System.currentTimeMillis()
        val tried = resolution.blankByTime + (resolution.scanTimeMs to true)
        val older = newestNonBlank(resolution.candidates, tried)
        if (older != null) {
            satTimestampCache.recordResolved(layer, older, nowMs)
            Log.i(
                TAG,
                "satellite WMS blank at ${formatWmsTime(resolution.scanTimeMs)} — " +
                    "stepping back to ${formatWmsTime(older)}"
            )
            return older
        }
        if (resolution.roundsProbed >= 2) return null
        var roundToProbe = if (resolution.roundsProbed == 0) 1 else 2
        while (roundToProbe <= 2) {
            val round = probeSatelliteRound(layer, bbox, roundToProbe, nowMs)
            val pick = newestNonBlank(
                round.candidates,
                round.blankByTime + (resolution.scanTimeMs to true)
            )
            if (pick != null) {
                satTimestampCache.recordResolved(layer, pick, nowMs)
                return pick
            }
            roundToProbe++
        }
        return null
    }

    /**
     * Fetch the full GetMap at the resolved time and place it. On a
     * blank image, step back once to the next older probed-good time
     * and retry the full GetMap once there. Any terminal failure
     * (fetch / XML / decode / step-back exhausted) falls back to the
     * WMTS tile grid — never a blank layer, never a shown blank.
     */
    private suspend fun fetchAndPlaceSatellite(
        layer: WmsSatelliteLayer,
        resolution: SatTimeResolution,
        bbox: WmsBbox3857,
        quad: LatLngQuad,
        centre: LatLng,
        zoomInt: Int,
        imgW: Int,
        imgH: Int,
        key: String,
        seq: Long
    ) {
        val url = wmsGetMapUrl(layer, bbox, imgW, imgH, resolution.scanTimeMs)
        val result = withTimeoutOrNull(RADAR_ENGINE_SAT_REQUEST_TIMEOUT_MS) {
            fetcher.fetch(url, satGenerationId)
        }
        if (result !is FetchResult.Success) {
            Log.w(
                TAG,
                "satellite WMS fetch failed ($layer @ ${formatWmsTime(resolution.scanTimeMs)}): " +
                    "${if (result == null) "timeout" else result.javaClass.simpleName} — tiles fallback"
            )
            if (seq == satResolveSeq) enterSatelliteTilesFallback(layer, "wms-fetch-failed")
            return
        }
        val bytes = result.bytes
        if (isXmlErrorBody(bytes)) {
            Log.w(
                TAG,
                "satellite WMS XML error ($layer @ ${formatWmsTime(resolution.scanTimeMs)}) — tiles fallback"
            )
            if (seq == satResolveSeq) enterSatelliteTilesFallback(layer, "wms-xml-error")
            return
        }
        putSatBytes(key, bytes)
        val idle = 1 - satVisibleSlot
        when (decodeSatFrameIntoSlot(
            idle, key, bytes, bbox, quad, centre, zoomInt,
            layer, resolution.scanTimeMs, seq
        )) {
            SatPlaceOutcome.Placed -> return
            SatPlaceOutcome.Dropped -> return // superseded; the newer resolve owns the chrome
            SatPlaceOutcome.DecodeFailed -> {
                satBytes.remove(key)
                Log.w(TAG, "satellite WMS decode failed ($layer) — tiles fallback")
                if (seq == satResolveSeq) enterSatelliteTilesFallback(layer, "wms-decode-failed")
                return
            }
            SatPlaceOutcome.Blank -> {
                satBytes.remove(key)
                Log.i(
                    TAG,
                    "satellite WMS blank at ${formatWmsTime(resolution.scanTimeMs)} — stepping back"
                )
            }
        }
        val older = stepBackSatelliteTime(layer, resolution, bbox)
        if (older == null) {
            if (seq == satResolveSeq) {
                enterSatelliteTilesFallback(layer, "wms-blank-stepback-exhausted")
            }
            return
        }
        val url2 = wmsGetMapUrl(layer, bbox, imgW, imgH, older)
        val result2 = withTimeoutOrNull(RADAR_ENGINE_SAT_REQUEST_TIMEOUT_MS) {
            fetcher.fetch(url2, satGenerationId)
        }
        if (result2 !is FetchResult.Success || isXmlErrorBody(result2.bytes)) {
            Log.w(TAG, "satellite WMS step-back fetch failed ($layer) — tiles fallback")
            if (seq == satResolveSeq) enterSatelliteTilesFallback(layer, "wms-stepback-failed")
            return
        }
        val key2 = satKey(layer, older, bbox)
        putSatBytes(key2, result2.bytes)
        when (decodeSatFrameIntoSlot(
            1 - satVisibleSlot, key2, result2.bytes, bbox, quad, centre, zoomInt,
            layer, older, seq
        )) {
            SatPlaceOutcome.Placed -> Unit
            SatPlaceOutcome.Blank -> {
                satBytes.remove(key2)
                if (seq == satResolveSeq) enterSatelliteTilesFallback(layer, "wms-stepback-blank")
            }
            SatPlaceOutcome.DecodeFailed -> {
                satBytes.remove(key2)
                if (seq == satResolveSeq) {
                    enterSatelliteTilesFallback(layer, "wms-stepback-decode-failed")
                }
            }
            SatPlaceOutcome.Dropped -> Unit
        }
    }

    /**
     * Place held bytes for [key]: if a slot already holds this exact
     * image, crossfade straight to it (no decode); else decode into
     * the idle slot. Returns true when an image is showing.
     */
    private suspend fun placeHeldSatelliteBytes(
        layer: WmsSatelliteLayer,
        scanTimeMs: Long,
        bbox: WmsBbox3857,
        quad: LatLngQuad,
        centre: LatLng,
        zoomInt: Int,
        key: String,
        bytes: ByteArray,
        seq: Long
    ): Boolean {
        val heldSlot = satSlotKey.indexOf(key)
        if (heldSlot >= 0 && satSlotBitmaps[heldSlot] != null) {
            withContext(Dispatchers.Main) {
                if (seq != satResolveSeq || selectedLayer != engineLayerFor(layer)) {
                    return@withContext
                }
                setOpacity(satVisLayer, 0f)
                setOpacity(satIrLayer, 0f)
                satBbox = bbox
                satQuad = quad
                satBboxCentre = centre
                satBboxZoom = zoomInt
                satMode = SatMode.WMS
                satState = SatState.SHOWING
                satShowingLayer = layer
                crossfadeSatTo(heldSlot)
                refreshUiState()
            }
            return true
        }
        val idle = 1 - satVisibleSlot
        return decodeSatFrameIntoSlot(
            idle, key, bytes, bbox, quad, centre, zoomInt,
            layer, scanTimeMs, seq
        ) == SatPlaceOutcome.Placed
    }

    /**
     * Decode WMS [bytes] into [slot], off the main thread, sampling a
     * coarse 64 px grid for the blank check BEFORE `setImage` — a
     * blank image is never shown. Mirrors `decodeMrmsFrameIntoSlot`:
     * on Main, the generation guard, `setCoordinates` + `setImage`,
     * then the crossfade only if the fetch is still current. A
     * rejected decode is recycled; a superseded slot's bitmap is
     * recycled once its replacement is set (native copied it).
     */
    private suspend fun decodeSatFrameIntoSlot(
        slot: Int,
        key: String,
        bytes: ByteArray,
        bbox: WmsBbox3857,
        quad: LatLngQuad,
        centre: LatLng,
        zoomInt: Int,
        layer: WmsSatelliteLayer,
        scanTimeMs: Long,
        seq: Long
    ): SatPlaceOutcome {
        satDecodingKey[slot] = key
        try {
            val decoded = decodeAndSampleSatellite(bytes)
                ?: return SatPlaceOutcome.DecodeFailed
            if (decoded.blank) {
                decoded.bitmap.recycle()
                return SatPlaceOutcome.Blank
            }
            return withContext(Dispatchers.Main) {
                if (seq != satResolveSeq || selectedLayer != engineLayerFor(layer)) {
                    decoded.bitmap.recycle()
                    return@withContext SatPlaceOutcome.Dropped
                }
                val source = if (slot == 0) satSourceA else satSourceB
                if (source == null) {
                    decoded.bitmap.recycle()
                    return@withContext SatPlaceOutcome.Dropped
                }
                if (slot == satVisibleSlot && satSlotsVisible && satSlotKey[slot] != key) {
                    // This slot became visible for another image while
                    // decoding; do not swap the visible image.
                    decoded.bitmap.recycle()
                    return@withContext SatPlaceOutcome.Dropped
                }
                satSlotBitmaps[slot]?.recycle()
                satSlotBitmaps[slot] = decoded.bitmap
                source.setCoordinates(quad)
                source.setImage(decoded.bitmap)
                satSlotKey[slot] = key
                satBbox = bbox
                satQuad = quad
                satBboxCentre = centre
                satBboxZoom = zoomInt
                setOpacity(satVisLayer, 0f)
                setOpacity(satIrLayer, 0f)
                satMode = SatMode.WMS
                satState = SatState.SHOWING
                satShowingLayer = layer
                crossfadeSatTo(slot)
                refreshUiState()
                SatPlaceOutcome.Placed
            }
        } finally {
            if (satDecodingKey[slot] == key) satDecodingKey[slot] = null
        }
    }

    /** Decode JPEG bytes and sample the coarse grid, on Default. */
    private suspend fun decodeAndSampleSatellite(bytes: ByteArray): SatDecoded? =
        withContext(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            SatDecoded(bitmap, isBlankSample(sampleSatelliteBitmap(bitmap)))
        }

    /**
     * Coarse-grid sampling, every 64 px — exactly the probe's grid
     * (~1,170 samples for a 1620x2880 image). Never every pixel, never
     * a downscaled thumbnail.
     */
    private fun sampleSatelliteBitmap(bitmap: Bitmap): List<Triple<Int, Int, Int>> {
        val samples = ArrayList<Triple<Int, Int, Int>>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                samples.add(
                    Triple((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
                )
                x += 64
            }
            y += 64
        }
        return samples
    }

    private fun crossfadeSatTo(slot: Int) {
        val showLayer = if (slot == 0) satLayerA else satLayerB
        val hideLayer = if (slot == 0) satLayerB else satLayerA
        setOpacity(showLayer, 1f)
        setOpacity(hideLayer, 0f)
        satVisibleSlot = slot
        satSlotsVisible = true
    }

    private fun hideSatWmsPair() {
        if (!satSlotsVisible) return
        setOpacity(satLayerA, 0f)
        setOpacity(satLayerB, 0f)
        satSlotsVisible = false
    }

    /**
     * WMTS tile fallback: rebuild this layer's JSON tile source with
     * the resolved WMS scan time (WMTS availability is a superset of
     * WMS at that time — the WMS backend lags WMTS), hide the WMS
     * pair, and show the tile grid. No satellite prefetch, parents
     * from cache only (`prefetchZoomDelta = 0`). One fallback per
     * visit: the mode stays TILES until RETRY or a fresh visit.
     * The failed WMS resolution is invalidated AFTER the fallback
     * reads it, so the next WMS attempt re-probes from scratch.
     */
    private fun enterSatelliteTilesFallback(layer: WmsSatelliteLayer, reason: String) {
        if (loadedStyle == null) return
        val isVisible = layer == WmsSatelliteLayer.VISIBLE
        val timeMs = satTimestampCache.resolvedTime(layer)
            ?: roundDownTo10Min(System.currentTimeMillis() - 3_600_000L)
        val timeStr = formatWmsTime(timeMs)
        if (!rebuildSatelliteTileSources(isVisible, timeStr)) {
            satMode = SatMode.TILES
            satState = SatState.UNAVAILABLE
            satShowingLayer = null
            Log.w(TAG, "satellite tiles fallback rebuild failed ($layer) — unavailable")
            refreshUiState()
            return
        }
        hideSatWmsPair()
        satMode = SatMode.TILES
        satState = SatState.SHOWING
        satShowingLayer = null
        setOpacity(if (isVisible) satVisLayer else satIrLayer, 1f)
        setOpacity(if (isVisible) satIrLayer else satVisLayer, 0f)
        satTimestampCache.recordFetchFailure(layer)
        Log.w(TAG, "satellite tiles fallback ($layer @ $timeStr): $reason")
        refreshUiState()
    }

    /**
     * Rebuild one JSON satellite tile source with [gibsTime]: remove
     * layer then source (a referenced source cannot be removed),
     * re-add, and re-insert BELOW the first live frame layer — plain
     * `addLayer` would put satellite on top of the radar layers.
     * Returns false when the rebuild itself throws.
     */
    private fun rebuildSatelliteTileSources(isVisible: Boolean, gibsTime: String): Boolean {
        val style = loadedStyle ?: return false
        val sourceId = if (isVisible) RADAR_ENGINE_SAT_VIS_SOURCE_ID else RADAR_ENGINE_SAT_IR_SOURCE_ID
        val layerId = if (isVisible) RADAR_ENGINE_SAT_VIS_LAYER_ID else RADAR_ENGINE_SAT_IR_LAYER_ID
        return try {
            try {
                style.removeLayer(layerId)
            } catch (e: Exception) {
                Log.w(TAG, "removing satellite layer failed: ${e.javaClass.simpleName}")
            }
            try {
                style.removeSource(sourceId)
            } catch (e: Exception) {
                Log.w(TAG, "removing satellite source failed: ${e.javaClass.simpleName}")
            }
            val tileSet = TileSet("2.2.0", gibsTileTemplate(isVisible, gibsTime)).apply {
                maxZoom = satelliteMaxZoom(isVisible).toFloat()
            }
            val source = RasterSource(sourceId, tileSet, 256)
            source.prefetchZoomDelta = 0
            style.addSource(source)
            val layer = RasterLayer(layerId, sourceId)
                .withProperties(PropertyFactory.rasterOpacity(0f))
            style.addLayerBelow(layer, radarLiveFrameLayerId(0))
            if (isVisible) satVisLayer = layer else satIrLayer = layer
            Log.i(TAG, "rebuilt satellite tile source $sourceId @ $gibsTime")
            true
        } catch (e: Exception) {
            Log.w(
                TAG,
                "rebuilding satellite tile source failed: ${e.javaClass.simpleName}: ${e.message}"
            )
            false
        }
    }

    /**
     * 10-min satellite re-resolve while staying on satellite: only
     * when the cached timestamp is stale, and never from tiles
     * fallback (no flapping back to WMS mid-visit). A refresh that
     * finds the same timestamp does nothing — no network.
     */
    private fun refreshSatelliteIfDue() {
        if (selectedLayer == EngineLayer.Radar) return
        if (satMode == SatMode.TILES) return
        val layer = wmsLayerFor(selectedLayer) ?: return
        if (!satTimestampCache.needsResolve(layer, System.currentTimeMillis())) return
        resolveSatellite(layer, SatResolveReason.REFRESH)
    }

    /**
     * Pan/zoom settle on satellite, WMS mode only: retarget when the
     * camera target moved past the 0.5° margin from the image's bbox
     * centre, or when the settled zoom differs from the zoom the
     * image was fetched at. The old image holds until the new one
     * decodes. In tiles mode MapLibre fetches the new area itself.
     */
    private fun onSatelliteMapIdle(target: LatLng, zoom: Double) {
        if (loadedStyle == null) return
        if (satMode != SatMode.WMS) return
        if (satState != SatState.SHOWING) return
        val layer = wmsLayerFor(selectedLayer) ?: return
        val zoomInt = zoom.toInt().coerceIn(0, 22)
        val centre = satBboxCentre
        val moved = centre == null ||
            panPastMargin(centre.latitude, centre.longitude, target.latitude, target.longitude)
        if (!moved && zoomInt == satBboxZoom) return
        Log.i(TAG, "satellite settle (moved=$moved zoom $satBboxZoom->$zoomInt) — retarget WMS")
        resolveSatellite(layer, SatResolveReason.RETARGET)
    }

    // -- Loop resolution (metadata, generation, fallback) ------------------

    private data class LoopMetadata(
        val mrmsTimesMs: List<Long>,
        val mrmsExtent: Pair<Long, Long>?,
        val libreWxrMaps: LibreWxrMaps?
    )

    /**
     * Fetch both metadata documents IN PARALLEL (never sequential — a
     * hung MRMS call must not stall LibreWXR behind it), each bounded
     * by [RADAR_ENGINE_METADATA_TIMEOUT_MS]. A timeout or failure
     * yields an empty result for that source; the caller falls through
     * to the next source in [DEFAULT_RADAR_SOURCE_ORDER] order.
     */
    private suspend fun fetchLoopMetadata(generationId: Long): LoopMetadata {
        return coroutineScope {
            val mrmsJson = async {
                withTimeoutOrNull(RADAR_ENGINE_METADATA_TIMEOUT_MS) {
                    fetcher.fetchText(MRMS_SERVICE_INFO_URL, generationId)
                }
            }
            val lwJson = async {
                withTimeoutOrNull(RADAR_ENGINE_METADATA_TIMEOUT_MS) {
                    fetcher.fetchText(LIBRE_WXR_MAPS_URL, generationId)
                }
            }
            val mrms = mrmsJson.await()
            val lw = lwJson.await()
            LoopMetadata(
                mrmsTimesMs = mrms?.let { mrmsFrameTimesFromServiceInfoJson(it) } ?: emptyList(),
                mrmsExtent = mrms?.let { parseMrmsTimeExtent(it) },
                libreWxrMaps = lw?.let { parseLibreWxrMaps(it) }
            )
        }
    }

    /** The LibreWXR observed-loop frames from a maps document: past
     *  frames only (nowcast is server-side advection, never observed
     *  radar), pruned per [libreWxrLoopFrames], newest 10. */
    private fun libreWxrLoopFramesFromMaps(maps: LibreWxrMaps): List<LibreWxrFrame> =
        libreWxrLoopFrames(maps).filter { !it.isNowcast }
            .takeLast(RADAR_ENGINE_FRAME_COUNT)

    private fun libreWxrLoopFrameLabel(
        frame: LibreWxrFrame,
        isNewest: Boolean,
        now: Instant
    ): String =
        if (isNewest) "LIVE NOW"
        else radarFrameLabel(Instant.ofEpochSecond(frame.timeEpochSec), now)

    private fun loopFramesForLibreWxr(
        frames: List<LibreWxrFrame>,
        now: Instant
    ): List<LoopFrame> =
        frames.mapIndexed { i, frame ->
            LoopFrame(
                Instant.ofEpochSecond(frame.timeEpochSec),
                libreWxrLoopFrameLabel(frame, i == frames.lastIndex, now)
            )
        }

    /** Entry resolution: MRMS when its extent yields frames, else
     *  LibreWXR when its maps yield past frames, else the IEM
     *  skeleton (terminal fallback — always 10 frames). */
    private fun resolveLiveLoop() {
        scope.launch {
            val meta = fetchLoopMetadata(liveStore.generationId)
            meta.libreWxrMaps?.let { libreWxrMaps = it }
            val now = Instant.now()
            val lwFrames = meta.libreWxrMaps?.let { libreWxrLoopFramesFromMaps(it) }
                ?: emptyList()
            val (source, frames) = when {
                meta.mrmsTimesMs.isNotEmpty() -> {
                    meta.mrmsExtent?.let { mrmsExtent = it }
                    RadarSourceKind.MRMS to
                        mrmsLiveFrames(meta.mrmsTimesMs, now)
                            .map { LoopFrame(it.validTime, it.label) }
                }
                lwFrames.isNotEmpty() -> {
                    libreWxrLoopFrames = lwFrames
                    RadarSourceKind.LIBRE_WXR to loopFramesForLibreWxr(lwFrames, now)
                }
                else -> {
                    RadarSourceKind.IEM to
                        radarRangeFrames(RadarRange.Live, now)
                            .map { LoopFrame(it.validTime, it.label) }
                }
            }
            val oldGen = liveStore.generationId
            val res = liveStore.resolveGeneration(source, frames) ?: return@launch
            fetcher.endGeneration(oldGen)
            onLiveGenerationResolved(res)
            Log.i(TAG, "live loop resolved: $source, ${frames.size} frames (generation ${res.generationId})")
        }
    }

    /**
     * Side effects of a live generation resolving: bbox (from the
     * current camera target), byte/slot bookkeeping, the fallback's
     * sources, and the first fetch burst. The store already holds the
     * generation; this only wires it to the map.
     */
    private fun onLiveGenerationResolved(res: GenerationResolution) {
        liveGenerationStartedAtMs = SystemClock.uptimeMillis()
        liveSlowBudgetFiredGen = -1L
        lastRefreshMs = SystemClock.uptimeMillis()
        val target = currentTarget()
        liveGenerationCentre = target
        frameShownAtMs = SystemClock.uptimeMillis()
        if (res.source == RadarSourceKind.MRMS) {
            val newBbox = expandBbox(
                mrmsBboxForCentre(target.latitude, target.longitude),
                RADAR_ENGINE_MRMS_BBOX_FACTOR
            )
            if (newBbox != mrmsBytesBbox) {
                // New area: the held bytes are for the old bbox.
                mrmsBytes.clear()
            }
            mrmsBbox = newBbox
            mrmsQuad = quadFor(newBbox)
            mrmsBytesBbox = newBbox
            slotFrameIndex[0] = -1
            slotFrameIndex[1] = -1
            decodingFrame[0] = -1
            decodingFrame[1] = -1
            decodingJobs.values.forEach { it.cancel() }
            decodingJobs.clear()
        }
        if (res.source == RadarSourceKind.LIBRE_WXR) {
            buildLibreWxrSources(libreWxrLoopFrames)
        } else {
            removeLibreWxrSources()
        }
        when (res.source) {
            RadarSourceKind.MRMS -> {
                for (i in 0 until res.frameCount) requestMrmsFrame(i, res.generationId)
            }
            RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM -> {
                requestTiledFramesAhead(liveStore) { i -> liveProvingUrls(i) }
            }
        }
        refreshDisplay()
        refreshUiState()
        pokeTicker()
    }

    /**
     * The store went UNAVAILABLE: try the next source DOWN the order
     * (whole-loop re-resolution, zero carry-over). Nothing left to
     * try means the chrome shows UNAVAILABLE + RETRY.
     */
    private fun handleLiveUnavailable() {
        val store = liveStore
        if (store.state != LiveLoopState.UNAVAILABLE) return
        val current = store.source ?: return
        val next = fallbackAfter(current) ?: run {
            Log.w(TAG, "live loop unavailable: no source left after $current")
            refreshUiState()
            return
        }
        val now = Instant.now()
        val (frames, lwFrames) = when (next) {
            RadarSourceKind.LIBRE_WXR -> {
                val maps = libreWxrMaps
                if (maps == null) {
                    Log.w(TAG, "live loop unavailable: LibreWXR maps not in hand, cannot fall back")
                    refreshUiState()
                    return
                }
                val lw = libreWxrLoopFramesFromMaps(maps)
                if (lw.isEmpty()) {
                    Log.w(TAG, "live loop unavailable: LibreWXR has no past frames")
                    refreshUiState()
                    return
                }
                loopFramesForLibreWxr(lw, now) to lw
            }
            RadarSourceKind.IEM -> {
                radarRangeFrames(RadarRange.Live, now)
                    .map { LoopFrame(it.validTime, it.label) } to emptyList()
            }
            RadarSourceKind.MRMS -> return // never fall back UP
        }
        if (next == RadarSourceKind.LIBRE_WXR) libreWxrLoopFrames = lwFrames
        val oldGen = store.generationId
        val res = store.fallbackTo(next, frames) ?: run {
            refreshUiState()
            return
        }
        fetcher.endGeneration(oldGen)
        onLiveGenerationResolved(res)
        Log.i(TAG, "live loop fell back to $next (${frames.size} frames)")
    }

    // -- Frame fetching ------------------------------------------------------

    /**
     * Request MRMS frame [index]: reuse held bytes for this valid time
     * when the bbox is unchanged (a re-resolution after a retry costs
     * no network), else fetch the image through the fetcher. The
     * store's blank check runs on the bytes: a known-blank image for
     * an out-of-extent time is a stale failure, never a ready frame.
     */
    private fun requestMrmsFrame(index: Int, generationId: Long) {
        if (liveStore.generationId != generationId) return
        if (liveStore.isReady(index)) return
        val timeMs = liveStore.frameValidTime(index)?.toEpochMilli() ?: return
        val held = mrmsBytes[timeMs]
        if (held != null) {
            liveStore.requestFrame(index, generationId)
            markMrmsReady(index, generationId, timeMs, held)
            return
        }
        liveStore.requestFrame(index, generationId)
        val url = mrmsUrl(timeMs)
        scope.launch {
            when (val result = fetcher.fetch(url, generationId)) {
                is FetchResult.Success -> {
                    if (liveStore.generationId == generationId) {
                        mrmsBytes[timeMs] = result.bytes
                        mrmsBytesBbox = mrmsBbox
                    }
                    markMrmsReady(index, generationId, timeMs, result.bytes)
                }
                else -> {
                    liveStore.markFrameFailed(index, generationId)
                    onFrameSettled(liveStore, index)
                }
            }
        }
    }

    private fun markMrmsReady(
        index: Int,
        generationId: Long,
        timeMs: Long,
        bytes: ByteArray
    ) {
        val withinExtent = mrmsExtent?.let { timeMs in it.first..it.second } ?: true
        val outcome = liveStore.markFrameReady(index, generationId, bytes, withinExtent)
        if (outcome == FrameMarkOutcome.REJECTED_BLANK_STALE) {
            mrmsBytes.remove(timeMs)
            Log.i(TAG, "MRMS frame $index: blank image for out-of-extent time — stale, failed")
        }
        onFrameSettled(liveStore, index)
    }

    /** Request one tiled frame's proving set through the fetcher. */
    private fun requestTiledFrame(
        store: RadarLoopStore,
        index: Int,
        generationId: Long,
        urls: List<String>
    ) {
        if (store.generationId != generationId) return
        if (store.isReady(index)) return
        if (urls.isEmpty()) {
            store.markFrameFailed(index, generationId)
            onFrameSettled(store, index)
            return
        }
        store.requestFrame(index, generationId)
        scope.launch {
            val results = urls.map { url ->
                async { fetcher.fetch(url, generationId) }
            }.awaitAll()
            if (store.generationId != generationId) return@launch
            val bytesSeen = results.count { it is FetchResult.Success }
            // Ready when every tile settled AND at least one returned
            // bytes: a 404 / exhausted failure is a known-missing tile
            // (the map draws a hole), but a frame with NO bytes at all
            // has nothing to show and is failed, not ready.
            if (bytesSeen > 0) {
                store.markFrameReady(index, generationId)
            } else {
                store.markFrameFailed(index, generationId)
            }
            onFrameSettled(store, index)
        }
    }

    /**
     * Prove the current frame + the look-ahead window for a tiled
     * store ([urlsFor] builds the proving URLs per index). Only
     * NOT_REQUESTED frames are issued: already in-flight / ready /
     * failed frames are left alone (a retry re-issues them via
     * [RadarLoopStore.retry] + [requestLiveFrame]).
     */
    private fun requestTiledFramesAhead(
        store: RadarLoopStore,
        urlsFor: (Int) -> List<String>
    ) {
        if (store.frameCount() == 0) return
        for (step in 0..TILED_LOOK_AHEAD) {
            val i = (store.currentIndex + step) % store.frameCount()
            if (store.fetchState(i) == FrameFetchState.NOT_REQUESTED) {
                requestTiledFrame(store, i, store.generationId, urlsFor(i))
            }
        }
    }

    /** Request one live frame, whatever the live source is (used by the
     *  retry path, which re-issues in-flight/queued AND failed frames:
     *  the fetcher collapses a duplicate of an in-flight URL to the
     *  same request, so a re-issue never double-fetches). */
    private fun requestLiveFrame(index: Int, generationId: Long) {
        if (liveStore.generationId != generationId) return
        if (liveStore.isReady(index)) return
        when (liveStore.source) {
            RadarSourceKind.MRMS -> requestMrmsFrame(index, generationId)
            RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM ->
                requestTiledFrame(liveStore, index, generationId, liveProvingUrls(index))
            null -> Unit
        }
    }

    /** The proving URLs for live tiled frame [index] (centre-first). */
    private fun liveProvingUrls(index: Int): List<String> = when (liveStore.source) {
        RadarSourceKind.LIBRE_WXR -> {
            val frame = libreWxrLoopFrames.getOrNull(index) ?: return emptyList()
            libreWxrProvingUrls(frame)
        }
        RadarSourceKind.IEM -> iemProvingUrls(iemLayerNameFor(index))
        else -> emptyList()
    }

    private fun provingUrlsFor(store: RadarLoopStore, index: Int): List<String> =
        if (store === liveStore) liveProvingUrls(index)
        else {
            val frame = futureSourceFrames.getOrNull(index) ?: return emptyList()
            hrrrProvingUrls(frame.layerName)
        }

    private fun currentTileZoom(): Int =
        currentZoom().toInt().coerceIn(0, 22)

    private fun iemProvingUrls(layerName: String): List<String> {
        val target = currentTarget()
        val zoom = currentTileZoom()
        val centreX = lonToTileX(target.longitude, zoom)
        val centreY = latToTileY(target.latitude, zoom)
        return centreFirstTileOffsets(PROVING_TILES_X, PROVING_TILES_Y).map { (dx, dy) ->
            iemTileUrl(layerName, zoom, centreX + dx, centreY + dy)
        }
    }

    private fun libreWxrProvingUrls(frame: LibreWxrFrame): List<String> {
        val maps = libreWxrMaps ?: return emptyList()
        val target = currentTarget()
        val zoom = (currentTileZoom() - 1).coerceAtLeast(0)
        val centreX = lonToTileX(target.longitude, zoom)
        val centreY = latToTileY(target.latitude, zoom)
        return centreFirstTileOffsets(
            LIBRE_WXR_PROVING_TILES_X, LIBRE_WXR_PROVING_TILES_Y
        ).map { (dx, dy) ->
            libreWxrTileUrl(
                maps.host, frame.path, zoom, centreX + dx, centreY + dy,
                tileSizePx = LIBRE_WXR_TILE_SIZE_512
            )
        }
    }

    private fun hrrrProvingUrls(layerName: String): List<String> {
        val target = currentTarget()
        val zoom = currentTileZoom()
        val centreX = lonToTileX(target.longitude, zoom)
        val centreY = latToTileY(target.latitude, zoom)
        return centreFirstTileOffsets(FUTURE_PROVING_TILES_X, FUTURE_PROVING_TILES_Y)
            .map { (dx, dy) ->
                iemTileUrl(layerName, zoom, centreX + dx, centreY + dy)
            }
    }

    // -- Readiness changes ---------------------------------------------------

    /**
     * Called after any frame settles (ready or failed) in [store].
     * Starts playback when the loop primes, eager-decodes the next
     * MRMS frame, runs the fallback chain from UNAVAILABLE, and wakes
     * the ticker + the chrome. Never called for a stale generation
     * (the store's generation guard already ignored those marks).
     */
    private fun onFrameSettled(store: RadarLoopStore, index: Int) {
        // Future tail cap first: a settle can cut the tail (and move
        // the playhead) before anything below reads the loop state.
        if (store === futureStore) {
            updateFutureTail(index)
            // A proved-ready future frame starts loading its tiles
            // now (visibility), ahead of the playhead — see
            // setFutureLayerVisible.
            if (store.isReady(index)) setFutureLayerVisible(index)
        }
        if (store.state == LiveLoopState.PRIMED && !store.isPaused) {
            store.startPlayback()
        }
        if (store === liveStore && store.source == RadarSourceKind.MRMS &&
            store === activeStore() && store.isReady(index)
        ) {
            if (index == store.currentIndex) {
                maybeDecodeMrmsFrame(index)
            } else if (index == store.nextIndex() &&
                slotFrameIndex.contains(store.currentIndex)
            ) {
                maybeDecodeMrmsFrame(index)
            }
        }
        if (store.state == LiveLoopState.UNAVAILABLE) {
            if (store === liveStore) {
                handleLiveUnavailable()
            }
        } else if (store === activeStore()) {
            if (store.isReady(store.currentIndex)) refreshDisplay()
            pokeTicker()
        }
        refreshUiState()
    }

    /**
     * Future tail cap (Phase 4): frame [index]'s proving settle is
     * also its served/failed signal. Record it, re-run
     * [capFutureFrames] over the store's frames, and cap the store
     * when the first failure cuts the tail. Runs on every future
     * settle — including the slow-budget marks (a stalled frame is
     * a failed signal) and in-flight proves that settle after a cap
     * (their signals are recorded against the full list, but the cap
     * over the smaller store list ignores them). A frame that
     * settles after its generation ended is ignored (its fetch
     * state is null out of range).
     */
    private fun updateFutureTail(index: Int) {
        val store = futureStore
        if (store.generationId == 0L) return
        if (index !in futureAvailability.indices) return
        val signal = when (store.fetchState(index)) {
            FrameFetchState.READY -> FutureFrameAvailability.SERVED
            FrameFetchState.FAILED -> FutureFrameAvailability.FAILED
            else -> return
        }
        if (futureAvailability[index] == signal) return
        futureAvailability[index] = signal
        applyFutureTailCap()
    }

    /** Re-run the tail cap over the future store's current frames. */
    private fun applyFutureTailCap() {
        val store = futureStore
        if (store.generationId == 0L || store.frameCount() == 0) return
        val storeFrames = store.frames.map { RadarFrame(it.validTime, "", it.label) }
        val result = capFutureFrames(
            storeFrames,
            futureAvailability.take(storeFrames.size)
        )
        futureTail = result
        if (result.playableFrames.size < storeFrames.size) {
            val changed = store.applyTailCap(
                result.playableFrames.map { LoopFrame(it.validTime, it.label) }
            )
            if (changed) {
                Log.i(
                    TAG,
                    "future tail capped: ${storeFrames.size} -> " +
                        "${result.playableFrames.size} frames, " +
                        "end note: ${result.endNote}"
                )
                refreshDisplay()
                pokeTicker()
            }
        }
    }

    // -- Ticker (dwell, no-timeout advance, slow budget) --------------------

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                if (activityResumed) tickLoop()
                val store = activeStore()
                val remaining = when {
                    !activityResumed -> 1000L
                    store.isPaused -> 1000L
                    store.state == LiveLoopState.LOADING ||
                        store.state == LiveLoopState.UNAVAILABLE ||
                        store.state == LiveLoopState.WAITING_NEXT -> 1000L
                    else -> {
                        val elapsed = SystemClock.uptimeMillis() - frameShownAtMs
                        (store.dwellMsFor(store.currentIndex) - elapsed).coerceAtLeast(50L)
                    }
                }
                // Poke-driven: a readiness change wakes the loop at once;
                // the timeout is only the backstop.
                withTimeoutOrNull<Unit>(remaining) { tickerPoke.receive() }
            }
        }
    }

    private fun tickLoop() {
        if (selectedLayer != EngineLayer.Radar) return
        val store = activeStore()
        if (store.frameCount() == 0) return
        checkSlowBudget(store)
        // The loop has primed but the playhead sits on a frame that
        // never became ready (frames settle in random order): move it
        // to the newest ready frame instead of holding on a blank map.
        // Only in PRIMED — a scrub to an unready frame leaves the
        // state as WAITING_NEXT/LOADING, never PRIMED, so a user's
        // scrub target is never yanked away.
        if (store.state == LiveLoopState.PRIMED &&
            !store.isReady(store.currentIndex) && store.readyCount() > 0
        ) {
            val newestReady = (store.frameCount() - 1 downTo 0)
                .firstOrNull { store.isReady(it) }
            if (newestReady != null && newestReady != store.currentIndex) {
                store.scrubTo(newestReady)
                frameShownAtMs = SystemClock.uptimeMillis()
                refreshDisplay()
                refreshUiState()
                return
            }
        }
        val elapsed = SystemClock.uptimeMillis() - frameShownAtMs
        when (val outcome = store.tryAdvance(elapsed)) {
            is AdvanceOutcome.Advanced -> onFrameAdvanced(store, outcome.toIndex)
            is AdvanceOutcome.Hold -> refreshUiState()
        }
    }

    private fun onFrameAdvanced(store: RadarLoopStore, toIndex: Int) {
        refreshDisplay()
        if (store.source == RadarSourceKind.MRMS) {
            val next = store.nextIndex()
            if (store.isReady(next)) maybeDecodeMrmsFrame(next)
        } else {
            requestTiledFramesAhead(store) { i -> provingUrlsFor(store, i) }
        }
        refreshUiState()
    }

    /**
     * The slow budget (20 s, [RADAR_ENGINE_LIVE_SLOW_BUDGET_MS]): a
     * generation with NO ready frame after 20 s counts as stalled. A
     * healthy cold MRMS loop primes in ~3 s and completes in ~6 s, so
     * 20 s is 3x past healthy and matches the forecast trigger's 20 s
     * precedent. The attempted frames are marked failed (a late
     * completion can still revive the loop — the store flips
     * UNAVAILABLE back to PRIMED on a ready frame), then the fallback
     * chain runs. Fires once per generation.
     */
    private fun checkSlowBudget(store: RadarLoopStore) {
        if (store.state != LiveLoopState.LOADING) return
        if (store.readyCount() > 0) return
        val startedAt = if (store === liveStore) liveGenerationStartedAtMs
            else futureGenerationStartedAtMs
        if (startedAt == 0L) return
        if (SystemClock.uptimeMillis() - startedAt < RADAR_ENGINE_LIVE_SLOW_BUDGET_MS) return
        val firedGen = if (store === liveStore) liveSlowBudgetFiredGen
            else futureSlowBudgetFiredGen
        if (firedGen == store.generationId) return
        if (store === liveStore) liveSlowBudgetFiredGen = store.generationId
        else futureSlowBudgetFiredGen = store.generationId
        val gen = store.generationId
        var anyAttempted = false
        for (i in 0 until store.frameCount()) {
            when (store.fetchState(i)) {
                FrameFetchState.IN_FLIGHT, FrameFetchState.FAILED -> {
                    anyAttempted = true
                    store.markFrameFailed(i, gen)
                }
                else -> Unit
            }
        }
        if (!anyAttempted) {
            Log.w(TAG, "slow budget: no frame ever requested for generation $gen")
            return
        }
        Log.w(TAG, "slow budget: no frame ready in 20 s, generation $gen marked failed")
        if (store === futureStore) {
            // A stalled frame is a failed signal: feed the tail cap
            // so the loop caps (or goes unavailable) on the same
            // facts instead of holding stale UNKNOWNs.
            for (i in 0 until store.frameCount()) updateFutureTail(i)
        }
        if (store === liveStore) handleLiveUnavailable()
        refreshUiState()
    }

    // -- Refresh (5-min timer + on resume) ---------------------------------

    private fun startRefreshTimer() {
        scope.launch {
            while (isActive) {
                delay(RADAR_ENGINE_LOOP_REFRESH_MS)
                if (activityResumed) refreshLoops()
            }
        }
    }

    private suspend fun refreshLoops() {
        if (refreshInFlight) return
        refreshInFlight = true
        try {
            refreshLiveLoop()
            if (futureStore.generationId > 0L) refreshFutureLoop()
            refreshSatelliteIfDue()
            lastRefreshMs = SystemClock.uptimeMillis()
        } finally {
            refreshInFlight = false
        }
    }

    /**
     * Re-resolve the live frame list + MRMS extent. Readiness carries
     * by valid time for MRMS ([RadarLoopStore.refreshFrames]); an empty
     * or backwards-moving list HOLDS the previous frames — never blanks
     * the loop. A tiled (LibreWXR) refresh is a re-resolution: its URLs
     * embed the frame time, so a changed list means new sources. IEM
     * needs no refresh (its layer names are relative — the same 10
     * sources always show the current loop).
     */
    private suspend fun refreshLiveLoop() {
        val store = liveStore
        if (store.generationId == 0L) return
        val meta = fetchLoopMetadata(store.generationId)
        meta.libreWxrMaps?.let { libreWxrMaps = it }
        when (store.source) {
            RadarSourceKind.MRMS -> {
                val extent = meta.mrmsExtent
                if (meta.mrmsTimesMs.isEmpty() || extent == null) {
                    store.markMetadataFailed()
                    Log.w(TAG, "refresh: MRMS metadata failed — loop keeps playing old frames")
                    return
                }
                mrmsExtent = extent
                val newFrames = mrmsLiveFrames(meta.mrmsTimesMs, Instant.now())
                    .map { LoopFrame(it.validTime, it.label) }
                when (val result = store.refreshFrames(newFrames, System.currentTimeMillis())) {
                    is RefreshResult -> when (result.outcome) {
                        RefreshOutcome.APPLIED -> {
                            Log.i(TAG, "refresh: MRMS list applied, ${result.carriedReady} frames carried ready")
                            for (i in newFrames.indices) {
                                if (!store.isReady(i)) requestMrmsFrame(i, store.generationId)
                            }
                        }
                        RefreshOutcome.HELD_PREVIOUS_EMPTY ->
                            Log.i(TAG, "refresh: new MRMS list empty — previous frames held")
                        RefreshOutcome.HELD_PREVIOUS_REGRESSED ->
                            Log.i(TAG, "refresh: MRMS extent regressed (${result.regression}) — previous frames held")
                        RefreshOutcome.NO_CHANGE, RefreshOutcome.NO_GENERATION -> Unit
                    }
                }
            }
            RadarSourceKind.LIBRE_WXR -> {
                val maps = meta.libreWxrMaps ?: run {
                    store.markMetadataFailed()
                    Log.w(TAG, "refresh: LibreWXR maps failed — loop keeps playing old frames")
                    return
                }
                val lwFrames = libreWxrLoopFramesFromMaps(maps)
                if (lwFrames.isEmpty()) {
                    Log.w(TAG, "refresh: LibreWXR has no past frames — previous frames held")
                    return
                }
                if (lwFrames.map { it.timeEpochSec } ==
                    libreWxrLoopFrames.map { it.timeEpochSec }
                ) return // no change
                libreWxrLoopFrames = lwFrames
                reresolveLiveGeneration(
                    RadarSourceKind.LIBRE_WXR,
                    loopFramesForLibreWxr(lwFrames, Instant.now())
                )
            }
            RadarSourceKind.IEM, null -> Unit
        }
        refreshUiState()
        pokeTicker()
    }

    /** Re-resolve the live generation, preserving the user's pause and
     *  the playhead's frame (by valid time) when it survives. */
    private fun reresolveLiveGeneration(source: RadarSourceKind, frames: List<LoopFrame>) {
        val store = liveStore
        val wasPaused = store.isPaused
        val oldTime = store.currentFrame()?.validTime
        val oldGen = store.generationId
        val res = store.resolveGeneration(source, frames) ?: return
        fetcher.endGeneration(oldGen)
        if (wasPaused) store.setPaused(true)
        oldTime?.let { t ->
            val idx = store.frames.indexOfFirst { it.validTime == t }
            if (idx >= 0) store.scrubTo(idx)
        }
        onLiveGenerationResolved(res)
    }

    private suspend fun refreshFutureLoop() {
        val store = futureStore
        if (store.generationId == 0L) return
        val frames = radarRangeFrames(futureRadarRange(), Instant.now())
        if (frames == futureSourceFrames) return
        rebuildFutureSources(frames)
        val wasPaused = store.isPaused
        val oldTime = store.currentFrame()?.validTime
        val oldGen = store.generationId
        val res = store.resolveGeneration(
            RadarSourceKind.IEM,
            frames.map { LoopFrame(it.validTime, it.label) }
        ) ?: return
        fetcher.endGeneration(oldGen)
        futureResolvedRange = selectedRange
        futureAvailability = frames.map { FutureFrameAvailability.UNKNOWN }.toMutableList()
        futureTail = null
        if (wasPaused) store.setPaused(true)
        oldTime?.let { t ->
            val idx = store.frames.indexOfFirst { it.validTime == t }
            if (idx >= 0) store.scrubTo(idx)
        }
        futureGenerationStartedAtMs = SystemClock.uptimeMillis()
        futureSlowBudgetFiredGen = -1L
        requestTiledFramesAhead(store) { i -> provingUrlsFor(store, i) }
        refreshDisplay()
        refreshUiState()
        pokeTicker()
    }

    // -- Pan / zoom settle ---------------------------------------------------

    /**
     * Map-idle settle (NOT a readiness signal — readiness is proven
     * only by the fetcher). Two jobs: re-resolve the MRMS loop when
     * the camera target moved past the 0.5° pan margin, and retarget
     * the satellite WMS image on pan/zoom settle. The zoom range is
     * deliberately NOT clamped here: the map's own min/max zoom
     * preferences (set in start) bound every gesture during the
     * pinch, so a zoom-out stays where the user's fingers left it.
     * A post-settle clamp here was the auto-zoom bug (2026-11-15).
     * Tiled sources need no re-resolution on pan: their URLs do not
     * embed the bbox, and MapLibre fetches the new area itself.
     */
    private fun onMapIdle() {
        val map = mapLibreMap ?: return
        if (loadedStyle == null) return
        val camera = map.cameraPosition
        val target = camera.target ?: placeCentre
        if (selectedLayer != EngineLayer.Radar) {
            onSatelliteMapIdle(target, camera.zoom)
            return
        }
        if (selectedRange != EngineRange.Live) return
        if (liveStore.generationId == 0L) return
        if (liveStore.source != RadarSourceKind.MRMS) return
        val centre = liveGenerationCentre ?: return
        if (panPastMargin(centre.latitude, centre.longitude, target.latitude, target.longitude)) {
            Log.i(TAG, "pan past margin (target ${target.latitude}, ${target.longitude}) — new generation")
            reresolveLiveForBbox()
        }
    }

    /** Re-resolve the MRMS loop with the same frames but a new bbox
     *  (every MRMS URL changes, so it is a new generation — readiness
     *  resets, the old image holds until the new one decodes). */
    private fun reresolveLiveForBbox() {
        val store = liveStore
        if (store.generationId == 0L) return
        if (store.source != RadarSourceKind.MRMS) return
        val frames = store.frames.toList()
        val wasPaused = store.isPaused
        val oldTime = store.currentFrame()?.validTime
        val oldGen = store.generationId
        val res = store.resolveGeneration(RadarSourceKind.MRMS, frames) ?: return
        fetcher.endGeneration(oldGen)
        if (wasPaused) store.setPaused(true)
        oldTime?.let { t ->
            val idx = store.frames.indexOfFirst { it.validTime == t }
            if (idx >= 0) store.scrubTo(idx)
        }
        onLiveGenerationResolved(res)
        Log.i(TAG, "live loop re-resolved for new bbox (generation ${res.generationId})")
    }

    // -- UI callbacks --------------------------------------------------------

    fun selectLayer(layer: EngineLayer) {
        selectedLayer = layer
        if (layer != EngineLayer.Radar) {
            wmsLayerFor(layer)?.let { beginSatelliteVisit(it) }
            applyLayerAndFrame()
            frameShownAtMs = SystemClock.uptimeMillis()
            refreshUiState()
            pokeTicker()
            wmsLayerFor(layer)?.let { resolveSatellite(it, SatResolveReason.LAYER_SWITCH) }
        } else {
            applyLayerAndFrame()
            frameShownAtMs = SystemClock.uptimeMillis()
            refreshUiState()
            pokeTicker()
        }
    }

    fun selectRange(range: EngineRange) {
        selectedRange = range
        if (range == EngineRange.Live) {
            if (liveStore.generationId == 0L) resolveLiveLoop()
        } else {
            // A range switch re-resolves even with a generation in
            // hand (6 h / 24 h / 48 h are different frame lists); a
            // re-tap of the same range does not.
            if (futureStore.generationId == 0L || futureResolvedRange != range) {
                resolveFutureLoop()
            }
        }
        applyLayerAndFrame()
        frameShownAtMs = SystemClock.uptimeMillis()
        refreshUiState()
        pokeTicker()
    }

    /** The [RadarRange] for the selected future range (Live never reaches here). */
    private fun futureRadarRange(): RadarRange =
        when (selectedRange) {
            EngineRange.Future48h -> RadarRange.Future48h
            EngineRange.Future6h -> RadarRange.Future6h
            else -> RadarRange.Future24h
        }

    /** Resolve the future range on first visit (fresh HRRR run), or
     *  on a range switch (6 h / 24 h / 48 h are different frame lists —
     *  the old 16-frame window made them look identical, so this
     *  used to resolve Future24h unconditionally). The playhead's
     *  valid time and the pause survive a re-resolution when the
     *  frame is still in the new list; a first resolution starts at
     *  the nearest forecast (frame 0), like Phase 1 — the store
     *  starts at the newest frame, which is ~2 days out. */
    private fun resolveFutureLoop() {
        val range = selectedRange
        if (range == EngineRange.Live) return
        val frames = radarRangeFrames(futureRadarRange(), Instant.now())
        rebuildFutureSources(frames)
        val hadGeneration = futureStore.generationId > 0L
        val wasPaused = futureStore.isPaused
        val oldTime = futureStore.currentFrame()?.validTime
        val oldGen = futureStore.generationId
        val res = futureStore.resolveGeneration(
            RadarSourceKind.IEM,
            frames.map { LoopFrame(it.validTime, it.label) }
        ) ?: return
        fetcher.endGeneration(oldGen)
        futureResolvedRange = range
        futureAvailability = frames.map { FutureFrameAvailability.UNKNOWN }.toMutableList()
        futureTail = null
        futureGenerationStartedAtMs = SystemClock.uptimeMillis()
        futureSlowBudgetFiredGen = -1L
        frameShownAtMs = SystemClock.uptimeMillis()
        if (hadGeneration) {
            if (wasPaused) futureStore.setPaused(true)
            oldTime?.let { t ->
                val idx = futureStore.frames.indexOfFirst { it.validTime == t }
                if (idx >= 0) futureStore.scrubTo(idx)
            }
        } else {
            // Start at the nearest forecast (frame 0), like Phase 1 — the
            // store starts at the newest frame, which is ~2 days out.
            futureStore.scrubTo(0)
        }
        requestTiledFramesAhead(futureStore) { i -> provingUrlsFor(futureStore, i) }
        refreshDisplay()
        refreshUiState()
        pokeTicker()
    }

    fun onPlayPause() {
        val store = activeStore()
        when {
            store.isPaused -> store.setPaused(false)
            store.state == LiveLoopState.PRIMED -> store.startPlayback()
            store.state == LiveLoopState.PLAYING ||
                store.state == LiveLoopState.WAITING_NEXT -> store.setPaused(true)
            else -> Unit // LOADING / UNAVAILABLE: the button is disabled
        }
        pokeTicker()
        refreshUiState()
    }

    /**
     * RETRY: on satellite, force a re-probe and re-attempt WMS first
     * (a fresh visit — even when the previous visit fell back to
     * tiles); on radar, re-resolve metadata and take the best source
     * available now (the path back UP to MRMS after an outage), or —
     * when the loop and the frame list are unchanged — re-request
     * everything not ready, current first (a retry that only
     * re-requests failures is a no-op for a stalled queue). Future
     * ranges retry as a fresh generation (new HRRR run check): the
     * cap and the availability signals reset with it, so a retry
     * can lift a cap the old in-generation re-request could not.
     */
    fun onRetry() {
        if (selectedLayer != EngineLayer.Radar) {
            val layer = wmsLayerFor(selectedLayer) ?: return
            satTimestampCache.recordFetchFailure(layer)
            beginSatelliteVisit(layer)
            applyLayerAndFrame()
            resolveSatellite(layer, SatResolveReason.RETRY)
            refreshUiState()
            pokeTicker()
            return
        }
        if (selectedRange != EngineRange.Live) {
            // Future RETRY is a fresh generation (new HRRR run
            // check): the cap, the availability signals and the
            // proving all reset, and the loop re-proves from the
            // playhead's frame. The old in-generation re-request
            // could not lift a cap — the capped frames were no
            // longer in the store to re-request.
            resolveFutureLoop()
            refreshUiState()
            pokeTicker()
            return
        }
        scope.launch {
            val store = liveStore
            val meta = fetchLoopMetadata(store.generationId)
            meta.libreWxrMaps?.let { libreWxrMaps = it }
            val now = Instant.now()
            val lwFrames = meta.libreWxrMaps?.let { libreWxrLoopFramesFromMaps(it) }
                ?: emptyList()
            val (bestSource, bestFrames) = when {
                meta.mrmsTimesMs.isNotEmpty() -> {
                    meta.mrmsExtent?.let { mrmsExtent = it }
                    RadarSourceKind.MRMS to
                        mrmsLiveFrames(meta.mrmsTimesMs, now)
                            .map { LoopFrame(it.validTime, it.label) }
                }
                lwFrames.isNotEmpty() -> {
                    libreWxrLoopFrames = lwFrames
                    RadarSourceKind.LIBRE_WXR to loopFramesForLibreWxr(lwFrames, now)
                }
                else -> {
                    RadarSourceKind.IEM to
                        radarRangeFrames(RadarRange.Live, now)
                            .map { LoopFrame(it.validTime, it.label) }
                }
            }
            val sameSource = store.source == bestSource
            val sameFrames = store.frames.map { it.validTime } ==
                bestFrames.map { it.validTime }
            if (store.generationId == 0L || !sameSource || !sameFrames) {
                val oldGen = store.generationId
                val res = store.resolveGeneration(bestSource, bestFrames) ?: return@launch
                fetcher.endGeneration(oldGen)
                onLiveGenerationResolved(res)
                Log.i(TAG, "retry: fresh generation on $bestSource")
            } else {
                val plan = store.retry()
                Log.i(TAG, "retry: re-requesting ${plan.frameIndices.size} frames")
                for (i in plan.frameIndices) requestLiveFrame(i, plan.generationId)
            }
            refreshUiState()
            pokeTicker()
        }
    }

    fun onScrubStart() {
        val store = activeStore()
        wasPlayingBeforeScrub = !store.isPaused &&
            (store.state == LiveLoopState.PLAYING || store.state == LiveLoopState.WAITING_NEXT)
        store.setPaused(true)
        pokeTicker()
        refreshUiState()
    }

    fun scrubTo(index: Int) {
        val store = activeStore()
        if (store.scrubTo(index)) {
            frameShownAtMs = SystemClock.uptimeMillis()
            refreshDisplay()
            if (store.source != RadarSourceKind.MRMS) {
                requestTiledFramesAhead(store) { i -> provingUrlsFor(store, i) }
            }
            refreshUiState()
            pokeTicker()
        }
    }

    fun onScrubEnd() {
        val store = activeStore()
        if (wasPlayingBeforeScrub) store.setPaused(false)
        pokeTicker()
        refreshUiState()
    }

    // -- Chrome state --------------------------------------------------------

    private fun sourceLabel(kind: RadarSourceKind): String = when (kind) {
        RadarSourceKind.MRMS -> "MRMS"
        RadarSourceKind.LIBRE_WXR -> "LIBREWXR"
        RadarSourceKind.IEM -> "IEM"
    }

    private fun refreshUiState() {
        // The blip's display state follows the loop beat, so it is
        // refreshed with the chrome (every advance, pause, scrub,
        // range/layer switch and settle funnels through here).
        updateBlip()
        val store = activeStore()
        val frames = store.frames.map { RadarFrame(it.validTime, "", it.label) }
        uiFrames = frames
        uiCurrentIndex = store.currentIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        uiIsPlaying = !store.isPaused &&
            (store.state == LiveLoopState.PLAYING || store.state == LiveLoopState.WAITING_NEXT)
        uiPlayEnabled = store.state == LiveLoopState.PRIMED ||
            store.state == LiveLoopState.PLAYING ||
            store.state == LiveLoopState.WAITING_NEXT
        if (selectedLayer != EngineLayer.Radar) {
            // Satellite chrome is driven by the satellite state, not
            // the loop store: a satellite visit can load while the
            // loop is still loading behind it, and must never show
            // "LOADING LOOP…" for a satellite fetch. Cost is the
            // whole switch's actual bytes (probes included), via the
            // satellite generation — never the loop's generation.
            uiStatusText = if (satState == SatState.LOADING) "LOADING SATELLITE…" else null
            uiUnavailableText = if (satState == SatState.UNAVAILABLE) {
                "SATELLITE UNAVAILABLE"
            } else {
                null
            }
            uiSourceLabel = when (satMode) {
                SatMode.WMS -> "GIBS WMS"
                SatMode.TILES -> "GIBS TILES"
                SatMode.NONE -> null
            }
            uiEndNote = null
            val satBytesFetched = fetcher.totalBytesFetched(satGenerationId)
            uiCostText = if (satBytesFetched > 0) {
                satelliteCostText(satBytesFetched)
            } else {
                null
            }
            return
        }
        uiStatusText = when (store.state) {
            LiveLoopState.LOADING -> "LOADING LOOP…"
            LiveLoopState.WAITING_NEXT -> "WAITING FOR NEXT FRAME…"
            else -> null
        }
        uiUnavailableText = if (store.state == LiveLoopState.UNAVAILABLE) {
            if (selectedRange == EngineRange.Live) "LIVE RADAR UNAVAILABLE"
            else "FORECAST RADAR UNAVAILABLE"
        } else {
            null
        }
        uiSourceLabel = when {
            selectedRange != EngineRange.Live -> "HRRR"
            else -> store.source?.let { sourceLabel(it) }
        }
        // The tail end note, only while the future loop is capped
        // with frames still to play ("FORECAST ENDS ABOUT 11:00
        // UTC"). When the cap leaves nothing playable the
        // unavailable panel above carries the state instead — the
        // note would only duplicate it.
        uiEndNote = if (selectedRange != EngineRange.Live &&
            futureTail?.capped == true && futureStore.frameCount() > 0
        ) {
            futureTail?.endNote
        } else {
            null
        }
        val bytes = fetcher.totalBytesFetched(store.generationId)
        uiCostText = if (bytes > 0) {
            // Future ranges report their actual fetched bytes
            // through the future helper — never the old "ABOUT
            // 3.5 MB / 5 MB THEN CACHED" estimates, and without a
            // cache claim nothing honours (see FutureRadarTail.kt).
            if (selectedRange != EngineRange.Live) futureCostTextFromBytes(bytes)
            else loopLoadCostText(bytes)
        } else {
            null
        }
    }

    private fun pokeTicker() {
        tickerPoke.trySend(Unit)
    }

    // -- Camera (MapLibre gestures handle pan/zoom/rotate; these are the
    //   +/- and recenter buttons, driving the camera directly) -------------

    fun zoomByStep(delta: Int) {
        val map = mapLibreMap ?: return
        val current = map.cameraPosition
        // Uniform 5..12 on every layer (RADAR_ENGINE_ZOOM_MIN/MAX) —
        // the same range the map preferences enforce for gestures,
        // so the buttons and the pinch agree on the floor.
        val newZoom = (current.zoom + delta)
            .coerceIn(
                RADAR_ENGINE_ZOOM_MIN.toDouble(),
                RADAR_ENGINE_ZOOM_MAX.toDouble()
            )
        map.cameraPosition = CameraPosition.Builder()
            .target(current.target).zoom(newZoom)
            .bearing(current.bearing).tilt(current.tilt).build()
    }

    fun recenter() {
        val map = mapLibreMap ?: return
        map.cameraPosition = CameraPosition.Builder()
            .target(placeCentre).zoom(RADAR_ENGINE_ZOOM)
            .bearing(0.0).tilt(0.0).build()
    }
}
