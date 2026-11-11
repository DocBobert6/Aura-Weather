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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.data.mapper.visibilityDescriptor
import com.example.data.mapper.RadarRange
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.domain.model.RegionalPoint
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import com.example.core.UnitFormatter
import com.example.ui.designsystem.RetroShapes
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    pirateWeatherApiKey: String,
    pirateWeatherWorking: Boolean,
    pirateWeatherAttempted: Boolean,

    selectedRadarLayer: RadarLayer,
    selectedRadarRange: RadarRange,
    currentRadarFrameIndex: Int,
    isRadarPlaying: Boolean,
    regionalPoints: List<RegionalPoint> = emptyList(),
    musicEnabled: Boolean = false,
    onToggleMusic: () -> Unit = {},
    alertsEnabled: Boolean = false,
    onSetAlertsEnabled: (Boolean) -> Unit = {},
    rainAlertsEnabled: Boolean = false,
    onSetRainAlertsEnabled: (Boolean) -> Unit = {},
    pendingDestination: ScreenDestination? = null,
    highlightAlertId: String? = null,
    onPendingDestinationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(ScreenDestination.Home) }

    // Remembered, not recreated per recomposition: the 6-second ticker
    // rotation recomposes this shell, and a fresh lambda here would
    // force every screen below it to recompose along with it. The state
    // holder behind currentDestination never changes identity, so this
    // stays valid for the shell's lifetime.
    val onNavigate = remember { { destination: ScreenDestination -> currentDestination = destination } }

    // 3.1 notification deep link: a tap switches the shell to Alerts
    // (and highlights the notified alert) exactly once.
    LaunchedEffect(pendingDestination) {
        if (pendingDestination != null) {
            currentDestination = pendingDestination
            onPendingDestinationConsumed()
        }
    }

    val activeAlertsCount = weatherState?.activeAlerts?.size ?: 0

    // Ticker facts come from the loaded weather only; a missing fact is
    // skipped, never invented — so an empty list means "no ticker bar".
    val unitFormatter = viewModel.getUnitFormatter()
    val tickerItems = remember(weatherState, useMetric) {
        buildTickerItems(weatherState, unitFormatter)
    }
    val tickerVisible = tickerItems.isNotEmpty()
    var tickerIndex by remember(tickerItems) { mutableStateOf(0) }
    LaunchedEffect(tickerItems) {
        while (tickerItems.size > 1) {
            delay(6000)
            tickerIndex = (tickerIndex + 1) % tickerItems.size
        }
    }
    val tickerText = tickerItems.getOrNull(tickerIndex) ?: tickerItems.firstOrNull()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetroTokens.Background)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        // The classic header sits at the top of the shell, above every
        // screen, in normal flow — it never overlays content. The
        // safe-drawing insets live on this column now (they used to live
        // on each branch's container), so the header clears the status bar.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            RetroHeader(
                destination = currentDestination,
                place = selectedPlace ?: weatherState?.place,
                musicEnabled = musicEnabled,
                onToggleMusic = onToggleMusic
            )

            if (isWideScreen) {
            // Wide screens (tablets, foldables unfolded): solid navy side
            // rail on the left, content filling the rest.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SolidNavRail(
                    currentDestination = currentDestination,
                    activeAlertsCount = activeAlertsCount,
                    onSelect = { currentDestination = it }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                            pirateWeatherApiKey = pirateWeatherApiKey,
                            pirateWeatherWorking = pirateWeatherWorking,
                            pirateWeatherAttempted = pirateWeatherAttempted,

                            selectedRadarLayer = selectedRadarLayer,
                            selectedRadarRange = selectedRadarRange,
                            currentRadarFrameIndex = currentRadarFrameIndex,
                            isRadarPlaying = isRadarPlaying,
                            onNavigate = onNavigate,
                            tickerVisible = tickerVisible,
                            regionalPoints = regionalPoints,
                            alertsEnabled = alertsEnabled,
                            onSetAlertsEnabled = onSetAlertsEnabled,
                            rainAlertsEnabled = rainAlertsEnabled,
                            onSetRainAlertsEnabled = onSetRainAlertsEnabled,
                            highlightAlertId = highlightAlertId
                        )
                    }

                    if (tickerVisible && tickerText != null) {
                        RetroTicker(text = tickerText)
                    }
                }
            }
        } else {
            // Compact screens (standard phones): content above the ticker
            // (when there is one) and a docked solid bottom bar. The
            // screens reserve the bar's height — plus the ticker's when
            // it is visible — in their own bottom content padding.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                    pirateWeatherApiKey = pirateWeatherApiKey,
                    pirateWeatherWorking = pirateWeatherWorking,
                    pirateWeatherAttempted = pirateWeatherAttempted,

                    selectedRadarLayer = selectedRadarLayer,
                    selectedRadarRange = selectedRadarRange,
                    currentRadarFrameIndex = currentRadarFrameIndex,
                    isRadarPlaying = isRadarPlaying,
                    onNavigate = onNavigate,
                    tickerVisible = tickerVisible,
                    regionalPoints = regionalPoints,
                    alertsEnabled = alertsEnabled,
                    onSetAlertsEnabled = onSetAlertsEnabled,
                    rainAlertsEnabled = rainAlertsEnabled,
                    onSetRainAlertsEnabled = onSetRainAlertsEnabled,
                    highlightAlertId = highlightAlertId
                )
            }

            if (tickerVisible && tickerText != null) {
                RetroTicker(text = tickerText)
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

/**
 * The classic header (R3): a red/orange gradient band across the top,
 * the "Aura Weather" logo box on the left, the segment title in yellow
 * in the middle, and a live clock on the right. It sits in normal flow
 * at the top of the shell — never an overlay — so content is never
 * hidden under it. Our own box with our own name: the real Weather
 * Channel logo is a trademark and is not reproduced.
 */
@Composable
private fun RetroHeader(
    destination: ScreenDestination,
    place: Place?,
    musicEnabled: Boolean = false,
    onToggleMusic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val zoneId = remember(place?.zoneId) {
        runCatching { ZoneId.of(place?.zoneId ?: "") }.getOrDefault(ZoneId.systemDefault())
    }
    // The title follows the current tab only. The place name is NOT
    // appended here — the PlaceRow pill below the header already shows
    // it, and appending it overflowed the title (the em dash and the
    // ellipsis are empty glyphs in Star4000, so it truncated hard).
    val title = destination.label.uppercase()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AuraBars.headerHeight)
            .background(
                Brush.horizontalGradient(
                    listOf(RetroTokens.HeaderRed, RetroTokens.HeaderOrange)
                )
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo box: a rounded blue square with our name in white.
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(RetroTokens.PanelLight, RetroShapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AURA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.TextPrimary
                )
                Text(
                    text = "WEATHER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.TextPrimary
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = RetroTokens.Yellow,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp)
        )

        // Background music toggle: Home only. It lived in the PlaceRow
        // and squeezed the place pill; here it costs the pill nothing.
        // Yellow note while playing, dimmed crossed note while off.
        if (destination == ScreenDestination.Home) {
            IconButton(onClick = onToggleMusic) {
                Icon(
                    imageVector = if (musicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                    contentDescription = if (musicEnabled) "Turn music off" else "Turn music on",
                    tint = if (musicEnabled) RetroTokens.Yellow else RetroTokens.TextPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        RetroClock(zoneId = zoneId)
    }
}

/**
 * A live clock in the stills' shape: time on one line, day/date on the
 * next, in the place's zone (or the device zone when no place is loaded).
 * Ticks every second; the loop is cancelled when the header leaves.
 */
@Composable
private fun RetroClock(
    zoneId: ZoneId,
    modifier: Modifier = Modifier
) {
    var now by remember(zoneId) { mutableStateOf(ZonedDateTime.now(zoneId)) }
    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US).withZone(zoneId)
    }
    val dateFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("EEE MMM d", Locale.US).withZone(zoneId)
    }
    LaunchedEffect(zoneId) {
        while (true) {
            delay(1000)
            now = ZonedDateTime.now(zoneId)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = timeFormatter.format(now).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = RetroTokens.TextPrimary
        )
        Text(
            text = dateFormatter.format(now).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = RetroTokens.TextPrimary
        )
    }
}

/**
 * The bottom ticker bar (R3): solid ticker blue, white all-caps text,
 * one fact at a time. The rotation lives in [WeatherNavDisplay]; this is
 * just the bar. Hidden entirely when there is nothing to show.
 */
@Composable
private fun RetroTicker(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AuraBars.tickerHeight)
            .background(RetroTokens.TickerBlue),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RetroTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/**
 * Ticker facts from the loaded weather only. A missing fact is skipped —
 * never "--", never an invented value. There is no ceiling field in the
 * data model, so there is deliberately no ceiling item.
 */
private fun buildTickerItems(
    state: FullWeatherState?,
    unitFormatter: UnitFormatter
): List<String> {
    val current = state?.current ?: return emptyList()
    val items = mutableListOf<String>()
    current.visibilityM?.let {
        val desc = visibilityDescriptor(it)?.let { d -> " ($d)" } ?: ""
        items.add("VISIBILITY: ${unitFormatter.formatVisibility(it).uppercase()}$desc")
    }
    current.pressurePa?.let {
        items.add("BAROMETRIC PRESSURE: ${unitFormatter.formatPressure(it).uppercase()}")
    }
    current.windMs?.let {
        items.add("WIND: ${unitFormatter.formatWindSpeed(it).uppercase()}")
    }
    // No humidity item: it already lives in the detail grid, and the
    // ticker repeating it is the duplicate Robert flagged.
    current.dewPointC?.let {
        items.add("DEW POINT: ${unitFormatter.formatTemperature(it).uppercase()}")
    }
    return items
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
    pirateWeatherApiKey: String,
    pirateWeatherWorking: Boolean,
    pirateWeatherAttempted: Boolean,

    selectedRadarLayer: RadarLayer,
    selectedRadarRange: RadarRange,
    currentRadarFrameIndex: Int,
    isRadarPlaying: Boolean,
    onNavigate: (ScreenDestination) -> Unit,
    tickerVisible: Boolean,
    regionalPoints: List<RegionalPoint> = emptyList(),
    alertsEnabled: Boolean = false,
    onSetAlertsEnabled: (Boolean) -> Unit = {},
    rainAlertsEnabled: Boolean = false,
    onSetRainAlertsEnabled: (Boolean) -> Unit = {},
    highlightAlertId: String? = null
) {
    // Callbacks are remembered, not recreated per recomposition: the
    // 6-second ticker rotation recomposes the shell above this, and
    // fresh lambdas would make every screen unskippable - the whole Home
    // list (hero, 48-hour strip, 14-day outlook, regional map) would
    // recompose every 6 seconds and stutter any scroll in flight. The
    // ViewModel instance never changes, so these stay valid.
    val onRefresh = remember(viewModel) { { viewModel.refresh() } }
    val onSelectPlace = remember(viewModel) { { id: String -> viewModel.selectPlace(id) } }
    val onUseCurrentLocation = remember(viewModel) { { highAccuracy: Boolean -> viewModel.useCurrentLocation(highAccuracy) } }
    val onSelectRadarLayer = remember(viewModel) { { layer: RadarLayer -> viewModel.setRadarLayer(layer) } }
    val onSelectRadarRange = remember(viewModel) { { range: RadarRange -> viewModel.setRadarRange(range) } }
    val onToggleRadarPlay = remember(viewModel) { { viewModel.toggleRadarPlay() } }
    val onScrubRadarFrame = remember(viewModel) { { index: Int -> viewModel.setRadarFrameIndex(index) } }
    val onSetUseMetric = remember(viewModel) { { metric: Boolean -> viewModel.setUseMetric(metric) } }
    val onSavePirateWeatherApiKey = remember(viewModel) { { key: String -> viewModel.setPirateWeatherApiKey(key) } }
    val onAddPlace = remember(viewModel) { { name: String, state: String, lat: Double, lon: Double -> viewModel.addPlace(name, state, lat, lon) } }
    val onNavigateToAlerts = remember(onNavigate) { { onNavigate(ScreenDestination.Alerts) } }
    val onNavigateToAirPollen = remember(onNavigate) { { onNavigate(ScreenDestination.AirSky) } }
    val onNavigateToSunMoon = remember(onNavigate) { { onNavigate(ScreenDestination.AirSky) } }
    val onNavigateToSettings = remember(onNavigate) { { onNavigate(ScreenDestination.Settings) } }
    val onViewOnRadar = remember(onNavigate) { { onNavigate(ScreenDestination.Radar) } }

    when (destination) {
        ScreenDestination.Home -> HomeScreen(
            state = weatherState,
            isRefreshing = isRefreshing,
            selectedPlace = selectedPlace,
            loadError = loadError,
            savedPlaces = savedPlaces,
            unitFormatter = viewModel.getUnitFormatter(),
            onRefresh = onRefresh,
            onSelectPlace = onSelectPlace,
            onUseCurrentLocation = onUseCurrentLocation,
            onNavigateToAlerts = onNavigateToAlerts,
            onNavigateToAirPollen = onNavigateToAirPollen,
            onNavigateToSunMoon = onNavigateToSunMoon,
            onNavigateToSettings = onNavigateToSettings,
            tickerVisible = tickerVisible,
            regionalPoints = regionalPoints
        )

        ScreenDestination.Radar -> RadarScreen(
            state = weatherState,
            selectedLayer = selectedRadarLayer,
            selectedRange = selectedRadarRange,
            currentFrameIndex = currentRadarFrameIndex,
            isPlaying = isRadarPlaying,
            onSelectLayer = onSelectRadarLayer,
            onSelectRange = onSelectRadarRange,
            onTogglePlay = onToggleRadarPlay,
            onScrubFrame = onScrubRadarFrame,
            tickerVisible = tickerVisible
        )

        ScreenDestination.Alerts -> AlertsScreen(
            alerts = weatherState?.activeAlerts ?: emptyList(),
            fetchedAt = weatherState?.fetchedAt,
            isStale = weatherState?.isStale ?: false,
            placeName = selectedPlace?.name,
            zoneId = runCatching { java.time.ZoneId.of(weatherState?.place?.zoneId ?: "") }
                .getOrDefault(java.time.ZoneId.systemDefault()),
            onViewOnRadar = onViewOnRadar,
            tickerVisible = tickerVisible,
            highlightAlertId = highlightAlertId
        )

        ScreenDestination.AirSky -> {
            // Tab switcher or composite view for Air & Pollen and Sun & Moon
            AirSkyCompositeView(
                weatherState = weatherState,
                selectedPlace = selectedPlace,
                tickerVisible = tickerVisible
            )
        }

        ScreenDestination.Settings -> SettingsScreen(
            useMetric = useMetric,
            pirateWeatherApiKey = pirateWeatherApiKey,
            pirateWeatherWorking = pirateWeatherWorking,
            pirateWeatherAttempted = pirateWeatherAttempted,
            savedPlaces = savedPlaces,
            selectedPlaceId = selectedPlace?.id,
            onSetUseMetric = onSetUseMetric,
            onSavePirateWeatherApiKey = onSavePirateWeatherApiKey,

            onSelectPlace = onSelectPlace,
            onAddPlace = onAddPlace,
            tickerVisible = tickerVisible,
            alertsEnabled = alertsEnabled,
            onSetAlertsEnabled = onSetAlertsEnabled,
            rainAlertsEnabled = rainAlertsEnabled,
            onSetRainAlertsEnabled = onSetRainAlertsEnabled
        )
    }
}

@Composable
private fun AirSkyCompositeView(
    weatherState: FullWeatherState?,
    selectedPlace: Place?,
    tickerVisible: Boolean
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
                tickerVisible = tickerVisible
            )
        } else {
            SunMoonScreen(
                solar = weatherState?.solar,
                lunar = weatherState?.lunar,
                tickerVisible = tickerVisible,
                zoneId = weatherState?.place?.zoneId,
                placeName = selectedPlace?.name
            )
        }
    }
}
