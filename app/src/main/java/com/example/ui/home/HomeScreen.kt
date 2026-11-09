package com.example.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import com.example.ui.designsystem.GlassIconButton
import com.example.domain.model.DailyPoint
import com.example.domain.model.FullWeatherState
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
import com.example.domain.model.RegionalPoint
import com.example.domain.model.WeatherCondition
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
    tickerVisible: Boolean = false,
    regionalPoints: List<RegionalPoint> = emptyList(),
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

    val listState = rememberLazyListState()

    // Freshness: no fetch yet means no freshness text at all — never the
    // "Updated just now" the empty state used to show for nothing fetched.
    val fetchedAt = state?.fetchedAt
    val freshnessText: String? = when {
        fetchedAt == null -> null
        isRefreshing -> "Refreshing..."
        else -> {
            val diffMins = (Instant.now().epochSecond - fetchedAt.epochSecond) / 60
            when {
                diffMins <= 1 -> "Updated just now"
                diffMins < 60 -> "Updated ${diffMins}m ago"
                else -> "Updated ${diffMins / 60}h ago"
            }
        }
    }
    val freshnessDetail: String? = when {
        freshnessText == null -> null
        state?.isStale == true -> "Cached • $freshnessText"
        else -> freshnessText
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_scroll"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // The classic header lives in the shell now, in normal flow
            // above this list — nothing overlays it — so the list just
            // needs its usual 16dp of breathing room at the top.
            top = 16.dp,
            bottom = AuraBars.contentBottomPadding(tickerVisible)
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Place row: the place pill (with its menu) + location /
        // refresh buttons. This is what the old floating top bar carried;
        // it lives in the list now, under the shell's classic header,
        // so no function is lost and nothing overlays the content.
        item {
            PlaceRow(
                place = state?.place ?: selectedPlace,
                savedPlaces = savedPlaces,
                isRefreshing = isRefreshing,
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
                val topAlert = state!!.activeAlerts.maxByOrNull { AuraColors.severityRank(it.severity) }
                    ?: state.activeAlerts.first()
                // Event + end time, not the raw headline: the headline
                // truncates to "issued Novemb…" and hides the useful part.
                val bannerZone = remember(state?.place?.zoneId) {
                    runCatching { ZoneId.of(state?.place?.zoneId ?: "") }.getOrDefault(ZoneId.systemDefault())
                }
                val bannerTimeFormatter = remember(bannerZone) {
                    DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(bannerZone)
                }
                val bannerEnd = topAlert.ends ?: topAlert.expires
                ActiveAlertBanner(
                    alertTitle = "${topAlert.event} - Ends ${bannerTimeFormatter.format(bannerEnd)}",
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
                unitFormatter = unitFormatter,
                // The freshness detail lives here, under the hi/lo chips.
                // It used to ride in a second top-bar pill, but place +
                // freshness + two buttons cannot fit a 360dp row — the
                // place name truncated to "Current..." — so the top bar is
                // the place pill + buttons only.
                freshnessText = freshnessDetail
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

        // 8. Regional map forecast (R5): the current place + neighbours
        // on a static map. Hidden entirely when the neighbour fetch
        // produced nothing — never an empty panel.
        if (regionalPoints.isNotEmpty()) {
            item {
                RegionalMapForecast(
                    state = state,
                    regionalPoints = regionalPoints,
                    unitFormatter = unitFormatter
                )
            }
        }

        // 9. 2x2 Details Grid (Wind, Humidity/Dewpoint, Pressure, Visibility)
        item {
            WeatherDetailsGrid(
                state = state,
                unitFormatter = unitFormatter
            )
        }

        // 10. Air Quality & Pollen Summary Card
        item {
            AirAndPollenSummaryCard(
                state = state,
                onClick = onNavigateToAirPollen
            )
        }

        // 11. Sun & Moon Summary Card
        item {
            SunAndMoonSummaryCard(
                state = state,
                onClick = onNavigateToSunMoon
            )
        }
        }
    }

    }
}

@Composable
private fun NoPlaceCard(
    onUseCurrentLocation: () -> Unit,
    onAddPlace: () -> Unit
) {
    RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = RetroTokens.Yellow,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "CHOOSE A PLACE TO SEE ITS WEATHER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aura Weather works anywhere. Use your current location, or add a place in Settings - no place is chosen for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextSecondary
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
                Text("USE CURRENT LOCATION")
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onAddPlace,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ADD A PLACE IN SETTINGS", color = RetroTokens.TextPrimary)
            }
        }
    }
}

@Composable
private fun LoadErrorCard(message: String, onRetry: () -> Unit) {
    RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AuraColors.Warning,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "COULDN'T LOAD THE WEATHER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextSecondary
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
                Text("TRY AGAIN")
            }
        }
    }
}

/**
 * The place row at the top of the Home list: a place pill (name +
 * chevron, opens the place menu) and plain retro location / refresh
 * buttons. This is what the old floating top bar carried; it lives in
 * the list now, under the shell's classic header, so the place, location
 * and refresh functions are all still reachable and nothing overlays
 * the content. The freshness text already lives on the hero card.
 */
@Composable
private fun PlaceRow(
    place: Place?,
    savedPlaces: List<Place>,
    isRefreshing: Boolean,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onSelectPlace: (String) -> Unit,
    onLocationClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeName = place?.let { "${it.name}${if (it.stateCode.isNotBlank()) ", ${it.stateCode}" else ""}" }
        ?: "No place selected"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Place pill (with the place menu anchored to it)
        Box(modifier = Modifier.weight(1f)) {
            RetroPanel(
                shape = AuraShapes.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(AuraShapes.pill)
                    .clickable(onClick = onToggleMenu)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = RetroTokens.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = placeName.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select location",
                        tint = RetroTokens.TextSecondary
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onToggleMenu,
                containerColor = RetroTokens.Panel
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
                                    color = if (isCurrent) {
                                        RetroTokens.Yellow
                                    } else {
                                        RetroTokens.TextPrimary
                                    }
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = RetroTokens.Yellow,
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

        GlassIconButton(
            onClick = onLocationClick
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Use GPS Location",
                tint = RetroTokens.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        GlassIconButton(
            onClick = onRefresh,
            enabled = !isRefreshing
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RetroTokens.Yellow
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = RetroTokens.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
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
    // Solid pill-banner with a severity pill: the severity colour lives
    // only in the pill; the banner itself is a solid navy panel.
    RetroPanel(
        shape = AuraShapes.pill,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AuraColors.severityColor(severity),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = AuraColors.severityLabel(severity) +
                        if (count > 1) " - $count" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = alertTitle.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "VIEW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun HeroWeatherCard(
    state: FullWeatherState?,
    unitFormatter: UnitFormatter,
    freshnessText: String? = null
) {
    val tempFormatted = unitFormatter.formatTemperature(state?.current?.tempC)
    val feelsLikeFormatted = unitFormatter.formatTemperature(state?.current?.feelsLikeC)
    val condition = state?.current?.condition ?: WeatherCondition.ClearDay
    // A blank description (an NWS observation can carry an empty
    // textDescription) must fall back to the condition name — a blank
    // string is not null, so plain ?: never fired and the hero pill
    // rendered as an empty grey circle.
    val description = state?.current?.description?.ifBlank { null } ?: condition.displayName

    val todayDaily = state?.daily7d?.firstOrNull()
    val highFormatted = unitFormatter.formatTemperature(todayDaily?.highC ?: state?.current?.tempC)
    // No invented low: when the daily low is missing the UI shows "--".
    val lowFormatted = unitFormatter.formatTemperature(todayDaily?.lowC)

    RetroPanel(
        shape = RetroShapes.large,
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
                tint = RetroTokens.Yellow,
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tempFormatted.uppercase(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Condition as plain white text, not a pill.
            Text(
                text = description.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Feels-like and hi/lo as one plain line.
            Text(
                text = "FEELS LIKE $feelsLikeFormatted   H: $highFormatted L: $lowFormatted",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextPrimary
            )

            // Freshness detail lives here, under the hi/lo line.
            if (freshnessText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = freshnessText.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroTokens.TextSecondary
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
    RetroPanel(shape = RetroShapes.medium, 
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
                        tint = AuraColors.Water,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXT 60 MINUTES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.Yellow
                    )
                }

                // Plain text, not a glass pill.
                Text(
                    text = "EVERY 15 MIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroTokens.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = summarySentence.uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = RetroTokens.TextPrimary
            )

            // No rain in the next hour: the chart is an empty ~300px box
            // with flat 2dp bars. Collapse to the summary line alone.
            if (minutePoints.any { it.mmPerHour > 0.0 }) {
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

                    val barColor = AuraColors.precipColor(point.mmPerHour)

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
                Text(text = "NOW", style = MaterialTheme.typography.labelSmall, color = RetroTokens.TextSecondary)
                Text(text = "15M", style = MaterialTheme.typography.labelSmall, color = RetroTokens.TextSecondary)
                Text(text = "30M", style = MaterialTheme.typography.labelSmall, color = RetroTokens.TextSecondary)
                Text(text = "45M", style = MaterialTheme.typography.labelSmall, color = RetroTokens.TextSecondary)
                Text(text = "60M", style = MaterialTheme.typography.labelSmall, color = RetroTokens.TextSecondary)
            }
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

    RetroPanel(shape = RetroShapes.medium, 
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hourly_forecast_card")
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "48-HOUR FORECAST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(hourlyPoints.take(24).size) { index ->
                    val point = hourlyPoints[index]
                    // The first hour is now — label it, so the strip reads
                    // against the present instead of a redundant "9 AM".
                    val hourLabel = if (index == 0) "NOW" else timeFormatter.format(point.time).uppercase()
                    val temp = unitFormatter.formatTemperature(point.tempC)
                    val pop = point.popPct

                    // Plain cell on the navy panel: no glass, no pill
                    // highlight. "Now" is plain yellow text, not a fill.
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = hourLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) RetroTokens.Yellow else RetroTokens.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = getWeatherIcon(point.condition, isDaytime = point.isDaytime),
                            contentDescription = point.condition.displayName,
                            tint = RetroTokens.TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = temp.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RetroTokens.Yellow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Rain % whenever there is any chance (not only
                        // above 10%). 0% is clutter, so it stays hidden.
                        if (pop != null && pop > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Rain chance",
                                    tint = AuraColors.PopBlue,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "$pop%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = AuraColors.PopBlue
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

    RetroPanel(shape = RetroShapes.medium, 
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_forecast_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "7-DAY OUTLOOK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
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
                        text = dayName.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color = RetroTokens.TextPrimary,
                        modifier = Modifier.width(48.dp)
                    )

                    // The icon must agree with the rain chance: a sun icon
                    // next to a 48% chance reads as a contradiction. When the
                    // chance is high, show rain; when moderate, partly cloudy
                    // (unless the condition already says rain).
                    val displayCondition = when {
                        pop >= 50 -> WeatherCondition.Rain
                        pop >= 30 && (point.condition == WeatherCondition.ClearDay || point.condition == WeatherCondition.ClearNight) -> WeatherCondition.PartlyCloudyDay
                        else -> point.condition
                    }

                    Row(
                        modifier = Modifier.width(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getWeatherIcon(displayCondition, isDaytime = true),
                            contentDescription = displayCondition.displayName,
                            tint = RetroTokens.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (pop > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pop%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.PopBlue
                            )
                        }
                    }

                    // Condition as a text column, not a bar: the tabular
                    // form of the classic 3-day panels.
                    Text(
                        text = displayCondition.displayName.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RetroTokens.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Text(
                        text = "LO $lo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RetroTokens.TextSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(54.dp)
                    )

                    Text(
                        text = "HI $hi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RetroTokens.Yellow,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(54.dp)
                    )
                }
            }
        }
    }
}

/**
 * 16-point compass label for a wind direction in degrees (0 = N).
 * "135°" means nothing to most people; "SE" does.
 */
private fun compassPoint(deg: Double): String {
    val points = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )
    val idx = ((deg % 360.0 + 360.0) % 360.0 / 22.5).roundToInt() % 16
    return points[idx]
}

/** Pressure in hPa -> a plain descriptor. */
private fun pressureDesc(pressurePa: Double?): String? {
    val hpa = pressurePa?.div(100.0) ?: return null
    return when {
        hpa >= 1022.0 -> "High pressure"
        hpa >= 1005.0 -> "Near average"
        else -> "Low pressure"
    }
}

/** Visibility in metres -> a plain descriptor. */
private fun visibilityDesc(visibilityM: Double?): String? {
    val m = visibilityM ?: return null
    return when {
        m >= 10000.0 -> "Excellent"
        m >= 4000.0 -> "Good"
        m >= 1000.0 -> "Moderate"
        else -> "Poor"
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
    val uvIndex = state?.daily7d?.firstOrNull()?.uvIndexMax
    val cloudCover = state?.current?.cloudCoverPct

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wind Card. The sub-line carries the compass point and the
            // gust (only when there is one — "Gusts to --" is noise), and
            // the arrow is a clean upward arrow rotated to the direction,
            // not the Navigation triangle that reads as a play button.
            DetailMiniCard(
                title = "WIND",
                icon = Icons.Default.Air,
                mainValue = windFormatted,
                subValue = buildString {
                    windDir?.let { append(compassPoint(it.toDouble())) }
                    if (state?.current?.windGustMs != null) {
                        if (isNotEmpty()) append(" - ")
                        append("Gusts to ${gustFormatted.replace(" ", "\u00A0")}")
                    }
                    if (isEmpty()) append("--")
                },
                trailing = if (windDir != null) {
                    {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Wind direction ${compassPoint(windDir.toDouble())}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(windDir.toFloat())
                        )
                    }
                } else null,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // Humidity & Dew Point Card
            DetailMiniCard(
                title = "HUMIDITY",
                icon = Icons.Default.Opacity,
                mainValue = humidity,
                subValue = "Dew point $dewPoint",
                modifier = Modifier.weight(1f).fillMaxHeight()
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
                subValue = pressureDesc(state?.current?.pressurePa) ?: "--",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // Visibility Card
            DetailMiniCard(
                title = "VISIBILITY",
                icon = Icons.Default.Visibility,
                mainValue = visibilityFormatted,
                subValue = visibilityDesc(state?.current?.visibilityM) ?: "--",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // UV Index Card (today's max; the data was fetched, never shown)
            DetailMiniCard(
                title = "UV INDEX",
                icon = Icons.Default.WbSunny,
                mainValue = uvIndex?.let { "${it.roundToInt()}" } ?: "--",
                subValue = uvIndex?.let { AuraColors.uvCategory(it) } ?: "--",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // Cloud Cover Card
            DetailMiniCard(
                title = "CLOUD COVER",
                icon = Icons.Default.Cloud,
                mainValue = cloudCover?.let { "${it.roundToInt()}%" } ?: "--",
                subValue = cloudCover?.let { AuraColors.cloudCoverDesc(it) } ?: "--",
                modifier = Modifier.weight(1f).fillMaxHeight()
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
    RetroPanel(shape = RetroShapes.medium, modifier = modifier) {
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
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                trailing?.invoke()
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = mainValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Clip
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

    val aqiColor = AuraColors.aqiColor(aqi)

    RetroPanel(shape = RetroShapes.medium, 
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
                    color = RetroTokens.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (aqi != null) "AQI $aqi" else "AQI --",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                // Guidance wraps (was maxLines = 1 + Ellipsis, which cut
                // it off mid-word: "…little or no health r…").
                Text(
                    text = state?.airQuality?.healthGuidance ?: "Air quality data is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "View Air Quality details",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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

    RetroPanel(shape = RetroShapes.medium, 
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
                    color = RetroTokens.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Sunrise",
                        tint = RetroTokens.Yellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunrise,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RetroTokens.TextPrimary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Default.WbTwilight,
                        contentDescription = "Sunset",
                        tint = AuraColors.Sunset,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunset,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RetroTokens.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$moonPhase ($illumination)".uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroTokens.TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "View Sun and Moon details",
                tint = RetroTokens.TextSecondary,
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
