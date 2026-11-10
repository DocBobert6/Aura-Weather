package com.example.ui.airpollen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.mapper.AllergyLevel
import com.example.data.mapper.allergyIndex
import com.example.domain.model.AirQuality
import com.example.domain.model.PollenData
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import kotlin.math.roundToInt

@Composable
fun AirPollenScreen(
    airQuality: AirQuality?,
    pollen: PollenData?,
    tickerVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    // No AQI from the provider means "unavailable" — never a made-up 28/Good.
    val aqi = airQuality?.aqi
    val category = airQuality?.category ?: "Unavailable"
    val guidance = airQuality?.healthGuidance ?: "Air quality data is unavailable."

    val aqiColor = AuraColors.aqiColor(aqi)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("air_pollen_screen"),
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
                    text = "AIR QUALITY & POLLEN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.Yellow
                )
                Text(
                    text = "OPEN-METEO AIR QUALITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroTokens.TextSecondary
                )
            }
        }

        // AQI Dial Card
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 18.dp.toPx()
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            // Background arc, segmented by the 6 AQI bands
                            // (0..500, so Hazardous is on the gauge — the old
                            // code normalised the foreground to 300 and cut
                            // it off). Each band is its scale colour at low
                            // alpha, so the track reads as the bands. The
                            // colours come from AuraColors.aqiColor (one
                            // representative value per band), never hardcoded.
                            val bandBounds = listOf(50, 100, 150, 200, 300, 500)
                            val bandColors = listOf(
                                AuraColors.aqiColor(25), AuraColors.aqiColor(75),
                                AuraColors.aqiColor(125), AuraColors.aqiColor(175),
                                AuraColors.aqiColor(250), AuraColors.aqiColor(400)
                            )
                            var bandStart = 0
                            bandBounds.forEachIndexed { i, bound ->
                                val startAngle = 150f + (bandStart / 500f) * 240f
                                val sweep = ((bound - bandStart) / 500f) * 240f
                                drawArc(
                                    color = bandColors[i].copy(alpha = 0.30f),
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                bandStart = bound
                            }

                            // Foreground AQI progress arc (normalised 0..500,
                            // the same scale as the bands). With no AQI, the
                            // arc stays at the background.
                            val sweep = if (aqi != null) ((aqi.coerceIn(0, 500) / 500f) * 240f).coerceAtLeast(6f) else 0f
                            drawArc(
                                color = aqiColor,
                                startAngle = 150f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = aqi?.toString() ?: "--",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.Yellow
                            )
                            Text(
                                text = "US AQI",
                                style = MaterialTheme.typography.labelSmall,
                                color = RetroTokens.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = aqiColor,
                        shape = AuraShapes.pill
                    ) {
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = guidance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RetroTokens.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Source: ${airQuality?.source ?: "Open-Meteo Air Quality"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RetroTokens.TextSecondary
                    )
                }
            }
        }

        // Pollutant Details
        item {
            Text(
                text = "POLLUTANT CONCENTRATION",
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
                PollutantCard(
                    title = "PM2.5",
                    label = "Fine Dust",
                    value = airQuality?.pm25?.let { "$it µg/m³" } ?: "--",
                    status = airQuality?.pm25?.let { if (it < 12.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )

                PollutantCard(
                    title = "PM10",
                    label = "Coarse Particles",
                    value = airQuality?.pm10?.let { "$it µg/m³" } ?: "--",
                    status = airQuality?.pm10?.let { if (it < 54.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )

                PollutantCard(
                    title = "OZONE",
                    label = "O₃ Level",
                    value = airQuality?.ozonePpb?.let { "${it.roundToInt()} ppb" } ?: "--",
                    status = airQuality?.ozonePpb?.let { if (it < 54.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Pollen Section
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "POLLEN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )
        }

        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        PollenGauge(
                            title = "Tree",
                            icon = Icons.Default.Park,
                            level = pollen?.treeIndex,
                            hasKey = pollen?.hasKey ?: false
                        )
                        PollenGauge(
                            title = "Grass",
                            icon = Icons.Default.Yard,
                            level = pollen?.grassIndex,
                            hasKey = pollen?.hasKey ?: false
                        )
                        PollenGauge(
                            title = "Weed",
                            icon = Icons.Default.Spa,
                            level = pollen?.weedIndex,
                            hasKey = pollen?.hasKey ?: false
                        )
                    }

                    // Build D: a simple allergy index from the pollen
                    // counts and AQI already on this screen — the worst of
                    // the two, with the driver named. It is NOT a medical
                    // forecast; the note under it says so. Hidden when
                    // there is no data at all (no counts, no AQI).
                    val allergy = allergyIndex(
                        treeIndex = pollen?.treeIndex,
                        grassIndex = pollen?.grassIndex,
                        weedIndex = pollen?.weedIndex,
                        aqi = airQuality?.aqi
                    )
                    if (allergy != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ALLERGY INDEX",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = allergy.level.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (allergy.level) {
                                AllergyLevel.LOW -> RetroTokens.TextPrimary
                                AllergyLevel.MODERATE -> RetroTokens.Yellow
                                AllergyLevel.HIGH -> RetroTokens.HeaderOrange
                                AllergyLevel.VERY_HIGH -> RetroTokens.HeaderRed
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = allergy.driver,
                            style = MaterialTheme.typography.bodySmall,
                            color = RetroTokens.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A SIMPLE INDEX FROM TODAY'S AIR QUALITY - POLLEN COUNTS ARE UNAVAILABLE, SO THEY DO NOT CONTRIBUTE. NOT A MEDICAL FORECAST.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RetroTokens.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pollen counts are unavailable - there is no free US pollen source. The allergy index above runs on air quality alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroTokens.TextSecondary
                    )
                }
            }
        }
    }
}                }
            }
        }
    }
}

@Composable
private fun PollutantCard(
    title: String,
    label: String,
    value: String,
    status: String,
    modifier: Modifier = Modifier
) {
    RetroPanel(shape = RetroShapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = RetroTokens.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.Yellow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = RetroTokens.PanelLight,
                shape = AuraShapes.pill
            ) {
                Text(
                    text = status.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = RetroTokens.TextPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PollenGauge(
    title: String,
    icon: ImageVector,
    level: Int?,
    hasKey: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (hasKey) AuraColors.PollenActive else RetroTokens.TextSecondary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = RetroTokens.TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (hasKey && level != null) "$level / 5" else "--",
            style = MaterialTheme.typography.labelSmall,
            color = RetroTokens.TextSecondary
        )
    }
}
