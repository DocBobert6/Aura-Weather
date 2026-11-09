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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LunarTimes
import com.example.domain.model.SolarTimes
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
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
    placeName: String? = null,
    tickerVisible: Boolean = false
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
            bottom = AuraBars.contentBottomPadding(tickerVisible)
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "SUN & MOON DYNAMICS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.Yellow
                )
                Text(
                    text = ("Sunrise, sunset and moon times" + (placeName?.let { " for $it" } ?: "")).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroTokens.TextSecondary
                )
            }
        }

        // Sun times as a classic table (R4): label + time rows, numbers in
        // yellow. The solar-arc Canvas is dropped — the golden/blue-hour
        // ranges below carry the twilight detail as text.
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SUN TIMES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.Yellow
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SunTimeRow(label = "SUNRISE", time = sunriseStr)
                    SunTimeRow(label = "SOLAR NOON", time = noonStr)
                    SunTimeRow(label = "SUNSET", time = sunsetStr)
                    SunTimeRow(label = "CIVIL DAWN", time = dawnStr)
                    SunTimeRow(label = "CIVIL DUSK", time = duskStr)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (dayHours != null && dayMinutes != null && deltaMins != null) {
                            "DAYLIGHT: ${dayHours}H ${dayMinutes}M (${if (deltaMins >= 0) "+$deltaMins MIN" else "$deltaMins MIN"})"
                        } else {
                            "DAYLIGHT: --"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RetroTokens.Yellow
                    )
                }
            }
        }

        // Golden Hour & Blue Hour Photography Bands
        item {
            Text(
                text = "LIGHTING & TWILIGHT PHASES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
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
                    accentColor = RetroTokens.Yellow,
                    morning = formatRange(solar?.goldenHourMorningStart, solar?.goldenHourMorningEnd, timeFormatter),
                    evening = formatRange(solar?.goldenHourEveningStart, solar?.goldenHourEveningEnd, timeFormatter),
                    modifier = Modifier.weight(1f)
                )

                TwilightCard(
                    title = "Blue Hour",
                    icon = Icons.Default.WbTwilight,
                    accentColor = RetroTokens.Border,
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
                color = RetroTokens.Yellow
            )
        }

        item {
            val phaseName = lunar?.phaseName ?: "--"
            val illFraction = lunar?.illuminatedFraction ?: 0.0
            val illPercent = (illFraction * 100).roundToInt()

            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
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
                                color = RetroTokens.TextPrimary,
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
                            color = RetroTokens.TextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (lunar != null) "$illPercent% illuminated disk" else "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RetroTokens.TextSecondary
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
                                text = fullText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (fullFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (fullFirst) RetroTokens.TextPrimary else RetroTokens.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = newText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!fullFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (!fullFirst) RetroTokens.TextPrimary else RetroTokens.TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                text = "MOONRISE ${lunar?.moonrise?.let { timeFormatter.format(it) } ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RetroTokens.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "MOONSET ${lunar?.moonset?.let { timeFormatter.format(it) } ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RetroTokens.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One row of the classic sun-times table: label left, time right. */
@Composable
private fun SunTimeRow(label: String, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RetroTokens.TextSecondary
        )
        Text(
            text = time.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = RetroTokens.Yellow
        )
    }
}

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

@Composable
private fun TwilightCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    morning: String,
    evening: String,
    modifier: Modifier = Modifier
) {
    RetroPanel(shape = RetroShapes.medium, modifier = modifier) {
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
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "MORNING",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = RetroTokens.TextSecondary
            )
            Text(
                text = morning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "EVENING",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = RetroTokens.TextSecondary
            )
            Text(
                text = evening,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.TextPrimary
            )
        }
    }
}
