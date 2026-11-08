package com.example.ui.radar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.request.ImageRequest
import com.example.domain.model.FullWeatherState
import com.example.ui.RadarLayer
import com.example.ui.designsystem.GlassCard
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

data class MapLandmark(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isRadarStation: Boolean = false
)

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Zoom level and pan offsets (Zoom 8 is optimal for Northern Kentucky & Greater Cincinnati)
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
    val iemLayerName = remember(currentFrameIndex) {
        when (currentFrameIndex) {
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
    }

    // Center coordinates: selected place point or default NKY center
    val centerLat = state?.place?.point?.lat ?: 38.9989
    val centerLon = state?.place?.point?.lon ?: -84.6266

    // Geographic landmarks across Northern Kentucky and Greater Cincinnati
    val landmarks = remember {
        listOf(
            MapLandmark("KILN Radar", 39.4200, -83.8200, isRadarStation = true),
            MapLandmark("Cincinnati", 39.1031, -84.5120),
            MapLandmark("Covington", 39.0837, -84.5086),
            MapLandmark("Florence", 38.9989, -84.6266),
            MapLandmark("Crittenden", 38.7915, -84.6033),
            MapLandmark("Newport", 39.0917, -84.4922),
            MapLandmark("CVG Airport", 39.0488, -84.6678)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("radar_screen")
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Tile coordinates at zoomLevel
        val centerTileX = lonToTileX(centerLon, zoomLevel)
        val centerTileY = latToTileY(centerLat, zoomLevel)

        // Base tile size in pixels (256 standard Mercator tile size, scaled by userScale)
        val tileSizePx = 256f * LocalDensity.current.density * userScale

        // Pixel center of the central tile
        val centerWorldX = lonToWorldPixel(centerLon, zoomLevel) * userScale
        val centerWorldY = latToWorldPixel(centerLat, zoomLevel) * userScale

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
            // Render a 3x3 grid of real map tiles centered on NKY
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val tileX = centerTileX + dx
                    val tileY = centerTileY + dy

                    // Calculate top-left position on screen for this tile
                    val tileWorldLeft = (tileX * 256f * LocalDensity.current.density) * userScale
                    val tileWorldTop = (tileY * 256f * LocalDensity.current.density) * userScale

                    val screenX = (screenWidthPx / 2f) + (tileWorldLeft - centerWorldX) + panOffsetX
                    val screenY = (screenHeightPx / 2f) + (tileWorldTop - centerWorldY) + panOffsetY

                    val basemapUrl = "https://a.basemaps.cartocdn.com/dark_all/$zoomLevel/$tileX/$tileY.png"
                    val radarUrl = when (selectedLayer) {
                        RadarLayer.Reflectivity, RadarLayer.KilnSite ->
                            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$iemLayerName/$zoomLevel/$tileX/$tileY.png"
                        RadarLayer.SatelliteVis ->
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/2026-11-06/GoogleMapsCompatible_Level8/$zoomLevel/$tileY/$tileX.png"
                        RadarLayer.SatelliteIr ->
                            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/2026-11-06/GoogleMapsCompatible_Level8/$zoomLevel/$tileY/$tileX.png"
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                            .size(with(LocalDensity.current) { tileSizePx.toDp() })
                    ) {
                        // 1. Live Dark Basemap Tile (Roads, Counties, Ohio River, Cities)
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

            // Real Geographic Landmarks overlay
            landmarks.forEach { lm ->
                val lmWorldX = lonToWorldPixel(lm.lon, zoomLevel) * userScale
                val lmWorldY = latToWorldPixel(lm.lat, zoomLevel) * userScale
                val lmScreenX = (screenWidthPx / 2f) + (lmWorldX - centerWorldX) + panOffsetX
                val lmScreenY = (screenHeightPx / 2f) + (lmWorldY - centerWorldY) + panOffsetY

                if (lmScreenX in -100f..(screenWidthPx + 100f) && lmScreenY in -100f..(screenHeightPx + 100f)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(lmScreenX.roundToInt() - 36, lmScreenY.roundToInt() - 16) }
                            .width(72.dp)
                    ) {
                        Surface(
                            color = if (lm.isRadarStation) Color(0xFFFF9100) else Color(0xFF1E88E5),
                            shape = CircleShape,
                            modifier = Modifier.size(if (lm.isRadarStation) 10.dp else 7.dp)
                        ) {}
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lm.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = if (lm.isRadarStation) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (lm.isRadarStation) Color(0xFFFFB74D) else Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Location Puck for the selected place
            val userWorldX = lonToWorldPixel(centerLon, zoomLevel) * userScale
            val userWorldY = latToWorldPixel(centerLat, zoomLevel) * userScale
            val userScreenX = (screenWidthPx / 2f) + (userWorldX - centerWorldX) + panOffsetX
            val userScreenY = (screenHeightPx / 2f) + (userWorldY - centerWorldY) + panOffsetY

            Box(
                modifier = Modifier
                    .offset { IntOffset(userScreenX.roundToInt() - 12, userScreenY.roundToInt() - 12) }
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.35f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {}
                Surface(
                    color = Color(0xFF00E676),
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

        // Top Toolbar: Station info, PiP, and status chip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCard(shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF00E676),
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KILN Radar (Wilmington, OH)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = onEnterPiP,
                    modifier = Modifier
                        .background(Color(0xFF131D2E).copy(alpha = 0.85f), CircleShape)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in Picture",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Layer Chooser
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadarLayer.entries.forEach { layer ->
                    val isSelected = layer == selectedLayer
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E293B).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectLayer(layer) }
                    ) {
                        Text(
                            text = when (layer) {
                                RadarLayer.Reflectivity -> "Composite"
                                RadarLayer.KilnSite -> "KILN Site"
                                RadarLayer.SatelliteVis -> "Sat Visible"
                                RadarLayer.SatelliteIr -> "Sat IR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF0D253F) else Color.White.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Zoom +/- In-Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (zoomLevel < 9) {
                        zoomLevel += 1
                        userScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF1E293B).copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
            }

            IconButton(
                onClick = {
                    if (zoomLevel > 7) {
                        zoomLevel -= 1
                        userScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF1E293B).copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }

            IconButton(
                onClick = {
                    panOffsetX = 0f
                    panOffsetY = 0f
                    userScale = 1f
                },
                modifier = Modifier
                    .background(Color(0xFF1E293B).copy(alpha = 0.9f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = Color.White)
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
            GlassCard(shape = RoundedCornerShape(14.dp)) {
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
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    LegendColorPill("Light", Color(0xFF00E676))
                    LegendColorPill("Moderate", Color(0xFFFFEA00))
                    LegendColorPill("Heavy", Color(0xFFFF9100))
                    LegendColorPill("Severe", Color(0xFFFF1744))
                    LegendColorPill("Hail", Color(0xFFD500F9))
                }
            }

            // Playback card with Frame timestamp & Scrubber
            GlassCard(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = frameLabels.getOrElse(currentFrameIndex) { "LIVE NOW" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (currentFrameIndex == 9) Color(0xFF00E676) else Color.White
                            )
                            Text(
                                text = "NWS/IEM NEXRAD • Frame ${currentFrameIndex + 1} of 10",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { onScrubFrame(it.toInt()) },
                        valueRange = 0f..9f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
