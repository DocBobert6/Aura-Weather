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
import com.example.core.HttpClients
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
import com.example.data.mapper.FORECAST_SLOW_WAIT_MS
import com.example.data.mapper.RADAR_LOOP_WAIT_TIMEOUT_MS
import com.example.data.mapper.isForecastSlow
import com.example.data.mapper.settledUrlCount
import com.example.data.mapper.radarFrameUrlsForRange
import com.example.data.mapper.radarPrefetchUrlsForRange
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.satelliteCapRemainingMs
import com.example.data.mapper.satelliteCostText
import com.example.data.mapper.satelliteTileUrlsForRange
import com.example.data.mapper.satelliteZoomLevel
import com.example.data.mapper.snappedTileSpan
import com.example.data.mapper.viewportTileRange
import com.example.data.mapper.LIBRE_WXR_MAPS_URL
import com.example.data.mapper.LibreWxrMaps
import com.example.data.mapper.MRMS_SERVICE_INFO_URL
import com.example.data.mapper.RadarFrame
import com.example.data.mapper.RadarFrameContent
import com.example.data.mapper.RadarSourceKind
import com.example.data.mapper.ResolvedLiveFrame
import com.example.data.mapper.expandedTileRange
import com.example.data.mapper.fallbackSourceAfterFailure
import com.example.data.mapper.liveFrameContentUrls
import com.example.data.mapper.liveFrameTileUrl
import com.example.data.mapper.lonLatBboxForTileRange
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.mrmsLiveFrames
import com.example.data.mapper.parseLibreWxrMaps
import com.example.data.mapper.resolveLiveFrame
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

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
    // Fix 13 seam: the ViewModel-owned frame list, when the caller
    // passes it (plumbing in WeatherNavDisplay/MainActivity still to
    // do — the seam is built here and in WeatherViewModel). Null keeps
    // the screen's own frozen-at-range-entry list.
    radarFrames: List<RadarFrame>? = null,
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

    // Observed-radar source metadata (AUDIT6 §9 Fixes 6+7): the MRMS
    // service-info timeExtent and the LibreWXR maps document, fetched
    // on Live entry by the effect below (after isSatellite). Either
    // may stay empty / null — a missing document just removes that
    // source from per-frame resolution, and the loop falls through to
    // the next source in the order. Keyed on the range, so re-entering
    // Live re-fetches fresh metadata (frame times move; old frames
    // prune within minutes).
    var mrmsFrameTimesMs by remember(selectedRange) { mutableStateOf<List<Long>>(emptyList()) }
    var libreWxrMaps by remember(selectedRange) { mutableStateOf<LibreWxrMaps?>(null) }

    // The frames for the selected range (Build B), oldest first.
    // Live is the 10-frame loop — from the MRMS extent once its
    // metadata arrives (mrmsLiveFrames: the newest 10 of the service's
    // window, true valid times), else the IEM skeleton; future ranges
    // are HRRR. Recomputed when the range changes (a fresh "now") or
    // when MRMS metadata arrives, not on every frame advance.
    // When the caller passes the ViewModel-owned list (Fix 13 seam,
    // `radarFrames`), that list is the fallback instead of a fresh
    // recompute — the ViewModel's list and this one agree on count,
    // both computed once at range entry.
    val frames = remember(selectedRange, mrmsFrameTimesMs, radarFrames) {
        if (selectedRange == RadarRange.Live) {
            val mrms = mrmsLiveFrames(mrmsFrameTimesMs, Instant.now())
            if (mrms.isNotEmpty()) mrms else radarFrames ?: radarRangeFrames(selectedRange, Instant.now())
        } else {
            radarFrames ?: radarRangeFrames(selectedRange, Instant.now())
        }
    }
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

    // Fetch the observed-radar source metadata (Fix 6+7): the MRMS
    // service info (timeExtent) and the LibreWXR maps document (frame
    // times + tile prefixes). Two small JSON documents over the plain
    // OkHttp client (no Retrofit needed — the pure parsers read the
    // numbers/frames needed). Fetched independently: either may fail
    // without blocking the other, and a failure just removes that
    // source from resolution. No periodic re-fetch: like the frozen
    // frame list, metadata refreshes on range re-entry. Satellite
    // has no loop, so no fetch while on satellite; switching back to
    // a radar layer re-runs this effect and fetches then.
    val radarSourceHttpClient = remember { HttpClients.plainClient() }
    LaunchedEffect(selectedRange, isSatellite) {
        if (selectedRange != RadarRange.Live || isSatellite) return@LaunchedEffect
        val mrmsJson = withContext(Dispatchers.IO) {
            fetchRadarSourceText(radarSourceHttpClient, MRMS_SERVICE_INFO_URL)
        }
        if (mrmsJson != null) mrmsFrameTimesMs = mrmsFrameTimesFromServiceInfoJson(mrmsJson)
        val libreJson = withContext(Dispatchers.IO) {
            fetchRadarSourceText(radarSourceHttpClient, LIBRE_WXR_MAPS_URL)
        }
        if (libreJson != null) parseLibreWxrMaps(libreJson)?.let { libreWxrMaps = it }
    }

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
    // Cheap-retry tick (AUDIT6 §9 Fix 12): a retry bumps THIS, not
    // forecastRetryGen. forecastRetryGen is the tile-set generation
    // key — bumping it recreates every tile AsyncImage via
    // key(forecastRetryGen) below and cancels in-flight tiles. The
    // tick instead resets the counters (in the retry handlers) and
    // re-arms the slow window/flag (keyed on it below), and drives
    // the cheap re-enqueue effect, which re-requests only the
    // current window's tiles whose PREFETCH failed (see
    // prefetchFailedUrls). Keyed on the range, so a range switch
    // starts a fresh tick.
    var forecastCheapRetryTick by remember(selectedRange) { mutableIntStateOf(0) }
    // Time-based slow flag (AUDIT6 §9 Fix 12): set by the effect near
    // the prefetch effects once the current frame has had
    // FORECAST_SLOW_WAIT_MS and still has too few tiles settled.
    // Keyed on the tile-set generation (same keys as
    // prefetchSettledUrls) and the retry tick: a layer/zoom/place
    // change or a retry re-arms a fresh 20 s window, so a fresh tile
    // set is never measured against a stale deadline and a stuck
    // flag never survives onto a new tile set.
    var forecastSlowTimedOut by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen, forecastCheapRetryTick) { mutableStateOf(false) }
    // Start of the current generation's slow window (uptime ms).
    // The remember initializer runs when the tile-set generation or
    // the retry tick changes, so a layer/zoom/place change or a
    // retry restarts the window.
    val forecastGenStartMs by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen, forecastCheapRetryTick) { mutableLongStateOf(SystemClock.uptimeMillis()) }
    // The full unavailable flag is defined after loopPrimed below:
    // it needs the prefetch settled set (declared later) for the
    // slow fraction, and the loop-primed gate. The error trigger
    // itself is this: forecastTileErr >= 3 with no successes means
    // IEM is down (or the layer is wrong), not a slow load.
    val forecastErrorUnavailable = isForecastRange && !isSatellite &&
        forecastTileErr >= 3 && forecastTileOk == 0

    // Held frame (AUDIT6 §9 Fix 14): the frame the map actually
    // shows. Scrubbing changes safeFrameIndex (the target) at once,
    // but the map keeps showing the last frame whose tiles are ready
    // until the target's tiles settle — the hold effect after the
    // loop effect below moves this forward. Keyed on the tile-set
    // generation, so a range/layer/zoom/centre change restarts the
    // hold at the current frame. Satellite never uses it (no loop,
    // no frame list); the hold effect returns early there.
    var heldFrameIndex by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableIntStateOf(safeFrameIndex) }
    val heldFrame = frames.getOrNull(heldFrameIndex)
    val heldLayerName = heldFrame?.layerName ?: iemLayerName

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
    var frameTileOk by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, heldFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
    var frameTileErr by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, heldFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
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
    // Tiles whose PREFETCH failed (Fix 12 cheap retry): the settled
    // set above cannot tell a loaded tile from a failed one, and a
    // retry must re-request only the failed ones — never the tiles
    // still in flight, never the loaded ones. Same generation keys
    // as the settled set, so a generation change starts it empty.
    var prefetchFailedUrls by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<Set<String>>(emptySet()) }
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
        if (url in prefetchFailedUrls) prefetchFailedUrls = prefetchFailedUrls - url
    }

    // One prefetch FAILURE for [url]: it counts as settled (the loop
    // must not wait on it forever) and is recorded as failed, so a
    // cheap retry can re-request exactly this tile.
    fun markPrefetchFailed(url: String) {
        if (url !in prefetchSettledUrls) prefetchSettledUrls = prefetchSettledUrls + url
        if (url !in prefetchFailedUrls) prefetchFailedUrls = prefetchFailedUrls + url
    }

    // Per-frame source overrides (Fix 6+7 fallback state machine).
    //
    // The machine, in full:
    // 1. RESOLVE (pure, RadarTiles.resolveLiveFrame): each Live frame
    //    resolves to the first source in MRMS -> LibreWXR -> IEM whose
    //    prerequisites hold (MRMS: extent time within half a step +
    //    viewport bbox known; LibreWXR: maps document + a past frame
    //    within half a step; IEM: always). The resolved content is
    //    what the loop prefetches, gates on and draws.
    // 2. DETECT (prefetch listener): a failed prefetch marks the URL
    //    in prefetchFailedUrls (markPrefetchFailed). For MRMS that is
    //    one URL — the frame's whole image — so the failure is
    //    unambiguous. For LibreWXR a single bad tile means nothing
    //    (settled-error semantics); only ALL of the frame's tiles
    //    failing demotes it.
    // 3. FALL BACK (watcher effect below): the pure
    //    fallbackSourceAfterFailure decides the next source per frame
    //    from the failure set, and writes it here. Overrides only ever
    //    move DOWN the order, and resolveLiveFrame starts from the
    //    override — so a fallback can never oscillate back to a source
    //    that already failed for that frame, even though
    //    cancelPrefetchOutside drops failed URLs from the failure set
    //    (the OVERRIDE, not the failure set, is the memory).
    // 4. RE-PREFETCH + RE-GATE: the resolved list feeds the prefetch
    //    effect and isFrameReady, so the fallback's URLs are enqueued
    //    and the loop waits on them (bounded by the 2 s timeout, as
    //    always). The held frame only releases when the FALLBACK
    //    content is ready (Fix 14 hold, unchanged).
    // Keyed on the tile-set generation, like the prefetch settled set:
    // a new generation retries the primary from scratch.
    var liveFrameSourceOverride by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<Map<Int, RadarSourceKind>>(emptyMap()) }

    // The MRMS viewport (Fix 6): the settled prefetch tile range,
    // expanded by one tile, as a lon/lat bbox + the pixel size the
    // exportImage is rendered at (the bbox's screen size at the
    // settled scale, so the image is drawn 1:1). Derived from state
    // published by the grid, so a pan settle rebuilds the bbox and
    // the MRMS URLs with it. Null / 0 until the grid has reported
    // once — until then MRMS cannot resolve and frames fall through
    // to LibreWXR/IEM.
    val mrmsTileRange = remember(prefetchTileRange, effectiveZoomLevel) {
        prefetchTileRange?.let { expandedTileRange(it, 1, effectiveZoomLevel) }
    }
    val mrmsBbox = remember(mrmsTileRange, effectiveZoomLevel) {
        mrmsTileRange?.let { lonLatBboxForTileRange(it, effectiveZoomLevel) }
    }
    val mrmsImageWidthPx = remember(mrmsTileRange, settledUserScale) {
        mrmsTileRange?.let { (it.width * 256f * settledUserScale).roundToInt().coerceIn(256, 2048) } ?: 0
    }
    val mrmsImageHeightPx = remember(mrmsTileRange, settledUserScale) {
        mrmsTileRange?.let { (it.height * 256f * settledUserScale).roundToInt().coerceIn(256, 2048) } ?: 0
    }

    // The resolved observed-radar content for every Live frame (Fix
    // 6+7): MRMS single image where the extent covers the frame, else
    // LibreWXR tiles, else IEM tiles — per frame, via resolveLiveFrame.
    // Future ranges resolve nothing here (IEM HRRR stays their only
    // source). Recomputed when the frame list, the source metadata,
    // the viewport bbox, the tile range or a per-frame fallback
    // override changes. The prefetch effect and isFrameReady both read
    // this list, so a fallback override re-prefetches and re-gates the
    // frame without touching the loop.
    val liveResolved: List<ResolvedLiveFrame> = remember(frames, mrmsFrameTimesMs, libreWxrMaps, mrmsBbox, mrmsImageWidthPx, mrmsImageHeightPx, effectiveZoomLevel, prefetchTileRange, liveFrameSourceOverride, isSatellite) {
        if (selectedRange != RadarRange.Live || isSatellite) emptyList()
        else frames.mapIndexed { index, frame ->
            resolveLiveFrame(
                validTime = frame.validTime,
                iemLayerName = frame.layerName,
                mrmsTimesMs = mrmsFrameTimesMs,
                libreWxrMaps = libreWxrMaps,
                bbox = mrmsBbox,
                imageWidthPx = mrmsImageWidthPx,
                imageHeightPx = mrmsImageHeightPx,
                zoom = effectiveZoomLevel,
                tileRange = prefetchTileRange,
                startFrom = liveFrameSourceOverride[index]
            )
        }
    }

    // Fallback watcher (Fix 6+7 state machine, step 3): when the
    // resolved list or the prefetch failure set changes, ask the pure
    // fallback rule whether any frame's current source failed, and
    // record the next source as that frame's override. Only ever
    // writes a move DOWN the order (the pure rule returns null for
    // IEM, the terminal source, and for failures that do not demote),
    // and a write recomputes liveResolved — which re-runs this effect
    // with the new source, whose URLs have not failed, so the watcher
    // goes quiet. No retry within a generation: a frame that fell
    // back stays fallen back until the tile-set generation changes.
    LaunchedEffect(liveResolved, prefetchFailedUrls) {
        if (selectedRange != RadarRange.Live || isSatellite) return@LaunchedEffect
        var updated: Map<Int, RadarSourceKind>? = null
        for ((index, resolved) in liveResolved.withIndex()) {
            val next = fallbackSourceAfterFailure(
                resolved.source, resolved.content, prefetchFailedUrls, libreWxrMaps != null
            ) ?: continue
            if (next == resolved.source) continue
            val base = updated ?: liveFrameSourceOverride
            updated = base + (index to next)
        }
        val nextOverrides = updated
        if (nextOverrides != null) liveFrameSourceOverride = nextOverrides
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
        // Live multi-source (Fix 6+7): readiness is the RESOLVED
        // content's settlement. A SingleImage frame is ready when its
        // one image settled in the prefetch lane; a Tiled frame when
        // every tile URL of its resolved grid settled. The resolved
        // source already accounts for per-frame fallback overrides, so
        // a frame that fell back to LibreWXR is gated on LibreWXR
        // tiles, not on MRMS. A Tiled frame with no URLs yet (grid not
        // reported) falls back to the displayed frame's own AsyncImages
        // below, so the loop is never wedged before the first layout.
        if (selectedRange == RadarRange.Live && !isSatellite) {
            val resolved = liveResolved.getOrNull(frameIndex) ?: return false
            return when (val content = resolved.content) {
                is RadarFrameContent.SingleImage -> content.url in prefetchSettledUrls
                is RadarFrameContent.Tiled -> if (content.urls.isEmpty()) {
                    frameIndex == heldFrameIndex && frameReady
                } else {
                    isTileSettled(content.urls, prefetchSettledUrls)
                }
            }
        }
        val range = prefetchTileRange
        if (range != null && !isSatellite) {
            val layer = frames.getOrNull(frameIndex)?.layerName ?: return false
            if (isTileSettled(radarFrameUrlsForRange(layer, effectiveZoomLevel, range), prefetchSettledUrls)) return true
        }
        return frameIndex == heldFrameIndex && frameReady
    }

    // Latest-state reader for frame readiness (Fix 6+7): the loop
    // effect below is long-lived (keyed on play state, not on the
    // frame list), and isFrameReady closes over liveResolved and
    // frames — plain values that CHANGE when MRMS metadata arrives
    // (skeleton -> MRMS list) or a fallback override lands. A
    // snapshotFlow holding the launch-time function would keep gating
    // on the stale IEM resolution — whose URLs the prefetch effect
    // already cancelled — and the loop would fall back to its 2 s
    // timeout on every step. rememberUpdatedState refreshes this
    // lambda every recomposition (the same pattern as
    // latestForecastSlowCheck), so the flows below re-evaluate against
    // the current resolution. displayedFrameReady above needs none of
    // this: it is computed during composition, fresh every time.
    val latestIsFrameReady by rememberUpdatedState({ index: Int -> isFrameReady(index) })
    // The frame on screen is the HELD frame (Fix 14), not the scrub
    // target: alpha follows the held frame's readiness, so scrubbing
    // to a frame whose tiles have not arrived yet cannot fade the
    // layer out from under the user.
    val displayedFrameReady = isFrameReady(heldFrameIndex)

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

    // Full forecast-unavailable flag (Fix 12): the settled-error
    // trigger above, OR the time-based slow trigger (the slow flag
    // is set by the effect near the prefetch effects). The slow
    // trigger only fires while the loop has never primed: once a
    // frame has shown, later waits hold the previous frame visible
    // (Fix 2) instead of an empty map, so there is no empty state
    // to report. The LOADING LOOP overlay below is suppressed by
    // this flag, so the two states never fight over the same
    // centred panel.
    val forecastUnavailable = forecastErrorUnavailable ||
        (forecastSlowTimedOut && !loopPrimed)

    // Latest-state reader for the slow check (Fix 12): the slow
    // effect below delays 20 s, so it must not read the settled
    // fraction captured at launch. rememberUpdatedState refreshes
    // this lambda every recomposition; the effect calls it after
    // the delay and sees the current frame, the current settled
    // set and the current primed state.
    val latestForecastSlowCheck by rememberUpdatedState {
        if (!isForecastRange || isSatellite || loopPrimed) false else {
            val range = prefetchTileRange
            if (range == null) false else {
                val urls = radarFrameUrlsForRange(iemLayerName, effectiveZoomLevel, range)
                isForecastSlow(
                    settledUrlCount(urls, prefetchSettledUrls),
                    urls.size,
                    SystemClock.uptimeMillis() - forecastGenStartMs
                )
            }
        }
    }

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
    var lastShownLayer by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf(heldLayerName) }
    // The previous held frame's resolved content (Fix 6+7): the
    // outgoing image for the crossfade when the previous frame was
    // MRMS (SingleImage — its URL, drawn by the overlay below) or
    // LibreWXR/IEM (Tiled — its per-tile URLs, via prevRadarUrl).
    // Null for future ranges (no resolved list) and on the first
    // frame. Same generation keys as the layer state, so a range
    // switch never crossfades from a stale frame.
    var crossfadeFromResolved by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<ResolvedLiveFrame?>(null) }
    var lastShownResolved by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableStateOf<ResolvedLiveFrame?>(liveResolved.getOrNull(heldFrameIndex)) }
    // The held frame this effect last crossfaded FOR (coordinator fix,
    // 2026-11-15): the effect below is keyed on the held frame's
    // resolved content as well as its index, so it also restarts when
    // the content changes under the same frame — and this index tells
    // the two restarts apart. Same generation keys as the layer state.
    var lastCrossfadeIndex by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen) { mutableIntStateOf(heldFrameIndex) }
    val crossfadeProgress by animateFloatAsState(
        targetValue = if (crossfadeFromLayer != null) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "radarCrossfade"
    )
    // Keyed on the HELD frame (Fix 14) AND its resolved content (Fix
    // 6+7): the crossfade runs when the map actually swaps, not when
    // the scrub target changes — and the content key keeps
    // lastShownResolved honest when the resolution changes under the
    // same frame. That happens on every Live entry: the list starts as
    // the IEM skeleton and rebuilds to MRMS once the metadata lands
    // (~1 s), while the held index stays put — without the sync below,
    // the first loop step would crossfade from the stale IEM content
    // (tiles the user never saw) instead of the MRMS image on screen.
    // A restart with the SAME index is a content-only change: sync the
    // bookkeeping and return without a crossfade (the incoming content
    // fades in via frameAlpha on its own). A restart with a NEW index
    // is a real swap: crossfade from the previous content. If a
    // content change cancels a crossfade mid-delay, crossfadeFromLayer
    // stays set until the next swap clears it — benign: the outgoing
    // is already invisible (progress 1f) and the next swap overwrites
    // it.
    LaunchedEffect(heldFrameIndex, liveResolved.getOrNull(heldFrameIndex)) {
        val previous = lastShownLayer
        val previousResolved = lastShownResolved
        val currentResolved = liveResolved.getOrNull(heldFrameIndex)
        if (previous == heldLayerName && previousResolved == currentResolved) return@LaunchedEffect
        if (heldFrameIndex == lastCrossfadeIndex) {
            lastShownLayer = heldLayerName
            lastShownResolved = currentResolved
            return@LaunchedEffect
        }
        lastCrossfadeIndex = heldFrameIndex
        crossfadeFromLayer = previous
        crossfadeFromResolved = previousResolved
        delay(150)
        crossfadeFromLayer = null
        crossfadeFromResolved = null
        lastShownLayer = heldLayerName
        lastShownResolved = currentResolved
    }

    // Auto-retry when the forecast is unavailable: up to 3 attempts
    // with exponential backoff (5s, 10s, 20s), then stop and leave the
    // RETRY button. Each attempt bumps the cheap-retry tick (Fix 12),
    // NOT the retry generation — bumping the generation would
    // recreate every tile AsyncImage and cancel in-flight tiles.
    // The tick resets the forecast counters and re-arms the slow
    // window, and drives the cheap re-enqueue effect, which
    // re-requests only the tiles whose prefetch failed. Coil does
    // not cache failures, so the re-request hits the network again.
    LaunchedEffect(forecastUnavailable, forecastAutoRetries) {
        if (!forecastUnavailable || forecastAutoRetries >= 3) return@LaunchedEffect
        val backoffMs = 5000L * (1L shl forecastAutoRetries)
        delay(backoffMs)
        if (forecastUnavailable) {
            forecastAutoRetries++
            // Cheap retry (Fix 12): bump the retry tick, NOT
            // forecastRetryGen — bumping the generation would
            // recreate every tile AsyncImage and cancel the tiles
            // still in flight. The tick resets the forecast
            // counters (below) and the slow window/flag (keyed on
            // it), and drives the cheap re-enqueue effect, which
            // re-requests only the tiles whose prefetch failed.
            forecastCheapRetryTick++
            forecastTileOk = 0
            forecastTileErr = 0
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
    // Scrubbing (AUDIT6 §9 Fix 14): true while the user's finger is
    // on the scrubber. The loop below pauses while it is true (it
    // checks the latest value each step, so no effect restart), and
    // the held frame above keeps the last ready frame on screen.
    // Set by the Slider's drag callbacks at the bottom of this file.
    var isScrubbing by remember { mutableStateOf(false) }
    val latestFrameIndex by rememberUpdatedState(safeFrameIndex)
    // Same treatment for the frame count (Fix 6+7): the loop effect
    // never restarts on a frame-list change, and the MRMS list is 10
    // frames in the normal case but fewer if the service extent ever
    // degrades — a stale count of 10 would advance the playhead past
    // the end of the list. (The slider and labels read frameCount
    // during composition, fresh every time; only this long-lived
    // effect needs the latest reader.)
    val latestFrameCount by rememberUpdatedState(frameCount)
    val latestOnScrubFrame by rememberUpdatedState(onScrubFrame)
    val latestIsScrubbing by rememberUpdatedState(isScrubbing)
    LaunchedEffect(isPlaying, isSatellite, selectedRange) {
        if (!isPlaying || isSatellite) return@LaunchedEffect
        while (true) {
            // Pause while the user drags (Fix 14): advancing
            // mid-drag would fight the scrub target and blank the
            // layer. After the drag ends the loop resumes below,
            // gating on the new frame's readiness — never blindly.
            if (latestIsScrubbing) {
                delay(200)
                continue
            }
            if (latestFrameCount < 1) {
                delay(1000)
                continue
            }
            // Hold the frame on screen until it is ready (a scrub
            // target or a timeout-advanced frame may not be yet).
            withTimeoutOrNull(RADAR_LOOP_WAIT_TIMEOUT_MS) {
                snapshotFlow { latestIsFrameReady(latestFrameIndex) }.first { it }
            }
            // Wait for the NEXT frame before leaving this one: the
            // current frame stays visible at full alpha throughout.
            // The next index is computed inside the flow so a scrub
            // mid-wait retargets the wait instead of waiting on a
            // stale frame.
            withTimeoutOrNull(RADAR_LOOP_WAIT_TIMEOUT_MS) {
                snapshotFlow { loopNextIndex(latestFrameIndex, latestFrameCount) to latestIsFrameReady(loopNextIndex(latestFrameIndex, latestFrameCount)) }
                    .first { (_, ready) -> ready }
            }
            val advancedTo = loopNextIndex(latestFrameIndex, latestFrameCount)
            latestOnScrubFrame(advancedTo)
            delay(loopDwellMs(advancedTo, latestFrameCount))
        }
    }

    // Scrub hold (AUDIT6 §9 Fix 14): move the held frame forward
    // only once the scrub/loop target's tiles are ready. While the
    // user drags, this effect restarts on every target change and
    // the map keeps showing the last ready frame; when the user
    // stops on a frame, the hold releases the moment its tiles
    // settle in the prefetch lane (the look-ahead effect below also
    // enqueues the scrub target itself, so a target outside the old
    // window still gets fetched). No timeout: a target that never
    // becomes ready simply never replaces the frame on screen, and
    // the loop's own 2 s timeout still bounds the playhead — the
    // loop advances the TARGET, this effect only moves what is
    // SHOWN. Satellite returns early (no loop, no held frame).
    LaunchedEffect(safeFrameIndex) {
        if (isSatellite) return@LaunchedEffect
        if (safeFrameIndex == heldFrameIndex) return@LaunchedEffect
        snapshotFlow { latestIsFrameReady(safeFrameIndex) }.first { it }
        heldFrameIndex = safeFrameIndex
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
                        onError = { _, _ -> markPrefetchFailed(url) }
                    )
                    .build()
            )
            prefetchDisposables[url] = disposable
        }
        // One satellite tile into the prefetch lane: same tracking,
        // memory cache on (Fix 3's disk-only rule is for the radar
        // loop's 770 tiles; a satellite frame is one 77-tile image),
        // no readiness listener — satellite readiness is satSettled.
        // Explicit memoryCacheKey (Fix 15, same note as the display
        // AsyncImage below): the satellite prefetch writes memory,
        // so its key must match the display's key for the same tile
        // URL, or the prefetch "hit" misses in memory and re-decodes.
        fun prefetchSatelliteTile(url: String) {
            if (!prefetchEnqueuedUrls.add(url)) return
            val disposable = prefetchLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
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
                    if (url in prefetchFailedUrls) prefetchFailedUrls = prefetchFailedUrls - url
                }
            }
        }
        LaunchedEffect(selectedLayer, effectiveZoomLevel, centerLat, centerLon, selectedRange, viewportPrefetchRange, liveResolved) {
            if (selectedLayer == RadarLayer.Reflectivity || selectedLayer == RadarLayer.LocalSite) {
                if (selectedRange == RadarRange.Live) {
                    // Whole-loop preload (Fix 3) over the RESOLVED
                    // contents (Fix 6+7): one exportImage per MRMS
                    // frame, one tile grid per LibreWXR/IEM frame —
                    // current frame first so the visible image/tiles
                    // are not queued behind the other nine. Disk-only
                    // (see prefetchRadarTile), ~100 KB first load on
                    // MRMS, then disk-cached.
                    // Viewport-driven (Fix 4): the resolved contents
                    // are built over the panned viewport range, so a
                    // pan settle re-runs this effect (via
                    // liveResolved), and the enqueue guard means only
                    // the newly visible URLs are actually enqueued;
                    // URLs that left the viewport are cancelled above.
                    // A per-frame fallback override rebuilds
                    // liveResolved, so the fallback's URLs are
                    // enqueued here and the failed source's URLs are
                    // cancelled — the override itself remembers the
                    // failure, so the cancellation cannot resurrect
                    // the failed source (see the fallback watcher).
                    // prefetchRadarTile enqueues any radar image URL,
                    // tile or single: the prefetch lane is the loop's
                    // disk-cache store for both shapes, and its
                    // listener is the fallback detection point.
                    val wanted = liveResolved.flatMapTo(mutableSetOf()) { liveFrameContentUrls(it) }
                    cancelPrefetchOutside(wanted)
                    val orderedIndices = listOf(safeFrameIndex) +
                        (0 until frameCount).filter { it != safeFrameIndex }
                    for (index in orderedIndices) {
                        val urls = liveResolved.getOrNull(index)?.let { liveFrameContentUrls(it) }.orEmpty()
                        for (url in urls) {
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
            // The scrub target itself (Fix 14): the look-ahead below
            // covers the NEXT 5 frames, not the frame the user just
            // scrubbed to — and the held frame only advances once the
            // target is ready — so the target's own tiles must be
            // enqueued here too, or a scrub to a frame outside the
            // old window would never become ready and the hold would
            // never release. Re-enqueueing is free: the guard in
            // prefetchRadarTile enqueues each URL once per generation.
            frames.getOrNull(safeFrameIndex)?.layerName?.let { layer ->
                for (url in radarFrameUrlsForRange(layer, effectiveZoomLevel, viewportPrefetchRange)) {
                    prefetchRadarTile(url)
                }
            }
            for (index in lookAheadFrameIndices(safeFrameIndex, frameCount)) {
                val layer = frames.getOrNull(index)?.layerName ?: continue
                val urls = radarFrameUrlsForRange(layer, effectiveZoomLevel, viewportPrefetchRange)
                for (url in urls) {
                    prefetchRadarTile(url)
                }
            }
        }

        // Time-based slow trigger (AUDIT6 §9 Fix 12): the settled-error
        // trigger cannot fire while the switch burst is still draining
        // — nothing has settled, so nothing is "unavailable" and the
        // user stares at an empty map for as long as the backlog takes
        // (~97 s worst case). This effect waits out the slow window
        // (FORECAST_SLOW_WAIT_MS) from the generation start, then asks
        // the latest-state check (which reads the CURRENT frame's
        // prefetch-settled fraction, not a launch-time snapshot)
        // whether too little has arrived. It fires at most once per
        // generation: the flag is keyed on the retry tick, and a retry
        // re-arms a fresh window.
        LaunchedEffect(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, forecastRetryGen, forecastCheapRetryTick) {
            if (!isForecastRange || isSatellite) return@LaunchedEffect
            val remainingMs = FORECAST_SLOW_WAIT_MS - (SystemClock.uptimeMillis() - forecastGenStartMs)
            if (remainingMs > 0) delay(remainingMs)
            if (latestForecastSlowCheck()) forecastSlowTimedOut = true
        }

        // Cheap retry (AUDIT6 §9 Fix 12): when the retry tick moves
        // (manual RETRY or the auto-retry above), re-request ONLY the
        // current window's tiles whose prefetch FAILED. Tiles still in
        // flight keep flying — nothing is cancelled, no AsyncImage is
        // recreated (forecastRetryGen is untouched) — and tiles that
        // already loaded are never re-requested. The failed URLs are
        // dropped from the enqueue guard and the settled/failed sets
        // first, so prefetchRadarTile enqueues them again. Display
        // tiles that errored recover from the disk cache the next time
        // their frame is shown (loop wrap or scrub re-requests the
        // same URL, which is now a disk hit).
        LaunchedEffect(forecastCheapRetryTick) {
            if (forecastCheapRetryTick == 0) return@LaunchedEffect
            if (!isForecastRange || isSatellite) return@LaunchedEffect
            val wanted = (listOf(safeFrameIndex) + lookAheadFrameIndices(safeFrameIndex, frameCount)).flatMapTo(mutableSetOf()) { index ->
                val layer = frames.getOrNull(index)?.layerName ?: return@flatMapTo emptyList<String>()
                radarFrameUrlsForRange(layer, effectiveZoomLevel, viewportPrefetchRange)
            }
            for (url in wanted.intersect(prefetchFailedUrls)) {
                prefetchEnqueuedUrls.remove(url)
                prefetchSettledUrls = prefetchSettledUrls - url
                prefetchFailedUrls = prefetchFailedUrls - url
                prefetchDisposables.remove(url)
                prefetchRadarTile(url)
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
            // The HELD frame's resolved observed-radar content (Fix
            // 6+7; Live only): the tile loop and the single-image
            // overlay below both read this, so a held MRMS frame draws
            // its overlay and no per-tile radar, while a held
            // LibreWXR/IEM frame draws its per-tile URLs. Future
            // ranges have no resolved list (null) and keep the IEM
            // layer path.
            val heldResolved = if (selectedRange == RadarRange.Live && !isSatellite) liveResolved.getOrNull(heldFrameIndex) else null
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
                    // The HELD layer (Fix 14), not the scrub target:
                    // the map shows the last ready frame until the
                    // target's tiles settle. The URL comes from the
                    // held frame's resolved content (Fix 6+7): a
                    // LibreWXR/IEM frame builds its per-tile URL from
                    // the frame's source identity, and an MRMS frame
                    // has NO per-tile URL (null) — its one overlay
                    // image is drawn after the tile loops, so this
                    // tile draws no radar tile at all (see the
                    // `if (radarUrl != null)` below). Future ranges
                    // have no resolved list and keep the IEM layer URL.
                    val radarUrl: String? = when (selectedLayer) {
                        RadarLayer.Reflectivity, RadarLayer.LocalSite -> when {
                            heldResolved == null ->
                                "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$heldLayerName/$effectiveZoomLevel/$tileX/$tileY.png"
                            heldResolved.source == RadarSourceKind.MRMS -> null
                            else -> liveFrameTileUrl(heldResolved, effectiveZoomLevel, tileX, tileY)
                                ?: "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$heldLayerName/$effectiveZoomLevel/$tileX/$tileY.png"
                        }
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
                    // Parent radar underlay (Fix 4) follows the held
                    // frame's resolved source (Fix 6+7): a LibreWXR
                    // parent at zoom-1, an IEM parent for IEM frames
                    // and future ranges. An MRMS frame has no parent
                    // underlay — a single exportImage has no
                    // lower-zoom parent tile — so this is null and the
                    // basemap (with its own parent underlay) shows
                    // underneath while the image loads. That is the
                    // one Fix 4 mechanism that does not apply to
                    // SingleImage frames.
                    val parentRadarUrl = parent?.let {
                        when (selectedLayer) {
                            RadarLayer.Reflectivity, RadarLayer.LocalSite -> when {
                                heldResolved == null ->
                                    iemTileUrl(heldLayerName, it.zoom, it.tileX, it.tileY)
                                heldResolved.source == RadarSourceKind.MRMS -> null
                                else -> liveFrameTileUrl(heldResolved, it.zoom, it.tileX, it.tileY)
                                    ?: iemTileUrl(heldLayerName, it.zoom, it.tileX, it.tileY)
                            }
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
                    // Outgoing frame's tile (Fix 2 two-slot swap),
                    // from the previous held frame's RESOLVED content
                    // (Fix 6+7): a LibreWXR/IEM previous frame gives
                    // its per-tile URL; an MRMS previous frame has no
                    // tile (its outgoing image is drawn by the overlay
                    // below); future ranges have no resolved content
                    // and keep the layer-name path.
                    val prevRadarUrl = if (!isSatellite) {
                        val fromResolved = crossfadeFromResolved
                        if (fromResolved != null) {
                            liveFrameTileUrl(fromResolved, effectiveZoomLevel, tileX, tileY)
                        } else {
                            crossfadeFromLayer?.let { iemTileUrl(it, effectiveZoomLevel, tileX, tileY) }
                        }
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

                        // 2. Radar tile for the held frame (Fix 6+7):
                        // the resolved source's tile — LibreWXR or IEM
                        // (transparent PNG when no rain). No tile at all
                        // for an MRMS frame (radarUrl is null; the
                        // overlay below draws its single image).
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
                        // Memory-cache key alignment (AUDIT6 §9 Fix 15,
                        // INFERRED not measured — audit §7.5: verify the
                        // hit rate on device): Coil's memory-cache key
                        // includes the requested size, and the display
                        // decodes at the snapped layout size (255/256/
                        // 257 px) while a prefetch with no explicit size
                        // decodes at the intrinsic size — so a prefetch
                        // "hit" could miss in memory and re-decode. An
                        // explicit memoryCacheKey (the tile URL) aligns
                        // prefetch and display at EVERY size, including
                        // pinch-zoomed sizes up to 512 px, where a fixed
                        // .size(256, 256) would cap the decode instead.
                        // The radar prefetch is disk-only (memory
                        // DISABLED, Fix 3), so it writes no memory entry
                        // to align — the entry this matches is the
                        // satellite prefetch's (memory ENABLED below),
                        // and it also makes loop-wrap display hits
                        // size-independent.
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
                                        .memoryCacheKey(prevRadarUrl)
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
                            // No per-tile radar for a SingleImage
                            // (MRMS) frame: radarUrl is null and the
                            // one overlay image below draws the frame.
                            // Skipping the AsyncImage also keeps the
                            // per-frame tile counters honest — they
                            // count tiles, and this frame has none
                            // (its readiness is its one image settling
                            // in the prefetch lane — see isFrameReady).
                            if (radarUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(radarUrl)
                                    .memoryCacheKey(radarUrl)
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

            // Single-image radar overlay (Fix 6+7): an MRMS frame is
            // ONE image for the whole viewport, not a tile grid — one
            // AsyncImage stretched over its bbox, above the basemap
            // tiles and below the reference labels (re-drawn below, so
            // city labels stay on top of the echo exactly as on the
            // tiled path). Positioned from the bbox's lon/lat edges
            // through the same world-pixel math as the tiles, at the
            // live pan/scale, so it tracks drags and pinch-zoom.
            // Whole-frame fade-in (Fix 2/3) works as for tiles:
            // frameAlpha is 0 until the image settles in the prefetch
            // lane, then 150 ms in. The crossfade (Fix 2) draws the
            // previous held frame's single image underneath while this
            // one fades in; a previous TILED frame instead crossfades
            // through its per-tile prev images above (prevRadarUrl).
            // Known limits, stated plainly: the Fix 4 parent-tile
            // underlay does not apply to this image (no lower-zoom
            // parent exists for an exportImage), so while a fresh
            // bbox's image loads the basemap shows underneath; and a
            // shape change from SingleImage to Tiled (a fallback
            // landing mid-loop) has no outgoing-image crossfade — the
            // previous image vanishes and the new tiles fade in.
            val heldContent = heldResolved?.content
            if (!isSatellite && selectedRange == RadarRange.Live && heldContent is RadarFrameContent.SingleImage) {
                val bbox = heldContent.bbox
                val leftPx = mapOriginX + lonToWorldPixel(bbox.westLon, effectiveZoomLevel) * userScale
                val topPx = mapOriginY + latToWorldPixel(bbox.northLat, effectiveZoomLevel) * userScale
                val widthPx = (lonToWorldPixel(bbox.eastLon, effectiveZoomLevel) - lonToWorldPixel(bbox.westLon, effectiveZoomLevel)) * userScale
                val heightPx = (latToWorldPixel(bbox.southLat, effectiveZoomLevel) - latToWorldPixel(bbox.northLat, effectiveZoomLevel)) * userScale
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(
                            with(LocalDensity.current) { widthPx.toDp() },
                            with(LocalDensity.current) { heightPx.toDp() }
                        )
                ) {
                    val fromSingleUrl = (crossfadeFromResolved?.content as? RadarFrameContent.SingleImage)?.url
                    if (fromSingleUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fromSingleUrl)
                                .memoryCacheKey(fromSingleUrl)
                                .crossfade(false)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Radar image",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.85f * (1f - crossfadeProgress))
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(heldContent.url)
                            .memoryCacheKey(heldContent.url)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Radar image",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (crossfadeFromLayer != null) 0.85f * crossfadeProgress else frameAlpha)
                    )
                }
                // Reference labels on top (Fix 6+7): the tile loop drew
                // the Esri reference layer under this overlay, so the
                // same tiles are drawn again here — same URLs, so
                // memory-cache hits, no new requests — and city labels
                // stay on top of the radar echo, as on the tiled path.
                for (tileX in displayRange.minX..displayRange.maxX) {
                    for (tileY in displayRange.minY..displayRange.maxY) {
                        val (tileLeftPx, tileWidthPx) = snappedTileSpan(mapOriginX, tileX, tileSizePx)
                        val (tileTopPx, tileHeightPx) = snappedTileSpan(mapOriginY, tileY, tileSizePx)
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$effectiveZoomLevel/$tileY/$tileX")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Place labels",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset { IntOffset(tileLeftPx, tileTopPx) }
                                .size(
                                    with(LocalDensity.current) { tileWidthPx.toDp() },
                                    with(LocalDensity.current) { tileHeightPx.toDp() }
                                )
                        )
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
                            text = loopPreloadCostText(selectedRange, liveResolved.getOrNull(safeFrameIndex)?.source),
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
                                // Cheap retry (Fix 12): see the
                                // auto-retry effect — the tick, not
                                // the generation, so no AsyncImage
                                // is recreated and no in-flight tile
                                // is cancelled.
                                forecastCheapRetryTick++
                                forecastAutoRetries = 0
                                forecastTileOk = 0
                                forecastTileErr = 0
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
                        onValueChange = {
                            // Fix 14: the loop pauses while the
                            // finger is down (isScrubbing) and the
                            // map holds the last ready frame until
                            // this target's tiles settle. There is no
                            // onValueChangeStart on Material3's Slider
                            // in this BOM (CI run 43680813930 failed
                            // on it) — onValueChange sets the flag
                            // from the first move, and
                            // onValueChangeFinished clears it on
                            // release, which is enough: the held frame
                            // still protects the display even if the
                            // loop advances once before the first move.
                            isScrubbing = true
                            onScrubFrame(it.toInt())
                        },
                        onValueChangeFinished = { isScrubbing = false },
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

// One small radar-source metadata document (AUDIT6 §9 Fix 6+7) over
// plain OkHttp — no Retrofit needed: the pure parsers in
// MrmsTiles/LibreWxrTiles read the numbers/frames needed from the
// service-info / weather-maps JSON. Null on any failure (HTTP error,
// empty body, exception): the caller falls back to the next source.
private fun fetchRadarSourceText(client: OkHttpClient, url: String): String? = runCatching {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) null else response.body?.string()
    }
}.getOrNull()

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
