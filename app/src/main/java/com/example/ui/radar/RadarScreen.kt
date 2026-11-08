package com.example.ui.radar

import androidx.compose.foundation.Canvas
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
import com.example.domain.model.FullWeatherState
import com.example.ui.RadarLayer
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.GlassIconButton
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface
import com.example.ui.designsystem.PillSegmentedControl
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
    paletteMode: String = "auto",
    modifier: Modifier = Modifier
) {
    // No place selected yet: the radar centres on the place, and there is
    // no default location to centre on instead.
    if (state == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a place to see its radar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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

    // Centre on the selected place. The location puck below marks it; there
    // are no hardcoded landmarks — the radar station's coordinates are not
    // part of the /points response, so it is named in the toolbar, not drawn.
    val centerLat = state.place.point.lat
    val centerLon = state.place.point.lon

    // GIBS satellite layers are dated: use today, never a hardcoded date.
    val gibsDate = remember { java.time.LocalDate.now().toString() }

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
                for (dx in -1..1) {
                    for (dy in -1..1) {
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
            .background(MaterialTheme.colorScheme.surface)
            .testTag("radar_screen")
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Tile coordinates at zoomLevel
        val centerTileX = lonToTileX(centerLon, effectiveZoomLevel)
        val centerTileY = latToTileY(centerLat, effectiveZoomLevel)

        // Base tile size in pixels (256 standard Mercator tile size, scaled by userScale)
        val tileSizePx = 256f * userScale

        // Pixel center of the central tile
        val centerWorldX = lonToWorldPixel(centerLon, effectiveZoomLevel) * userScale
        val centerWorldY = latToWorldPixel(centerLat, effectiveZoomLevel) * userScale

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomLevel) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        userScale = (userScale * zoom).coerceIn(0.75f, 2.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            // Render a 3x3 grid of real map tiles centered on the place
            for (dx in -1..1) {
                for (dy in -1..1) {
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
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/$gibsDate/GoogleMapsCompatible_Level7/$effectiveZoomLevel/$tileY/$tileX.png"
                        RadarLayer.SatelliteIr ->
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/$gibsDate/GoogleMapsCompatible_Level6/$effectiveZoomLevel/$tileY/$tileX.png"
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
                GlassSurface(
                    level = GlassLevel.Bar,
                    shape = AuraShapes.pill,
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
                        Text(
                            text = state.radarStation?.let { "$it Radar" } ?: "NEXRAD Radar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                GlassSurface(
                    level = GlassLevel.Bar,
                    shape = AuraShapes.pill,
                    modifier = Modifier.height(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = frameLabels.getOrElse(currentFrameIndex) { "LIVE NOW" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFrameIndex == 9) {
                                AuraColors.Accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
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
                isExpressive = paletteMode == "expressive",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Zoom +/- In-Map Controls (48dp glass buttons)
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
                isExpressive = paletteMode == "expressive"
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = MaterialTheme.colorScheme.onSurface
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
                isExpressive = paletteMode == "expressive"
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            GlassIconButton(
                onClick = {
                    panOffsetX = 0f
                    panOffsetY = 0f
                    userScale = 1f
                },
                isExpressive = paletteMode == "expressive"
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Recenter",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Bottom Controls: Legend, Playback & Scrub Slider
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Radar Legend Bar
            GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "dBZ:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    for ((label, color) in AuraColors.RadarLegend) {
                        LegendColorPill(label, color)
                    }
                }
            }

            // Floating transport pill: play/pause + frame scrubber inside
            // the pill, PiP attached to its end. The frame label lives in
            // the top pill now.
            GlassSurface(
                level = GlassLevel.Bar,
                shape = AuraShapes.pill,
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        isExpressive = paletteMode == "expressive"
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { onScrubFrame(it.toInt()) },
                        valueRange = 0f..9f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    GlassIconButton(
                        onClick = onEnterPiP,
                        isExpressive = paletteMode == "expressive"
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = "Picture in Picture",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
