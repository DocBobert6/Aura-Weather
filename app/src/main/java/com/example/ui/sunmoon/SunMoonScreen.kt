package com.example.ui.sunmoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LunarTimes
import com.example.domain.model.SolarTimes
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.LocalSkyHeaderColor
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.AuraSky
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SunMoonScreen(
    solar: SolarTimes?,
    lunar: LunarTimes?,
    modifier: Modifier = Modifier,
    zoneId: String? = null,
    placeName: String? = null
) {
    val zone = remember(zoneId) {
        runCatching { ZoneId.of(zoneId ?: "") }.getOrDefault(ZoneId.systemDefault())
    }
    val timeFormatter = remember(zone) {
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zone)
    }

    // No invented sun/moon values: missing data renders as "--".
    val sunriseStr = solar?.sunrise?.let { timeFormatter.format(it) } ?: "--"
    val sunsetStr = solar?.sunset?.let { timeFormatter.format(it) } ?: "--"
    val noonStr = solar?.solarNoon?.let { timeFormatter.format(it) } ?: "--"
    val dawnStr = solar?.civilDawn?.let { timeFormatter.format(it) } ?: "--"
    val duskStr = solar?.civilDusk?.let { timeFormatter.format(it) } ?: "--"

    val dayHours = solar?.dayLengthMinutes?.div(60)
    val dayMinutes = solar?.dayLengthMinutes?.rem(60)
    val deltaMins = solar?.dayLengthDeltaYesterdayMinutes

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sun_moon_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Room for the floating phone bar, so the last row never
            // hides under it.
            bottom = AuraBars.contentBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "SUN & MOON DYNAMICS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalSkyHeaderColor.current
                )
                Text(
                    text = "Sunrise, sunset and moon times" + (placeName?.let { " for $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        // Solar Arc Visualization Card
        item {
            GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SOLAR ARC",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Text(
                            text = if (dayHours != null && dayMinutes != null && deltaMins != null) {
                                "Daylight: ${dayHours}h ${dayMinutes}m (${if (deltaMins >= 0) "+$deltaMins min" else "$deltaMins min"})"
                            } else {
                                "Daylight: --"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AuraSky.celestialColor(true)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // The horizon line is drawn inside the Canvas (a DrawScope,
                    // where the scheme cannot be read), so resolve it here.
                    val horizonLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    val textMeasurer = rememberTextMeasurer()

                    // Canvas Solar Arc
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val horizonY = h * 0.85f

                            // Horizon line
                            drawLine(
                                color = horizonLineColor,
                                start = Offset(0f, horizonY),
                                end = Offset(w, horizonY),
                                strokeWidth = 1.5f
                            )

                            // Solar trajectory arc
                            val arcPath = Path().apply {
                                moveTo(w * 0.12f, horizonY)
                                quadraticBezierTo(w * 0.5f, -h * 0.15f, w * 0.88f, horizonY)
                            }
                            drawPath(
                                path = arcPath,
                                color = AuraSky.celestialColor(true).copy(alpha = 0.35f),
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                            )

                            // Sun position calculation
                            val now = Instant.now().epochSecond
                            // Without real sunrise/sunset there is no arc to draw.
                            val rise = solar?.sunrise?.epochSecond ?: return@Canvas
                            val set = solar?.sunset?.epochSecond ?: return@Canvas
                            // After sunset the sun is below the horizon: there is no
                            // position to draw, so the dot is hidden instead of
                            // parking at the arc apex (the old 0.5f fallback).
                            val isDaytime = now in rise..set
                            val progress = if (isDaytime) {
                                ((now - rise).toFloat() / (set - rise).toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }

                            // Quadratic Bezier point on arc: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
                            val t = progress
                            val sunX = (1 - t) * (1 - t) * (w * 0.12f) + 2 * (1 - t) * t * (w * 0.5f) + t * t * (w * 0.88f)
                            val sunY = (1 - t) * (1 - t) * horizonY + 2 * (1 - t) * t * (-h * 0.15f) + t * t * horizonY

                            // Draw glowing sun (only while it is up)
                            if (isDaytime) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(AuraSky.celestialColor(true).copy(alpha = 0.5f), Color.Transparent),
                                        center = Offset(sunX, sunY),
                                        radius = 28f
                                    ),
                                    radius = 28f,
                                    center = Offset(sunX, sunY)
                                )
                                drawCircle(
                                    color = AuraSky.celestialColor(true),
                                    radius = 8f,
                                    center = Offset(sunX, sunY)
                                )
                                // 'Now' under the sun dot, so the marker is
                                // labelled (not just a floating dot).
                                val nowLayout = textMeasurer.measure(
                                    text = "Now",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                drawText(
                                    textLayoutResult = nowLayout,
                                    topLeft = Offset(
                                        (sunX - nowLayout.size.width / 2f).coerceIn(0f, size.width - nowLayout.size.width),
                                        (sunY + 14f).coerceAtMost(size.height - nowLayout.size.height)
                                    )
                                )
                            } else {
                                // Night: shade the arc dark, so the card reads
                                // as night (not an empty daytime arc).
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    size = size
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // The arc runs from 12% to 88% of the width; the label
                    // Row takes the same inset, so Sunrise / Solar Noon /
                    // Sunset sit under the arc's start / apex / end instead
                    // of drifting to the screen's edges.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val inset = maxWidth * 0.12f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = inset),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        Column {
                            Text(text = "Sunrise", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = sunriseStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Solar Noon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = noonStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Sunset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = sunsetStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        }
                    }
                }
            }
        }

        // Golden Hour & Blue Hour Photography Bands
        item {
            Text(
                text = "LIGHTING & TWILIGHT PHASES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TwilightCard(
                    title = "Golden Hour",
                    icon = Icons.Default.WbSunny,
                    accentColor = AuraColors.Warning,
                    morning = formatRange(solar?.goldenHourMorningStart, solar?.goldenHourMorningEnd, timeFormatter),
                    evening = formatRange(solar?.goldenHourEveningStart, solar?.goldenHourEveningEnd, timeFormatter),
                    modifier = Modifier.weight(1f)
                )

                TwilightCard(
                    title = "Blue Hour",
                    icon = Icons.Default.WbTwilight,
                    accentColor = AuraColors.Water,
                    morning = formatRange(solar?.blueHourMorningStart, solar?.blueHourMorningEnd, timeFormatter),
                    evening = formatRange(solar?.blueHourEveningStart, solar?.blueHourEveningEnd, timeFormatter),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Lunar phase
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "LUNAR CYCLE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
            )
        }

        item {
            val phaseName = lunar?.phaseName ?: "--"
            val illFraction = lunar?.illuminatedFraction ?: 0.0
            val illPercent = (illFraction * 100).roundToInt()

            GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Moon Phase Canvas
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2.2f
                            val center = Offset(size.width / 2, size.height / 2)

                            // Illuminated moon disk
                            drawCircle(
                                color = AuraSky.celestialColor(false),
                                radius = r,
                                center = center
                            )

                            // Shadow mask based on illuminated fraction.
                            // At 2% the offset is ~1px, so the disk reads as
                            // fully dark. Keep a minimum visible sliver
                            // whenever there is any illumination.
                            val shadowOffset = if (illFraction > 0.005) {
                                (illFraction.toFloat() * r * 1.8f).coerceAtLeast(r * 0.10f)
                            } else 0f
                            drawCircle(
                                color = AuraColors.MoonShadow,
                                radius = r * 0.98f,
                                center = Offset(center.x - shadowOffset, center.y)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Text(
                            text = phaseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (lunar != null) "$illPercent% illuminated disk" else "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // The nearer event is prominent; the farther one is dim.
                        // "16d" reads as jargon — say "in 16 days".
                        Row {
                            val fullFirst = (lunar?.nextFullMoonDays ?: Int.MAX_VALUE) <= (lunar?.nextNewMoonDays ?: Int.MAX_VALUE)
                            val fullText = lunar?.nextFullMoonDays?.let {
                                if (it <= 0) "Full Moon today" else "Full Moon in $it\u00A0day${if (it == 1) "" else "s"}"
                            } ?: "Full Moon: --"
                            val newText = lunar?.nextNewMoonDays?.let {
                                if (it <= 0) "New Moon today" else "New Moon in $it\u00A0day${if (it == 1) "" else "s"}"
                            } ?: "New Moon: --"
                            Text(
                                text = fullText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (fullFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (fullFirst) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = newText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!fullFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (!fullFirst) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                text = "Moonrise ${lunar?.moonrise?.let { timeFormatter.format(it) } ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Moonset ${lunar?.moonset?.let { timeFormatter.format(it) } ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
/**
 * A twilight window as a start–end range ("6:56 AM – 7:13 AM"). The model
 * has both ends; showing only the start hid half the window.
 */
private fun formatRange(
    start: Instant?,
    end: Instant?,
    timeFormatter: DateTimeFormatter
): String {
    if (start == null || end == null) return "--"
    return "${timeFormatter.format(start)} – ${timeFormatter.format(end)}"
}

private fun TwilightCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    morning: String,
    evening: String,
    modifier: Modifier = Modifier
) {
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Morning",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = morning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Evening",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = evening,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
