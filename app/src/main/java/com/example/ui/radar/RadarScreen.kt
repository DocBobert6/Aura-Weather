package com.example.ui.radar

import android.os.SystemClock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import com.example.AuraWeatherApp
import com.example.data.mapper.PAN_SETTLE_DELAY_MS
import com.example.data.mapper.ParentTile
import com.example.data.mapper.TileRange
import com.example.data.mapper.VIEWPORT_TILE_MARGIN
import com.example.data.mapper.gibsTileUrl
import com.example.data.mapper.gibsTimestamp
import com.example.data.mapper.RadarRange
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.isSatelliteFrameSettled
import com.example.data.mapper.isTileSettled
import com.example.data.mapper.lookAheadFrameIndices
import com.example.data.mapper.loopDwellMs
import com.example.data.mapper.loopNextIndex
import com.example.data.mapper.loopPreloadCostText
import com.example.data.mapper.panDirection
import com.example.data.mapper.parentTileFor
import com.example.data.mapper.RADAR_LOOP_WAIT_TIMEOUT_MS
import com.example.data.mapper.radarFrameUrlsForRange
import com.example.data.mapper.radarPrefetchUrlsForRange
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.satelliteCapRemainingMs
import com.example.data.mapper.satelliteCostText
import com.example.data.mapper.satelliteTileUrlsForRange
import com.example.data.mapper.satelliteZoomLevel
import com.example.data.mapper.snappedTileSpan
import com.example.data.mapper.viewportTileRange
import com.example.domain.model.FullWeatherState
import com.example.ui.RadarLayer
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroTokens
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.GlassIconButton
import com.example.ui.designsystem.PillSegmentedControl
import java.time.Instant
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// The scrubber's custom thumb is an experimental Material3 API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    state: FullWeatherState?,
    selectedLayer: RadarLayer,
    selectedRange: RadarRange,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    onSelectLayer: (RadarLayer) -> Unit,
    onSelectRange: (RadarRange) -> Unit,
    onTogglePlay: () -> Unit,
    onScrubFrame: (Int) -> Unit,
    tickerVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    // No place selected yet: the radar centres on the place, and there is
    // no default location to centre on instead.
    if (state == null) {
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

    // Zoom level and pan offsets. The pan offsets are keyed on the
    // range and layer (AUDIT6 §9 Fix 4): the old unkeyed remember let
    // a pan offset survive a layer/range switch, so the navy gap
    // followed the user into satellite. A switch recentres the map.
    var zoomLevel by remember { mutableIntStateOf(8) }
    var panOffsetX by remember(selectedRange, selectedLayer) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(selectedRange, selectedLayer) { mutableFloatStateOf(0f) }
    var userScale by remember { mutableFloatStateOf(1f) }

    // Settled (reconciled) pan/scale — AUDIT6 §9 Fix 4: the tile SET
    // follows the settled viewport, not the live drag. During a drag
    // the live offsets only transform the existing tiles (the map
    // moves under the finger with no refetch); 300 ms after the last
    // move the settle effect below copies the live values here, and
    // the grid + prefetch reconcile to the new viewport. The pan
    // direction is the cumulative pan since the last settle, so the
    // prefetch can add +1 tile in the direction the user is moving.
    // The zoom +/- and recenter buttons reset these directly, so a
    // tap recentres at once instead of waiting out the settle delay.
    var settledPanOffsetX by remember(selectedRange, selectedLayer) { mutableFloatStateOf(0f) }
    var settledPanOffsetY by remember(selectedRange, selectedLayer) { mutableFloatStateOf(0f) }
    var settledUserScale by remember(selectedRange, selectedLayer) { mutableFloatStateOf(1f) }
    var panDirectionX by remember(selectedRange, selectedLayer) { mutableIntStateOf(0) }
    var panDirectionY by remember(selectedRange, selectedLayer) { mutableIntStateOf(0) }
    var settledPanDirectionX by remember(selectedRange, selectedLayer) { mutableIntStateOf(0) }
    var settledPanDirectionY by remember(selectedRange, selectedLayer) { mutableIntStateOf(0) }

    // The viewport tile ranges, published up from the
    // BoxWithConstraints scope (which owns the screen size) via
    // SideEffect: the display range the grid draws, and the prefetch
    // range (display + pan-velocity tile) the loop gates on. Null
    // until the grid has reported once; isFrameReady falls back to
    // the display counters until then.
    var displayTileRange by remember { mutableStateOf<TileRange?>(null) }
    var prefetchTileRange by remember { mutableStateOf<TileRange?>(null) }

    // The frames for the selected range (Build B), oldest first.
    // Live is the existing 10-frame loop; past ranges are the archived
    // mosaic, future ranges are HRRR. Recomputed when the range changes
    // (a fresh "now"), not on every frame advance.
    val frames = remember(selectedRange) { radarRangeFrames(selectedRange, Instant.now()) }
    val frameCount = frames.size
    // Defensive clamp: the ViewModel coerces too, but its "now" and the
    // screen's can straddle a sampling boundary.
    val safeFrameIndex = currentFrameIndex.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
    val currentFrame = frames.getOrNull(safeFrameIndex)
    // The IEM layer for the current frame. For Live this is the same
    // iemLayerNameFor the old code used; for ranges it is the archived
    // mosaic / HRRR layer name.
    val iemLayerName = currentFrame?.layerName ?: iemLayerNameFor(9)
    // Future ranges are model output, never observed radar.
    val isForecastRange = selectedRange.isForecast

    // Satellite layers have a max zoom in GIBS below the radar zoom range
    // (7..9), so clamp the effective zoom per layer. The stored zoomLevel
    // is untouched, so switching back to radar restores the radar zoom.
    // The cap is the set's max MATRIX, not its level count: Level7 holds
    // matrices 0..6 and Level6 holds 0..5 (see satelliteZoomLevel).
    val effectiveZoomLevel = when (selectedLayer) {
        RadarLayer.SatelliteVis -> satelliteZoomLevel(isVisible = true, zoomLevel)
        RadarLayer.SatelliteIr -> satelliteZoomLevel(isVisible = false, zoomLevel)
        else -> zoomLevel
    }

    // Satellite layers are a single image (no loop, no dBZ): the legend,
    // the clear-air caption and the transport pill are radar-only
    // (audit fix E).
    val isSatellite = selectedLayer == RadarLayer.SatelliteVis || selectedLayer == RadarLayer.SatelliteIr

    // Satellite loading overlay (audit findings 1+2): counts GIBS tiles
    // that have finished (loaded or errored). Resets when the layer or
    // zoom changes, since that is a new tile set. The overlay shows until
    // every tile has settled, so the map never reads as a blank navy void
    // (finding 1) or a broken gray rectangle (finding 2) while satellite
    // imagery is still arriving. The Esri basemap stays visible
    // underneath as the placeholder for unloaded regions.
    var satSettled by remember(selectedLayer, effectiveZoomLevel) { mutableIntStateOf(0) }

    // Satellite overlay cap, per VISIT (AUDIT6 §9 Fix 9): the old cap
    // was LaunchedEffect(selectedLayer, effectiveZoomLevel) { delay(12000);
    // satSettled = totalTiles } — every layer switch restarted the 12 s
    // timer, so switching layers within 12 s kept the overlay up
    // indefinitely. The cap now starts when the user ENTERS satellite
    // (either satellite layer) and does not restart on a layer or zoom
    // switch within that visit; leaving satellite resets it. This
    // effect is keyed on isSatellite only, so a VIS-to-IR switch does
    // not re-run it. The flag is separate from satSettled: the settled
    // count stays the honest count of tiles that arrived, and the cap
    // only ever hides the overlay / shows the layer, never fakes a
    // settled tile.
    var satCapExpired by remember { mutableStateOf(false) }
    var satVisitStartMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isSatellite) {
        if (isSatellite) {
            satVisitStartMs = SystemClock.uptimeMillis()
            satCapExpired = false
        } else {
            satVisitStartMs = 0L
            satCapExpired = false
        }
    }

    // Future-radar (HRRR) tile health (radar fix A): counts the current
    // range's radar tiles that loaded vs errored, for forecast ranges
    // only. A forecast range with no tiles at all would otherwise read
    // as a blank navy void. Reset when the range changes or a retry is
    // requested, since that is a new tile set. The retry generation is
    // declared first: the counters are keyed on it.
    var forecastRetryGen by remember(selectedRange) { mutableIntStateOf(0) }
    var forecastAutoRetries by remember(selectedRange) { mutableIntStateOf(0) }
    var forecastTileOk by remember(selectedRange, forecastRetryGen) { mutableIntStateOf(0) }
    var forecastTileErr by remember(selectedRange, forecastRetryGen) { mutableIntStateOf(0) }
    // Unavailable when the first few forecast tiles all fail and none
    // loaded: IEM is down (or the layer is wrong), not a slow load.
    val forecastUnavailable = isForecastRange && !isSatellite &&
        forecastTileErr >= 3 && forecastTileOk == 0

    // Per-frame tile settlement (radar smoothness fix, 2026-11-14):
    // the loop used to advance every 750ms whether or not the next
    // frame's tiles had arrived, so it showed blank dark basemap and
    // tiles popping in one by one. These counters track the CURRENT
    // frame's radar tiles only (loaded or errored) and reset whenever
    // the frame, range, layer, zoom or centre changes — i.e. whenever
    // the tile set changes. Satellite layers do not count here: their
    // tiles are tracked by satSettled and the loop never runs for them.
    // frameTotalTiles is pushed up from the tile grid (BoxWithConstraints
    // scope) via SideEffect; the guard keeps the frame "not ready"
    // until the grid has reported its size at least once.
    // Pan deliberately does NOT reset these counters (Fix 4): they are
    // only the displayed frame's fallback — the loop gates on the
    // URL-set prefetch readiness, which is exact across pans. A pan
    // can inflate these counters by at most the tiles that left the
    // viewport (one shifted column/row), so the fallback may call the
    // displayed frame ready a few tiles early; the loop gate never does.
    var frameTileOk by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, safeFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
    var frameTileErr by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, safeFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
    var frameTotalTiles by remember { mutableIntStateOf(0) }
    val frameReady = frameTotalTiles > 0 && frameTileOk + frameTileErr >= frameTotalTiles

    // Per-frame PREFETCH readiness (AUDIT6 §9 Fixes 2-3, 2026-11-15):
    // the display counters above can only see the frame on screen —
    // they reset the moment the displayed frame changes — so they
    // cannot say whether frame N+1 is ready while frame N is still
    // displayed. That is the crux of the old inversion: the loop
    // advanced first and then waited while the NEW frame sat at
    // alpha 0. Readiness is therefore tracked per tile URL, from the
    // prefetch lane's completion listener (see prefetchRadarTile
    // below): the settled set survives frame advances. Generation
    // keys are the tile-set keys WITHOUT safeFrameIndex — a new grid
    // (range/layer/zoom/centre/retry) starts empty; a frame advance
    // must not reset it. The set is the loop's gate (the loop waits
    // for N+1 here before advancing) and the alpha's source (a frame
    // swapped in was already ready, so it never sits at alpha 0).
    //
    // URL-set, not per-frame counts (AUDIT6 §9 Fix 4): a count cannot
    // survive a pan — tiles that left the viewport stay counted while
    // newly visible tiles are still in flight, so a frame could read
    // ready with a whole column missing. A set is exact across pans:
    // tiles still visible keep their settled status, new tiles are
    // not ready until they settle, departed tiles simply stop being
    // asked about. The pan therefore changes WHAT isFrameReady asks
    // about (the viewport range) without resetting the readiness of
    // tiles that are still visible. The generation keys deliberately
    // do NOT include the viewport: a pan must not clear this set.
    var prefetchSettledUrls by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<Set<String>>(emptySet()) }
    // URLs already enqueued in this generation. The rolling
    // look-ahead re-runs on every frame advance and overlaps the
    // range-entry burst, and Coil 2.7.0 does NOT coalesce in-flight
    // requests (AUDIT6 §2.4) — without this guard the same tile
    // would be fetched twice and counted twice. One enqueue per URL
    // per generation, so one listener fire per tile, so the settled
    // set above is exact.
    val prefetchEnqueuedUrls = remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableSetOf<String>() }
    // In-flight prefetch requests by URL, so the reconcile can cancel
    // the ones that left the viewport (Fix 4): disposing a request
    // also drops it from the enqueue guard and the settled set, so a
    // tile that comes back into view is enqueued — and counted — again.
    // Display (AsyncImage) requests need no map: a tile leaving the
    // grid leaves composition, and Coil cancels its request.
    val prefetchDisposables = remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableMapOf<String, Disposable>() }

    // One prefetch settlement for [url]: loaded or errored both
    // count — a settled error is a known-missing tile, not a tile
    // still in flight, and the loop must not wait on it forever.
    // Called on the main thread: Coil's Listener callbacks are
    // @MainThread (verified against the coil-base 2.7.0 sources).
    fun markPrefetchSettled(url: String) {
        if (url !in prefetchSettledUrls) prefetchSettledUrls = prefetchSettledUrls + url
    }

    // Is [frameIndex] ready to show? Prefetch settlement first: every
    // tile URL of that frame over the CURRENT viewport prefetch range
    // must be settled. The displayed frame's own AsyncImages are the
    // fallback, so a frame the prefetch never enqueued (a scrub
    // target) still becomes ready when its tiles settle on screen,
    // and a prefetch listener that never fires cannot wedge the loop
    // or the alpha. Satellite has no loop and no frame list — its
    // readiness is satSettled, not this function.
    fun isFrameReady(frameIndex: Int): Boolean {
        val range = prefetchTileRange
        if (range != null && !isSatellite) {
            val layer = frames.getOrNull(frameIndex)?.layerName ?: return false
            if (isTileSettled(radarFrameUrlsForRange(layer, effectiveZoomLevel, range), prefetchSettledUrls)) return true
        }
        return frameIndex == safeFrameIndex && frameReady
    }
    val displayedFrameReady = isFrameReady(safeFrameIndex)

    // Whole-frame fade-in: the radar layer stays hidden until its
    // tiles have settled, then fades in complete over 150ms instead
    // of tiles popping in one by one. The readiness here is the
    // DISPLAYED frame's prefetch readiness (plus the display
    // fallback), not just its display counters — so a frame the loop
    // swapped in after gating on its readiness is already at full
    // alpha the moment it appears.
    // Satellite (AUDIT6 §9 Fix 9) gets the same gate instead of its
    // old fixed 0.85 alpha: the layer stays at alpha 0 until the
    // whole frame has settled (satSettled over the grid the screen
    // draws — loaded or errored both count) and then fades in whole
    // over 150ms, instead of assembling tile by tile behind the
    // overlay. The Fix 4 parent underlay stays visible underneath
    // meanwhile, so the wait shows low-res satellite imagery, never
    // a hole. The per-visit 12 s cap (satCapExpired) is the
    // backstop: after 12 s in satellite the layer shows whatever
    // has arrived rather than waiting forever.
    val satFrameReady = isSatellite && isSatelliteFrameSettled(satSettled, frameTotalTiles)
    val frameAlpha by animateFloatAsState(
        targetValue = if (isSatellite) {
            if (satFrameReady || satCapExpired) 0.85f else 0f
        } else if (displayedFrameReady) 0.85f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "radarFrameAlpha"
    )

    // Loop priming (Fix 3): the "LOADING LOOP..." overlay below shows
    // until the current frame is ready for the first time in this
    // generation. Sticky — once any frame has shown, later waits hold
    // the previous frame visible (Fix 2) and never show the overlay.
    var loopPrimed by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf(false) }
    SideEffect { if (displayedFrameReady) loopPrimed = true }

    // Two-slot swap crossfade (Fix 2): when the frame changes, the
    // outgoing frame's radar tiles stay composed for 150ms underneath
    // while the incoming frame fades in over them — a layer swap, not
    // interpolation (no advection, no double-imaged motion: 150ms is
    // a blink, and the audit forbids on-device interpolation).
    // lastShownLayer is the layer the user last saw complete; the
    // effect below captures it before each change. Both reset on a
    // generation change, so a range switch never crossfades from a
    // stale layer of another range.
    var crossfadeFromLayer by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<String?>(null) }
    var lastShownLayer by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf(iemLayerName) }
    val crossfadeProgress by animateFloatAsState(
        targetValue = if (crossfadeFromLayer != null) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "radarCrossfade"
    )
    LaunchedEffect(safeFrameIndex) {
        val previous = lastShownLayer
        if (previous == iemLayerName) return@LaunchedEffect
        crossfadeFromLayer = previous
        delay(150)
        crossfadeFromLayer = null
        lastShownLayer = iemLayerName
    }

    // Auto-retry when the forecast is unavailable: up to 3 attempts
    // with exponential backoff (5s, 10s, 20s), then stop and leave the
    // RETRY button. Each attempt bumps the retry generation, which
    // recreates the tile AsyncImages (keyed below) so the failed set
    // is re-requested. Coil does not cache failures, so the retry hits
    // the network again.
    LaunchedEffect(forecastUnavailable, forecastAutoRetries) {
        if (!forecastUnavailable || forecastAutoRetries >= 3) return@LaunchedEffect
        val backoffMs = 5000L * (1L shl forecastAutoRetries)
        delay(backoffMs)
        if (forecastUnavailable) {
            forecastAutoRetries++
            forecastRetryGen++
        }
    }

    // The radar loop lives here now (3.3), not in the ViewModel: it runs
    // only while this screen is composed and playing, and only for radar
    // layers. Leaving the screen cancels the effect, so no radar job runs
    // when the screen was never visited. The ViewModel keeps only the
    // frame index + play state. rememberUpdatedState keeps the frame
    // index fresh inside the loop without restarting the effect on every
    // frame advance (which would reset the 750ms delay each time).
    //
    // Hold-previous-frame loop (AUDIT6 §9 Fix 2, 2026-11-15): the old
    // loop advanced FIRST and then waited while the NEW frame sat at
    // alpha 0 — future frames were invisible 73-100% of the time.
    // This loop waits BEFORE advancing: it holds the current frame
    // until it is ready (usually already true), then waits for the
    // NEXT frame's tiles to settle in the prefetch lane, and only
    // then swaps — with the 150ms crossfade above — and dwells on the
    // frame just shown. The frame the user sees is always complete.
    // The 2s timeout (RADAR_LOOP_WAIT_TIMEOUT_MS) is the honest
    // fallback, unchanged: a dead or slow server can delay a step,
    // never freeze the loop. Do not raise it — the audit shows the
    // timeout is not the bottleneck; the hold inversion was.
    // Dwell is 750ms per frame, 2250ms on the newest frame (end pause
    // before the loop wraps — research §5), via loopDwellMs.
    val latestFrameIndex by rememberUpdatedState(safeFrameIndex)
    val latestOnScrubFrame by rememberUpdatedState(onScrubFrame)
    LaunchedEffect(isPlaying, isSatellite, selectedRange) {
        if (!isPlaying || isSatellite) return@LaunchedEffect
        while (true) {
            if (frameCount < 1) {
                delay(1000)
                continue
            }
            // Hold the frame on screen until it is ready (a scrub
            // target or a timeout-advanced frame may not be yet).
            withTimeoutOrNull(RADAR_LOOP_WAIT_TIMEOUT_MS) {
                snapshotFlow { isFrameReady(latestFrameIndex) }.first { it }
            }
            // Wait for the NEXT frame before leaving this one: the
            // current frame stays visible at full alpha throughout.
            // The next index is computed inside the flow so a scrub
            // mid-wait retargets the wait instead of waiting on a
            // stale frame.
            withTimeoutOrNull(RADAR_LOOP_WAIT_TIMEOUT_MS) {
                snapshotFlow { loopNextIndex(latestFrameIndex, frameCount) to isFrameReady(loopNextIndex(latestFrameIndex, frameCount)) }
                    .first { (_, ready) -> ready }
            }
            val advancedTo = loopNextIndex(latestFrameIndex, frameCount)
            latestOnScrubFrame(advancedTo)
            delay(loopDwellMs(advancedTo, frameCount))
        }
    }

    // Pan settle (AUDIT6 §9 Fix 4): the tile set reconciles on gesture
    // end, not per move. Every pan/zoom move restarts this effect's
    // delay, so it only fires once the gesture has been still for
    // PAN_SETTLE_DELAY_MS (300 ms) — then the settled offsets/scale/
    // direction below become the viewport the grid and prefetch use.
    // During the drag itself the live offsets only transform the
    // existing tiles (see the gesture handler); nothing refetches.
    LaunchedEffect(panOffsetX, panOffsetY, userScale) {
        delay(PAN_SETTLE_DELAY_MS)
        settledPanOffsetX = panOffsetX
        settledPanOffsetY = panOffsetY
        settledUserScale = userScale
        settledPanDirectionX = panDirectionX
        settledPanDirectionY = panDirectionY
        panDirectionX = 0
        panDirectionY = 0
    }

    // Centre on the selected place. The location puck below marks it; there
    // are no hardcoded landmarks — the radar station's coordinates are not
    // part of the /points response, so it is named in the toolbar, not drawn.
    val centerLat = state.place.point.lat
    val centerLon = state.place.point.lon

    // GIBS satellite layers need a full UTC timestamp: a date-only
    // value resolves to 00:00 UTC (a nighttime image at noon). Use the
    // latest 10-minute GOES scan, refreshed when the layer changes
    // (audit fix E). Never a hardcoded date.
    val gibsTime = remember(selectedLayer) { gibsTimestamp(Instant.now()) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetroTokens.Background)
            .testTag("radar_screen")
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Base tile size in pixels (256 standard Mercator tile size, scaled by userScale)
        val tileSizePx = 256f * userScale

        // Viewport-driven tile set (AUDIT6 §9 Fix 4): the grid is
        // computed from the PANNED VIEWPORT, not the place. The map
        // origin is the screen position of world x/y = 0; panning
        // moves it, so the visible tiles move with it. The DISPLAY
        // range uses the settled pan/scale (reconciled on gesture
        // end) with a 1-tile margin ring, so a pan of up to a tile
        // shows no gap at all during the drag. The PREFETCH range
        // adds one more tile in the settled pan-velocity direction.
        // Both are capped at 9 x 11 tiles, as the old grid was.
        // Pixel centre of the central tile (moved up from below so
        // the ranges can be computed before the prefetch effects).
        val centerWorldX = lonToWorldPixel(centerLon, effectiveZoomLevel) * userScale
        val centerWorldY = latToWorldPixel(centerLat, effectiveZoomLevel) * userScale
        // Live origin: the transform during the drag. Tile POSITIONS
        // always use this, so the map moves under the finger.
        val mapOriginX = (screenWidthPx / 2f) - centerWorldX + panOffsetX
        val mapOriginY = (screenHeightPx / 2f) - centerWorldY + panOffsetY
        // Settled origin: the tile SET follows this one only.
        val settledTileSizePx = 256f * settledUserScale
        val settledMapOriginX = (screenWidthPx / 2f) - lonToWorldPixel(centerLon, effectiveZoomLevel) * settledUserScale + settledPanOffsetX
        val settledMapOriginY = (screenHeightPx / 2f) - latToWorldPixel(centerLat, effectiveZoomLevel) * settledUserScale + settledPanOffsetY
        val displayRange = viewportTileRange(
            settledMapOriginX, settledMapOriginY, screenWidthPx, screenHeightPx,
            settledTileSizePx, effectiveZoomLevel, marginTiles = VIEWPORT_TILE_MARGIN
        )
        val viewportPrefetchRange = viewportTileRange(
            settledMapOriginX, settledMapOriginY, screenWidthPx, screenHeightPx,
            settledTileSizePx, effectiveZoomLevel, marginTiles = VIEWPORT_TILE_MARGIN,
            velocityDx = settledPanDirectionX, velocityDy = settledPanDirectionY
        )
        // Tiles actually rendered: the display range's tile count.
        val totalTiles = displayRange.tileCount

        // Push the rendered tile set up to the per-frame settlement
        // state (fix 2) and the loop's readiness gate (fixes 2-4):
        // the loop's frame-ready gate needs the total the grid
        // actually draws, and the URL-set readiness check needs the
        // ranges themselves — both are only known here.
        SideEffect {
            frameTotalTiles = totalTiles
            displayTileRange = displayRange
            prefetchTileRange = viewportPrefetchRange
        }

        // Safety: never leave the satellite loading overlay up forever.
        // The cap is per satellite VISIT (Fix 9): it fires 12 s after
        // the user entered satellite and does NOT restart on a layer
        // or zoom switch within the visit — the old effect was keyed
        // on (selectedLayer, effectiveZoomLevel), so switching layers
        // within 12 s restarted the timer and the overlay could stay
        // up indefinitely. The remaining delay is computed from the
        // visit start, so a recomposition cannot stretch it either.
        // It sets only the cap flag — the settled count stays honest.
        LaunchedEffect(satVisitStartMs) {
            if (satVisitStartMs == 0L) return@LaunchedEffect
            val remainingMs = satelliteCapRemainingMs(satVisitStartMs, SystemClock.uptimeMillis())
            if (remainingMs > 0) delay(remainingMs)
            satCapExpired = true
        }

        // Prefetch the loop for the visible tile grid (3.3, AUDIT6
        // §9 Fix 3), so a scrub 0->9 is a cache hit instead of a
        // per-frame network fetch. LIVE preloads the whole 10-frame
        // loop; future ranges preload the visible window (current +
        // next 5 — loopPreloadWindowSize). The current frame is
        // enqueued first so the visible tiles are not queued behind
        // the other frames. Runs on entry, when the layer, zoom, or
        // centre changes, and when the panned viewport settles (Fix 4:
        // the effect is keyed on the viewport prefetch range) — not on
        // every frame advance, and not during a drag.
        // Satellite layers get the same treatment below (fix 4): the
        // GIBS grid is a tile set like radar, not a single image. The
        // grid is capped (9 x 11 tiles) and Coil bounds the
        // network concurrency, so this is a bounded burst, not a flood.
        // The prefetch and the AsyncImage loads share the same disk
        // cache and the same URLs, so a prefetched tile is a disk hit
        // for the screen (no network fetch on the second pass). They
        // do NOT share a memory cache entry — prefetch is disk-only
        // (see prefetchRadarTile) — and they do NOT share an OkHttp
        // client: prefetch runs on its own capped lane (AUDIT6
        // section 9 Fix 1), display on the singleton's lane.
        // Coil does NOT deduplicate in-flight requests (AUDIT6 §2.4):
        // the per-generation prefetchEnqueuedUrls guard in
        // prefetchRadarTile is what keeps a re-run from fetching the
        // same tile twice.
        val prefetchLoader = (context.applicationContext as AuraWeatherApp).tilePrefetchLoader
        // One radar tile into the prefetch lane (AUDIT6 §9 Fix 3):
        // disk-only, so the loop's store is the disk cache and the
        // memory cache is never asked to hold the whole loop (AUDIT6
        // §7.6: 770 LIVE tiles decode to ~202 MB; the memory cache
        // holds ~195-390 tiles). memoryCachePolicy DISABLED keeps
        // prefetched tiles out of memory — the displayed frame's
        // tiles decode into memory on screen instead (~77 tiles,
        // ~20 MB) — while diskCachePolicy ENABLED writes the raw
        // bytes to disk, so the later AsyncImage is a disk hit, not
        // a network fetch. (Coil 2.7.0 has no BlackholeDecoder, so
        // the decode still runs transiently; only the retention is
        // skipped — the saving is memory, not decode CPU.)
        // The listener is the per-frame readiness signal: it fires
        // on success or error (both count as settled) and marks the
        // tile's URL settled in prefetchSettledUrls, which is what the
        // loop gates on. Skipped if this URL was already enqueued in
        // this generation (see prefetchEnqueuedUrls).
        fun prefetchRadarTile(url: String) {
            if (!prefetchEnqueuedUrls.add(url)) return
            val disposable = prefetchLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .listener(
                        onSuccess = { _, _ -> markPrefetchSettled(url) },
                        onError = { _, _ -> markPrefetchSettled(url) }
                    )
                    .build()
            )
            prefetchDisposables[url] = disposable
        }
        // One satellite tile into the prefetch lane: same tracking,
        // memory cache on (Fix 3's disk-only rule is for the radar
        // loop's 770 tiles; a satellite frame is one 77-tile image),
        // no readiness listener — satellite readiness is satSettled.
        fun prefetchSatelliteTile(url: String) {
            if (!prefetchEnqueuedUrls.add(url)) return
            val disposable = prefetchLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
            prefetchDisposables[url] = disposable
        }
        // Cancel in-flight prefetches for tiles that left the wanted
        // set (AUDIT6 §9 Fix 4, e.g. a fast pan away): the request is
        // disposed, and the URL is dropped from the enqueue guard and
        // the settled set, so a tile that comes back into view is
        // enqueued — and counted — again. A disposed request that had
        // already completed into the disk cache is harmless: the
        // re-enqueue is a disk hit, not a network fetch.
        fun cancelPrefetchOutside(wanted: Set<String>) {
            val iterator = prefetchDisposables.iterator()
            while (iterator.hasNext()) {
                val (url, disposable) = iterator.next()
                if (url !in wanted) {
                    disposable.dispose()
                    iterator.remove()
                    prefetchEnqueuedUrls.remove(url)
                    if (url in prefetchSettledUrls) prefetchSettledUrls = prefetchSettledUrls - url
                }
            }
        }
        LaunchedEffect(selectedLayer, effectiveZoomLevel, centerLat, centerLon, selectedRange, viewportPrefetchRange) {
            if (selectedLayer == RadarLayer.Reflectivity || selectedLayer == RadarLayer.LocalSite) {
                if (selectedRange == RadarRange.Live) {
                    // Whole-loop preload (Fix 3): all 10 LIVE frames
                    // (loopPreloadWindowSize), current frame first so
                    // the visible tiles are not queued behind the
                    // other nine. Disk-only (see prefetchRadarTile),
                    // ~25 MB first load, then disk-cached.
                    // Viewport-driven (Fix 4): the loop is prefetched
                    // over the panned viewport range, not the place —
                    // a pan settle re-runs this effect, and the
                    // enqueue guard means only the newly visible
                    // tiles are actually enqueued; tiles that left
                    // the viewport are cancelled above.
                    val frames = radarPrefetchUrlsForRange(effectiveZoomLevel, viewportPrefetchRange)
                    cancelPrefetchOutside(frames.flatten().toSet())
                    val current = frames.getOrElse(safeFrameIndex) { emptyList() }
                    val ordered = listOf(safeFrameIndex to current) +
                        frames.mapIndexedNotNull { index, urls ->
                            if (index != safeFrameIndex) index to urls else null
                        }
                    for ((frameIndex, frameUrls) in ordered) {
                        for (url in frameUrls) {
                            prefetchRadarTile(url)
                        }
                    }
                } else {
                    // Switch burst (fix 1): prefetch the current frame
                    // AND the next 5 frames (the rolling look-ahead
                    // window), so the loop does not arrive at unloaded
                    // frames right after a range switch. Bounded: 6
                    // frames x the visible grid (~77 tiles on a phone
                    // grid = ~460 requests), never the whole range —
                    // 49-99 frames would be thousands of requests.
                    // The overlap with the rolling look-ahead effect
                    // below is free: prefetchRadarTile enqueues each
                    // URL once per generation. Full-range cost is
                    // ~3.5 MB for IN 24H, ~5 MB for IN 48H (mostly
                    // transparent PNGs) — stated in the loading state.
                    val burstIndices = listOf(safeFrameIndex) +
                        lookAheadFrameIndices(safeFrameIndex, frameCount)
                    val wanted = burstIndices.flatMapTo(mutableSetOf()) { index ->
                        val layer = frames.getOrNull(index)?.layerName ?: return@flatMapTo emptyList<String>()
                        radarFrameUrlsForRange(layer, effectiveZoomLevel, viewportPrefetchRange)
                    }
                    cancelPrefetchOutside(wanted)
                    for (url in wanted) {
                        prefetchRadarTile(url)
                    }
                }
            } else if (isSatellite) {
                // Satellite prefetch (fix 4): the GIBS grid is a tile
                // set like radar, not a single image — prefetch the
                // visible grid on layer/zoom/centre change so the
                // overlay clears fast instead of tile-by-tile. One
                // bounded burst (~77 tiles on a phone grid).
                val urls = satelliteTileUrlsForRange(
                    selectedLayer == RadarLayer.SatelliteVis,
                    gibsTime, effectiveZoomLevel, viewportPrefetchRange
                )
                cancelPrefetchOutside(urls.toSet())
                for (url in urls) {
                    prefetchSatelliteTile(url)
                }
            }
        }

        // Rolling look-ahead prefetch for future ranges (loop smoothing
        // + AUDIT6 §9 Fix 3): the range effect above prefetches the
        // current frame plus the first 5, so every loop advance used
        // to download the next frame's tiles on demand and stutter.
        // This keeps the next 5 frames ahead of the playhead cached,
        // re-running as the frame index advances (and when the user
        // scrubs). Bounded: 5 frames x the visible grid. Live needs
        // none of this — all 10 of its frames are prefetched by the
        // range effect. This effect is what makes the NEXT frame
        // ready for the loop gate: without it the loop would wait
        // out its 2s timeout on every future-range step. Re-enqueueing
        // a frame is free — prefetchRadarTile enqueues each URL once
        // per generation, so a re-run only enqueues the newly entered
        // frame at the window's far end.
        LaunchedEffect(selectedRange, selectedLayer, effectiveZoomLevel, centerLat, centerLon, safeFrameIndex, viewportPrefetchRange) {
            if (selectedRange == RadarRange.Live) return@LaunchedEffect
            if (selectedLayer != RadarLayer.Reflectivity && selectedLayer != RadarLayer.LocalSite) return@LaunchedEffect
            // Wrap around the end of the loop (L-4): at the last frames
            // the next indices are 0, 1, 2, 3, 4, so frame 0 is prefetched
            // before the loop wraps onto it instead of downloading on
            // demand and stuttering once per cycle. This effect is keyed
            // on safeFrameIndex, so the wrap prefetch runs as soon as the
            // playhead reaches the last frame. Viewport-driven (Fix 4):
            // the look-ahead covers the panned viewport range too.
            for (index in lookAheadFrameIndices(safeFrameIndex, frameCount)) {
                val layer = frames.getOrNull(index)?.layerName ?: continue
                val urls = radarFrameUrlsForRange(layer, effectiveZoomLevel, viewportPrefetchRange)
                for (url in urls) {
                    prefetchRadarTile(url)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tiles are offset outside this Box when panning/zooming;
                // without a clip they paint over the header above
                // (audit fix E).
                .clipToBounds()
                .pointerInput(zoomLevel) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        userScale = (userScale * zoom).coerceIn(0.75f, 2.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                        // Pan direction for the prefetch velocity tile
                        // (Fix 4): from the cumulative pan since the
                        // last settle, not the per-event delta — a real
                        // drag always clears the threshold, jitter
                        // never does. The tile SET does not change
                        // here: the live offsets only transform the
                        // existing tiles until the settle effect fires.
                        panDirectionX = panDirection(panOffsetX - settledPanOffsetX)
                        panDirectionY = panDirection(panOffsetY - settledPanOffsetY)
                    }
                }
        ) {
            // Render the tiles that cover the panned viewport (Fix 4):
            // the settled display range, not the fixed centre-anchored
            // grid. Tile POSITIONS still use the live map origin, so
            // a drag transforms the tiles smoothly and the set only
            // reconciles on settle.
            for (tileX in displayRange.minX..displayRange.maxX) {
                for (tileY in displayRange.minY..displayRange.maxY) {

                    // Snapped tile edges (fix 4): each tile's left/top
                    // edge is rounded once from the common map origin
                    // and its width comes from the NEXT tile's left edge
                    // (snappedTileSpan), so adjacent tiles share edges
                    // exactly. Rounding each tile's offset and size
                    // independently left 1px gaps or overlaps whenever
                    // the tile step was fractional (pinch zoom) — visible
                    // as seams on satellite.
                    val (tileLeftPx, tileWidthPx) = snappedTileSpan(mapOriginX, tileX, tileSizePx)
                    val (tileTopPx, tileHeightPx) = snappedTileSpan(mapOriginY, tileY, tileSizePx)

                    val basemapUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$effectiveZoomLevel/$tileY/$tileX"
                    val radarUrl = when (selectedLayer) {
                        RadarLayer.Reflectivity, RadarLayer.LocalSite ->
                            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$iemLayerName/$effectiveZoomLevel/$tileX/$tileY.png"
                        RadarLayer.SatelliteVis ->
                            gibsTileUrl(true, gibsTime, effectiveZoomLevel, tileX, tileY)
                        RadarLayer.SatelliteIr ->
                            gibsTileUrl(false, gibsTime, effectiveZoomLevel, tileX, tileY)
                    }

                    // Parent-tile fallback (Fix 4): the lower-zoom
                    // parent for this tile, drawn scaled 2x underneath
                    // (see ParentTileUnderlay) so a tile still loading
                    // shows low-res imagery instead of a hole. The
                    // parent URLs are the same tile services at zoom-1.
                    val parent = parentTileFor(tileX, tileY, effectiveZoomLevel)
                    val parentBasemapUrl = parent?.let {
                        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/${it.zoom}/${it.tileY}/${it.tileX}"
                    }
                    val parentRadarUrl = parent?.let {
                        when (selectedLayer) {
                            RadarLayer.Reflectivity, RadarLayer.LocalSite ->
                                iemTileUrl(iemLayerName, it.zoom, it.tileX, it.tileY)
                            RadarLayer.SatelliteVis ->
                                gibsTileUrl(true, gibsTime, it.zoom, it.tileX, it.tileY)
                            RadarLayer.SatelliteIr ->
                                gibsTileUrl(false, gibsTime, it.zoom, it.tileX, it.tileY)
                        }
                    }

                    // Outgoing frame's tile (Fix 2 two-slot swap): the
                    // layer the loop just left, rendered underneath
                    // during the 150ms crossfade. Null when no swap is
                    // in progress, or on satellite layers (no loop).
                    // Built with iemTileUrl — the same URL the screen
                    // loads for that layer — so it is a cache hit.
                    val prevRadarUrl = if (!isSatellite) {
                        crossfadeFromLayer?.let { iemTileUrl(it, effectiveZoomLevel, tileX, tileY) }
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(tileLeftPx, tileTopPx) }
                            .size(
                                with(LocalDensity.current) { tileWidthPx.toDp() },
                                with(LocalDensity.current) { tileHeightPx.toDp() }
                            )
                            // The parent underlays below are drawn 2x
                            // and shifted, so they overflow this box —
                            // clip them to the tile (Fix 4).
                            .clipToBounds()
                    ) {
                        // Parent basemap underlay (Fix 4): low-res map
                        // under the basemap, so a tile still loading
                        // shows map instead of a hole. No settlement
                        // callbacks — parents are fallback imagery,
                        // not the frame's tiles.
                        if (parent != null && parentBasemapUrl != null) {
                            ParentTileUnderlay(parentBasemapUrl, parent, tileSizePx)
                        }

                        // 1. Esri World Dark Gray Base (dark, keyless). CARTO dark_all now returns an
                        // "API KEY REQUIRED" watermark tile, so it cannot be used.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(basemapUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Map tile",
                            placeholder = ColorPainter(RetroTokens.Panel),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Parent radar/satellite underlay (Fix 4):
                        // low-res layer imagery under the layer tile.
                        if (parent != null && parentRadarUrl != null) {
                            ParentTileUnderlay(parentRadarUrl, parent, tileSizePx)
                        }

                        // 2. Real IEM NEXRAD Composite Radar Tile (Transparent PNG when no rain!)
                        // Satellite layers report each tile as it settles
                        // (loaded or errored) so the loading overlay knows
                        // when the imagery has arrived. Radar layers
                        // report each tile to the per-frame settlement
                        // counters: the fallback readiness for the frame
                        // on screen (the loop gates on prefetch
                        // readiness — see isFrameReady — and the layer
                        // fades in whole once ready). Forecast ranges also
                        // report each tile, so a down IEM backend shows
                        // the "future radar unavailable" state instead of
                        // a blank void. Explicit cache policies: the
                        // prefetch and the screen share the same disk
                        // cache and URLs, so a prefetched tile is a disk
                        // hit here. key(forecastRetryGen)
                        // recreates the request on a retry.
                        key(forecastRetryGen) {
                            // Outgoing frame, fading out (Fix 2): no
                            // settlement callbacks — its tiles must not
                            // count toward the INCOMING frame's counters.
                            // Per-tile crossfade off: the layer alpha on
                            // this AsyncImage is the crossfade.
                            if (prevRadarUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(prevRadarUrl)
                                        .crossfade(false)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = "Radar tile",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.85f * (1f - crossfadeProgress))
                                )
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(radarUrl)
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "Radar tile",
                                contentScale = ContentScale.FillBounds,
                                onSuccess = {
                                    if (isSatellite) satSettled++
                                    if (isForecastRange && !isSatellite) forecastTileOk++
                                    if (!isSatellite) frameTileOk++
                                },
                                onError = {
                                    if (isSatellite) satSettled++
                                    if (isForecastRange && !isSatellite) forecastTileErr++
                                    if (!isSatellite) frameTileErr++
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (crossfadeFromLayer != null) 0.85f * crossfadeProgress else frameAlpha)
                            )
                        }

                        // 3. Esri reference layer (city labels + boundaries,
                        // keyless) on top, so the map names places instead
                        // of showing bare terrain under the radar. Radar
                        // layers only: the GIBS satellite imagery carries
                        // its own embedded labels, so the reference layer
                        // draws every city twice on satellite ("Sault Ste.
                        // Marie" stacked over the same dot).
                        if (!isSatellite) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$effectiveZoomLevel/$tileY/$tileX")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Place labels",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Location Puck for the selected place
            val userWorldX = lonToWorldPixel(centerLon, effectiveZoomLevel) * userScale
            val userWorldY = latToWorldPixel(centerLat, effectiveZoomLevel) * userScale
            val userScreenX = (screenWidthPx / 2f) + (userWorldX - centerWorldX) + panOffsetX
            val userScreenY = (screenHeightPx / 2f) + (userWorldY - centerWorldY) + panOffsetY

            Box(
                modifier = Modifier
                    .offset { IntOffset(userScreenX.roundToInt() - 12, userScreenY.roundToInt() - 12) }
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = AuraColors.Accent.copy(alpha = 0.35f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {}
                Surface(
                    color = AuraColors.Accent,
                    shape = CircleShape,
                    modifier = Modifier.size(12.dp)
                ) {}
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(4.dp)
                ) {}
            }
        }

        // Satellite loading overlay (audit findings 1+2): while the GIBS
        // tiles are still arriving, the map is a blank navy void with no
        // feedback, and unloaded regions read as broken gray rectangles.
        // A spinner + "LOADING SATELLITE..." over the map says it is
        // loading, not broken. The Fix 4 parent underlay is the
        // placeholder underneath — low-res satellite imagery, never a
        // hole — and the layer fades in whole the moment the frame is
        // settled (Fix 9), at the same moment this overlay clears.
        // The overlay clears on the whole-frame settled count, or on
        // the per-visit 12 s cap — whichever comes first. The cap does
        // not restart on a layer switch, so the overlay can never stay
        // up indefinitely. The cost note is honest: a layer switch
        // re-fetches the whole grid, about 6 MB visible / 5 MB IR
        // (audit appendix) — no Wi-Fi-only gate, just the number.
        if (isSatellite && !isSatelliteFrameSettled(satSettled, totalTiles) && !satCapExpired) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                RetroPanel(shape = RetroShapes.medium) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RetroTokens.Yellow,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "LOADING SATELLITE...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = satelliteCostText(selectedLayer == RadarLayer.SatelliteVis),
                            style = MaterialTheme.typography.labelSmall,
                            color = RetroTokens.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Loop loading (AUDIT6 §9 Fix 3): until the current frame is
        // ready for the first time in this generation, the map is bare
        // basemap — say the loop is loading, and say what it costs.
        // The cost is the honest first-load figure from the audit's
        // probe (§9 Fix 3): LIVE downloads ~25 MB of speckle-heavy
        // tiles the first time, then reads them from the disk cache;
        // future ranges are mostly transparent PNGs (~3.5 MB for
        // 24H, ~5 MB for 48H). No Wi-Fi-only gate: the rule is honest
        // cost, not a gate. Once primed this never shows again in the
        // generation — later waits hold the previous frame visible
        // instead (Fix 2).
        if (!isSatellite && !loopPrimed && !forecastUnavailable) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                RetroPanel(shape = RetroShapes.medium) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RetroTokens.Yellow,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "LOADING LOOP...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = loopPreloadCostText(selectedRange),
                            style = MaterialTheme.typography.labelSmall,
                            color = RetroTokens.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Future-radar unavailable (radar fix A): when the forecast
        // tiles all fail (IEM down), the map would otherwise be a blank
        // navy void with the Esri basemap and no radar. Say so, and
        // offer a retry. Auto-retry (above) already tried with backoff;
        // this is the manual path after those attempts.
        if (forecastUnavailable) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                RetroPanel(shape = RetroShapes.medium) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FUTURE RADAR UNAVAILABLE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.Yellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "THE HRRR FORECAST IS NOT LOADING RIGHT NOW.",
                            style = MaterialTheme.typography.labelSmall,
                            color = RetroTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        RetroPanel(
                            shape = RetroShapes.medium,
                            modifier = Modifier.clickable {
                                forecastRetryGen++
                                forecastAutoRetries = 0
                            }
                        ) {
                            Text(
                                text = "RETRY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.Yellow,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top floating pill cluster: station pill + frame label pill, then
        // the layer chooser as a pill segmented control.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroPanel(
                    shape = RetroShapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = AuraColors.Accent,
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        // The header names the layer and the area, not the
                        // bare station code: 'Composite Radar' for the
                        // composite, the place name for the local site
                        // ('Covington Radar', not 'KILN Radar').
                        Text(
                            text = (when (selectedLayer) {
                                RadarLayer.Reflectivity -> "Composite Radar"
                                RadarLayer.LocalSite -> {
                                    val area = state.place.name.ifBlank { null }
                                        ?: state.radarStation
                                    if (area != null) "$area Radar" else "NEXRAD Radar"
                                }
                                RadarLayer.SatelliteVis -> "Visible Satellite"
                                RadarLayer.SatelliteIr -> "Infrared Satellite"
                            }).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.Yellow,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                RetroPanel(
                    shape = RetroShapes.medium,
                    modifier = Modifier.height(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = (currentFrame?.label ?: "LIVE NOW").uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFrame?.label == "LIVE NOW" || currentFrame?.label == "NOW") {
                                RetroTokens.Yellow
                            } else {
                                RetroTokens.TextPrimary
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Layer chooser as a pill segmented control.
            PillSegmentedControl(
                options = listOf(
                    "Composite",
                    "${state.radarStation ?: "Local"} Site",
                    "Sat Visible",
                    "Sat IR"
                ),
                selectedIndex = selectedLayer.ordinal,
                onSelect = { onSelectLayer(RadarLayer.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )

            // Time-range chooser (Build B): only for radar layers, never
            // satellite (a single image). The range is never mixed into
            // the live loop — it replaces the loop's frame list outright.
            if (!isSatellite) {
                Spacer(modifier = Modifier.height(10.dp))
                PillSegmentedControl(
                    options = RadarRange.entries.map { it.shortTitle },
                    selectedIndex = selectedRange.ordinal,
                    onSelect = { onSelectRange(RadarRange.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Future ranges are model output: say so plainly and in a
            // distinct colour, so a forecast is never mistaken for
            // observed radar.
            if (isForecastRange) {
                Spacer(modifier = Modifier.height(10.dp))
                RetroPanel(shape = RetroShapes.medium) {
                    Text(
                        text = "FORECAST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.HeaderOrange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Zoom +/- In-Map Controls (48dp solid navy buttons)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassIconButton(
                onClick = {
                    if (zoomLevel < 9) {
                        zoomLevel += 1
                        userScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                        // Recentre the settled viewport at once (Fix 4)
                        // instead of waiting out the settle delay.
                        settledPanOffsetX = 0f
                        settledPanOffsetY = 0f
                        settledUserScale = 1f
                    }
                },
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = RetroTokens.TextPrimary
                )
            }

            GlassIconButton(
                onClick = {
                    if (zoomLevel > 7) {
                        zoomLevel -= 1
                        userScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                        settledPanOffsetX = 0f
                        settledPanOffsetY = 0f
                        settledUserScale = 1f
                    }
                },
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = RetroTokens.TextPrimary
                )
            }

            GlassIconButton(
                onClick = {
                    panOffsetX = 0f
                    panOffsetY = 0f
                    userScale = 1f
                    settledPanOffsetX = 0f
                    settledPanOffsetY = 0f
                    settledUserScale = 1f
                },
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Recenter",
                    tint = RetroTokens.TextPrimary
                )
            }
        }

        // Bottom Controls: Legend, Playback & Scrub Slider. The bottom
        // padding is the nav-bar clearance (bar height + margin + inset),
        // not a fixed 16dp — with 16dp the transport pill sat behind the
        // floating nav bar and was unusable. Radar layers only: satellite
        // is a single image, so there is no dBZ legend and no loop to
        // play (audit fix E).
        if (!isSatellite) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = AuraBars.contentBottomPadding(tickerVisible)
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Radar Legend Bar
            RetroPanel(shape = RetroShapes.medium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DBZ:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.Yellow
                    )
                    for ((label, color) in AuraColors.RadarLegend) {
                        LegendColorPill(label, color)
                    }
                }
            }

            // Forecast ranges are model output: a short one-line caption
            // on a solid panel (like the dBZ legend bar), so it never
            // wraps with an orphaned word or collides with map labels.
            // The live clear-air caption stays a plain line.
            if (isForecastRange) {
                RetroPanel(shape = RetroShapes.medium) {
                    Text(
                        text = "FORECAST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.HeaderOrange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Clear-air speckle: the lightest echoes can be insects or
                // ground clutter, not rain. The tiles are pre-rendered, so
                // labelling (not filtering) is what the app can do. On a
                // solid panel like the dBZ legend bar, so the caption never
                // sits bare on the map competing with city labels.
                RetroPanel(shape = RetroShapes.medium) {
                    Text(
                        text = "LIGHTEST ECHOES CAN BE CLEAR-AIR RETURNS, NOT RAIN.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Classic transport pill: play/pause + frame scrubber inside
            // the pill. The frame label lives in the top pill now.
            RetroPanel(
                shape = RetroShapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        onClick = onTogglePlay,
                        containerColor = RetroTokens.Yellow,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = RetroTokens.Background
                        )
                    }

                    // A 28dp thumb: the default thumb is a tiny dot that is
                    // hard to grab precisely. The track keeps the default
                    // 48dp touch height.
                    // The range's extent, flanking the scrubber so it costs
                    // no extra row: first frame label to last frame label.
                    Text(
                        text = (frames.firstOrNull()?.label ?: "-45M").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = RetroTokens.Yellow
                    )

                    Slider(
                        value = safeFrameIndex.toFloat(),
                        onValueChange = { onScrubFrame(it.toInt()) },
                        valueRange = 0f..(frameCount - 1).coerceAtLeast(1).toFloat(),
                        steps = (frameCount - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = RetroTokens.Yellow,
                            activeTrackColor = RetroTokens.Yellow,
                            inactiveTrackColor = RetroTokens.Border
                        ),
                        thumb = { _ ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = RetroTokens.Yellow,
                                        shape = CircleShape
                                    )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = (frames.lastOrNull()?.label ?: "NOW").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = RetroTokens.Yellow
                    )

                }
            }
        }
        }
    }
}

// Spherical Mercator projection utilities (tile coordinates now live in
// the mapper, RadarTiles, where the prefetch tests cover them).
private fun lonToWorldPixel(lon: Double, zoom: Int): Float {
    val density = 1f
    val fullSize = (256 * (1 shl zoom)).toFloat()
    return (((lon + 180.0) / 360.0).toFloat() * fullSize)
}

private fun latToWorldPixel(lat: Double, zoom: Int): Float {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val fullSize = (256 * (1 shl zoom)).toFloat()
    val yFrac = (1.0 - (ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI)) / 2.0
    return (yFrac.toFloat() * fullSize)
}

@Composable
private fun LegendColorPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = CircleShape,
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = RetroTokens.TextPrimary
        )
    }
}

// Parent-tile underlay (AUDIT6 §9 Fix 4): the lower-zoom parent tile,
// drawn at 2x tile size and shifted so the child's quadrant of the
// parent lands exactly on the child's box. The tile Box clips it (it
// has clipToBounds). No crossfade, no settlement callbacks — it is
// fallback imagery while the child loads, and the child covers it
// completely once it arrives (the basemap is opaque; the radar parent
// is the same data at lower resolution, so it reads as a soft version
// of the child, not a ghost).
@Composable
private fun ParentTileUnderlay(parentUrl: String, parent: ParentTile, tileSizePx: Float) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .offset { IntOffset((-parent.offsetX * tileSizePx).roundToInt(), (-parent.offsetY * tileSizePx).roundToInt()) }
            .size(
                with(LocalDensity.current) { (tileSizePx * 2f).toDp() },
                with(LocalDensity.current) { (tileSizePx * 2f).toDp() }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(parentUrl)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}
