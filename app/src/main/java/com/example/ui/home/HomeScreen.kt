package com.example.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.UnitFormatter
import com.example.domain.model.AlertSeverity
import com.example.ui.designsystem.AuraBars
import com.example.domain.model.DailyPoint
import com.example.domain.model.FullWeatherState
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
import com.example.domain.model.WeatherCondition
import com.example.ui.designsystem.GlassCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: FullWeatherState?,
    isRefreshing: Boolean,
    selectedPlace: Place?,
    loadError: String?,
    savedPlaces: List<Place>,
    unitFormatter: UnitFormatter,
    onRefresh: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToAirPollen: () -> Unit,
    onNavigateToSunMoon: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var locationMenuExpanded by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onUseCurrentLocation()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_scroll"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            // The old trailing 24dp spacer is now this: room for the
            // floating phone bar, so the last card never hides under it.
            bottom = AuraBars.contentBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Location Header & Freshness Bar
        item {
            HeaderBar(
                place = selectedPlace ?: state?.place,
                savedPlaces = savedPlaces,
                isRefreshing = isRefreshing,
                isStale = state?.isStale ?: false,
                fetchedAt = state?.fetchedAt ?: Instant.now(),
                menuExpanded = locationMenuExpanded,
                onToggleMenu = { locationMenuExpanded = !locationMenuExpanded },
                onSelectPlace = {
                    locationMenuExpanded = false
                    onSelectPlace(it)
                },
                onLocationClick = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                onRefresh = onRefresh
            )
        }

        // 2. Offline / Error Banner if any
        if (state?.errorMessage != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // No place selected yet, or the load failed with nothing cached:
        // show that state plainly instead of empty cards or invented weather.
        if (selectedPlace == null && state == null) {
            item {
                if (loadError != null) {
                    LoadErrorCard(message = loadError, onRetry = onRefresh)
                } else {
                    NoPlaceCard(
                        onUseCurrentLocation = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                        onAddPlace = onNavigateToSettings
                    )
                }
            }
        }

        // 3. Active Alert Banner (if severe alert is active)
        if (state != null && !state.activeAlerts.isNullOrEmpty()) {
            item {
                val topAlert = state!!.activeAlerts.first()
                ActiveAlertBanner(
                    alertTitle = topAlert.headline,
                    severity = topAlert.severity,
                    count = state.activeAlerts.size,
                    onClick = onNavigateToAlerts
                )
            }
        }

        // 4-10. Weather sections — only when there is a state to show.
        // With no place and no state, the no-place / error card above is the
        // whole screen; empty cards with dashes would look broken.
        if (state != null) {
        // 4. Hero Section: Current temperature, Condition icon, Feels-like, Hi/Lo
        item {
            HeroWeatherCard(
                state = state,
                unitFormatter = unitFormatter
            )
        }

        // 5. Minute-Cast Card (60-Minute Precipitation curve)
        item {
            MinuteCastCard(
                minutePoints = state?.minutely60m ?: emptyList(),
                summarySentence = state?.minuteSummary ?: "No precipitation expected for the next 60 minutes."
            )
        }

        // 6. 48-Hour Hourly Forecast Strip
        item {
            HourlyForecastCard(
                hourlyPoints = state?.hourly48h ?: emptyList(),
                unitFormatter = unitFormatter,
                zoneId = runCatching { ZoneId.of(state?.place?.zoneId ?: "") }.getOrDefault(ZoneId.systemDefault())
            )
        }

        // 7. 7-Day Forecast Card
        item {
            DailyForecastCard(
                dailyPoints = state?.daily7d ?: emptyList(),
                unitFormatter = unitFormatter
            )
        }

        // 8. 2x2 Details Grid (Wind, Humidity/Dewpoint, Pressure, Visibility)
        item {
            WeatherDetailsGrid(
                state = state,
                unitFormatter = unitFormatter
            )
        }

        // 9. Air Quality & Pollen Summary Card
        item {
            AirAndPollenSummaryCard(
                state = state,
                onClick = onNavigateToAirPollen
            )
        }

        // 10. Sun & Moon Summary Card
        item {
            SunAndMoonSummaryCard(
                state = state,
                onClick = onNavigateToSunMoon
            )
        }
        }
    }
}

@Composable
private fun NoPlaceCard(
    onUseCurrentLocation: () -> Unit,
    onAddPlace: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Choose a place to see its weather",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aura Weather works anywhere. Use your current location, or add a place in Settings — no place is chosen for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onUseCurrentLocation,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use current location")
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onAddPlace,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add a place in Settings", color = Color.White)
            }
        }
    }
}

@Composable
private fun LoadErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Couldn't load the weather",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun HeaderBar(
    place: Place?,
    savedPlaces: List<Place>,
    isRefreshing: Boolean,
    isStale: Boolean,
    fetchedAt: Instant,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onLocationClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val placeName = place?.let { "${it.name}${if (it.stateCode.isNotBlank()) ", ${it.stateCode}" else ""}" }
        ?: "No place selected"

    val diffMins = (Instant.now().epochSecond - fetchedAt.epochSecond) / 60
    val freshnessText = when {
        isRefreshing -> "Refreshing..."
        diffMins <= 1 -> "Updated just now"
        diffMins < 60 -> "Updated ${diffMins}m ago"
        else -> "Updated ${(diffMins / 60)}h ago"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggleMenu)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = placeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select location",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = if (isStale) "Cached • $freshnessText" else freshnessText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStale) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onToggleMenu,
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                savedPlaces.forEach { p ->
                    val isCurrent = p.id == place?.id
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (p.stateCode.isNotBlank()) "${p.name}, ${p.stateCode}" else p.name,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = { onSelectPlace(p.id) }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onLocationClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Use GPS Location",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.size(40.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveAlertBanner(
    alertTitle: String,
    severity: AlertSeverity,
    count: Int,
    onClick: () -> Unit
) {
    val (bgColor, iconColor) = when (severity) {
        AlertSeverity.Extreme -> Color(0xFFD32F2F) to Color.White
        AlertSeverity.Severe -> Color(0xFFF57C00) to Color.White
        else -> Color(0xFFE65100) to Color.White
    }

    Surface(
        color = bgColor.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 1) "Active Alerts ($count)" else "Severe Weather Warning",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconColor.copy(alpha = 0.85f)
                )
                Text(
                    text = alertTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = iconColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "VIEW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = iconColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun HeroWeatherCard(
    state: FullWeatherState?,
    unitFormatter: UnitFormatter
) {
    val tempFormatted = unitFormatter.formatTemperature(state?.current?.tempC)
    val feelsLikeFormatted = unitFormatter.formatTemperature(state?.current?.feelsLikeC)
    val condition = state?.current?.condition ?: WeatherCondition.ClearDay
    val description = state?.current?.description ?: condition.displayName

    val todayDaily = state?.daily7d?.firstOrNull()
    val highFormatted = unitFormatter.formatTemperature(todayDaily?.highC ?: state?.current?.tempC)
    // No invented low: when the daily low is missing the UI shows "--".
    val lowFormatted = unitFormatter.formatTemperature(todayDaily?.lowC)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_weather_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = getWeatherIcon(condition, state?.solar?.isDaytime ?: true),
                contentDescription = description,
                tint = if (state?.solar?.isDaytime == false) Color(0xFFE2E8F0) else Color(0xFFFFD54F),
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tempFormatted,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Light,
                color = Color.White
            )

            Text(
                text = description,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Feels like $feelsLikeFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "H: $highFormatted   L: $lowFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun MinuteCastCard(
    minutePoints: List<MinutePrecip>,
    summarySentence: String
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minute_cast_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Precipitation",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXT 60 MINUTES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "15-min resolution",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = summarySentence,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 60-Minute Canvas bar chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val width = size.width
                val height = size.height
                val count = minutePoints.size.coerceAtLeast(1)
                val barWidth = width / count

                val maxRate = (minutePoints.maxOfOrNull { it.mmPerHour } ?: 0.0).coerceAtLeast(2.0)

                minutePoints.forEachIndexed { idx, point ->
                    val barHeight = ((point.mmPerHour / maxRate) * height).toFloat().coerceIn(2f, height)
                    val x = idx * barWidth
                    val y = height - barHeight

                    val barColor = when {
                        point.mmPerHour > 5.0 -> Color(0xFFE53935) // Heavy
                        point.mmPerHour > 2.0 -> Color(0xFFFFB300) // Moderate
                        point.mmPerHour > 0.1 -> Color(0xFF42A5F5) // Light
                        else -> Color.White.copy(alpha = 0.15f)    // Baseline
                    }

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Now", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text(text = "15m", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text(text = "30m", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text(text = "45m", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text(text = "60m", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(
    hourlyPoints: List<HourlyPoint>,
    unitFormatter: UnitFormatter,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("h a", Locale.US).withZone(zoneId)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hourly_forecast_card")
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "48-HOUR FORECAST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(hourlyPoints.take(24)) { point ->
                    val hourLabel = timeFormatter.format(point.time)
                    val temp = unitFormatter.formatTemperature(point.tempC)
                    val pop = point.popPct

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(52.dp)
                    ) {
                        Text(
                            text = hourLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = getWeatherIcon(point.condition, isDaytime = point.isDaytime),
                            contentDescription = point.condition.displayName,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = temp,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (pop != null && pop > 10) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Rain chance",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "$pop%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64B5F6)
                                )
                            }
                        } else {
                            Text(text = " ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyForecastCard(
    dailyPoints: List<DailyPoint>,
    unitFormatter: UnitFormatter
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.US) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_forecast_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "7-DAY OUTLOOK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            dailyPoints.forEachIndexed { index, point ->
                val dayName = if (index == 0) "Today" else dayFormatter.format(point.date)
                val hi = unitFormatter.formatTemperature(point.highC)
                val lo = unitFormatter.formatTemperature(point.lowC)
                val pop = point.popPct ?: 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        modifier = Modifier.width(60.dp)
                    )

                    Row(
                        modifier = Modifier.width(65.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getWeatherIcon(point.condition, isDaytime = true),
                            contentDescription = point.condition.displayName,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        if (pop > 15) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pop%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64B5F6)
                            )
                        }
                    }

                    Text(
                        text = lo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp)
                    )

                    // Temperature gradient range bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 8.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF64B5F6), Color(0xFFFFB74D))
                                ),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )

                    Text(
                        text = hi,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailsGrid(
    state: FullWeatherState?,
    unitFormatter: UnitFormatter
) {
    val windFormatted = unitFormatter.formatWindSpeed(state?.current?.windMs)
    val windDir = state?.current?.windDirDeg?.roundToInt()
    val gustFormatted = unitFormatter.formatWindSpeed(state?.current?.windGustMs)

    // Missing readings render as "--", never as invented 55% / 8 C / 180 deg.
    val humidity = state?.current?.humidityPct?.let { "${it.roundToInt()}%" } ?: "--"
    val dewPoint = unitFormatter.formatTemperature(state?.current?.dewPointC)

    val pressureFormatted = unitFormatter.formatPressure(state?.current?.pressurePa)
    val visibilityFormatted = unitFormatter.formatVisibility(state?.current?.visibilityM)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wind Card
            DetailMiniCard(
                title = "WIND",
                icon = Icons.Default.Air,
                mainValue = windFormatted,
                subValue = "Gusts to $gustFormatted",
                trailing = if (windDir != null) {
                    {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Wind direction",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(windDir.toFloat())
                        )
                    }
                } else null,
                modifier = Modifier.weight(1f)
            )

            // Humidity & Dew Point Card
            DetailMiniCard(
                title = "HUMIDITY",
                icon = Icons.Default.Opacity,
                mainValue = humidity,
                subValue = "Dew point $dewPoint",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pressure Card
            DetailMiniCard(
                title = "PRESSURE",
                icon = Icons.Default.Compress,
                mainValue = pressureFormatted,
                subValue = "Barometric reading",
                modifier = Modifier.weight(1f)
            )

            // Visibility Card
            DetailMiniCard(
                title = "VISIBILITY",
                icon = Icons.Default.Visibility,
                mainValue = visibilityFormatted,
                subValue = "Clear horizon view",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailMiniCard(
    title: String,
    icon: ImageVector,
    mainValue: String,
    subValue: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
                trailing?.invoke()
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = mainValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AirAndPollenSummaryCard(
    state: FullWeatherState?,
    onClick: () -> Unit
) {
    // No AQI from the provider means "unavailable" — never a made-up 28/Good.
    val aqi = state?.airQuality?.aqi
    val category = state?.airQuality?.category ?: "Unavailable"

    val aqiColor = when {
        aqi == null -> Color.White.copy(alpha = 0.5f)
        aqi <= 50 -> Color(0xFF00E676)
        aqi <= 100 -> Color(0xFFFFEB3B)
        aqi <= 150 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AIR QUALITY & POLLEN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (aqi != null) "AQI $aqi" else "AQI --",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = aqiColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = aqiColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state?.airQuality?.healthGuidance ?: "Air quality data is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "View Air Quality details",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.rotate(270f)
            )
        }
    }
}

@Composable
private fun SunAndMoonSummaryCard(
    state: FullWeatherState?,
    onClick: () -> Unit
) {
    val zoneId = remember(state?.place?.zoneId) {
        runCatching { ZoneId.of(state?.place?.zoneId ?: "") }.getOrDefault(ZoneId.systemDefault())
    }
    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zoneId)
    }

    // No invented sun/moon values: missing data renders as "--".
    val sunrise = state?.solar?.sunrise?.let { timeFormatter.format(it) } ?: "--"
    val sunset = state?.solar?.sunset?.let { timeFormatter.format(it) } ?: "--"
    val moonPhase = state?.lunar?.phaseName ?: "--"
    val illumination = state?.lunar?.illuminatedFraction?.let { "${(it * 100).roundToInt()}% illuminated" } ?: "--"

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SUN & MOON DYNAMICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Sunrise",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunrise,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = "Sunset",
                        tint = Color(0xFFFF8A65),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunset,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$moonPhase ($illumination)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "View Sun and Moon details",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.rotate(270f)
            )
        }
    }
}

fun getWeatherIcon(condition: WeatherCondition, isDaytime: Boolean = true): ImageVector {
    return when (condition) {
        WeatherCondition.ClearDay -> Icons.Default.WbSunny
        WeatherCondition.ClearNight -> Icons.Default.DarkMode
        WeatherCondition.PartlyCloudyDay -> Icons.Default.WbSunny
        WeatherCondition.PartlyCloudyNight -> Icons.Default.NightsStay
        WeatherCondition.Cloudy -> Icons.Default.Cloud
        WeatherCondition.Fog, WeatherCondition.Haze -> Icons.Default.Cloud
        WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.HeavyRain -> Icons.Default.WaterDrop
        WeatherCondition.Thunderstorm -> Icons.Default.Thunderstorm
        WeatherCondition.Snow, WeatherCondition.Sleet, WeatherCondition.FreezingRain -> Icons.Default.WaterDrop
        WeatherCondition.Windy -> Icons.Default.Air
        WeatherCondition.Unknown -> if (isDaytime) Icons.Default.WbSunny else Icons.Default.NightsStay
    }
}
