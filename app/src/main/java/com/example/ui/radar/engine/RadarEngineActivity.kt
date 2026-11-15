package com.example.ui.radar.engine

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.BuildConfig
import com.example.core.HttpClients
import com.example.data.mapper.LonLatBbox
import com.example.data.mapper.RadarFrame
import com.example.data.mapper.RadarRange
import com.example.data.mapper.gibsTimestamp
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.MRMS_SERVICE_INFO_URL
import com.example.data.mapper.mrmsExportImageUrl
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.radarRangeFrames
import com.example.ui.theme.AuraWeatherTheme
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Phase 1 — the MapLibre map engine, hosted standalone.
 *
 * Debug-gated entry (the proven spike pattern): the activity finishes
 * immediately on non-debug builds, and its launcher entry exists only
 * in `app/src/debug/AndroidManifest.xml`, so the release APK gains no
 * launcher entry and no engine behaviour. The old Radar tab is
 * untouched — this screen runs beside it until the Phase 5 cutover.
 *
 * What this activity owns (the screen file is chrome only):
 * - `MapView` in `AndroidView`, full lifecycle forwarding;
 *   `MapLibre.getInstance` before creation.
 * - The raster-only style (JSON from [buildRadarStyleJson]) + the
 *   programmatically added sources, in draw order: 10 volatile live
 *   frame sources (shape (a), prefetch OFF), a bounded window of
 *   volatile HRRR future sources (prefetch OFF), 2 MRMS ImageSource
 *   slots, 150 ms opacity crossfades. Volatile/prefetch are set in
 *   Kotlin at source creation — never the `"volatile": true` JSON flag.
 * - Live = IEM frames, scrubbed manually with the slider; when the
 *   MRMS metadata arrives, the newest MRMS frame fades in over the
 *   newest IEM frame (MRMS is the primary; older frames stay IEM).
 *   No loop, no frame store, no prefetch of the loop — that is Phase 2.
 * - Pan/zoom/rotate are MapLibre's own gestures (on by default);
 *   the zoom/recenter buttons drive the camera directly.
 * - Centre is Cincinnati (Robert's area) until the Phase 5 cutover
 *   wires the screen to the place state.
 *
 * Known Phase 1 limits (Phase 2/3/4 own them, stated here so nobody
 * re-derives them as bugs): the MRMS image is fixed to the entry
 * bbox (1.5x wide margin — small pans are free, large pans expose
 * basemap at the edge until Phase 2 refetches on settle); the GIBS
 * timestamp is frozen at style build (Phase 3 owns probing/refresh);
 * future ranges show only the first [RADAR_ENGINE_FUTURE_WINDOW]
 * frames (Phase 4 owns the full ranges through the frame store);
 * the frame list and MRMS metadata are fetched once at entry, never
 * refreshed (Phase 2 owns the 5-min refresh).
 */
class RadarEngineActivity : ComponentActivity() {

    companion object {
        private const val TAG = "RadarEngine"

        /** Camera default: Cincinnati, OH (Robert's area). */
        private val ENGINE_CENTER = LatLng(RADAR_ENGINE_CENTER_LAT, RADAR_ENGINE_CENTER_LON)
    }

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null

    private val liveFrameLayers = mutableListOf<RasterLayer>()
    private val futureFrameLayers = mutableListOf<RasterLayer>()
    private var satVisLayer: RasterLayer? = null
    private var satIrLayer: RasterLayer? = null
    private var mrmsSourceA: ImageSource? = null
    private var mrmsSourceB: ImageSource? = null
    private var mrmsLayerA: RasterLayer? = null
    private var mrmsLayerB: RasterLayer? = null

    // Frame lists, computed once at entry (Phase 2 owns refresh).
    private lateinit var liveFrames: List<RadarFrame>
    private lateinit var future24Frames: List<RadarFrame>
    private lateinit var future48Frames: List<RadarFrame>

    // UI state.
    private var selectedLayer by mutableStateOf(EngineLayer.Radar)
    private var selectedRange by mutableStateOf(EngineRange.Live)
    private var frameIndex by mutableStateOf(RADAR_ENGINE_FRAME_COUNT - 1)

    private val displayFrames: List<RadarFrame>
        get() = when (selectedRange) {
            EngineRange.Live -> liveFrames
            EngineRange.Future24h -> future24Frames.take(RADAR_ENGINE_FUTURE_WINDOW)
            EngineRange.Future48h -> future48Frames.take(RADAR_ENGINE_FUTURE_WINDOW)
        }

    // MRMS: newest frame, once its metadata arrives.
    private var mrmsReady = false
    private var mrmsRetargeted = false
    private var pendingMrmsNewestMs: Long? = null
    private val mrmsBbox: RadarBbox =
        expandBbox(RADAR_ENGINE_MRMS_BBOX_1X, RADAR_ENGINE_MRMS_BBOX_FACTOR)
    private val mrmsQuad: LatLngQuad
        get() = LatLngQuad(
            LatLng(mrmsBbox.northLat, mrmsBbox.westLon),
            LatLng(mrmsBbox.northLat, mrmsBbox.eastLon),
            LatLng(mrmsBbox.southLat, mrmsBbox.eastLon),
            LatLng(mrmsBbox.southLat, mrmsBbox.westLon)
        )

    /** MRMS shows only on the newest live frame, over the IEM frames. */
    private val isMrmsShown: Boolean
        get() = mrmsReady &&
            selectedLayer == EngineLayer.Radar &&
            selectedRange == EngineRange.Live &&
            frameIndex == liveFrames.size - 1

    private fun mrmsUrl(timeMs: Long): String = mrmsExportImageUrl(
        LonLatBbox(mrmsBbox.westLon, mrmsBbox.southLat, mrmsBbox.eastLon, mrmsBbox.northLat),
        timeMs,
        RADAR_ENGINE_MRMS_IMAGE_WIDTH_PX,
        RADAR_ENGINE_MRMS_IMAGE_HEIGHT_PX
    )

    /** MRMS frame time: now rounded down to the 5-min cadence, minus [backMin] min. */
    private fun mrmsTime(backMin: Long): Long {
        val now = System.currentTimeMillis()
        val step = 5 * 60 * 1000L
        return (now / step) * step - backMin * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            // Release builds carry the engine code (it ships at the
            // Phase 5 cutover), but the standalone entry never runs
            // outside debug.
            finish()
            return
        }
        val now = Instant.now()
        liveFrames = radarRangeFrames(RadarRange.Live, now)
        future24Frames = radarRangeFrames(RadarRange.Future24h, now)
        future48Frames = radarRangeFrames(RadarRange.Future48h, now)

        MapLibre.getInstance(this)
        val view = MapView(this)
        mapView = view
        view.onCreate(savedInstanceState)
        view.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder()
                .target(ENGINE_CENTER).zoom(RADAR_ENGINE_ZOOM).build()
            // GIBS time is frozen at style build in Phase 1 (Phase 3
            // owns per-path probing and refresh).
            val gibsTime = gibsTimestamp(now)
            map.setStyle(
                Style.Builder().fromJson(buildRadarStyleJson(gibsTime, gibsTime)),
                Style.OnStyleLoaded { style ->
                    loadedStyle = style
                    Log.i(TAG, "style loaded: raster-only JSON OK")
                    addEngineSourcesAndLayers(style)
                    applyLayerAndFrame()
                    maybeApplyMrms()
                }
            )
        }
        fetchMrmsMetadata()
        setContent {
            AuraWeatherTheme {
                RadarEngineScreen(
                    mapView = mapView!!,
                    selectedLayer = selectedLayer,
                    selectedRange = selectedRange,
                    frames = displayFrames,
                    currentFrameIndex = frameIndex,
                    onSelectLayer = { selectLayer(it) },
                    onSelectRange = { selectRange(it) },
                    onScrubFrame = { scrubTo(it) },
                    onZoomIn = { zoomByStep(+1) },
                    onZoomOut = { zoomByStep(-1) },
                    onRecenter = { recenter() }
                )
            }
        }
    }

    // -- MapView lifecycle -------------------------------------------------

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    // -- Style setup ---------------------------------------------------------

    private fun addEngineSourcesAndLayers(style: Style) {
        // Shape (a): 10 per-frame volatile live sources + layers.
        // Volatile = never persist to the ambient cache; prefetch
        // delta 0 = no low-res parent prefetch on radar sources
        // (Robert's data budget). Newest frame visible.
        try {
            for (i in liveFrames.indices) {
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
                        PropertyFactory.rasterOpacity(if (i == liveFrames.size - 1) 1f else 0f)
                    )
                style.addLayer(layer)
                liveFrameLayers.add(layer)
            }
            Log.i(TAG, "added ${liveFrames.size} volatile live frame sources; newest visible")
        } catch (e: Exception) {
            Log.w(TAG, "adding live frame sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Future window: one volatile source per HRRR frame in the
        // window (first 16 of the range; Phase 4 owns the rest).
        // The 24 h and 48 h ranges share this prefix (same HRRR run),
        // so one window serves both range pills.
        try {
            val window = future24Frames.take(RADAR_ENGINE_FUTURE_WINDOW)
            for ((i, frame) in window.withIndex()) {
                val source = RasterSource(
                    radarFutureFrameSourceId(i),
                    TileSet("2.2.0", hrrrFrameTileTemplate(frame.layerName)),
                    256
                )
                source.isVolatile = true
                source.prefetchZoomDelta = 0
                style.addSource(source)
                val layer = RasterLayer(radarFutureFrameLayerId(i), radarFutureFrameSourceId(i))
                    .withProperties(PropertyFactory.rasterOpacity(0f))
                style.addLayer(layer)
                futureFrameLayers.add(layer)
            }
            Log.i(TAG, "added ${window.size} volatile future frame sources (window)")
        } catch (e: Exception) {
            Log.w(TAG, "adding future frame sources failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // MRMS two-slot ImageSources (wide-margin bbox, png32
        // exportImage). Placeholder times until the metadata fetch
        // lands; slot A is retargeted to the true newest time then.
        try {
            mrmsSourceA = ImageSource(
                RADAR_ENGINE_MRMS_SLOT_A, mrmsQuad, URI(mrmsUrl(mrmsTime(backMin = 15)))
            )
            mrmsSourceB = ImageSource(
                RADAR_ENGINE_MRMS_SLOT_B, mrmsQuad, URI(mrmsUrl(mrmsTime(backMin = 20)))
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
                for (layer in liveFrameLayers) setOpacity(layer, 0f)
                for (layer in futureFrameLayers) setOpacity(layer, 0f)
                setOpacity(mrmsLayerA, 0f)
                setOpacity(mrmsLayerB, 0f)
                setOpacity(satVisLayer, if (selectedLayer == EngineLayer.SatelliteVis) 1f else 0f)
                setOpacity(satIrLayer, if (selectedLayer == EngineLayer.SatelliteIr) 1f else 0f)
            }
            EngineLayer.Radar -> {
                setOpacity(satVisLayer, 0f)
                setOpacity(satIrLayer, 0f)
                if (selectedRange == EngineRange.Live) {
                    applyLiveFrame()
                    for (layer in futureFrameLayers) setOpacity(layer, 0f)
                } else {
                    applyFutureFrame()
                    for (layer in liveFrameLayers) setOpacity(layer, 0f)
                    setOpacity(mrmsLayerA, 0f)
                    setOpacity(mrmsLayerB, 0f)
                }
            }
        }
    }

    private fun applyLiveFrame() {
        if (isMrmsShown) {
            // MRMS primary on the newest frame, over the IEM frames.
            setOpacity(mrmsLayerA, 1f)
            setOpacity(mrmsLayerB, 0f)
            for (layer in liveFrameLayers) setOpacity(layer, 0f)
        } else {
            setOpacity(mrmsLayerA, 0f)
            setOpacity(mrmsLayerB, 0f)
            for ((i, layer) in liveFrameLayers.withIndex()) {
                setOpacity(layer, if (i == frameIndex) 1f else 0f)
            }
        }
    }

    private fun applyFutureFrame() {
        for ((i, layer) in futureFrameLayers.withIndex()) {
            setOpacity(layer, if (i == frameIndex) 1f else 0f)
        }
    }

    // -- UI callbacks --------------------------------------------------------

    private fun selectLayer(layer: EngineLayer) {
        selectedLayer = layer
        if (layer != EngineLayer.Radar) clampZoomForSatellite()
        applyLayerAndFrame()
    }

    private fun selectRange(range: EngineRange) {
        selectedRange = range
        frameIndex = if (range == EngineRange.Live) liveFrames.size - 1 else 0
        applyLayerAndFrame()
    }

    private fun scrubTo(index: Int) {
        frameIndex = index.coerceIn(0, (displayFrames.size - 1).coerceAtLeast(0))
        applyLayerAndFrame()
    }

    // -- Camera (MapLibre gestures handle pan/zoom/rotate; these are the
    //   +/- and recenter buttons, driving the camera directly) -------------

    private fun zoomByStep(delta: Int) {
        val map = mapLibreMap ?: return
        val current = map.cameraPosition
        val minZoom = if (selectedLayer == EngineLayer.Radar) {
            RADAR_ENGINE_ZOOM_MIN
        } else {
            // Satellite: down to z5, the IR native max; overzoom past
            // the native max renders scaled tiles, so the top stays 9.
            5
        }.toDouble()
        val newZoom = (current.zoom + delta)
            .coerceIn(minZoom, RADAR_ENGINE_ZOOM_MAX.toDouble())
        map.cameraPosition = CameraPosition.Builder()
            .target(current.target).zoom(newZoom)
            .bearing(current.bearing).tilt(current.tilt).build()
    }

    private fun clampZoomForSatellite() {
        val map = mapLibreMap ?: return
        val maxZoom = satelliteMaxZoom(selectedLayer == EngineLayer.SatelliteVis).toDouble()
        val current = map.cameraPosition
        if (current.zoom > maxZoom) {
            map.cameraPosition = CameraPosition.Builder()
                .target(current.target).zoom(maxZoom)
                .bearing(current.bearing).tilt(current.tilt).build()
        }
    }

    private fun recenter() {
        val map = mapLibreMap ?: return
        map.cameraPosition = CameraPosition.Builder()
            .target(ENGINE_CENTER).zoom(RADAR_ENGINE_ZOOM)
            .bearing(0.0).tilt(0.0).build()
    }

    // -- MRMS metadata (service info, then retarget slot A) -----------------

    private fun fetchMrmsMetadata() {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                fetchRadarSourceText(HttpClients.plainClient(), MRMS_SERVICE_INFO_URL)
            }
            val times = json?.let { mrmsFrameTimesFromServiceInfoJson(it) } ?: emptyList()
            if (times.isNotEmpty()) {
                pendingMrmsNewestMs = times.last()
                Log.i(TAG, "MRMS metadata: ${times.size} frames, newest $pendingMrmsNewestMs")
                maybeApplyMrms()
            } else {
                Log.w(TAG, "MRMS metadata fetch failed or empty — IEM frames stay visible")
            }
        }
    }

    /**
     * Retarget the hidden MRMS slot A to the true newest frame once
     * BOTH the style (slot exists) and the metadata (newest time)
     * have arrived — whichever lands second triggers it.
     */
    private fun maybeApplyMrms() {
        val newest = pendingMrmsNewestMs ?: return
        val source = mrmsSourceA ?: return
        if (mrmsRetargeted) return
        try {
            source.setUri(URI(mrmsUrl(newest)))
            source.setCoordinates(mrmsQuad)
            mrmsRetargeted = true
            mrmsReady = true
            Log.i(TAG, "MRMS slot A retargeted to newest frame $newest")
            applyLiveFrame()
        } catch (e: Exception) {
            Log.w(TAG, "MRMS retarget failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fetchRadarSourceText(client: OkHttpClient, url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()
}
