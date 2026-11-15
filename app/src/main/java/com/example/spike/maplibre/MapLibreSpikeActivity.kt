package com.example.spike.maplibre

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.data.mapper.LonLatBbox
import com.example.data.mapper.mrmsExportImageUrl
import java.net.URI
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
 * Phase 0 spike activity — MapLibre Native 13.7.1 DIRECT (MapView in
 * AndroidView, Vulkan single-backend), throwaway, branch
 * `fix/radar-phase0-spikes` only. NOT wired into the real app: no
 * intent filter, `exported=false`, debug-gated at runtime (release
 * builds carry the code — that is how the release APK size is measured
 * — but the activity finishes immediately when not a debug build).
 *
 * Launch on a device (debug APK from CI):
 *   adb shell am start -n com.docbobert6.auraweather/.spike.maplibre.MapLibreSpikeActivity
 *
 * What it proves on device (each probe logs to the on-screen log AND
 * logcat tag `MapLibreSpike`):
 *  - Spike 1: raster-only style JSON loads; 10 per-frame volatile
 *    raster sources + 2 MRMS ImageSources render; `isVolatile` /
 *    `prefetchZoomDelta` read back as set; opacity crossfade works
 *    with no source/layer churn; first-rendered-frame timing is
 *    recorded for the cold-vs-relaunch volatile check.
 *  - Spike 2: the runtime retarget question — whether a raster
 *    source's tile URLs can be changed in place (they cannot: there
 *    is no setter in the 13.7.1 API; the probe records what
 *    `removeSource` does while a layer is attached, and that the
 *    remove-layer/remove-source/add-source/add-layer sequence works);
 *    and that `ImageSource.setUri` / `setCoordinates` retarget an
 *    image in place with NO style reload (`OnStyleLoaded` must fire
 *    exactly once).
 *
 * What it does NOT prove (no device/emulator in this VM): any runtime
 * behaviour — cache, crossfade, retarget — is compile-time + API
 * evidence only until Robert runs this activity on his phone. The
 * results file (`RADAR_PHASE0_MAPLIBRE.md`) states that plainly.
 */
class MapLibreSpikeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MapLibreSpike"

        /** Camera target: Cincinnati, OH (Robert's area). */
        private val SPIKE_CENTER = LatLng(39.1, -84.5)
        private const val SPIKE_ZOOM = 7.0

        /** 1.0x MRMS bbox around the centre; the request uses 1.5x. */
        private val MRMS_BBOX_1X = SpikeBbox(
            westLon = -87.5, southLat = 36.6, eastLon = -81.5, northLat = 41.6
        )
        private const val MRMS_IMAGE_WIDTH_PX = 1080
        private const val MRMS_IMAGE_HEIGHT_PX = 1000
    }

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var styleLoadCount = 0
    private var styleLoadedAtMs = 0L
    private var firstFrameAtMs = 0L

    private val frameSources = mutableListOf<RasterSource>()
    private val frameLayers = mutableListOf<RasterLayer>()
    private var mrmsSourceA: ImageSource? = null
    private var mrmsSourceB: ImageSource? = null
    private var mrmsLayerA: RasterLayer? = null
    private var mrmsLayerB: RasterLayer? = null
    private var mrmsVisibleSlot: String? = null // null = MRMS hidden, frames show
    private var mrmsNextTimeMs: Long = 0L

    private val logLines = mutableStateListOf<String>()
    private var visibleFrame by mutableIntStateOf(0)

    // Wide-margin MRMS bbox + its LatLngQuad (corners: TL, TR, BR, BL).
    private val mrmsBbox: SpikeBbox = expandBbox(MRMS_BBOX_1X, 1.5)
    private val mrmsQuad: LatLngQuad
        get() = LatLngQuad(
            LatLng(mrmsBbox.northLat, mrmsBbox.westLon),
            LatLng(mrmsBbox.northLat, mrmsBbox.eastLon),
            LatLng(mrmsBbox.southLat, mrmsBbox.eastLon),
            LatLng(mrmsBbox.southLat, mrmsBbox.westLon)
        )

    private fun log(line: String) {
        Log.i(TAG, line)
        logLines.add(line)
    }

    /** MRMS frame time: now rounded down to the 5-min cadence, minus [backMin] min. */
    private fun mrmsTime(backMin: Long): Long {
        val now = System.currentTimeMillis()
        val step = 5 * 60 * 1000L
        return (now / step) * step - backMin * 60 * 1000L
    }

    private fun mrmsUrl(timeMs: Long): String = mrmsExportImageUrl(
        LonLatBbox(mrmsBbox.westLon, mrmsBbox.southLat, mrmsBbox.eastLon, mrmsBbox.northLat),
        timeMs, MRMS_IMAGE_WIDTH_PX, MRMS_IMAGE_HEIGHT_PX
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            // Release builds carry the spike code for the APK-size
            // measurement, but the spike never runs outside debug.
            finish()
            return
        }
        MapLibre.getInstance(this)
        val view = MapView(this)
        mapView = view
        view.onCreate(savedInstanceState)
        view.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder()
                .target(SPIKE_CENTER).zoom(SPIKE_ZOOM).build()
            map.setStyle(
                Style.Builder().fromJson(buildSpikeStyleJson()),
                Style.OnStyleLoaded { style ->
                    styleLoadCount++
                    styleLoadedAtMs = SystemClock.elapsedRealtime()
                    loadedStyle = style
                    log("style loaded (count=$styleLoadCount): raster-only JSON OK")
                    addSpikeSourcesAndLayers(style)
                    map.setOnFpsChangedListener { fps ->
                        if (firstFrameAtMs == 0L) {
                            firstFrameAtMs = SystemClock.elapsedRealtime()
                            log(
                                "first rendered frame " +
                                    "${firstFrameAtMs - styleLoadedAtMs}ms after style load " +
                                    "(fps=$fps) — repeat after kill+relaunch for the volatile check"
                            )
                        }
                    }
                }
            )
        }
        setContent {
            Column(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { mapView!! },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Button(
                    onClick = { probeVolatileReadback() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("Probe: volatile / prefetch read-back") }
                Button(
                    onClick = { probeRasterRetarget() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("Probe: retarget raster source (Spike 2)") }
                Button(
                    onClick = { nextRadarFrame() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("Next radar frame (shape a) — now $visibleFrame") }
                Button(
                    onClick = { mrmsCrossfadeStep() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Text("MRMS: show / setUri + crossfade") }
                Text(
                    "SPIKE LOG (also logcat MapLibreSpike)",
                    Modifier.padding(start = 8.dp, top = 4.dp),
                    fontSize = 11.sp
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    items(logLines) { line ->
                        Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
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

    private fun addSpikeSourcesAndLayers(style: Style) {
        // Shape (a): 10 per-frame volatile raster sources + layers.
        // Volatile = never persist to the ambient cache; prefetch delta 0
        // = no low-res parent prefetch on radar sources (data budget).
        try {
            for (i in 0 until SPIKE_FRAME_COUNT) {
                val source = RasterSource(
                    spikeFrameSourceId(i),
                    TileSet("2.2.0", spikeIemFrameTileTemplate(i)),
                    256
                )
                source.isVolatile = true
                source.prefetchZoomDelta = 0
                style.addSource(source)
                frameSources.add(source)
                val layer = RasterLayer(spikeFrameLayerId(i), spikeFrameSourceId(i))
                    .withProperties(
                        PropertyFactory.rasterOpacity(if (i == 0) 1f else 0f)
                    )
                style.addLayer(layer)
                frameLayers.add(layer)
            }
            log("added $SPIKE_FRAME_COUNT volatile frame sources (IEM tiles); frame 0 visible")
        } catch (e: Exception) {
            log("ERROR adding frame sources: ${e.javaClass.simpleName}: ${e.message}")
        }

        // MRMS two-slot ImageSources (wide-margin bbox, png32 exportImage).
        try {
            val timeA = mrmsTime(backMin = 15)
            val timeB = mrmsTime(backMin = 20)
            mrmsNextTimeMs = mrmsTime(backMin = 10)
            mrmsSourceA = ImageSource(SPIKE_MRMS_SLOT_A, mrmsQuad, URI(mrmsUrl(timeA)))
            mrmsSourceB = ImageSource(SPIKE_MRMS_SLOT_B, mrmsQuad, URI(mrmsUrl(timeB)))
            style.addSource(mrmsSourceA!!)
            style.addSource(mrmsSourceB!!)
            mrmsLayerA = RasterLayer(
                spikeMrmsLayerId(SPIKE_MRMS_SLOT_A), SPIKE_MRMS_SLOT_A
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            mrmsLayerB = RasterLayer(
                spikeMrmsLayerId(SPIKE_MRMS_SLOT_B), SPIKE_MRMS_SLOT_B
            ).withProperties(PropertyFactory.rasterOpacity(0f))
            style.addLayer(mrmsLayerA!!)
            style.addLayer(mrmsLayerB!!)
            log("added MRMS ImageSources A(-15m)/B(-20m), bbox ${spikeBboxToParam(mrmsBbox)} (1.5x)")
        } catch (e: Exception) {
            log("ERROR adding MRMS sources: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Basemap read-back (JSON-declared source, prefetch default).
        try {
            val basemap = style.getSourceAs<RasterSource>(SPIKE_BASEMAP_SOURCE_ID)
            log(
                "basemap read-back: isVolatile=${basemap?.isVolatile} " +
                    "prefetchZoomDelta=${basemap?.prefetchZoomDelta}"
            )
        } catch (e: Exception) {
            log("ERROR basemap read-back: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // -- Probes --------------------------------------------------------------

    /** Spike 1: prove the volatile/prefetch flags are set on the live objects. */
    private fun probeVolatileReadback() {
        try {
            for ((i, source) in frameSources.withIndex()) {
                log(
                    "frame $i: isVolatile=${source.isVolatile} " +
                        "prefetchZoomDelta=${source.prefetchZoomDelta} " +
                        "maxOverscale=${source.maxOverscaleFactorForParentTiles}"
                )
            }
            val allVolatile = frameSources.all { it.isVolatile == true }
            val allNoPrefetch = frameSources.all { it.prefetchZoomDelta == 0 }
            log("read-back verdict: allVolatile=$allVolatile allPrefetchOff=$allNoPrefetch")
            log(
                "DEVICE CHECK (a): kill the app, relaunch this activity, compare " +
                    "'first rendered frame …ms after style load' with the cold-launch " +
                    "number above — volatile tiles must REFETCH (slow first frame), " +
                    "not appear instantly from cache"
            )
        } catch (e: Exception) {
            log("ERROR volatile probe: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Spike 2, raster half: there is NO `setTileSet` / `setUri` on
     * `RasterSource` in 13.7.1 (verified by javap on the published AAR).
     * The only runtime "retarget" is remove + re-add. This probe records
     * what actually happens, step by step.
     */
    private fun probeRasterRetarget() {
        val style = loadedStyle ?: run {
            log("retarget probe: style not loaded yet")
            return
        }
        try {
            // Step 1: try removing the source while its layer is attached.
            val removedWhileAttached = style.removeSource(spikeFrameSourceId(1))
            log("removeSource(frame-1) with layer attached -> $removedWhileAttached")
            if (removedWhileAttached) {
                // Source is gone but the layer still references the id:
                // re-add the source with a DIFFERENT frame's tiles.
                reAddFrameSource(style, frameIndex = 1, tilesFromFrame = 2)
                log("retarget done via removeSource alone (layer kept its id)")
            } else {
                // Step 2: the documented sequence — layer off, source off,
                // source on (new tiles), layer on.
                val layerRemoved = style.removeLayer(spikeFrameLayerId(1))
                val sourceRemoved = style.removeSource(spikeFrameSourceId(1))
                log("removeLayer(frame-1-layer) -> $layerRemoved; removeSource(frame-1) -> $sourceRemoved")
                reAddFrameSource(style, frameIndex = 1, tilesFromFrame = 2)
                val layer = RasterLayer(spikeFrameLayerId(1), spikeFrameSourceId(1))
                    .withProperties(PropertyFactory.rasterOpacity(0f))
                style.addLayer(layer)
                frameLayers[1] = layer
                log("retarget done via layer+source remove/re-add (frame-1 now serves frame-2 tiles)")
            }
            log("styleLoadCount=$styleLoadCount (must stay 1: no style reload happened)")
        } catch (e: Exception) {
            log("ERROR retarget probe: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun reAddFrameSource(style: Style, frameIndex: Int, tilesFromFrame: Int) {
        val source = RasterSource(
            spikeFrameSourceId(frameIndex),
            TileSet("2.2.0", spikeIemFrameTileTemplate(tilesFromFrame)),
            256
        )
        source.isVolatile = true
        source.prefetchZoomDelta = 0
        style.addSource(source)
        frameSources[frameIndex] = source
    }

    /** Shape (a) demo: cycle the visible per-frame layer, 150 ms crossfade. */
    private fun nextRadarFrame() {
        try {
            val next = (visibleFrame + 1) % SPIKE_FRAME_COUNT
            for ((i, layer) in frameLayers.withIndex()) {
                layer.setRasterOpacityTransition(TransitionOptions(SPIKE_CROSSFADE_MS, 0))
                layer.withProperties(
                    PropertyFactory.rasterOpacity(if (i == next) 1f else 0f)
                )
            }
            // MRMS sits above the frames: hide it while stepping frames.
            mrmsLayerA?.withProperties(PropertyFactory.rasterOpacity(0f))
            mrmsLayerB?.withProperties(PropertyFactory.rasterOpacity(0f))
            mrmsVisibleSlot = null
            visibleFrame = next
            log("frame $visibleFrame visible (opacity crossfade ${SPIKE_CROSSFADE_MS}ms, no tile reload)")
        } catch (e: Exception) {
            log("ERROR next frame: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Spike 2, image half: `ImageSource.setUri` / `setCoordinates`
     * retarget an image in place. First tap fades MRMS-A in over the
     * frames; later taps retarget the HIDDEN slot to a newer time and
     * crossfade to it — with no style reload (`styleLoadCount` must stay 1).
     */
    private fun mrmsCrossfadeStep() {
        val style = loadedStyle ?: run {
            log("MRMS step: style not loaded yet")
            return
        }
        try {
            if (mrmsVisibleSlot == null) {
                mrmsLayerA?.setRasterOpacityTransition(TransitionOptions(SPIKE_CROSSFADE_MS, 0))
                mrmsLayerA?.withProperties(PropertyFactory.rasterOpacity(1f))
                for (layer in frameLayers) {
                    layer.withProperties(PropertyFactory.rasterOpacity(0f))
                }
                mrmsVisibleSlot = SPIKE_MRMS_SLOT_A
                log("MRMS-A faded in over frames (opacity only, no reload)")
                return
            }
            val hiddenIsA = mrmsVisibleSlot == SPIKE_MRMS_SLOT_B
            val hiddenSource = if (hiddenIsA) mrmsSourceA else mrmsSourceB
            val hiddenLayer = if (hiddenIsA) mrmsLayerA else mrmsLayerB
            val visibleLayer = if (hiddenIsA) mrmsLayerB else mrmsLayerA
            val newTime = mrmsNextTimeMs
            mrmsNextTimeMs = mrmsTime(backMin = 5)
            hiddenSource?.setUri(URI(mrmsUrl(newTime)))
            hiddenSource?.setCoordinates(mrmsQuad)
            log(
                "setUri on hidden slot (${if (hiddenIsA) "A" else "B"}) to time $newTime; " +
                    "styleLoadCount=$styleLoadCount (must stay 1)"
            )
            hiddenLayer?.setRasterOpacityTransition(TransitionOptions(SPIKE_CROSSFADE_MS, 0))
            visibleLayer?.setRasterOpacityTransition(TransitionOptions(SPIKE_CROSSFADE_MS, 0))
            hiddenLayer?.withProperties(PropertyFactory.rasterOpacity(1f))
            visibleLayer?.withProperties(PropertyFactory.rasterOpacity(0f))
            mrmsVisibleSlot = if (hiddenIsA) SPIKE_MRMS_SLOT_A else SPIKE_MRMS_SLOT_B
            log("crossfaded to MRMS slot $mrmsVisibleSlot (${SPIKE_CROSSFADE_MS}ms, no tile reload)")
        } catch (e: Exception) {
            log("ERROR MRMS step: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
