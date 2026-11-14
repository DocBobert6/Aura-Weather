package com.example.ui.radar

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
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.mapper.gibsTileUrl
import com.example.data.mapper.gibsTimestamp
import com.example.data.mapper.RadarRange
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.lookAheadFrameIndices
import com.example.data.mapper.radarFrameUrls
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.latToTileY
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.radarPrefetchUrls
import com.example.data.mapper.satelliteTileUrls
import com.example.data.mapper.satelliteZoomLevel
import com.example.data.mapper.snappedTileSpan
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

    // Zoom level and pan offsets
    var zoomLevel by remember { mutableIntStateOf(8) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var userScale by remember { mutableFloatStateOf(1f) }

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
    var frameTileOk by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, safeFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
    var frameTileErr by remember(selectedRange, selectedLayer, effectiveZoomLevel, state.place.point.lat, state.place.point.lon, safeFrameIndex, forecastRetryGen) { mutableIntStateOf(0) }
    var frameTotalTiles by remember { mutableIntStateOf(0) }
    val frameReady = frameTotalTiles > 0 && frameTileOk + frameTileErr >= frameTotalTiles
    // The loop effect below does not restart on frame changes, so it
    // reads readiness through rememberUpdatedState (same pattern as
    // latestFrameIndex) instead of capturing a stale value.
    val latestFrameReady by rememberUpdatedState(frameReady)
    // Whole-frame fade-in (fix 3): the radar layer stays hidden until
    // its tiles have settled, then fades in complete over 150ms
    // instead of tiles popping in one by one. Satellite keeps the
    // fixed 0.85 alpha (its own loading overlay covers the wait).
    val frameAlpha by animateFloatAsState(
        targetValue = if (isSatellite) 0.85f else if (frameReady) 0.85f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "radarFrameAlpha"
    )

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
    // Frame-ready hold (radar smoothness fix, 2026-11-14): the loop no
    // longer steps blindly every 750ms. Each step waits for the CURRENT
    // frame's tiles to settle (loaded or errored) — so the frame the
    // user sees is always complete — then dwells 750ms, then advances.
    // The 750ms is now a minimum dwell, not a blind step. The 2s
    // timeout is the honest fallback: a dead or slow server can delay
    // a step, never freeze the loop.
    val latestFrameIndex by rememberUpdatedState(safeFrameIndex)
    val latestOnScrubFrame by rememberUpdatedState(onScrubFrame)
    LaunchedEffect(isPlaying, isSatellite, selectedRange) {
        if (!isPlaying || isSatellite) return@LaunchedEffect
        while (true) {
            withTimeoutOrNull(2000L) {
                snapshotFlow { latestFrameReady }.first { it }
            }
            delay(750)
            latestOnScrubFrame((latestFrameIndex + 1) % frameCount.coerceAtLeast(1))
        }
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

        // Tile coordinates at zoomLevel
        val centerTileX = lonToTileX(centerLon, effectiveZoomLevel)
        val centerTileY = latToTileY(centerLat, effectiveZoomLevel)

        // Base tile size in pixels (256 standard Mercator tile size, scaled by userScale)
        val tileSizePx = 256f * userScale

        // How many tiles cover the screen (plus a margin for panning), so
        // the map fills the available space instead of a central 3x3 rect
        // with container background around it. Capped so a very large
        // screen does not fire hundreds of tile requests.
        val tilesX = ((screenWidthPx / tileSizePx).toInt() + 2).coerceIn(3, 9)
        val tilesY = ((screenHeightPx / tileSizePx).toInt() + 2).coerceIn(3, 11)
        val halfTilesX = tilesX / 2
        val halfTilesY = tilesY / 2
        // Tiles actually rendered: the loop runs -half..half on each axis.
        val totalSatTiles = (2 * halfTilesX + 1) * (2 * halfTilesY + 1)

        // Push the rendered tile count up to the per-frame settlement
        // state (fix 2): the loop's frame-ready gate needs the total
        // the grid actually draws, which is only known here.
        SideEffect { frameTotalTiles = totalSatTiles }

        // Safety: never leave the satellite loading overlay up forever.
        // If the tiles have not all settled in 12s, hide it anyway.
        LaunchedEffect(selectedLayer, effectiveZoomLevel) {
            delay(12000)
            satSettled = totalSatTiles
        }

        // Prefetch all 10 loop frames for the visible tile grid (3.3), so
        // a scrub 0->9 is a cache hit instead of a per-frame network
        // fetch. The current frame is enqueued first so the visible tiles
        // are not queued behind the other nine frames. Runs on entry and
        // when the layer, zoom, or centre changes — not on every frame
        // advance, and not on pinch-zoom (the grid is captured when the
        // effect last ran; a resize loads its new tiles on demand).
        // Satellite layers get the same treatment below (fix 4): the
        // GIBS grid is a tile set like radar, not a single image. The
        // grid is capped (tilesX <= 9, tilesY <= 11) and Coil bounds the
        // network concurrency, so this is a bounded burst, not a flood.
        // The prefetch and the AsyncImage loads share the same
        // ImageLoader, the same URLs, and explicit cache policies, so a
        // prefetched tile is a cache hit for the screen (no network
        // fetch on the second pass).
        val prefetchLoader = context.imageLoader
        LaunchedEffect(selectedLayer, effectiveZoomLevel, centerLat, centerLon, selectedRange) {
            if (selectedLayer == RadarLayer.Reflectivity || selectedLayer == RadarLayer.LocalSite) {
                if (selectedRange == RadarRange.Live) {
                    val frames = radarPrefetchUrls(centerLat, centerLon, effectiveZoomLevel, tilesX, tilesY)
                    val current = frames.getOrElse(safeFrameIndex) { emptyList() }
                    val ordered = listOf(current) + frames.filterIndexed { index, _ -> index != safeFrameIndex }
                    for (frameUrls in ordered) {
                        for (url in frameUrls) {
                            prefetchLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            )
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
                    // Coil deduplicates URLs already cached or in
                    // flight, so the overlap with the rolling
                    // look-ahead effect below is free.
                    val burstIndices = listOf(safeFrameIndex) +
                        lookAheadFrameIndices(safeFrameIndex, frameCount)
                    for (index in burstIndices) {
                        val layer = frames.getOrNull(index)?.layerName ?: continue
                        val urls = radarFrameUrls(layer, centerLat, centerLon, effectiveZoomLevel, tilesX, tilesY)
                        for (url in urls) {
                            prefetchLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            )
                        }
                    }
                }
            } else if (isSatellite) {
                // Satellite prefetch (fix 4): the GIBS grid is a tile
                // set like radar, not a single image — prefetch the
                // visible grid on layer/zoom/centre change so the
                // overlay clears fast instead of tile-by-tile. One
                // bounded burst (~77 tiles on a phone grid).
                val urls = satelliteTileUrls(
                    selectedLayer == RadarLayer.SatelliteVis,
                    gibsTime, centerLat, centerLon, effectiveZoomLevel, tilesX, tilesY
                )
                for (url in urls) {
                    prefetchLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
        }

        // Rolling look-ahead prefetch for future ranges (loop smoothing):
        // the range effect above prefetches the current frame plus the
        // first 5, so every loop advance used to download the next
        // frame's tiles on demand and stutter. This keeps the next 5
        // frames ahead of the playhead cached, re-running as the frame
        // index advances (and when the user scrubs). Bounded: 5 frames
        // x the visible grid. Live needs none of this — all 10 of its
        // frames are prefetched by the range effect. Coil deduplicates
        // URLs already cached or in flight, so re-enqueueing a frame is
        // a cheap cache hit.
        LaunchedEffect(selectedRange, selectedLayer, effectiveZoomLevel, centerLat, centerLon, safeFrameIndex) {
            if (selectedRange == RadarRange.Live) return@LaunchedEffect
            if (selectedLayer != RadarLayer.Reflectivity && selectedLayer != RadarLayer.LocalSite) return@LaunchedEffect
            // Wrap around the end of the loop (L-4): at the last frames
            // the next indices are 0, 1, 2, 3, 4, so frame 0 is prefetched
            // before the loop wraps onto it instead of downloading on
            // demand and stuttering once per cycle. This effect is keyed
            // on safeFrameIndex, so the wrap prefetch runs as soon as the
            // playhead reaches the last frame.
            for (index in lookAheadFrameIndices(safeFrameIndex, frameCount)) {
                val layer = frames.getOrNull(index)?.layerName ?: continue
                val urls = radarFrameUrls(layer, centerLat, centerLon, effectiveZoomLevel, tilesX, tilesY)
                for (url in urls) {
                    prefetchLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
        }

        // Pixel center of the central tile
        val centerWorldX = lonToWorldPixel(centerLon, effectiveZoomLevel) * userScale
        val centerWorldY = latToWorldPixel(centerLat, effectiveZoomLevel) * userScale

        // Map origin: the screen position of world x/y = 0. Every tile's
        // snapped edges (below) are derived from this one origin, so
        // adjacent tiles share edges exactly.
        val mapOriginX = (screenWidthPx / 2f) - centerWorldX + panOffsetX
        val mapOriginY = (screenHeightPx / 2f) - centerWorldY + panOffsetY

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
                    }
                }
        ) {
            // Render the tiles that cover the screen, centered on the place.
            for (dx in -halfTilesX..halfTilesX) {
                for (dy in -halfTilesY..halfTilesY) {
                    val tileX = centerTileX + dx
                    val tileY = centerTileY + dy

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

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(tileLeftPx, tileTopPx) }
                            .size(
                                with(LocalDensity.current) { tileWidthPx.toDp() },
                                with(LocalDensity.current) { tileHeightPx.toDp() }
                            )
                    ) {
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

                        // 2. Real IEM NEXRAD Composite Radar Tile (Transparent PNG when no rain!)
                        // Satellite layers report each tile as it settles
                        // (loaded or errored) so the loading overlay knows
                        // when the imagery has arrived. Radar layers
                        // report each tile to the per-frame settlement
                        // counters (fix 2/3): the loop holds until the
                        // frame's tiles have settled, and the layer fades
                        // in whole once they have. Forecast ranges also
                        // report each tile, so a down IEM backend shows
                        // the "future radar unavailable" state instead of
                        // a blank void. Explicit cache policies: the
                        // prefetch and the screen share the same
                        // ImageLoader and URLs, so a prefetched tile is
                        // a cache hit here. key(forecastRetryGen)
                        // recreates the request on a retry.
                        key(forecastRetryGen) {
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
                                    .alpha(frameAlpha)
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
        // loading, not broken. The Esri basemap stays visible underneath
        // as the placeholder for unloaded regions.
        if (isSatellite && satSettled < totalSatTiles) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                RetroPanel(shape = RetroShapes.medium) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
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
