package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.UnitFormatter
import com.example.data.geocoding.GeocodingRepository
import com.example.data.geocoding.PlaceSearchResult
import com.example.data.geocoding.PlaceSearchUiState
import com.example.data.repo.WeatherRepository
import com.example.data.settings.SettingsManager
import com.example.data.work.AlertScheduler
import com.example.domain.model.FullWeatherState
import com.example.domain.model.Place
import com.example.domain.model.RegionalPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val settingsManager: SettingsManager,
    private val fusedLocationClient: FusedLocationProviderClient? = null,
    private val appContext: Context? = null,
    private val geocodingRepository: GeocodingRepository? = null
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

    val pirateWeatherApiKey: StateFlow<String> = settingsManager.pirateWeatherApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Pirate Weather "KEY ACTIVE" pill: true only after the client has
     * actually fetched usable minutely data with the stored key. */
    val pirateWeatherWorking: StateFlow<Boolean> = settingsManager.pirateWeatherWorkingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** "KEY FAILED" pills: true once a fetch was attempted with the key. */
    val pirateWeatherAttempted: StateFlow<Boolean> = settingsManager.pirateWeatherAttemptedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Background music toggle (Home screen): off by default. */
    val musicEnabled: StateFlow<Boolean> = settingsManager.musicEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 90s CRT filter on the radar map (Settings toggle): on by default. */
    val crtFilterEnabled: StateFlow<Boolean> = settingsManager.crtFilterEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 3.1 alert notifications toggle (Settings): off by default. */
    val alertsEnabled: StateFlow<Boolean> = settingsManager.alertsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // Build C: rain-starting alerts, off by default (opt-in).
    val rainAlertsEnabled: StateFlow<Boolean> = settingsManager.rainAlertsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    /** Place search state (0.2.0). */
    private val _placeSearchState = MutableStateFlow<PlaceSearchUiState>(PlaceSearchUiState.Idle)
    val placeSearchState = _placeSearchState.asStateFlow()

    private var placeSearchJob: Job? = null

    /**
     * Searches for places as the user types, debounced ~300 ms. Queries
     * under 2 characters are not searched (Open-Meteo returns nothing
     * for them) — the state goes back to Idle.
     */
    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        if (query.trim().length < 2) {
            _placeSearchState.value = PlaceSearchUiState.Idle
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(300)
            _placeSearchState.value = PlaceSearchUiState.Searching
            val repository = geocodingRepository
            if (repository == null) {
                _placeSearchState.value = PlaceSearchUiState.Failed(query.trim())
                return@launch
            }
            runCatching { repository.search(query) }.fold(
                onSuccess = { results ->
                    _placeSearchState.value = if (results.isEmpty()) {
                        PlaceSearchUiState.NoResults(query.trim())
                    } else {
                        PlaceSearchUiState.Results(results)
                    }
                },
                onFailure = {
                    _placeSearchState.value = PlaceSearchUiState.Failed(query.trim())
                }
            )
        }
    }

    fun clearPlaceSearch() {
        placeSearchJob?.cancel()
        _placeSearchState.value = PlaceSearchUiState.Idle
    }

    /**
     * Adds the picked search result via the normal add path, so the
     * 500 m near-duplicate check applies (a searched place within
     * 500 m of a saved row dedupes onto it, including Current Location).
     */
    fun addSearchedPlace(result: PlaceSearchResult) {
        clearPlaceSearch()
        addPlace(result.name, result.storedStateCode(), result.latitude, result.longitude)
    }

    /**
     * Removes a saved place (0.2.0). Current Location is not removable
     * — the repository guards it too; this returns early so the UI
     * never even launches the work for it.
     */
    fun removePlace(placeId: String) {
        if (placeId == WeatherRepository.CURRENT_LOCATION_ID) return
        viewModelScope.launch {
            weatherRepository.removePlace(placeId)
            _selectedPlace.value = weatherRepository.getSelectedPlace()
            syncAlertWork()
        }
    }

    fun setUseMetric(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setUseMetric(enabled)
        }
    }

    fun setPirateWeatherApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.setPirateWeatherApiKey(key.trim())
            refresh()
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setMusicEnabled(enabled)
        }
    }

    fun setCrtFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setCrtFilterEnabled(enabled)
        }
    }

    /**
     * 3.1: persist the alerts toggle, then start or stop the periodic
     * check. Disabling cancels the unique work ONLY when the rain
     * toggle is off too — the same worker serves both (Build C), so the
     * work must keep running while either toggle is on.
     */
    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAlertsEnabled(enabled)
            syncAlertWork()
        }
    }

    /** Build C: same shape as setAlertsEnabled, for the rain toggle. */
    fun setRainAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setRainAlertsEnabled(enabled)
            syncAlertWork()
        }
    }

    /**
     * 3.1 (Build C: shared by both toggles): (re-)establish the periodic
     * alert check when there is a place to check and EITHER toggle is on;
     * cancel it only when both are off. KEEP makes repeated calls
     * harmless. Reads the persisted flags directly (not the StateFlow
     * values) so a place change before the flow's first emission still
     * schedules.
     */
    private fun syncAlertWork() {
        val context = appContext ?: return
        viewModelScope.launch {
            val alertsOn = settingsManager.alertsEnabledFlow.first()
            val rainOn = settingsManager.rainAlertsEnabledFlow.first()
            val place = _selectedPlace.value ?: weatherRepository.getSelectedPlace()
            if ((alertsOn || rainOn) && place != null) {
                AlertScheduler.enqueue(context)
            } else {
                AlertScheduler.cancel(context)
            }
        }
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

    // Memoized: a fresh UnitFormatter on every call made every screen
    // unskippable, so the shell's 6-second ticker rotation recomposed
    // the whole Home list (and every other screen) each time it ticked.
    // The instance is recreated only when the unit flips.
    private var cachedUnitFormatter: UnitFormatter? = null
    private var cachedUnitFormatterMetric: Boolean? = null

    fun getUnitFormatter(): UnitFormatter {
        val metric = useMetric.value
        if (cachedUnitFormatter == null || cachedUnitFormatterMetric != metric) {
            cachedUnitFormatter = UnitFormatter(metric)
            cachedUnitFormatterMetric = metric
        }
        return cachedUnitFormatter!!
    }

    class Factory(
        private val weatherRepository: WeatherRepository,
        private val settingsManager: SettingsManager,
        private val fusedLocationClient: FusedLocationProviderClient?,
        private val appContext: Context? = null,
        private val geocodingRepository: GeocodingRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WeatherViewModel(weatherRepository, settingsManager, fusedLocationClient, appContext, geocodingRepository) as T
        }
    }
}
