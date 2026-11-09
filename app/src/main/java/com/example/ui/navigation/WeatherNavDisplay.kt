package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.ui.RadarLayer
import com.example.ui.WeatherViewModel
import com.example.ui.airpollen.AirPollenScreen
import com.example.ui.alerts.AlertsScreen
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.NavPillItem
import com.example.ui.designsystem.PillSegmentedControl
import com.example.ui.designsystem.RetroTokens
import com.example.ui.home.HomeScreen
import com.example.ui.radar.RadarScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.sunmoon.SunMoonScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny

enum class ScreenDestination(val label: String, val icon: ImageVector) {
    Home("Forecast", Icons.Default.WbSunny),
    Radar("Radar", Icons.Default.Map),
    Alerts("Alerts", Icons.Default.Warning),
    AirSky("Air Quality", Icons.Default.Air),
    Settings("Settings", Icons.Default.Settings)
}

/**
 * The app shell (R1 of the retro reskin). Flat navy background, the five
 * tabs in a SOLID navy bottom bar (docked, full-width, no floating
 * margins, no blur) on phones, and a solid navy side rail on wide
 * screens. Same five tabs, same order, same destinations, yellow active
 * indicator, white/yellow labels. The mesh sky, the backdrop-blur
 * recording, and the palette/theme wiring are gone with R1.
 */
@Composable
fun WeatherNavDisplay(
    viewModel: WeatherViewModel,
    weatherState: FullWeatherState?,
    isRefreshing: Boolean,
    selectedPlace: Place?,
    loadError: String?,
    savedPlaces: List<Place>,
    useMetric: Boolean,
    tomorrowApiKey: String,
    selectedRadarLayer: RadarLayer,
    currentRadarFrameIndex: Int,
    isRadarPlaying: Boolean,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(ScreenDestination.Home) }

    val activeAlertsCount = weatherState?.activeAlerts?.size ?: 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetroTokens.Background)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Wide screens (tablets, foldables unfolded): solid navy side
            // rail on the left, content filling the rest.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                SolidNavRail(
                    currentDestination = currentDestination,
                    activeAlertsCount = activeAlertsCount,
                    onSelect = { currentDestination = it }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ScreenContent(
                        destination = currentDestination,
                        viewModel = viewModel,
                        weatherState = weatherState,
                        isRefreshing = isRefreshing,
                        selectedPlace = selectedPlace,
                        loadError = loadError,
                        savedPlaces = savedPlaces,
                        useMetric = useMetric,
                        tomorrowApiKey = tomorrowApiKey,
                        selectedRadarLayer = selectedRadarLayer,
                        currentRadarFrameIndex = currentRadarFrameIndex,
                        isRadarPlaying = isRadarPlaying,
                        onNavigate = { currentDestination = it },
                        onEnterPiP = onEnterPiP
                    )
                }
            }
        } else {
            // Compact screens (standard phones): content above a docked
            // solid bottom bar. The screens reserve the bar's height in
            // their own bottom content padding.
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                ) {
                    ScreenContent(
                        destination = currentDestination,
                        viewModel = viewModel,
                        weatherState = weatherState,
                        isRefreshing = isRefreshing,
                        selectedPlace = selectedPlace,
                        loadError = loadError,
                        savedPlaces = savedPlaces,
                        useMetric = useMetric,
                        tomorrowApiKey = tomorrowApiKey,
                        selectedRadarLayer = selectedRadarLayer,
                        currentRadarFrameIndex = currentRadarFrameIndex,
                        isRadarPlaying = isRadarPlaying,
                        onNavigate = { currentDestination = it },
                        onEnterPiP = onEnterPiP
                    )
                }

                SolidNavBar(
                    currentDestination = currentDestination,
                    activeAlertsCount = activeAlertsCount,
                    onSelect = { currentDestination = it }
                )
            }
        }
    }
}

/**
 * The solid navy bottom bar: full-width, docked at the bottom, a 1dp
 * lighter-blue top edge, ~64dp tall, with the 5 destinations as icon +
 * label items inside it. The Alerts badge sits on the pill.
 */
@Composable
private fun SolidNavBar(
    currentDestination: ScreenDestination,
    activeAlertsCount: Int,
    onSelect: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RetroTokens.Panel)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RetroTokens.Border)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraBars.barHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenDestination.entries.forEach { destination ->
                NavPillItem(
                    icon = destination.icon,
                    label = destination.label,
                    selected = destination == currentDestination,
                    badgeCount = if (destination == ScreenDestination.Alerts) {
                        activeAlertsCount.takeIf { it > 0 }
                    } else {
                        null
                    },
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("nav_bar_${destination.name.lowercase()}")
                )
            }
        }
    }
}

/**
 * The solid navy wide navigation rail: a full-height strip on the left
 * with the same 5 destinations and the same badge as the phone bar.
 */
@Composable
private fun SolidNavRail(
    currentDestination: ScreenDestination,
    activeAlertsCount: Int,
    onSelect: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(AuraBars.railWidth)
            .fillMaxHeight()
            .background(RetroTokens.Panel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScreenDestination.entries.forEach { destination ->
                NavPillItem(
                    icon = destination.icon,
                    label = destination.label,
                    selected = destination == currentDestination,
                    badgeCount = if (destination == ScreenDestination.Alerts) {
                        activeAlertsCount.takeIf { it > 0 }
                    } else {
                        null
                    },
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nav_rail_${destination.name.lowercase()}")
                )
            }
        }
        // 1dp lighter-blue right edge.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(RetroTokens.Border)
        )
    }
}

@Composable
private fun ScreenContent(
    destination: ScreenDestination,
    viewModel: WeatherViewModel,
    weatherState: FullWeatherState?,
    isRefreshing: Boolean,
    selectedPlace: Place?,
    loadError: String?,
    savedPlaces: List<Place>,
    useMetric: Boolean,
    tomorrowApiKey: String,
    selectedRadarLayer: RadarLayer,
    currentRadarFrameIndex: Int,
    isRadarPlaying: Boolean,
    onNavigate: (ScreenDestination) -> Unit,
    onEnterPiP: () -> Unit
) {
    when (destination) {
        ScreenDestination.Home -> HomeScreen(
            state = weatherState,
            isRefreshing = isRefreshing,
            selectedPlace = selectedPlace,
            loadError = loadError,
            savedPlaces = savedPlaces,
            unitFormatter = viewModel.getUnitFormatter(),
            onRefresh = { viewModel.refresh() },
            onSelectPlace = { viewModel.selectPlace(it) },
            onUseCurrentLocation = { viewModel.useCurrentLocation() },
            onNavigateToAlerts = { onNavigate(ScreenDestination.Alerts) },
            onNavigateToAirPollen = { onNavigate(ScreenDestination.AirSky) },
            onNavigateToSunMoon = { onNavigate(ScreenDestination.AirSky) },
            onNavigateToSettings = { onNavigate(ScreenDestination.Settings) }
        )

        ScreenDestination.Radar -> RadarScreen(
            state = weatherState,
            selectedLayer = selectedRadarLayer,
            currentFrameIndex = currentRadarFrameIndex,
            isPlaying = isRadarPlaying,
            onSelectLayer = { viewModel.setRadarLayer(it) },
            onTogglePlay = { viewModel.toggleRadarPlay() },
            onScrubFrame = { viewModel.setRadarFrameIndex(it) },
            onEnterPiP = onEnterPiP
        )

        ScreenDestination.Alerts -> AlertsScreen(
            alerts = weatherState?.activeAlerts ?: emptyList(),
            fetchedAt = weatherState?.fetchedAt,
            isStale = weatherState?.isStale ?: false,
            placeName = selectedPlace?.name,
            zoneId = runCatching { java.time.ZoneId.of(weatherState?.place?.zoneId ?: "") }
                .getOrDefault(java.time.ZoneId.systemDefault()),
            onViewOnRadar = { onNavigate(ScreenDestination.Radar) }
        )

        ScreenDestination.AirSky -> {
            // Tab switcher or composite view for Air & Pollen and Sun & Moon
            AirSkyCompositeView(
                weatherState = weatherState,
                selectedPlace = selectedPlace,
                onNavigateToSettings = { onNavigate(ScreenDestination.Settings) }
            )
        }

        ScreenDestination.Settings -> SettingsScreen(
            useMetric = useMetric,
            tomorrowApiKey = tomorrowApiKey,
            savedPlaces = savedPlaces,
            selectedPlaceId = selectedPlace?.id,
            onSetUseMetric = { viewModel.setUseMetric(it) },
            onSaveTomorrowApiKey = { viewModel.setTomorrowApiKey(it) },
            onSelectPlace = { viewModel.selectPlace(it) },
            onAddPlace = { name, state, lat, lon -> viewModel.addPlace(name, state, lat, lon) }
        )
    }
}

@Composable
private fun AirSkyCompositeView(
    weatherState: FullWeatherState?,
    selectedPlace: Place?,
    onNavigateToSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Air & Pollen, 1: Sun & Moon

    Column(modifier = Modifier.fillMaxSize()) {
        PillSegmentedControl(
            options = listOf("Air & Pollen", "Sun & Moon"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (selectedTab == 0) {
            AirPollenScreen(
                airQuality = weatherState?.airQuality,
                pollen = weatherState?.pollen,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            SunMoonScreen(
                solar = weatherState?.solar,
                lunar = weatherState?.lunar,
                zoneId = weatherState?.place?.zoneId,
                placeName = selectedPlace?.name
            )
        }
    }
}
