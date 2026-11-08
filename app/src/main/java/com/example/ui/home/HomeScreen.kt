package com.example.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.designsystem.AuraMotion
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.AuraSky
import com.example.ui.designsystem.GlassIconButton
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface
import com.example.ui.designsystem.LocalSkyHeaderColor
import com.example.ui.designsystem.ProgressiveEdge
import com.example.domain.model.DailyPoint
import com.example.domain.model.FullWeatherState
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
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
    paletteMode: String = "auto",
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

    // The condition's deep sky tone, for the top bar's ambient shadow (2.4b).
    val shadowTint = remember(state?.current?.condition, state?.solar?.isDaytime) {
        AuraSky.gradientColors(
            state?.current?.condition ?: WeatherCondition.ClearDay,
            state?.solar?.isDaytime ?: true
        ).last()
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
            // Room for the floating top bar pinned over the list, plus a
            // 16dp gap below it on startup so the bar never touches the
            // first card — the alert banner / hero sat flush against it.
            top = 72.dp,
            bottom = AuraBars.contentBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    alertTitle = "${topAlert.event} · Ends ${bannerTimeFormatter.format(bannerEnd)}",
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
                isExpressive = paletteMode == "expressive",
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

    // Floating top bar pinned over the sky: place pill + circular
    // location / refresh buttons. Safe-drawing insets are part of the
    // bar's placement (the ScreenContent container applies them above
    // this screen), so it never collides with the status bar.
    FloatingTopBar(
        place = selectedPlace ?: state?.place,
        savedPlaces = savedPlaces,
        isRefreshing = isRefreshing,
        isExpressive = paletteMode == "expressive",
        shadowColor = shadowTint,
        menuExpanded = locationMenuExpanded,
        onToggleMenu = { locationMenuExpanded = !locationMenuExpanded },
        onSelectPlace = {
            locationMenuExpanded = false
            onSelectPlace(it)
        },
        onLocationClick = {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
        onRefresh = onRefresh,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
    )
    }
}

@Composable
private fun NoPlaceCard(
    onUseCurrentLocation: () -> Unit,
    onAddPlace: () -> Unit
) {
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aura Weather works anywhere. Use your current location, or add a place in Settings — no place is chosen for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
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
                Text("Add a place in Settings", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun LoadErrorCard(message: String, onRetry: () -> Unit) {
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AuraColors.Warning,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Couldn't load the weather",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
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

/**
 * The floating top bar pinned over the sky: a place pill (name + chevron,
 * opens the place menu), a freshness pill, and circular location / refresh
 * buttons. On scroll it collapses to the place pill alone and the
 * freshness detail moves into the hero card instead.
 */
@Composable
private fun FloatingTopBar(
    place: Place?,
    savedPlaces: List<Place>,
    isRefreshing: Boolean,
    isExpressive: Boolean,
    shadowColor: Color?,
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
            GlassSurface(
                level = GlassLevel.Bar,
                shape = AuraShapes.pill,
                progressive = true,
                progressiveEdge = ProgressiveEdge.Bottom,
                shadowColor = shadowColor,
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = placeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select location",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onToggleMenu,
                containerColor = MaterialTheme.colorScheme.surface
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
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
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


        GlassIconButton(
            onClick = onLocationClick,
            isExpressive = isExpressive
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Use GPS Location",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }

        GlassIconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            isExpressive = isExpressive
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
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
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
    // Glass pill-banner with a severity pill, not a solid surface: the
    // severity colour lives only in the pill; the banner itself is glass.
    GlassSurface(
        level = GlassLevel.Card,
        shape = AuraShapes.pill,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AuraColors.severityColor(severity),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = AuraColors.severityLabel(severity) +
                        if (count > 1) " · $count" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = alertTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "VIEW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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

    GlassSurface(
        level = GlassLevel.Card,
        shape = AuraShapes.large,
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
                tint = AuraSky.celestialColor(state?.solar?.isDaytime != false),
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tempFormatted,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Condition pill: the description as a chip, not bare text.
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feels-like and hi/lo as chips.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = AuraShapes.pill
                ) {
                    Text(
                        text = "Feels like $feelsLikeFormatted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = AuraShapes.pill
                ) {
                    Text(
                        text = "H: $highFormatted   L: $lowFormatted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Freshness detail lives here, under the hi/lo chips.
            if (freshnessText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = freshnessText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, 
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = AuraShapes.pill
                ) {
                    Text(
                        text = "15-min resolution",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = summarySentence,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
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
                Text(text = "Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(text = "15m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(text = "30m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(text = "45m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(text = "60m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(
    hourlyPoints: List<HourlyPoint>,
    unitFormatter: UnitFormatter,
    isExpressive: Boolean,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("h a", Locale.US).withZone(zoneId)
    }

    // Tapping an hour selects it (pill highlight); the press squashes
    // with the press spring (2.4d). The selection is visual — it marks
    // the hour under the finger, nothing else reads it yet.
    var selectedIndex by remember { mutableIntStateOf(0) }

    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, 
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hourly_forecast_card")
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "48-HOUR FORECAST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(hourlyPoints.take(24).size) { index ->
                    val point = hourlyPoints[index]
                    val selected = index == selectedIndex
                    val hourLabel = timeFormatter.format(point.time)
                    val temp = unitFormatter.formatTemperature(point.tempC)
                    val pop = point.popPct

                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.92f else 1f,
                        animationSpec = AuraMotion.spec(isExpressive) { press },
                        label = "hour_press"
                    )

                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(AuraShapes.pill)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { selectedIndex = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = hourLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Icon(
                                imageVector = getWeatherIcon(point.condition, isDaytime = point.isDaytime),
                                contentDescription = point.condition.displayName,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = temp,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (pop != null && pop > 10) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = "Rain chance",
                                        tint = AuraColors.Water,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "$pop%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = AuraColors.Water
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
}

@Composable
private fun DailyForecastCard(
    dailyPoints: List<DailyPoint>,
    unitFormatter: UnitFormatter
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.US) }

    // The week's overall min/max, so each day's range bar is scaled to the
    // week (a 29–45° day is visibly shorter than a 43–64° day).
    val weekMin = dailyPoints.mapNotNull { it.lowC }.minOrNull()
    val weekMax = dailyPoints.mapNotNull { it.highC }.maxOrNull()

    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, 
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_forecast_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "7-DAY OUTLOOK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(60.dp)
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
                        modifier = Modifier.width(65.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getWeatherIcon(displayCondition, isDaytime = true),
                            contentDescription = displayCondition.displayName,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        if (pop > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pop%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.Water
                            )
                        }
                    }

                    Text(
                        text = lo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp)
                    )

                    // Temperature range bar, scaled to the week's min/max and
                    // coloured by the actual temperature (not a fixed gradient).
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(3.dp)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (weekMin != null && weekMax != null && weekMax > weekMin && point.lowC != null && point.highC != null) {
                            val startFrac = ((point.lowC - weekMin) / (weekMax - weekMin)).toFloat().coerceIn(0f, 1f)
                            val endFrac = ((point.highC - weekMin) / (weekMax - weekMin)).toFloat().coerceIn(0f, 1f)
                            Spacer(modifier = Modifier.weight(startFrac))
                            Box(
                                modifier = Modifier
                                    .weight((endFrac - startFrac).coerceAtLeast(0.02f))
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(AuraColors.tempColor(point.lowC), AuraColors.tempColor(point.highC))
                                        ),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.weight((1f - endFrac).coerceAtLeast(0f)))
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(AuraColors.Water, AuraColors.Warning)
                                        ),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }

                    Text(
                        text = hi,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
    val uvIndex = state?.daily7d?.firstOrNull()?.uvIndexMax
    val cloudCover = state?.current?.cloudCoverPct

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
                modifier = Modifier.weight(1f)
            )

            // Cloud Cover Card
            DetailMiniCard(
                title = "CLOUD COVER",
                icon = Icons.Default.Cloud,
                mainValue = cloudCover?.let { "${it.roundToInt()}%" } ?: "--",
                subValue = cloudCover?.let { AuraColors.cloudCoverDesc(it) } ?: "--",
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
    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, modifier = modifier) {
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

    val aqiColor = AuraColors.aqiColor(aqi)

    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, 
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
                    color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
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

    GlassSurface(level = GlassLevel.Card, shape = AuraShapes.medium, 
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
                    color = LocalSkyHeaderColor.current.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Sunrise",
                        tint = AuraSky.celestialColor(true),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunrise,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = "Sunset",
                        tint = AuraColors.Sunset,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sunset,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$moonPhase ($illumination)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "View Sun and Moon details",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
