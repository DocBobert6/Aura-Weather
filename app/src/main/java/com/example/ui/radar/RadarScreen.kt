package com.example.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.data.mapper.gibsTimestamp
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

// IEM NEXRAD composite layer name for frame 0..9 (0 = 45m ago, 9 = LIVE).
private fun iemLayerNameFor(frameIndex: Int): String = when (frameIndex) {
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

// The scrubber's custom thumb is an experimental Material3 API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    state: FullWeatherState?,
    selectedLayer: RadarLayer,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    onSelectLayer: (RadarLayer) -> Unit,
    onTogglePlay: () -> Unit,
    onScrubFrame: (Int) -> Unit,
    onEnterPiP: () -> Unit,
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

    // Frame timestamps (0 = 45m ago, 9 = LIVE)
    val frameLabels = remember {
        listOf(
            "45m ago", "40m ago", "35m ago", "30m ago", "25m ago",
            "20m ago", "15m ago", "10m ago", "5m ago", "LIVE NOW"
        )
    }

    // IEM NEXRAD composite layer names corresponding to 0..9
    val iemLayerName = remember(currentFrameIndex) { iemLayerNameFor(currentFrameIndex) }

    // Satellite layers have a max zoom in GIBS (GeoColor Level7, Band13
    // Level6) below the radar zoom range (7..9), so clamp the effective
    // zoom per layer. The stored zoomLevel is untouched, so switching back
    // to radar restores the radar zoom.
    val effectiveZoomLevel = when (selectedLayer) {
        RadarLayer.SatelliteVis -> minOf(zoomLevel, 7)
        RadarLayer.SatelliteIr -> minOf(zoomLevel, 6)
        else -> zoomLevel
    }

    // Satellite layers are a single image (no loop, no dBZ): the legend,
    // the clear-air caption and the transport pill are radar-only
    // (audit fix E).
    val isSatellite = selectedLayer == RadarLayer.SatelliteVis || selectedLayer == RadarLayer.SatelliteIr

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

    // Prefetch the current and next loop frames' radar tiles so a frame
    // switch is atomic. Without this, the 9 tiles reload independently on
    // each 750ms frame advance and the screen briefly shows a mix of two
    // frames — the doubled/shifted map in the 2026-11-08 video. Satellite
    // layers are a single image, so they need no prefetch.
    val imageLoader = context.imageLoader
    LaunchedEffect(currentFrameIndex, selectedLayer, effectiveZoomLevel) {
        if (selectedLayer == RadarLayer.Reflectivity || selectedLayer == RadarLayer.LocalSite) {
            for (frameIndex in listOf(currentFrameIndex, (currentFrameIndex + 1) % 10)) {
                val layerName = iemLayerNameFor(frameIndex)
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        val tileX = lonToTileX(centerLon, effectiveZoomLevel) + dx
                        val tileY = latToTileY(centerLat, effectiveZoomLevel) + dy
                        val url = "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layerName/$effectiveZoomLevel/$tileX/$tileY.png"
                        imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                    }
                }
            }
        }
    }

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

        // Pixel center of the central tile
        val centerWorldX = lonToWorldPixel(centerLon, effectiveZoomLevel) * userScale
        val centerWorldY = latToWorldPixel(centerLat, effectiveZoomLevel) * userScale

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

                    // Calculate top-left position on screen for this tile
                    val tileWorldLeft = tileX * 256f * userScale
                    val tileWorldTop = tileY * 256f * userScale

                    val screenX = (screenWidthPx / 2f) + (tileWorldLeft - centerWorldX) + panOffsetX
                    val screenY = (screenHeightPx / 2f) + (tileWorldTop - centerWorldY) + panOffsetY

                    val basemapUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$effectiveZoomLevel/$tileY/$tileX"
                    val radarUrl = when (selectedLayer) {
                        RadarLayer.Reflectivity, RadarLayer.LocalSite ->
                            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$iemLayerName/$effectiveZoomLevel/$tileX/$tileY.png"
                        RadarLayer.SatelliteVis ->
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/$gibsTime/GoogleMapsCompatible_Level7/$effectiveZoomLevel/$tileY/$tileX.png"
                        RadarLayer.SatelliteIr ->
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/$gibsTime/GoogleMapsCompatible_Level6/$effectiveZoomLevel/$tileY/$tileX.png"
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                            .size(with(LocalDensity.current) { tileSizePx.toDp() })
                    ) {
                        // 1. Esri World Dark Gray Base (dark, keyless). CARTO dark_all now returns an
                        // "API KEY REQUIRED" watermark tile, so it cannot be used.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(basemapUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Map tile",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Real IEM NEXRAD Composite Radar Tile (Transparent PNG when no rain!)
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(radarUrl)
                                .crossfade(false)
                                .build(),
                            contentDescription = "Radar tile",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.85f)
                        )

                        // 3. Esri reference layer (city labels + boundaries,
                        // keyless) on top, so the map names places instead
                        // of showing bare terrain under the radar.
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

        // Top floating pill cluster: station pill + frame label pill, then
        // the layer chooser as a pill segmented control. PiP lives on the
        // transport pill at the bottom now.
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
                            text = frameLabels.getOrElse(currentFrameIndex) { "LIVE NOW" }.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFrameIndex == 9) {
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

            // Clear-air speckle: the lightest echoes can be insects or
            // ground clutter, not rain. The tiles are pre-rendered, so
            // labelling (not filtering) is what the app can do.
            Text(
                text = "Lightest echoes can be clear-air returns, not rain.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = RetroTokens.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Classic transport pill: play/pause + frame scrubber inside
            // the pill, PiP attached to its end. The frame label lives in
            // the top pill now.
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
                    // The loop's range, flanking the scrubber so it costs
                    // no extra row: -45m (first frame) to Now (frame 9).
                    Text(
                        text = "-45M",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = RetroTokens.Yellow
                    )

                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { onScrubFrame(it.toInt()) },
                        valueRange = 0f..9f,
                        steps = 8,
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
                        text = "NOW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = RetroTokens.Yellow
                    )

                    // Labelled 'PiP': the icon alone did not say what it
                    // does. A small pill with the icon + text.
                    Surface(
                        color = RetroTokens.PanelLight,
                        shape = RetroShapes.small,
                        modifier = Modifier.clickable(
                            onClick = onEnterPiP,
                            role = Role.Button
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Picture in Picture",
                                tint = RetroTokens.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PiP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

// Spherical Mercator projection utilities
private fun lonToTileX(lon: Double, zoom: Int): Int {
    val x = ((lon + 180.0) / 360.0) * (1 shl zoom)
    return x.toInt()
}

private fun latToTileY(lat: Double, zoom: Int): Int {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = (1.0 - (ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI)) / 2.0 * (1 shl zoom)
    return y.toInt()
}

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
