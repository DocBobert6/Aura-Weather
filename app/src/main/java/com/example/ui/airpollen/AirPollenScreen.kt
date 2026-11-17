package com.example.ui.airpollen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.mapper.AllergyLevel
import com.example.data.mapper.allergyIndex
import com.example.data.mapper.aqiSegments
import com.example.domain.model.AirQuality
import com.example.domain.model.PollenData
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import com.example.ui.widget.star4000Safe
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
                    text = "AIR QUALITY",
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

        // AQI Card (segmented bar, Item 9)
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Era segmented AQI bar (Item 9, 0.3.3): the
                    // semicircular gauge was a modern element. Six
                    // segments match the EPA categories; each
                    // segment's fill comes from the pure
                    // aqiSegments() function (AqiSegments.kt) —
                    // Compose only draws what it returns. The big
                    // number and the category pill stay.
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        aqiSegments(aqi).forEach { segment ->
                            Box(
                                modifier = Modifier
                                    .weight(segment.widthFraction)
                                    .height(18.dp)
                            ) {
                                // Unfilled track: the band colour at
                                // low alpha, as the old gauge did.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            AuraColors.aqiColor(segment.representativeAqi)
                                                .copy(alpha = 0.30f)
                                        )
                                )
                                // Filled part, left to right.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(segment.fillFraction)
                                        .fillMaxHeight()
                                        .background(
                                            AuraColors.aqiColor(segment.representativeAqi)
                                        )
                                )
                            }
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
            Text(
                text = "CURRENT HOUR",
                style = MaterialTheme.typography.labelSmall,
                color = RetroTokens.TextSecondary
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
                    value = airQuality?.pm25?.let { star4000Safe("$it UG/M3") } ?: "--",
                    status = airQuality?.pm25?.let { if (it < 12.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )

                PollutantCard(
                    title = "PM10",
                    label = "Coarse Particles",
                    value = airQuality?.pm10?.let { star4000Safe("$it UG/M3") } ?: "--",
                    status = airQuality?.pm10?.let { if (it < 54.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )

                PollutantCard(
                    title = "OZONE",
                    label = "O3 Level",
                    value = airQuality?.ozonePpb?.let { star4000Safe("${it.roundToInt()} ppb") } ?: "--",
                    status = airQuality?.ozonePpb?.let { if (it < 54.0) "Good" else "Moderate" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Allergy Index (AQI-only): the pollen gauges are gone (L-9) -
        // pollen counts are unavailable, so the index runs on air
        // quality alone. The panel and its honest note stay.
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
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

