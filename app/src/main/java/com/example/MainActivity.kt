package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.work.AlertNotifier
import com.example.music.BackgroundMusicPlayer
import com.example.ui.WeatherViewModel
import com.example.ui.navigation.ScreenDestination
import com.example.ui.navigation.WeatherNavDisplay
import com.example.ui.theme.AuraWeatherTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    // Background music: owned by the Activity (never by a composable, so
    // it survives recomposition). Foreground-only — no service, no
    // notification, no background playback.
    private val musicPlayer by lazy { BackgroundMusicPlayer(this) }

    private val viewModel: WeatherViewModel by viewModels {
        val app = application as AuraWeatherApp
        WeatherViewModel.Factory(
            weatherRepository = app.container.weatherRepository,
            settingsManager = app.container.settingsManager,
            fusedLocationClient = app.container.fusedLocationClient,
            appContext = applicationContext
        )
    }

    // 3.1 notification deep link: a tap opens the Alerts screen on the
    // alert that was notified. Set from the launch intent (cold start) or
    // onNewIntent (app already running); the shell consumes it once.
    private val pendingDestination = MutableStateFlow<ScreenDestination?>(null)
    private val highlightAlertId = MutableStateFlow<String?>(null)

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getStringExtra(AlertNotifier.EXTRA_DESTINATION) == AlertNotifier.DESTINATION_ALERTS) {
            highlightAlertId.value = intent.getStringExtra(AlertNotifier.EXTRA_ALERT_ID)
            pendingDestination.value = ScreenDestination.Alerts
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)

        setContent {
            val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
            val loadError by viewModel.loadError.collectAsStateWithLifecycle()
            val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
            val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
            val selectedRadarLayer by viewModel.selectedRadarLayer.collectAsStateWithLifecycle()
            val selectedRadarRange by viewModel.selectedRadarRange.collectAsStateWithLifecycle()
            val currentRadarFrameIndex by viewModel.currentRadarFrameIndex.collectAsStateWithLifecycle()
            val isRadarPlaying by viewModel.isRadarPlaying.collectAsStateWithLifecycle()
            val pirateWeatherApiKey by viewModel.pirateWeatherApiKey.collectAsStateWithLifecycle()
            val pirateWeatherWorking by viewModel.pirateWeatherWorking.collectAsStateWithLifecycle()
            val pirateWeatherAttempted by viewModel.pirateWeatherAttempted.collectAsStateWithLifecycle()
            val regionalPoints by viewModel.regionalPoints.collectAsStateWithLifecycle()
            val musicEnabled by viewModel.musicEnabled.collectAsStateWithLifecycle()
            val alertsEnabled by viewModel.alertsEnabled.collectAsStateWithLifecycle()
            val rainAlertsEnabled by viewModel.rainAlertsEnabled.collectAsStateWithLifecycle()
            val pendingNav by pendingDestination.collectAsStateWithLifecycle()
            val highlightAlert by highlightAlertId.collectAsStateWithLifecycle()

            // The Home music toggle is the master switch: flip the player
            // on/off. The player itself gates on the foreground state.
            LaunchedEffect(musicEnabled) {
                musicPlayer.setEnabled(musicEnabled)
            }

            // The app is always the dark navy retro look, so status-bar
            // icons are always light (isAppearanceLightStatusBars = false).
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = false
            }

            AuraWeatherTheme {
                WeatherNavDisplay(
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
                    regionalPoints = regionalPoints,
                    musicEnabled = musicEnabled,
                    onToggleMusic = remember(musicEnabled) { { viewModel.setMusicEnabled(!musicEnabled) } },
                    alertsEnabled = alertsEnabled,
                    onSetAlertsEnabled = remember(viewModel) { { enabled: Boolean -> viewModel.setAlertsEnabled(enabled) } },
                    rainAlertsEnabled = rainAlertsEnabled,
                    onSetRainAlertsEnabled = remember(viewModel) { { enabled: Boolean -> viewModel.setRainAlertsEnabled(enabled) } },
                    pendingDestination = pendingNav,
                    highlightAlertId = highlightAlert,
                    onPendingDestinationConsumed = remember { {
                        pendingDestination.value = null
                        highlightAlertId.value = null
                    } }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        musicPlayer.onForeground()
    }

    override fun onStop() {
        musicPlayer.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        musicPlayer.release()
        super.onDestroy()
    }
}
