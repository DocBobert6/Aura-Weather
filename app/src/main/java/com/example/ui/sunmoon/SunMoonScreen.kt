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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LunarTimes
import com.example.domain.model.SolarTimes
import com.example.ui.designsystem.GlassCard
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
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneId.of("America/New_York"))
    }

    val sunriseStr = solar?.sunrise?.let { timeFormatter.format(it) } ?: "7:02 AM"
    val sunsetStr = solar?.sunset?.let { timeFormatter.format(it) } ?: "6:38 PM"
    val noonStr = solar?.solarNoon?.let { timeFormatter.format(it) } ?: "1:20 PM"
    val dawnStr = solar?.civilDawn?.let { timeFormatter.format(it) } ?: "6:34 AM"
    val duskStr = solar?.civilDusk?.let { timeFormatter.format(it) } ?: "7:06 PM"

    val dayHours = (solar?.dayLengthMinutes ?: 700) / 60
    val dayMinutes = (solar?.dayLengthMinutes ?: 700) % 60
    val deltaMins = solar?.dayLengthDeltaYesterdayMinutes ?: 1

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sun_moon_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "SUN & MOON DYNAMICS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Astronomical Solar Track & Lunar Illuminance for NKY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }

        // Solar Arc Visualization Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Text(
                            text = "Daylight: ${dayHours}h ${dayMinutes}m (${if (deltaMins >= 0) "+$deltaMins min" else "$deltaMins min"})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD54F)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                                color = Color.White.copy(alpha = 0.2f),
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
                                color = Color(0xFFFFD54F).copy(alpha = 0.35f),
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                            )

                            // Sun position calculation
                            val now = Instant.now().epochSecond
                            val rise = solar?.sunrise?.epochSecond ?: (now - 3600)
                            val set = solar?.sunset?.epochSecond ?: (now + 3600)
                            val progress = if (now in rise..set) {
                                ((now - rise).toFloat() / (set - rise).toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }

                            // Quadratic Bezier point on arc: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
                            val t = progress
                            val sunX = (1 - t) * (1 - t) * (w * 0.12f) + 2 * (1 - t) * t * (w * 0.5f) + t * t * (w * 0.88f)
                            val sunY = (1 - t) * (1 - t) * horizonY + 2 * (1 - t) * t * (-h * 0.15f) + t * t * horizonY

                            // Draw glowing sun
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.5f), Color.Transparent),
                                    center = Offset(sunX, sunY),
                                    radius = 28f
                                ),
                                radius = 28f,
                                center = Offset(sunX, sunY)
                            )
                            drawCircle(
                                color = Color(0xFFFFD54F),
                                radius = 8f,
                                center = Offset(sunX, sunY)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Sunrise", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text(text = sunriseStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Solar Noon", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text(text = noonStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Sunset", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text(text = sunsetStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TwilightCard(
                    title = "Golden Hour",
                    icon = Icons.Default.CameraAlt,
                    accentColor = Color(0xFFFFB74D),
                    morning = solar?.goldenHourMorningStart?.let { timeFormatter.format(it) } ?: "7:00 AM",
                    evening = solar?.goldenHourEveningStart?.let { timeFormatter.format(it) } ?: "6:05 PM",
                    modifier = Modifier.weight(1f)
                )

                TwilightCard(
                    title = "Blue Hour",
                    icon = Icons.Default.WbTwilight,
                    accentColor = Color(0xFF64B5F6),
                    morning = solar?.blueHourMorningStart?.let { timeFormatter.format(it) } ?: "6:40 AM",
                    evening = solar?.blueHourEveningStart?.let { timeFormatter.format(it) } ?: "6:42 PM",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Lunar Phase Dynamics
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "LUNAR CYCLE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        item {
            val phaseName = lunar?.phaseName ?: "Waxing Crescent"
            val illFraction = lunar?.illuminatedFraction ?: 0.42
            val illPercent = (illFraction * 100).roundToInt()

            GlassCard(modifier = Modifier.fillMaxWidth()) {
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

                            // Dark moon silhouette
                            drawCircle(color = Color(0xFF1E293B), radius = r, center = center)

                            // Illuminated moon disk
                            drawCircle(
                                color = Color(0xFFE2E8F0),
                                radius = r,
                                center = center
                            )

                            // Shadow mask based on illuminated fraction
                            val shadowOffset = (1.0f - illFraction.toFloat()) * r * 1.8f
                            drawCircle(
                                color = Color(0xFF0F172A),
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
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "$illPercent% illuminated disk",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                text = "Next Full Moon: ${lunar?.nextFullMoonDays ?: 12}d",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF81D4FA)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "New Moon: ${lunar?.nextNewMoonDays ?: 26}d",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwilightCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    morning: String,
    evening: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
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
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Dawn / Morning",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = morning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Dusk / Evening",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = evening,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
