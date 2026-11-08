package com.example.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.domain.model.WeatherCondition
import com.example.ui.RadarLayer
import com.example.ui.WeatherViewModel
import com.example.ui.airpollen.AirPollenScreen
import com.example.ui.alerts.AlertsScreen
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraMotion
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.AuraSky
import com.example.ui.designsystem.BackdropBlurState
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface
import com.example.ui.designsystem.LocalBackdropBlur
import com.example.ui.designsystem.LocalSkyHeaderColor
import com.example.ui.designsystem.NavPillItem
import com.example.ui.designsystem.PillSegmentedControl
import com.example.ui.designsystem.ProgressiveEdge
import com.example.ui.designsystem.WeatherSky
import com.example.ui.designsystem.rememberBackdropBlurEnabled
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
    AirSky("Air & Sky", Icons.Default.Air),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun WeatherNavDisplay(
    viewModel: WeatherViewModel,
    weatherState: FullWeatherState?,
    isRefreshing: Boolean,
    selectedPlace: Place?,
    loadError: String?,
    savedPlaces: List<Place>,
    useMetric: Boolean,
    paletteMode: String,
    themeMode: String,
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
    val isExpressive = paletteMode == "expressive"

    // Hide-on-scroll for the floating phone bar: it hides on a sustained
    // downward scroll and springs back on any upward scroll. It never hides
    // while the Home alert banner is visible or on the Alerts screen.
    val canHideBar by rememberUpdatedState(
        !(currentDestination == ScreenDestination.Home && activeAlertsCount > 0) &&
            currentDestination != ScreenDestination.Alerts
    )
    var barHidden by remember { mutableStateOf(false) }
    // Sky parallax (2.4a): total scrolled distance, 0..256px. The mesh
    // shifts a quarter of it, so the sky moves slower than the content.
    // Accumulated for every nested scroll, not only when the bar may hide.
    var scrollAccumPx by remember { mutableStateOf(0f) }
    val nestedScrollConnection = remember {
        var downAccum = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scrollAccumPx = (scrollAccumPx - available.y).coerceIn(0f, 256f)
                if (!canHideBar) {
                    barHidden = false
                    downAccum = 0f
                    return Offset.Zero
                }
                if (available.y < 0f) {
                    downAccum += -available.y
                    if (downAccum > 96f) barHidden = true
                } else if (available.y > 0f) {
                    downAccum = 0f
                    barHidden = false
                }
                return Offset.Zero
            }
        }
    }
    val density = LocalDensity.current
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val hideOffsetPx = with(density) {
        (AuraBars.barHeight + AuraBars.bottomMargin).toPx() +
            WindowInsets.safeDrawing.getBottom(this).toFloat()
    }
    val barOffsetY by animateFloatAsState(
        targetValue = if (barHidden) hideOffsetPx else 0f,
        animationSpec = AuraMotion.spec(isExpressive) { bar },
        label = "navBarOffset"
    )

    // Backdrop blur (2.4b): the sky is recorded into a graphics layer so
    // the floating glass can sample a real blurred copy of it. Off on
    // low-RAM devices and in battery saver (tint-only fallback).
    val skyLayer = rememberGraphicsLayer()
    val blurEnabled = rememberBackdropBlurEnabled()
    val backdropBlurState = remember(skyLayer, blurEnabled) {
        BackdropBlurState(skyLayer, blurEnabled)
    }
    // The condition's deep sky tone, for the bars' ambient shadows (2.4b).
    val shadowTint = remember(condition, isDaytime) {
        AuraSky.gradientColors(condition, isDaytime).last()
    }
    // Headings that sit directly on the sky take their colour from the
    // sky's own luminance (light text on a dark sky), not onSurface.
    val skyHeaderColor = remember(condition, isDaytime) {
        AuraSky.headerTextColor(AuraSky.gradientColors(condition, isDaytime))
    }

    CompositionLocalProvider(
        LocalBackdropBlur provides backdropBlurState,
        LocalSkyHeaderColor provides skyHeaderColor
    ) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // Full-screen mesh sky, recorded into the backdrop-blur layer.
            // Only the sky is recorded: the screens' own glass sits over
            // the sky, and Radar's glass opts out (its backdrop is the
            // map, not the sky).
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        backdropBlurState.skyOriginInWindow = coords.positionInWindow()
                    }
                    .drawWithContent {
                        backdropBlurState.record(density, layoutDirection, size.toIntSize()) {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(backdropBlurState.skyLayer)
                    }
            ) {
                WeatherSky(
                    condition = condition,
                    isDaytime = isDaytime,
                    parallaxPx = scrollAccumPx,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isWideScreen) {
                // Wide screens (tablets, foldables unfolded): floating
                // vertical pill rail, detached from the edges and centred,
                // instead of a full-height opaque strip.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .width(AuraBars.railWidth + 16.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingNavRail(
                            currentDestination = currentDestination,
                            activeAlertsCount = activeAlertsCount,
                            isExpressive = isExpressive,
                            shadowColor = shadowTint,
                            onSelect = { currentDestination = it }
                        )
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
                            selectedPlace = selectedPlace,
                            loadError = loadError,
                            savedPlaces = savedPlaces,
                            useMetric = useMetric,
                            paletteMode = paletteMode,
                            themeMode = themeMode,
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
                // Compact screens (standard phones): content scrolls
                // edge-to-edge under a floating pill bar.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                            paletteMode = paletteMode,
                            themeMode = themeMode,
                            tomorrowApiKey = tomorrowApiKey,
                            selectedRadarLayer = selectedRadarLayer,
                            currentRadarFrameIndex = currentRadarFrameIndex,
                            isRadarPlaying = isRadarPlaying,
                            onNavigate = { currentDestination = it },
                            onEnterPiP = onEnterPiP
                        )
                    }

                    FloatingNavBar(
                        currentDestination = currentDestination,
                        activeAlertsCount = activeAlertsCount,
                        isExpressive = isExpressive,
                        shadowColor = shadowTint,
                        onSelect = { currentDestination = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = AuraBars.sideMargin,
                                end = AuraBars.sideMargin,
                                bottom = AuraBars.bottomMargin + bottomInset
                            )
                            .graphicsLayer { translationY = barOffsetY }
                    )
                }
            }
            // Top fade: scrolled cards fade into the sky under the status
            // bar instead of hard-clipping at the screen's top edge. Drawn
            // after the content (so it sits over the scrolling cards) and
            // above the top bar pill's own area.
            val skyTopColor = remember(condition, isDaytime) {
                AuraSky.gradientColors(condition, isDaytime).first()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(skyTopColor, skyTopColor.copy(alpha = 0f))
                        )
                    )
            )
        }
    }
    }
}

/**
 * The floating phone navigation bar: one glass pill, 16dp side margins,
 * 12dp above the bottom gesture area, ~64dp tall, with the 5 destinations
 * as icon + label items inside it. The Alerts badge sits on the pill.
 */
@Composable
private fun FloatingNavBar(
    currentDestination: ScreenDestination,
    activeAlertsCount: Int,
    isExpressive: Boolean,
    shadowColor: Color?,
    onSelect: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        level = GlassLevel.Bar,
        shape = AuraShapes.pill,
        progressive = true,
        progressiveEdge = ProgressiveEdge.Top,
        shadowColor = shadowColor,
        modifier = modifier.height(AuraBars.barHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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
                    isExpressive = isExpressive,
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
 * The floating wide navigation rail: a vertical glass pill, detached from
 * the left edge and vertically centred, with the same 5 destinations and
 * the same badge as the phone bar.
 */
@Composable
private fun FloatingNavRail(
    currentDestination: ScreenDestination,
    activeAlertsCount: Int,
    isExpressive: Boolean,
    shadowColor: Color?,
    onSelect: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        level = GlassLevel.Bar,
        shape = AuraShapes.pill,
        shadowColor = shadowColor,
        modifier = modifier.width(AuraBars.railWidth)
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
                    isExpressive = isExpressive,
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nav_rail_${destination.name.lowercase()}")
                )
            }
        }
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
    paletteMode: String,
    themeMode: String,
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
            paletteMode = paletteMode,
            onRefresh = { viewModel.refresh() },
            onSelectPlace = { viewModel.selectPlace(it) },
            onUseCurrentLocation = { viewModel.useCurrentLocation() },
            onNavigateToAlerts = { onNavigate(ScreenDestination.Alerts) },
            onNavigateToAirPollen = { onNavigate(ScreenDestination.AirSky) },
            onNavigateToSunMoon = { onNavigate(ScreenDestination.AirSky) },
            onNavigateToSettings = { onNavigate(ScreenDestination.Settings) }
        )

        ScreenDestination.Radar -> CompositionLocalProvider(LocalBackdropBlur provides null) {
            // Radar's backdrop is the map, not the sky: its glass falls
            // back to the stronger tint-only scrim (2.4b).
            RadarScreen(
                state = weatherState,
                selectedLayer = selectedRadarLayer,
                currentFrameIndex = currentRadarFrameIndex,
                isPlaying = isRadarPlaying,
                onSelectLayer = { viewModel.setRadarLayer(it) },
                onTogglePlay = { viewModel.toggleRadarPlay() },
                onScrubFrame = { viewModel.setRadarFrameIndex(it) },
                onEnterPiP = onEnterPiP,
                paletteMode = paletteMode
            )
        }

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
                paletteMode = paletteMode,
                onNavigateToSettings = { onNavigate(ScreenDestination.Settings) }
            )
        }

        ScreenDestination.Settings -> SettingsScreen(
            useMetric = useMetric,
            paletteMode = paletteMode,
            themeMode = themeMode,
            tomorrowApiKey = tomorrowApiKey,
            savedPlaces = savedPlaces,
            selectedPlaceId = selectedPlace?.id,
            onSetUseMetric = { viewModel.setUseMetric(it) },
            onSetPaletteMode = { viewModel.setPaletteMode(it) },
            onSetThemeMode = { viewModel.setThemeMode(it) },
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
    paletteMode: String,
    onNavigateToSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Air & Pollen, 1: Sun & Moon

    Column(modifier = Modifier.fillMaxSize()) {
        PillSegmentedControl(
            options = listOf("Air & Pollen", "Sun & Moon"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            isExpressive = paletteMode == "expressive",
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
