package com.example.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.UnitFormatter
import com.example.data.mapper.shortMapLabel
import com.example.domain.model.FullWeatherState
import com.example.domain.model.RegionalPoint
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Regional map forecast (R5, the last retro phase): the current place
 * plus its neighbours plotted on a STATIC Esri map, in the shape of the
 * plan's stills 1 and 4. Fixed zoom, centred on the current place, no
 * pan / zoom / loop — the radar screen is the interactive map; this one
 * is a picture with labels.
 *
 * The section is hidden entirely when [regionalPoints] is empty (the
 * neighbour fetch is still pending, or it produced nothing). A point
 * with no temperature is never plotted with an invented value.
 */
@Composable
fun RegionalMapForecast(
    state: FullWeatherState,
    regionalPoints: List<RegionalPoint>,
    unitFormatter: UnitFormatter,
    modifier: Modifier = Modifier
) {
    if (regionalPoints.isEmpty()) return

    RetroPanel(shape = RetroShapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "REGIONAL FORECAST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )

            Spacer(modifier = Modifier.height(14.dp))

            RegionalStaticMap(
                state = state,
                regionalPoints = regionalPoints,
                unitFormatter = unitFormatter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REGIONAL_MAP_HEIGHT)
                    .clip(RetroShapes.medium)
            )
        }
    }
}

/** Fixed zoom: at zoom 8 the ~80 km neighbours sit ~170 px from the
 * centre on a ~260dp panel — spread across the middle of the map, one
 * per octant, with room for their labels. */
private const val REGIONAL_MAP_ZOOM = 8

/** Map panel height. At this height the neighbour labels fit above /
 * below their markers without clamping at mid-latitudes. */
private val REGIONAL_MAP_HEIGHT = 260.dp

/** Gap between a marker dot and its label. */
private const val LABEL_GAP_PX = 6f

/** The static map: Esri tiles + marker dots + city labels. Label sizes
 * are measured (per point) so the label offsets can be clamped to the
 * panel bounds. */
@Composable
private fun RegionalStaticMap(
    state: FullWeatherState,
    regionalPoints: List<RegionalPoint>,
    unitFormatter: UnitFormatter,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val labelSizes = remember { mutableStateMapOf<RegionalPoint, IntSize>() }
    // Dot sizes in px, resolved once in composition — the offset lambdas
    // below run at layout time and cannot read a CompositionLocal.
    val density = LocalDensity.current
    val centreDotPx = with(density) { 12.dp.toPx() }
    val neighbourDotPx = with(density) { 8.dp.toPx() }
    // Tiles are 256 px and positioned in px (offset{} takes pixels), so
    // the tile box must be 256 px wide — NOT 256.dp, which would stretch
    // each tile to ~670 px at phone density and overlap the grid.
    val tileSizeDp = with(density) { 256.toDp() }

    BoxWithConstraints(modifier = modifier.background(RetroTokens.Panel)) {
        val panelWidthPx = constraints.maxWidth.toFloat()
        val panelHeightPx = constraints.maxHeight.toFloat()

        val centerLat = state.place.point.lat
        val centerLon = state.place.point.lon
        val centerWorldX = lonToWorldPixel(centerLon, REGIONAL_MAP_ZOOM)
        val centerWorldY = latToWorldPixel(centerLat, REGIONAL_MAP_ZOOM)

        // The exact tile range covering the panel — no gaps at any tile
        // alignment. A 260dp panel needs at most 4 columns x 4 rows
        // (typically 4 x 3); still small next to the radar's ~99 tiles.
        val tileSize = 256f
        val leftWorld = centerWorldX - panelWidthPx / 2f
        val topWorld = centerWorldY - panelHeightPx / 2f
        val firstTileX = floor(leftWorld / tileSize).toInt()
        val lastTileX = floor((centerWorldX + panelWidthPx / 2f) / tileSize).toInt()
        val firstTileY = floor(topWorld / tileSize).toInt()
        val lastTileY = floor((centerWorldY + panelHeightPx / 2f) / tileSize).toInt()

        Box(modifier = Modifier.fillMaxSize()) {
            for (tileX in firstTileX..lastTileX) {
                for (tileY in firstTileY..lastTileY) {
                    val screenX = tileX * tileSize - leftWorld
                    val screenY = tileY * tileSize - topWorld

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                            .size(tileSizeDp)
                    ) {
                        // Esri World Dark Gray Base (dark, keyless) —
                        // the same basemap the radar uses. It is the ONLY
                        // tile layer here: the Esri reference layer
                        // (World_Boundaries_and_Places) used to be drawn on
                        // top, but it paints its own place names
                        // ("Dayton", "Lexington") under our overlay labels
                        // and the two collide (audit fix C). The base keeps
                        // the geography and its own administrative
                        // boundaries, so the map still reads as a map.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$REGIONAL_MAP_ZOOM/$tileY/$tileX")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Map tile",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                    }
                }
            }

            // Markers + labels, one per point. The centre is the bigger
            // yellow dot; neighbours are white dots.
            for (point in regionalPoints) {
                val markerX = panelWidthPx / 2f +
                    (lonToWorldPixel(point.point.lon, REGIONAL_MAP_ZOOM) - centerWorldX)
                val markerY = panelHeightPx / 2f +
                    (latToWorldPixel(point.point.lat, REGIONAL_MAP_ZOOM) - centerWorldY)
                val dotSize = if (point.isCentre) 12.dp else 8.dp
                val dotPx = if (point.isCentre) centreDotPx else neighbourDotPx
                val dotColor = if (point.isCentre) RetroTokens.Yellow else RetroTokens.TextPrimary

                Surface(
                    color = dotColor,
                    shape = CircleShape,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (markerX - dotPx / 2f).roundToInt(),
                                (markerY - dotPx / 2f).roundToInt()
                            )
                        }
                        .size(dotSize)
                ) {}

                val labelSize = labelSizes[point] ?: IntSize.Zero
                val labelOffset = regionalLabelOffset(
                    markerX = markerX,
                    markerY = markerY,
                    centerX = panelWidthPx / 2f,
                    centerY = panelHeightPx / 2f,
                    labelWidth = labelSize.width,
                    labelHeight = labelSize.height,
                    panelWidth = panelWidthPx,
                    panelHeight = panelHeightPx
                )

                Box(
                    modifier = Modifier
                        .offset { labelOffset }
                        .widthIn(max = 160.dp)
                        .onSizeChanged { labelSizes[point] = it },
                    contentAlignment = Alignment.Center
                ) {
                    RegionalCityLabel(point = point, unitFormatter = unitFormatter)
                }
            }
        }
    }
}

/** One plotted city: white name with a hard shadow (so it reads on the
 * map), yellow temperature, small condition icon. All uppercase,
 * Star4000 via the theme typography. */
@Composable
private fun RegionalCityLabel(
    point: RegionalPoint,
    unitFormatter: UnitFormatter
) {
    val hardShadow = Shadow(
        offset = Offset(1f, 1f),
        color = RetroTokens.Shadow,
        blurRadius = 0f
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = shortMapLabel(point.label).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(shadow = hardShadow),
            fontWeight = FontWeight.Bold,
            color = RetroTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getWeatherIcon(point.condition, point.isDaytime),
                contentDescription = point.condition.displayName,
                tint = RetroTokens.Yellow,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = (point.tempC?.let { unitFormatter.formatTemperature(it) } ?: "--").uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(shadow = hardShadow),
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )
        }
    }
}

/**
 * Where a point's label goes, as a top-left offset in panel pixels.
 * Labels sit OUTSIDE their marker, away from the centre — above the
 * marker for the north half, below for the south half, to the side for
 * east/west — so one-per-octant labels diverge instead of colliding.
 * The centre point's label sits below its marker, centred. The result
 * is clamped inside the panel (a label never runs off the edge); a
 * zero measured size (first frame, before onSizeChanged fires) places
 * the label unclamped, and the next frame corrects it.
 */
private fun regionalLabelOffset(
    markerX: Float,
    markerY: Float,
    centerX: Float,
    centerY: Float,
    labelWidth: Int,
    labelHeight: Int,
    panelWidth: Float,
    panelHeight: Float
): IntOffset {
    val dx = markerX - centerX
    val dy = markerY - centerY
    val isCentre = dx == 0f && dy == 0f
    val w = labelWidth.toFloat()
    val h = labelHeight.toFloat()

    var left: Float
    var top: Float
    when {
        // North half (N, NE, NW): label above the marker.
        dy < 0f -> {
            top = markerY - LABEL_GAP_PX - h
            left = when {
                dx > 20f -> markerX + LABEL_GAP_PX
                dx < -20f -> markerX - LABEL_GAP_PX - w
                else -> markerX - w / 2f
            }
        }
        // South half (S, SE, SW) and the centre: label below the marker.
        dy > 0f || isCentre -> {
            top = markerY + LABEL_GAP_PX
            left = when {
                dx > 20f -> markerX + LABEL_GAP_PX
                dx < -20f -> markerX - LABEL_GAP_PX - w
                else -> markerX - w / 2f
            }
        }
        // Due east / west: label to the side, vertically centred.
        dx > 0f -> {
            left = markerX + LABEL_GAP_PX
            top = markerY - h / 2f
        }
        else -> {
            left = markerX - LABEL_GAP_PX - w
            top = markerY - h / 2f
        }
    }

    // Clamp inside the panel. A label wider/taller than the panel (it
    // cannot be — widthIn caps it at 160dp) pins to the top-left instead
    // of crashing coerceIn with an empty range.
    left = if (w >= panelWidth - 8f) 4f else left.coerceIn(4f, panelWidth - w - 4f)
    top = if (h >= panelHeight - 8f) 4f else top.coerceIn(4f, panelHeight - h - 4f)
    return IntOffset(left.roundToInt(), top.roundToInt())
}

// Web Mercator projection — the same math the radar tiles use
// (RadarScreen.kt), so the markers land on the tiles they belong to.
private fun lonToWorldPixel(lon: Double, zoom: Int): Float {
    val fullSize = (256 * (1 shl zoom)).toFloat()
    return (((lon + 180.0) / 360.0).toFloat() * fullSize)
}

private fun latToWorldPixel(lat: Double, zoom: Int): Float {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val fullSize = (256 * (1 shl zoom)).toFloat()
    val yFrac = (1.0 - (ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI)) / 2.0
    return (yFrac.toFloat() * fullSize)
}
