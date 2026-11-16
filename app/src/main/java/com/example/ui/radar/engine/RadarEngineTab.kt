package com.example.ui.radar.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.Place
import com.example.ui.designsystem.RetroTokens
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/**
 * Phase 5 cutover — the engine IS the Radar tab.
 *
 * Hosts a [RadarEngineController] in the nav shell (ticker above,
 * SolidNavBar below, both owned by `WeatherNavDisplay`). The
 * controller and the MapView live exactly as long as the tab is
 * composed: leaving the tab destroys the engine — the same reset the
 * old screen had, whose remembered loop state reset on every visit —
 * and returning rebuilds it. A place change retargets the engine in
 * place ([RadarEngineController.onPlaceChanged]) instead of
 * rebuilding it.
 *
 * No place selected yet: the radar centres on the place, and there is
 * no default location to centre on instead — the same empty state
 * the old `RadarScreen` showed, so the first-launch tab never opens
 * on a hardcoded place.
 */
@Composable
fun RadarEngineTab(
    place: Place?,
    tickerVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (place == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(RetroTokens.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a place to see its radar.",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextSecondary,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { RadarEngineController(context, place) }
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    LaunchedEffect(controller, mapView) { controller.start(mapView) }
    // Runs on first composition too, but the controller was just built
    // for this place, so that call is a no-op; later changes retarget.
    LaunchedEffect(place.id, place.point.lat, place.point.lon) {
        controller.onPlaceChanged(place)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.onStart()
                Lifecycle.Event.ON_RESUME -> controller.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.onPause()
                Lifecycle.Event.ON_STOP -> controller.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }

    RadarEngineScreen(
        mapView = mapView,
        selectedLayer = controller.selectedLayer,
        selectedRange = controller.selectedRange,
        frames = controller.uiFrames,
        currentFrameIndex = controller.uiCurrentIndex,
        isPlaying = controller.uiIsPlaying,
        playEnabled = controller.uiPlayEnabled,
        statusText = controller.uiStatusText,
        unavailableText = controller.uiUnavailableText,
        sourceLabel = controller.uiSourceLabel,
        costText = controller.uiCostText,
        endNote = controller.uiEndNote,
        onSelectLayer = { controller.selectLayer(it) },
        onSelectRange = { controller.selectRange(it) },
        onPlayPause = { controller.onPlayPause() },
        onRetry = { controller.onRetry() },
        onScrubStart = { controller.onScrubStart() },
        onScrubFrame = { controller.scrubTo(it) },
        onScrubEnd = { controller.onScrubEnd() },
        onZoomIn = { controller.zoomByStep(+1) },
        onZoomOut = { controller.zoomByStep(-1) },
        onRecenter = { controller.recenter() },
        tickerVisible = tickerVisible,
        modifier = modifier
    )
}
