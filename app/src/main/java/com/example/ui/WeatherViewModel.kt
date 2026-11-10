package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.UnitFormatter
import com.example.data.repo.WeatherRepository
import com.example.data.settings.SettingsManager
import com.example.data.work.AlertScheduler
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.domain.model.RegionalPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class RadarLayer(val title: String) {
    Reflectivity("NEXRAD Composite"),
    // The station shown is the one NWS /points returned for the selected
    // place — never a hardcoded site.
    LocalSite("Local Radar Site"),
    SatelliteVis("Satellite Visible"),
    SatelliteIr("Satellite Clean IR")
}

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val settingsManager: SettingsManager,
    private val fusedLocationClient: FusedLocationProviderClient? = null,
    private val appContext: Context? = null
) : ViewModel() {

    val weatherState: StateFlow<FullWeatherState?> = weatherRepository.weatherState

    val isRefreshing: StateFlow<Boolean> = weatherRepository.isRefreshing

    /** Set when a load fails with no cached state to show. */
    val loadError: StateFlow<String?> = weatherRepository.loadError

    /** The selected place, or null on first launch before the user picks one. */
    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace = _selectedPlace.asStateFlow()

    val savedPlaces: StateFlow<List<Place>> = weatherRepository.getSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val useMetric: StateFlow<Boolean> = settingsManager.useMetricFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tomorrowApiKey: StateFlow<String> = settingsManager.tomorrowApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Tomorrow.io "KEY ACTIVE" pill: true only after the client has
     * actually fetched usable data with the stored key. */
    val tomorrowWorking: StateFlow<Boolean> = settingsManager.tomorrowWorkingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val airNowApiKey: StateFlow<String> = settingsManager.airNowApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Background music toggle (Home screen): off by default. */
    val musicEnabled: StateFlow<Boolean> = settingsManager.musicEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 3.1 alert notifications toggle (Settings): off by default. */
    val alertsEnabled: StateFlow<Boolean> = settingsManager.alertsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedRadarLayer = MutableStateFlow(RadarLayer.Reflectivity)
    val selectedRadarLayer = _selectedRadarLayer.asStateFlow()

    private val _currentRadarFrameIndex = MutableStateFlow(9) // 0..9 (9 = most recent "now")
    val currentRadarFrameIndex = _currentRadarFrameIndex.asStateFlow()

    private val _isRadarPlaying = MutableStateFlow(true)
    val isRadarPlaying = _isRadarPlaying.asStateFlow()

    /**
     * Regional map points (R5): the centre place first, then its
     * neighbours. Empty while the neighbour fetch is pending, and empty
     * when it produced nothing — the Home section hides in both cases.
     */
    private val _regionalPoints = MutableStateFlow<List<RegionalPoint>>(emptyList())
    val regionalPoints = _regionalPoints.asStateFlow()

    private var regionalJob: Job? = null

    init {
        // Initial load — only when a place is already selected. With no
        // place, the UI shows the no-place state and nothing is fetched.
        viewModelScope.launch {
            val place = weatherRepository.getSelectedPlace()
            _selectedPlace.value = place
            if (place != null) {
                weatherRepository.loadWeather(place, forceRefresh = false)
            } else {
                weatherRepository.clearWeather()
            }
            syncAlertWork()
        }
        startRegionalLoad()
    }

    /**
     * Regional map (R5): after every main load, fetch the neighbours in
     * their own coroutine. A slow or failed neighbour fetch never blocks
     * the main weather, and a new place cancels the in-flight fetch.
     */
    private fun startRegionalLoad() {
        viewModelScope.launch {
            weatherRepository.weatherState.collect { state ->
                regionalJob?.cancel()
                if (state == null) {
                    _regionalPoints.value = emptyList()
                    return@collect
                }
                regionalJob = launch {
                    val neighbours = runCatching {
                        weatherRepository.fetchRegionalNeighbours(
                            centre = state.place.point,
                            zoneId = runCatching { ZoneId.of(state.place.zoneId) }
                                .getOrDefault(ZoneId.systemDefault())
                        )
                    }.getOrElse { emptyList() }
                    if (neighbours.isEmpty()) {
                        _regionalPoints.value = emptyList()
                        return@launch
                    }
                    val centre = RegionalPoint(
                        point = state.place.point,
                        label = state.place.name,
                        tempC = state.current.tempC,
                        condition = state.current.condition,
                        isDaytime = state.solar.isDaytime,
                        isCentre = true
                    )
                    _regionalPoints.value = listOf(centre) + neighbours
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val place = weatherRepository.getSelectedPlace()
            _selectedPlace.value = place
            if (place != null) {
                weatherRepository.loadWeather(place, forceRefresh = true)
            } else {
                weatherRepository.clearWeather()
            }
        }
    }

    fun selectPlace(placeId: String) {
        viewModelScope.launch {
            weatherRepository.selectPlace(placeId)
            _selectedPlace.value = weatherRepository.getSelectedPlace()
            syncAlertWork()
        }
    }

    fun addPlace(name: String, stateCode: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val place = weatherRepository.addCustomPlace(name, stateCode, lat, lon)
            _selectedPlace.value = place
            syncAlertWork()
        }
    }

    fun setUseMetric(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setUseMetric(enabled)
        }
    }

    fun setTomorrowApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.setTomorrowApiKey(key.trim())
            refresh()
        }
    }

    fun setAirNowApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.setAirNowApiKey(key.trim())
            refresh()
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setMusicEnabled(enabled)
        }
    }

    /**
     * 3.1: persist the alerts toggle, then start or stop the periodic
     * check. Disabling cancels the unique work — that is the acceptance
     * check, verified by inspection (AlertScheduler.cancel calls
     * cancelUniqueWork with the same name enqueue used).
     */
    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAlertsEnabled(enabled)
            val context = appContext ?: return@launch
            if (enabled) {
                syncAlertWork()
            } else {
                AlertScheduler.cancel(context)
            }
        }
    }

    /**
     * 3.1: (re-)establish the periodic alert check when there is a place
     * to check and alerts are on. KEEP makes repeated calls harmless.
     * Reads the persisted flag directly (not the StateFlow value) so a
     * place change before the flow's first emission still schedules.
     */
    private fun syncAlertWork() {
        val context = appContext ?: return
        viewModelScope.launch {
            val enabled = settingsManager.alertsEnabledFlow.first()
            val place = _selectedPlace.value ?: weatherRepository.getSelectedPlace()
            if (enabled && place != null) {
                AlertScheduler.enqueue(context)
            }
        }
    }

    fun setRadarLayer(layer: RadarLayer) {
        _selectedRadarLayer.value = layer
    }

    fun setRadarFrameIndex(index: Int) {
        _currentRadarFrameIndex.value = index.coerceIn(0, 9)
    }

    // 3.3: this only flips the play state. The loop itself lives in
    // RadarScreen (a LaunchedEffect keyed on isPlaying), so no radar job
    // runs when the screen was never visited. The ViewModel keeps only
    // the frame index + play state.
    fun toggleRadarPlay() {
        _isRadarPlaying.value = !_isRadarPlaying.value
    }

    fun useCurrentLocation(
        highAccuracy: Boolean = true,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val client = fusedLocationClient ?: return
        // Precise (fine) grant -> high-accuracy GPS fix; coarse-only
        // grant -> balanced accuracy, so Current Location still works
        // for users who only grant approximate location.
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        try {
            client.getCurrentLocation(priority, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModelScope.launch {
                            // One fixed "Current Location" row, updated in
                            // place — a tap never creates a duplicate place.
                            val place = weatherRepository.setCurrentLocationPlace(
                                lat = location.latitude,
                                lon = location.longitude
                            )
                            _selectedPlace.value = place
                            syncAlertWork()
                            onSuccess(place.name)
                        }
                    } else {
                        onError("Unable to resolve GPS coordinates.")
                    }
                }
                .addOnFailureListener {
                    onError(it.localizedMessage ?: "Location request failed.")
                }
        } catch (e: SecurityException) {
            onError("Location permission required.")
        }
    }

    fun getUnitFormatter(): UnitFormatter {
        return UnitFormatter(useMetric.value)
    }

    class Factory(
        private val weatherRepository: WeatherRepository,
        private val settingsManager: SettingsManager,
        private val fusedLocationClient: FusedLocationProviderClient?,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WeatherViewModel(weatherRepository, settingsManager, fusedLocationClient, appContext) as T
        }
    }
}
