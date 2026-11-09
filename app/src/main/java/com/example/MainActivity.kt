package com.example

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WeatherViewModel
import com.example.ui.navigation.WeatherNavDisplay
import com.example.ui.theme.AuraWeatherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels {
        val app = application as AuraWeatherApp
        WeatherViewModel.Factory(
            weatherRepository = app.container.weatherRepository,
            settingsManager = app.container.settingsManager,
            fusedLocationClient = app.container.fusedLocationClient
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
            val loadError by viewModel.loadError.collectAsStateWithLifecycle()
            val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
            val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
            val selectedRadarLayer by viewModel.selectedRadarLayer.collectAsStateWithLifecycle()
            val currentRadarFrameIndex by viewModel.currentRadarFrameIndex.collectAsStateWithLifecycle()
            val isRadarPlaying by viewModel.isRadarPlaying.collectAsStateWithLifecycle()
            val tomorrowApiKey by viewModel.tomorrowApiKey.collectAsStateWithLifecycle()
            val regionalPoints by viewModel.regionalPoints.collectAsStateWithLifecycle()

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
                    tomorrowApiKey = tomorrowApiKey,
                    selectedRadarLayer = selectedRadarLayer,
                    currentRadarFrameIndex = currentRadarFrameIndex,
                    isRadarPlaying = isRadarPlaying,
                    onEnterPiP = { enterRadarPictureInPicture() },
                    regionalPoints = regionalPoints
                )
            }
        }
    }

    private fun enterRadarPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
