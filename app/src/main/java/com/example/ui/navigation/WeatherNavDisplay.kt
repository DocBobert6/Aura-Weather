package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.domain.model.WeatherCondition
import com.example.ui.RadarLayer
import com.example.ui.WeatherViewModel
import com.example.ui.airpollen.AirPollenScreen
import com.example.ui.alerts.AlertsScreen
import com.example.ui.designsystem.WeatherSky
import com.example.ui.home.HomeScreen
import com.example.ui.radar.RadarScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.sunmoon.SunMoonScreen

enum class ScreenDestination(val label: String, val icon: ImageVector) {
    Home("Forecast", Icons.Default.WbSunny),
    Radar("Radar", Icons.Default.Map),
    Alerts("Alerts", Icons.Default.Warning),
    AirSky("Air & Sky", Icons.Default.Air),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun WeatherNavDisplay(
    viewModel: WeatherViewModel,
    weatherState: FullWeatherState?,
    isRefreshing: Boolean,
    savedPlaces: List<Place>,
    useMetric: Boolean,
    paletteMode: String,
    tomorrowApiKey: String,
    selectedRadarLayer: RadarLayer,
    currentRadarFrameIndex: Int,
    isRadarPlaying: Boolean,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(ScreenDestination.Home) }

    val activeAlertsCount = weatherState?.activeAlerts?.size ?: 0
    val condition = weatherState?.current?.condition ?: WeatherCondition.ClearDay
    val isDaytime = weatherState?.solar?.isDaytime ?: true

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // Full-screen dynamic atmospheric sky
            WeatherSky(
                condition = condition,
                isDaytime = isDaytime,
                modifier = Modifier.fillMaxSize()
            )

            if (isWideScreen) {
                // Wide screens (Tablets, Foldables unfolded): NavigationRail layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    NavigationRail(
                        containerColor = Color(0xFF0F172A).copy(alpha = 0.85f),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        ScreenDestination.entries.forEach { destination ->
                            val isSelected = destination == currentDestination
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    NavIconWithBadge(
                                        destination = destination,
                                        activeAlertsCount = activeAlertsCount,
                                        isSelected = isSelected
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.testTag("nav_rail_${destination.name.lowercase()}")
                            )
                        }
                    }

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
                            savedPlaces = savedPlaces,
                            useMetric = useMetric,
                            paletteMode = paletteMode,
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
                // Compact screens (Standard phones): NavigationBar layout
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF0F172A).copy(alpha = 0.90f),
                            contentColor = Color.White,
                            tonalElevation = 4.dp
                        ) {
                            ScreenDestination.entries.forEach { destination ->
                                val isSelected = destination == currentDestination
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        NavIconWithBadge(
                                            destination = destination,
                                            activeAlertsCount = activeAlertsCount,
                                            isSelected = isSelected
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.testTag("nav_bar_${destination.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ScreenContent(
                            destination = currentDestination,
                            viewModel = viewModel,
                            weatherState = weatherState,
                            isRefreshing = isRefreshing,
                            savedPlaces = savedPlaces,
                            useMetric = useMetric,
                            paletteMode = paletteMode,
                            tomorrowApiKey = tomorrowApiKey,
                            selectedRadarLayer = selectedRadarLayer,
                            currentRadarFrameIndex = currentRadarFrameIndex,
                            isRadarPlaying = isRadarPlaying,
                            onNavigate = { currentDestination = it },
                            onEnterPiP = onEnterPiP
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIconWithBadge(
    destination: ScreenDestination,
    activeAlertsCount: Int,
    isSelected: Boolean
) {
    if (destination == ScreenDestination.Alerts && activeAlertsCount > 0) {
        BadgedBox(badge = {
            Badge(containerColor = Color(0xFFD32F2F)) {
                Text("$activeAlertsCount")
            }
        }) {
            Icon(imageVector = destination.icon, contentDescription = destination.label)
        }
    } else {
        Icon(imageVector = destination.icon, contentDescription = destination.label)
    }
}

@Composable
private fun ScreenContent(
    destination: ScreenDestination,
    viewModel: WeatherViewModel,
    weatherState: FullWeatherState?,
    isRefreshing: Boolean,
    savedPlaces: List<Place>,
    useMetric: Boolean,
    paletteMode: String,
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
            savedPlaces = savedPlaces,
            unitFormatter = viewModel.getUnitFormatter(),
            onRefresh = { viewModel.refresh() },
            onSelectPlace = { viewModel.selectPlace(it) },
            onUseCurrentLocation = { viewModel.useCurrentLocation() },
            onNavigateToAlerts = { onNavigate(ScreenDestination.Alerts) },
            onNavigateToAirPollen = { onNavigate(ScreenDestination.AirSky) },
            onNavigateToSunMoon = { onNavigate(ScreenDestination.AirSky) }
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
            onViewOnRadar = { onNavigate(ScreenDestination.Radar) }
        )

        ScreenDestination.AirSky -> {
            // Tab switcher or composite view for Air & Pollen and Sun & Moon
            AirSkyCompositeView(
                weatherState = weatherState,
                onNavigateToSettings = { onNavigate(ScreenDestination.Settings) }
            )
        }

        ScreenDestination.Settings -> SettingsScreen(
            useMetric = useMetric,
            paletteMode = paletteMode,
            tomorrowApiKey = tomorrowApiKey,
            savedPlaces = savedPlaces,
            selectedPlaceId = weatherState?.place?.id,
            onSetUseMetric = { viewModel.setUseMetric(it) },
            onSetPaletteMode = { viewModel.setPaletteMode(it) },
            onSaveTomorrowApiKey = { viewModel.setTomorrowApiKey(it) },
            onSelectPlace = { viewModel.selectPlace(it) },
            onAddPlace = { name, state, lat, lon -> viewModel.addPlace(name, state, lat, lon) }
        )
    }
}

@Composable
private fun AirSkyCompositeView(
    weatherState: FullWeatherState?,
    onNavigateToSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Air & Pollen, 1: Sun & Moon

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Air & Pollen", "Sun & Moon").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                androidx.compose.material3.Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E293B).copy(alpha = 0.8f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = index }
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (isSelected) Color(0xFF0D253F) else Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (selectedTab == 0) {
            AirPollenScreen(
                airQuality = weatherState?.airQuality,
                pollen = weatherState?.pollen,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            SunMoonScreen(
                solar = weatherState?.solar,
                lunar = weatherState?.lunar
            )
        }
    }
}
