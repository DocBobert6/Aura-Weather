package com.example

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.WeatherCondition
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
            val paletteMode by viewModel.paletteMode.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val selectedRadarLayer by viewModel.selectedRadarLayer.collectAsStateWithLifecycle()
            val currentRadarFrameIndex by viewModel.currentRadarFrameIndex.collectAsStateWithLifecycle()
            val isRadarPlaying by viewModel.isRadarPlaying.collectAsStateWithLifecycle()
            val tomorrowApiKey by viewModel.tomorrowApiKey.collectAsStateWithLifecycle()

            val condition = weatherState?.current?.condition ?: WeatherCondition.ClearDay
            val isDaytime = weatherState?.solar?.isDaytime ?: true

            AuraWeatherTheme(
                paletteMode = paletteMode,
                condition = condition,
                isDaytime = isDaytime,
                // "auto" follows the system; "dark"/"light" force the 2.2 schemes.
                darkTheme = when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                },
            ) {
                WeatherNavDisplay(
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
                    onEnterPiP = { enterRadarPictureInPicture() }
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
