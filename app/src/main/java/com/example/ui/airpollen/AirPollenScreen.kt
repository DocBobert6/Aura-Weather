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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.domain.model.AirQuality
import com.example.domain.model.PollenData
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.LocalSkyHeaderColor
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface

@Composable
fun AirPollenScreen(
    airQuality: AirQuality?,
    pollen: PollenData?,
    onNavigateToSettings: () -> Unit,
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
            bottom = AuraBars.contentBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AIR QUALITY & POLLEN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalSkyHeaderColor.current
                )
                Text(
                    text = "Open-Meteo Air Quality",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        // AQI Dial Card
        item {
            GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The dial arcs are drawn inside the Canvas (a DrawScope,
                    // where the scheme cannot be read), so resolve the track here.
                    val dialTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 18.dp.toPx()
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            // Background arc (240 degrees)
                            drawArc(
                                color = dialTrackColor,
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            // Foreground AQI progress arc (normalized 0..300).
                            // With no AQI, the arc stays at the background.
                            val sweep = if (aqi != null) ((aqi.coerceIn(0, 300) / 300f) * 240f).coerceAtLeast(6f) else 0f
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "US AQI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = aqiColor.copy(alpha = 0.2f),
                        shape = AuraShapes.pill
                    ) {
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = aqiColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = guidance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Source: ${airQuality?.source ?: "Open-Meteo Air Quality"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
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
                    title = "Ozone",
                    label = "O₃ Level",
                    value = airQuality?.ozonePpb?.let { "$it ppb" } ?: "--",
                    status = airQuality?.ozonePpb?.let { if (it < 54.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Pollen Section
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ALLERGEN & POLLEN OUTLOOK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
            )
        }

        item {
            GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
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

                    if (pollen?.hasKey != true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = AuraShapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Key required",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Live Pollen Telemetry",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pollen counts aren't connected yet — no live values are shown. A Tomorrow.io key can be stored in Settings for a future update, but it does not enable pollen data today.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onNavigateToSettings,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        // onPrimaryContainer (dark) reads on the pale gold;
                                        // primary (gold) on pale gold was washed out.
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Configure in Settings", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
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
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = AuraColors.Accent.copy(alpha = 0.2f),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = AuraColors.Accent,
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
            tint = if (hasKey) AuraColors.PollenActive else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (hasKey && level != null) "$level / 5" else "--",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
