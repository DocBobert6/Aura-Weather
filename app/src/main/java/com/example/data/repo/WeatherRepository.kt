package com.example.data.repo

import com.example.data.api.AirNowApi
import com.example.data.api.TomorrowApi
import com.example.data.api.NwsAlertFeature
import com.example.data.api.NwsApi
import com.example.data.api.NwsForecastPeriod
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.api.OpenMeteoWeatherResponse
import com.example.data.db.SavedPlaceDao
import com.example.data.db.SavedPlaceEntity
import com.example.data.db.WeatherCacheDao
import com.example.data.settings.SettingsManager
import com.example.data.db.WeatherCacheEntity
import com.example.data.mapper.AirNowReading
import com.example.data.mapper.TomorrowMinutePoint
import com.example.data.mapper.InstantJsonAdapter
import com.example.data.mapper.LocalDateJsonAdapter
import com.example.data.mapper.WeatherMappers
import com.example.data.mapper.destinationPoint
import com.example.data.mapper.distanceMeters
import com.example.data.mapper.isDaytimeAt
import com.example.data.mapper.parseOpenMeteoUtcInstant
import com.example.data.mapper.regionalCompassLabel
import com.example.data.mapper.fillDailyHighs
import com.example.data.mapper.stableAlertId
import com.example.domain.astro.LunarCalculator
import com.example.domain.astro.SolarCalculator
import com.example.domain.model.CurrentConditions
import com.example.domain.model.DailyPoint
import com.example.domain.model.FullWeatherState
import com.example.domain.model.GeoPoint
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
import com.example.domain.model.PollenData
import com.example.domain.model.RegionalPoint
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import com.example.ui.widget.WidgetState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Offline-first weather repository.
 *
 * Two rules govern this file:
 * 1. No invented data. A value the providers did not return is null, and the
 *    UI renders null as "unavailable" — it is never replaced with a plausible
 *    constant (no 16 C fallback temperature, no 17 C partly-cloudy fallback
 *    state, no hardcoded pollen, no invented AQI).
 * 2. No default place. There are no seeded places and no fallback
 *    coordinates, station, or timezone. A place's station, radar site,
 *    forecast zone, and timezone all come from NWS /points for that place
 *    (or, outside NWS coverage, from Open-Meteo). With no place selected,
 *    nothing is fetched and the UI shows the no-place state.
 */
class WeatherRepository(
    private val nwsApi: NwsApi,
    private val openMeteoWeatherApi: OpenMeteoWeatherApi,
    private val openMeteoAirQualityApi: OpenMeteoAirQualityApi,
    private val airNowApi: AirNowApi,
    private val tomorrowApi: TomorrowApi,
    private val settingsManager: SettingsManager,
    private val weatherCacheDao: WeatherCacheDao,
    private val savedPlaceDao: SavedPlaceDao
) {
    private val moshi = Moshi.Builder()
        .add(InstantJsonAdapter)
        .add(LocalDateJsonAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    private val _weatherState = MutableStateFlow<FullWeatherState?>(null)
    val weatherState = _weatherState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    /** Set when a load fails and there is no cached state to show. Null otherwise. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    fun getSavedPlaces(): Flow<List<Place>> {
        return savedPlaceDao.getAllPlaces().map { entities ->
            entities.map { entity -> entity.toPlace() }
        }
    }

    /** The selected place, or null when the user has not chosen one yet. */
    suspend fun getSelectedPlace(): Place? = withContext(Dispatchers.IO) {
        savedPlaceDao.getSelectedPlace()?.toPlace()
    }

    /**
     * Active NWS alerts for one place (3.1): the alert worker's fetch.
     * Same parser as the main refresh, so the worker and the UI never
     * disagree about which alerts are active. Throws on a network
     * failure — the worker retries; it never invents an empty list.
     */
    suspend fun getActiveAlertsForPlace(place: Place): List<WeatherAlert> =
        withContext(Dispatchers.IO) {
            val response = nwsApi.getActiveAlerts(
                String.format(Locale.US, "%.4f,%.4f", place.point.lat, place.point.lon)
            )
            parseNwsAlerts(response.features, Instant.now())
        }

    /**
     * 3.2: the widget's data. Reads the in-memory state first (freshest),
     * else the Room cache. Never triggers a network fetch. Null when there
     * is no selected place, no cache, or an unparseable cache — the widget
     * shows the empty prompt, never invented weather.
     */
    suspend fun getWidgetState(useMetric: Boolean): WidgetState? = withContext(Dispatchers.IO) {
        val place = getSelectedPlace() ?: return@withContext null
        val state = weatherState.value?.takeIf { it.place.id == place.id }
            ?: run {
                val cached = weatherCacheDao.getCache(place.id) ?: return@withContext null
                val isStale = (Instant.now().toEpochMilli() - cached.cachedAt) > 15 * 60 * 1000
                parseCacheToState(place, cached, isStale) ?: return@withContext null
            }
        val today = state.daily7d.firstOrNull()
        WidgetState(
            placeName = state.place.name,
            tempC = state.current.tempC,
            condition = state.current.condition,
            highC = today?.highC,
            lowC = today?.lowC,
            fetchedAt = state.fetchedAt,
            isStale = state.isStale,
            zoneId = state.place.zoneId,
            useMetric = useMetric,
            isDaytime = state.solar.isDaytime
        )
    }

    /** Clears the displayed state (used when there is no selected place). */
    fun clearWeather() {
        _weatherState.value = null
        _loadError.value = null
    }

    suspend fun selectPlace(placeId: String) = withContext(Dispatchers.IO) {
        savedPlaceDao.setSelectedPlace(placeId)
        getSelectedPlace()?.let { loadWeather(it, forceRefresh = false) }
    }

    /**
     * Adds a place the user named. A place within 500 m of a saved one is the
     * same place: the saved row is selected instead of inserting a duplicate.
     */
    suspend fun addCustomPlace(name: String, stateCode: String, lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        val point = GeoPoint(lat, lon)
        val nearby = findNearbyPlace(point)
        if (nearby != null) {
            savedPlaceDao.setSelectedPlace(nearby.id)
            loadWeather(nearby, forceRefresh = true)
            return@withContext nearby
        }
        val id = "custom_${System.currentTimeMillis()}"
        val entity = SavedPlaceEntity(
            id = id,
            name = name,
            stateCode = stateCode,
            lat = lat,
            lon = lon,
            isPreset = false,
            isSelected = true
        )
        savedPlaceDao.insertPlace(entity)
        savedPlaceDao.setSelectedPlace(id)
        val newPlace = Place(id, name, point, stateCode, isPreset = false)
        loadWeather(newPlace, forceRefresh = true)
        newPlace
    }

    /**
     * Records the device's current location as the one "Current Location"
     * place. Every tap updates the same row — it never mints a new place.
     */
    suspend fun setCurrentLocationPlace(lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        val existing = savedPlaceDao.getPlaceById(CURRENT_LOCATION_ID)
        val entity = if (existing != null) {
            existing.copy(lat = lat, lon = lon, isSelected = true)
        } else {
            SavedPlaceEntity(
                id = CURRENT_LOCATION_ID,
                name = "Current Location",
                stateCode = "",
                lat = lat,
                lon = lon,
                isPreset = false,
                isSelected = true
            )
        }
        savedPlaceDao.insertPlace(entity)
        savedPlaceDao.setSelectedPlace(CURRENT_LOCATION_ID)
        val place = entity.toPlace()
        loadWeather(place, forceRefresh = true)
        place
    }

    private suspend fun findNearbyPlace(point: GeoPoint): Place? {
        return savedPlaceDao.getAllPlacesList()
            .map { it.toPlace() }
            .firstOrNull { distanceMeters(it.point, point) < NEARBY_PLACE_METERS }
    }

    /**
     * Regional map neighbours (R5): 8 points at compass bearings around
     * [centre], [REGIONAL_NEIGHBOUR_DISTANCE_METERS] away, with current
     * conditions. The centre itself is NOT in the returned list — the
     * caller plots it from the already-loaded state.
     *
     * One multi-location Open-Meteo call fetches all 8 current
     * conditions; if it fails or returns the wrong count, one
     * single-location call per point is tried instead. A point whose
     * weather fetch fails, or which has no temperature, is OMITTED —
     * never plotted with an invented value. Names come from NWS /points
     * relativeLocation (city, state) per point; outside NWS coverage, or
     * when /points fails, the point is labelled by compass direction.
     * An empty list means "hide the section".
     */
    suspend fun fetchRegionalNeighbours(centre: GeoPoint, zoneId: ZoneId): List<RegionalPoint> =
        withContext(Dispatchers.IO) {
            val bearings = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
            val destinations = bearings.map { destinationPoint(centre, it, REGIONAL_NEIGHBOUR_DISTANCE_METERS) }

            val multi = runCatching {
                openMeteoWeatherApi.getCurrentMulti(
                    latitudes = destinations.joinToString(",") { it.lat.toString() },
                    longitudes = destinations.joinToString(",") { it.lon.toString() }
                )
            }.getOrNull()?.takeIf { it.size == destinations.size }

            // Current conditions per point, in bearing order. The
            // multi-location call is preferred (one request); the
            // per-point loop is the fallback when it fails.
            val currents = if (multi != null) {
                multi.map { it.current }
            } else {
                destinations.map { dest ->
                    runCatching { openMeteoWeatherApi.getForecast(dest.lat, dest.lon) }
                        .getOrNull()?.current
                }
            }

            val now = Instant.now()
            coroutineScope {
                destinations.mapIndexed { index, dest ->
                    async {
                        val current = currents[index] ?: return@async null
                        val tempC = current.temperature2m ?: return@async null
                        val rel = runCatching { nwsApi.getPoints(dest.lat, dest.lon) }
                            .getOrNull()?.properties?.relativeLocation?.properties
                        val city = rel?.city
                        val stateCode = rel?.state
                        val label = if (city != null) {
                            if (stateCode != null) "$city, $stateCode" else city
                        } else {
                            regionalCompassLabel(bearings[index])
                        }
                        val daytime = isDaytimeAt(dest, now, zoneId)
                        RegionalPoint(
                            point = dest,
                            label = label,
                            tempC = tempC,
                            condition = WeatherMappers.mapWmoCodeToCondition(current.weatherCode, daytime),
                            isDaytime = daytime
                        )
                    }
                }.awaitAll().filterNotNull()
            }
        }

    suspend fun loadWeather(place: Place, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        _isRefreshing.value = true

        // 1. Read Room cache first (offline-first). A cache row in the old
        // bar-delimited format cannot be parsed and is treated as absent.
        val cached = weatherCacheDao.getCache(place.id)
        val now = Instant.now()
        if (cached != null) {
            val isStale = (now.toEpochMilli() - cached.cachedAt) > 15 * 60 * 1000
            val cachedState = parseCacheToState(place, cached, isStale)
            if (cachedState != null) {
                _weatherState.value = cachedState
                _loadError.value = null
                if (!isStale && !forceRefresh) {
                    _isRefreshing.value = false
                    return@withContext
                }
            }
        }

        // 2. Fetch fresh weather from the providers.
        try {
            val freshState = fetchFreshWeather(place, now)
            _weatherState.value = freshState
            _loadError.value = null
            saveToCache(place.id, freshState)
        } catch (e: Exception) {
            // No invented fallback state. With a cached state, show it marked
            // stale; with nothing at all, surface the failure and show no
            // weather rather than a fabricated 17 C partly-cloudy day.
            val currentState = _weatherState.value
            if (currentState != null) {
                _weatherState.value = currentState.copy(
                    isStale = true,
                    errorMessage = "Network offline — showing cached data"
                )
            } else {
                _loadError.value = e.localizedMessage
                    ?: "Couldn't load weather. Check your connection and try again."
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchFreshWeather(place: Place, now: Instant): FullWeatherState {
        val lat = place.point.lat
        val lon = place.point.lon

        // Open-Meteo first: nowcast, hourly, daily, AQI, and (outside NWS
        // coverage) the UTC offset used to resolve the place's timezone.
        // The calls run one after another today; parallelise when adding more.
        val openMeteoResult = runCatching {
            openMeteoWeatherApi.getForecast(lat, lon)
        }.getOrNull()

        // NWS /points for this place: grid cell, radar station, forecast
        // zone, and timezone. Null outside NWS coverage — everything below
        // treats that as "NWS unavailable", never as a reason to use another
        // city's data.
        val points = runCatching { nwsApi.getPoints(lat, lon) }.getOrNull()?.properties
        val gridId = points?.gridId
        val gridX = points?.gridX
        val gridY = points?.gridY

        // Resolve the place's timezone and persist it on the place row, so
        // every screen renders this place in its own zone from then on.
        val zoneId = resolvePlaceZone(points?.timeZone, openMeteoResult?.utcOffsetSeconds, place.zoneId)
        var resolvedPlace = place.copy(zoneId = zoneId.id)
        if (resolvedPlace.zoneId != place.zoneId) {
            runCatching { savedPlaceDao.updateZoneId(place.id, zoneId.id) }
        }

        // The Current Location place is labelled with its real name from
        // NWS /points relativeLocation ("Covington, KY"), not the generic
        // "Current Location". Only the current-location row is renamed —
        // saved places keep the name the user gave them.
        val relCity = points?.relativeLocation?.properties?.city
        val relState = points?.relativeLocation?.properties?.state
        if (place.id == CURRENT_LOCATION_ID && relCity != null) {
            val realName = if (relState != null) "$relCity, $relState" else relCity
            if (realName != place.name) {
                runCatching { savedPlaceDao.updateName(place.id, realName) }
                resolvedPlace = resolvedPlace.copy(name = realName)
            }
        }

        // Nearest NWS station for this place (ordered by distance by the API).
        val stationId = runCatching { nwsApi.getStations(lat, lon) }.getOrNull()
            ?.features?.firstNotNullOfOrNull { it.properties?.stationIdentifier }
        val nwsObsResult = stationId?.let { id ->
            runCatching { nwsApi.getLatestObservation(id) }.getOrNull()
        }

        // NWS forecast + hourly for this grid cell, when /points resolved.
        val nwsForecastResult = if (gridId != null && gridX != null && gridY != null) {
            runCatching { nwsApi.getForecast(gridId, gridX, gridY) }.getOrNull()
        } else null
        val nwsHourlyResult = if (gridId != null && gridX != null && gridY != null) {
            runCatching { nwsApi.getHourlyForecast(gridId, gridX, gridY) }.getOrNull()
        } else null

        // NWS alerts for the point.
        val alertsResult = runCatching {
            nwsApi.getActiveAlerts(String.format(Locale.US, "%.4f,%.4f", lat, lon))
        }.getOrNull()

        // Air quality.
        val aqResult = runCatching {
            openMeteoAirQualityApi.getAirQuality(lat, lon)
        }.getOrNull()

        // Astro calculations in the place's zone.
        val solar = SolarCalculator.calculate(resolvedPlace.point, now, zoneId)
        val lunarBase = LunarCalculator.calculate(now)
        val (moonrise, moonset) = LunarCalculator.calculateRiseSet(
            date = now.atZone(zoneId).toLocalDate(),
            zoneId = zoneId,
            latitude = resolvedPlace.point.lat,
            longitude = resolvedPlace.point.lon
        )
        val lunar = lunarBase.copy(moonrise = moonrise, moonset = moonset)

        // --- Current conditions. Every value is nullable: a provider that
        // did not return it leaves a null, and the UI shows "unavailable".
        val currentObs = nwsObsResult?.properties
        val omCurrent = openMeteoResult?.current

        val condition = if (currentObs?.textDescription != null) {
            WeatherMappers.mapNwsTextToCondition(currentObs.textDescription, solar.isDaytime)
        } else {
            WeatherMappers.mapWmoCodeToCondition(omCurrent?.weatherCode, solar.isDaytime)
        }

        // A blank NWS textDescription must not become the cached value:
        // it rendered the hero condition pill as an empty grey circle.
        val description = currentObs?.textDescription?.ifBlank { null } ?: condition.displayName

        // NWS temperature is in degC; Open-Meteo temperature_2m is degC.
        val tempC = currentObs?.temperature?.value ?: omCurrent?.temperature2m
        val feelsLikeC = currentObs?.heatIndex?.value
            ?: currentObs?.windChill?.value
            ?: omCurrent?.apparentTemperature
            ?: tempC
        val dewPointC = currentObs?.dewpoint?.value
        val humidityPct = currentObs?.relativeHumidity?.value
            ?: omCurrent?.relativeHumidity2m
        // NWS wind speed is km/h; Open-Meteo wind_speed_10m is km/h.
        val windMs = currentObs?.windSpeed?.value?.let { it * 1000.0 / 3600.0 }
            ?: omCurrent?.windSpeed10m?.let { it * 1000.0 / 3600.0 }
        val windGustMs = currentObs?.windGust?.value?.let { it * 1000.0 / 3600.0 }
        val windDirDeg = currentObs?.windDirection?.value ?: omCurrent?.windDirection10m
        // NWS pressure is Pa; Open-Meteo surface_pressure is hPa.
        val pressurePa = currentObs?.barometricPressure?.value
            ?: omCurrent?.surfacePressure?.let { it * 100.0 }
        val visibilityM = currentObs?.visibility?.value

        val current = CurrentConditions(
            observedAt = now,
            station = stationId,
            condition = condition,
            description = description,
            tempC = tempC,
            feelsLikeC = feelsLikeC,
            dewPointC = dewPointC,
            humidityPct = humidityPct,
            windMs = windMs,
            windGustMs = windGustMs,
            windDirDeg = windDirDeg,
            pressurePa = pressurePa,
            visibilityM = visibilityM,
            cloudCoverPct = omCurrent?.cloudCover
        )

        // --- Hourly (48h). NWS hourly is primary when /points resolved;
        // Open-Meteo is the fallback (and the only source outside the US).
        val hourlyList = parseNwsHourly(nwsHourlyResult?.properties?.periods, now)
            .ifEmpty { parseOpenMeteoHourly(openMeteoResult, resolvedPlace, zoneId, now) }
            // The first hour is "now": its icon must match the hero, which
            // comes from the observation. The forecast period's shortForecast
            // can disagree with the observation for the same hour.
            .let { list ->
                if (list.isNotEmpty()) {
                    list.mapIndexed { idx, pt -> if (idx == 0) pt.copy(condition = condition) else pt }
                } else list
            }

        // --- Daily (7d). Same split as hourly.
        val omDailyList = parseOpenMeteoDaily(openMeteoResult, zoneId)
        val dailyList = parseNwsDaily(nwsForecastResult?.properties?.periods, zoneId)
            .ifEmpty { omDailyList }
            // NWS daily has no UV index (parseNwsDaily leaves uvIndexMax
            // null), so the UV card read "--" even at midday. Merge
            // Open-Meteo's uv_index_max in by date (audit fix D). When
            // the list already came from Open-Meteo it carries uvIndexMax,
            // so this is a no-op for it.
            .let { list ->
                if (list.any { it.uvIndexMax != null }) list else {
                    val omUv = omDailyList.associateBy({ it.date }, { it.uvIndexMax })
                    list.map { pt -> pt.copy(uvIndexMax = omUv[pt.date]) }
                }
            }
            // Today's high is null once the daytime period ends: at
            // 8 PM today's bucket only got a nighttime period, so
            // highC is null and the outlook shows "HI --". Fill null
            // highs/lows from Open-Meteo's daily max/min by date
            // (audit fix, item 3). The hourly list cannot do this:
            // it starts at the current hour, so its max would be the
            // overnight temp, not the daytime high.
            .let { list -> fillDailyHighs(list, omDailyList) }

        // --- 60-minute nowcast (Open-Meteo minutely_15, anchored on now).
        // This is the fallback: when a Tomorrow.io key is set and the
        // "1m" timeline is available, the Tomorrow.io block below
        // replaces these with real 1-minute data.
        val (omMinutely, omMinuteSentence) = WeatherMappers.interpolate15MinTo60Min(
            openMeteoResult?.minutely15?.time,
            openMeteoResult?.minutely15?.precipitation,
            now,
            zoneId
        )

        // --- Alerts. Parsed by the shared parser below (also used by the
        // 3.1 alert worker), so the app and the worker never disagree
        // about which alerts are active.
        val alerts = parseNwsAlerts(alertsResult?.features, now)

        // --- Air quality. Null when the provider returned no AQI.
        // Open-Meteo first (concentrations + fallback AQI); then, when an
        // AirNow key is set, the headline AQI comes from the nearest EPA
        // monitoring station instead (AirNow reports AQI only, no
        // concentrations, so the pollutant values stay Open-Meteo's).
        // Any AirNow failure (bad key, outside US coverage, network)
        // falls back to the Open-Meteo AQI — never a blank screen.
        val aqCurrent = aqResult?.current
        val openMeteoAqi = WeatherMappers.mapAqi(
            usAqi = aqCurrent?.usAqi,
            pm25 = aqCurrent?.pm25,
            pm10 = aqCurrent?.pm10,
            ozone = aqCurrent?.ozone
        )
        val airQuality = runCatching {
            val airNowKey = settingsManager.airNowApiKeyFlow.first()
            if (airNowKey.isBlank()) {
                openMeteoAqi
            } else {
                val observations = airNowApi.getCurrentObservations(lat, lon, apiKey = airNowKey)
                WeatherMappers.mapAirNowAqi(
                    readings = observations.map { AirNowReading(it.parameterName, it.aqi) },
                    openMeteoAqi = openMeteoAqi
                )
            }
        }.getOrNull() ?: openMeteoAqi

        // --- Tomorrow.io: 1-minute precipitation + live pollen.
        // Two independent Timelines calls, only when a key is set and
        // only as part of this refresh (no timer) — free tier is
        // 500 calls/day, and a refresh makes at most two. The "1m"
        // timestep is Enterprise in Tomorrow.io's docs, so a free key
        // may return no 1m timeline; that falls back to the 15-minute
        // Open-Meteo interpolation and the pollen (current index,
        // available on free keys) still flows. A failure of either
        // call is a fallback, never a blank screen. tomorrowWorking
        // records whether the key actually delivered usable data, so
        // the Settings "KEY ACTIVE" pill is honest.
        val tomorrowKey = settingsManager.tomorrowApiKeyFlow.first()
        var minutely = omMinutely
        var minuteSentence = omMinuteSentence
        var pollen = PollenData(
            treeIndex = null,
            grassIndex = null,
            weedIndex = null,
            source = "Tomorrow.io",
            hasKey = tomorrowKey.isNotBlank()
        )
        if (tomorrowKey.isNotBlank()) {
            var tomorrowWorked = false
            // 1-minute precipitation for the next 60 minutes.
            runCatching {
                val resp = tomorrowApi.getTimelines(
                    location = "$lat,$lon",
                    fields = "precipitationIntensity,precipitationProbability",
                    timesteps = "1m",
                    startTime = now.toString(),
                    endTime = now.plus(65, ChronoUnit.MINUTES).toString(),
                    apiKey = tomorrowKey
                )
                val timeline = resp.data?.timelines?.firstOrNull { it.timestep == "1m" }
                val points = timeline?.intervals?.mapNotNull { interval ->
                    val timeStr = interval.time ?: return@mapNotNull null
                    val t = runCatching { Instant.parse(timeStr) }.getOrNull()
                        ?: return@mapNotNull null
                    TomorrowMinutePoint(
                        time = t,
                        mmPerHour = interval.values?.precipitationIntensity,
                        probabilityPct = interval.values?.precipitationProbability?.roundToInt()
                    )
                } ?: emptyList()
                if (points.isNotEmpty()) {
                    val mapped = WeatherMappers.mapTomorrowMinutely(points, now, zoneId)
                    minutely = mapped.first
                    minuteSentence = mapped.second
                    tomorrowWorked = true
                }
            }
            // Live pollen (current index: tree, grass, weed).
            runCatching {
                val resp = tomorrowApi.getTimelines(
                    location = "$lat,$lon",
                    fields = "treeIndex,grassIndex,weedIndex",
                    timesteps = "current",
                    startTime = now.toString(),
                    endTime = now.plus(5, ChronoUnit.MINUTES).toString(),
                    apiKey = tomorrowKey
                )
                val values = resp.data?.timelines
                    ?.firstOrNull { it.timestep == "current" }
                    ?.intervals?.firstOrNull()?.values
                if (values != null) {
                    pollen = WeatherMappers.mapTomorrowPollen(
                        treeIndex = values.treeIndex,
                        grassIndex = values.grassIndex,
                        weedIndex = values.weedIndex,
                        hasKey = true
                    )
                    tomorrowWorked = true
                }
            }
            settingsManager.setTomorrowWorking(tomorrowWorked)
        }

        return FullWeatherState(
            place = resolvedPlace,
            current = current,
            hourly48h = hourlyList,
            daily7d = dailyList,
            minutely60m = minutely,
            minuteSummary = minuteSentence,
            activeAlerts = alerts,
            airQuality = airQuality,
            pollen = pollen,
            solar = solar,
            lunar = lunar,
            radarStation = points?.radarStation,
            forecastZone = points?.forecastZone?.substringAfterLast("/"),
            isStale = false,
            fetchedAt = now
        )
    }

    /**
     * NWS alert features → active alerts. One parser for the main refresh
     * and the 3.1 worker. An alert with no usable times is dropped, not
     * given a made-up expiry; expired alerts are dropped too. An alert
     * with no NWS id gets a stable derived id (a hash of its event, area,
     * and effective time) — never a timestamp — so a refetch produces the
     * same id and dedupe-by-id works.
     */
    private fun parseNwsAlerts(
        features: List<NwsAlertFeature>?,
        now: Instant
    ): List<WeatherAlert> {
        return features?.mapNotNull { feature ->
            val props = feature.properties ?: return@mapNotNull null
            val effective = props.effective?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val expires = props.expires?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val ends = props.ends?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (expires.isBefore(now)) return@mapNotNull null
            val id = props.id ?: stableAlertId(props.event, props.areaDesc, effective)
            WeatherAlert(
                id = id,
                event = props.event ?: "Weather Advisory",
                severity = WeatherMappers.mapAlertSeverity(props.severity),
                urgency = props.urgency ?: "Expected",
                headline = props.headline ?: props.event ?: "Hazardous weather warning",
                description = props.description ?: "",
                instruction = props.instruction,
                effective = effective,
                expires = expires,
                ends = ends,
                senderName = props.senderName,
                areaDesc = props.areaDesc ?: ""
            )
        } ?: emptyList()
    }

    /**
     * Resolves a place's timezone, in priority order:
     * 1. NWS /points `timeZone` (IANA id) — authoritative in NWS coverage.
     * 2. Open-Meteo `utc_offset_seconds` — a fixed offset, correct for the
     *    fetch but without DST rules; used outside NWS coverage.
     * 3. The zone already stored on the place (itself resolved on an earlier
     *    fetch, or the device zone at creation).
     * Never a hardcoded zone.
     */
    private fun resolvePlaceZone(nwsTimeZone: String?, utcOffsetSeconds: Int?, storedZoneId: String): ZoneId {
        nwsTimeZone?.let { runCatching { return ZoneId.of(it) } }
        utcOffsetSeconds?.let { return ZoneOffset.ofTotalSeconds(it) }
        return runCatching { ZoneId.of(storedZoneId) }.getOrDefault(ZoneId.systemDefault())
    }

    /** NWS hourly periods → hourly points, starting at the current hour. */
    private fun parseNwsHourly(periods: List<NwsForecastPeriod>?, now: Instant): List<HourlyPoint> {
        if (periods.isNullOrEmpty()) return emptyList()
        val cutoff = now.minusSeconds(3600)
        return periods.mapNotNull { period ->
            val start = period.startTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            if (start.isBefore(cutoff)) return@mapNotNull null
            val tempC = period.temperature?.let { t ->
                if (period.temperatureUnit == "F") (t - 32.0) * 5.0 / 9.0 else t
            }
            val windMs = parseNwsWindSpeedMph(period.windSpeed)?.let { it * 0.44704 }
            val condition = WeatherMappers.mapNwsTextToCondition(period.shortForecast, period.isDaytime)
            HourlyPoint(
                time = start,
                tempC = tempC,
                popPct = period.probabilityOfPrecipitation?.value?.toInt(),
                precipMm = null,
                condition = condition,
                windMs = windMs,
                isDaytime = period.isDaytime
            )
        }.take(48)
    }

    /** NWS forecast periods (day + night pairs) → daily points. */
    private fun parseNwsDaily(periods: List<NwsForecastPeriod>?, zoneId: ZoneId): List<DailyPoint> {
        if (periods.isNullOrEmpty()) return emptyList()
        data class DayBucket(var highC: Double? = null, var lowC: Double? = null, var popPct: Int? = null, var dayShort: String? = null, var dayCond: WeatherCondition? = null)
        val buckets = linkedMapOf<LocalDate, DayBucket>()
        for (period in periods) {
            val start = period.startTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: continue
            val date = start.atZone(zoneId).toLocalDate()
            val bucket = buckets.getOrPut(date) { DayBucket() }
            val tempC = period.temperature?.let { t ->
                if (period.temperatureUnit == "F") (t - 32.0) * 5.0 / 9.0 else t
            }
            val pop = period.probabilityOfPrecipitation?.value?.toInt()
            if (pop != null) bucket.popPct = maxOf(bucket.popPct ?: 0, pop)
            if (period.isDaytime) {
                bucket.highC = tempC
                bucket.dayShort = period.shortForecast
                bucket.dayCond = WeatherMappers.mapNwsTextToCondition(period.shortForecast, true)
            } else {
                bucket.lowC = tempC
            }
        }
        return buckets.entries.take(7).map { (date, b) ->
            val cond = b.dayCond ?: WeatherCondition.ClearDay
            val summary = b.dayShort?.let { short ->
                val hi = b.highC?.let { "${it.toInt()}°" } ?: "--"
                "$short, high $hi"
            } ?: cond.displayName
            DailyPoint(date, b.highC, b.lowC, b.popPct, cond, summary, null)
        }
    }

    /** "7 mph" or "7 to 12 mph" → first number in mph, or null. */
    private fun parseNwsWindSpeedMph(windSpeed: String?): Double? {
        if (windSpeed.isNullOrBlank()) return null
        return Regex("""(\d+(?:\.\d+)?)""").find(windSpeed)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    /** Open-Meteo hourly fallback: UTC times, starting at the current hour. */
    private fun parseOpenMeteoHourly(
        result: OpenMeteoWeatherResponse?,
        place: Place,
        zoneId: ZoneId,
        now: Instant
    ): List<HourlyPoint> {
        val omHourly = result?.hourly ?: return emptyList()
        val times = omHourly.time ?: return emptyList()
        val cutoff = now.minusSeconds(3600)
        val points = mutableListOf<HourlyPoint>()
        for (i in times.indices) {
            val instant = parseOpenMeteoUtcInstant(times[i]) ?: continue
            if (instant.isBefore(cutoff)) continue
            val code = omHourly.weatherCode?.getOrNull(i)
            val daytime = isDaytimeAt(place.point, instant, zoneId)
            points.add(
                HourlyPoint(
                    time = instant,
                    tempC = omHourly.temperature2m?.getOrNull(i),
                    popPct = omHourly.precipitationProbability?.getOrNull(i),
                    precipMm = omHourly.precipitation?.getOrNull(i),
                    condition = WeatherMappers.mapWmoCodeToCondition(code, daytime),
                    windMs = omHourly.windSpeed10m?.getOrNull(i)?.let { it * 1000.0 / 3600.0 },
                    isDaytime = daytime
                )
            )
            if (points.size >= 48) break
        }
        return points
    }

    /** Open-Meteo daily fallback. */
    private fun parseOpenMeteoDaily(result: OpenMeteoWeatherResponse?, zoneId: ZoneId): List<DailyPoint> {
        val omDaily = result?.daily ?: return emptyList()
        val times = omDaily.time ?: return emptyList()
        val today = LocalDate.now(zoneId)
        val points = mutableListOf<DailyPoint>()
        for (i in times.indices) {
            val date = runCatching { LocalDate.parse(times[i]) }.getOrNull() ?: continue
            if (date.isBefore(today)) continue
            val hi = omDaily.temperature2mMax?.getOrNull(i)
            val lo = omDaily.temperature2mMin?.getOrNull(i)
            val code = omDaily.weatherCode?.getOrNull(i)
            val cond = WeatherMappers.mapWmoCodeToCondition(code, isDaytime = true)
            val summary = hi?.let { "${cond.displayName} with high of ${it.toInt()}°" } ?: cond.displayName
            points.add(
                DailyPoint(
                    date = date,
                    highC = hi,
                    lowC = lo,
                    popPct = omDaily.precipitationProbabilityMax?.getOrNull(i),
                    condition = cond,
                    summary = summary,
                    uvIndexMax = omDaily.uvIndexMax?.getOrNull(i)
                )
            )
            if (points.size >= 7) break
        }
        return points
    }

    // --- Cache: real JSON, one adapter per section. A row whose JSON does not
    // parse (including any old bar-delimited row) is treated as absent.
    private fun <T> listAdapter(clazz: Class<T>) =
        moshi.adapter<List<T>>(Types.newParameterizedType(List::class.java, clazz))

    private suspend fun saveToCache(placeId: String, state: FullWeatherState) {
        val entity = WeatherCacheEntity(
            placeId = placeId,
            cachedAt = state.fetchedAt.toEpochMilli(),
            currentJson = moshi.adapter(CurrentConditions::class.java).toJson(state.current),
            hourlyJson = listAdapter(HourlyPoint::class.java).toJson(state.hourly48h),
            dailyJson = listAdapter(DailyPoint::class.java).toJson(state.daily7d),
            minutelyJson = listAdapter(MinutePrecip::class.java).toJson(state.minutely60m),
            alertsJson = listAdapter(WeatherAlert::class.java).toJson(state.activeAlerts),
            aqiJson = moshi.adapter(com.example.domain.model.AirQuality::class.java).toJson(state.airQuality),
            pollenJson = moshi.adapter(PollenData::class.java).toJson(state.pollen),
            radarStation = state.radarStation,
            forecastZone = state.forecastZone,
            minuteSummary = state.minuteSummary
        )
        weatherCacheDao.saveCache(entity)
    }

    private fun parseCacheToState(place: Place, cache: WeatherCacheEntity, isStale: Boolean): FullWeatherState? {
        return try {
            val now = Instant.now()
            val zoneId = runCatching { ZoneId.of(place.zoneId) }.getOrDefault(ZoneId.systemDefault())
            val solar = SolarCalculator.calculate(place.point, now, zoneId)
            val lunar = LunarCalculator.calculate(now)

            val current = moshi.adapter(CurrentConditions::class.java).fromJson(cache.currentJson)
                ?: return null
            val hourly = listAdapter(HourlyPoint::class.java).fromJson(cache.hourlyJson)
                ?: return null
            val daily = listAdapter(DailyPoint::class.java).fromJson(cache.dailyJson)
                ?: return null
            val minutely = listAdapter(MinutePrecip::class.java).fromJson(cache.minutelyJson)
                ?: return null
            val alerts = (listAdapter(WeatherAlert::class.java).fromJson(cache.alertsJson)
                ?: return null).filter { !it.expires.isBefore(now) }
            val airQuality = moshi.adapter(com.example.domain.model.AirQuality::class.java)
                .fromJson(cache.aqiJson)
            val pollen = moshi.adapter(PollenData::class.java).fromJson(cache.pollenJson)
                ?: PollenData(null, null, null, "Tomorrow.io", false)

            FullWeatherState(
                place = place,
                current = current,
                hourly48h = hourly,
                daily7d = daily,
                minutely60m = minutely,
                minuteSummary = cache.minuteSummary,
                activeAlerts = alerts,
                airQuality = airQuality,
                pollen = pollen,
                solar = solar,
                lunar = lunar,
                radarStation = cache.radarStation,
                forecastZone = cache.forecastZone,
                isStale = isStale,
                fetchedAt = Instant.ofEpochMilli(cache.cachedAt)
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** The one fixed id for the device's current location (see 1.6). */
        const val CURRENT_LOCATION_ID = "current_location"

        /** Two places closer than this are the same place (see 1.6). */
        const val NEARBY_PLACE_METERS = 500.0

        /** Regional map (R5): neighbours sit this far from the centre
         * place, at the 8 compass bearings — roughly the spacing of the
         * classic stills' cities (~50 mi). One constant, easy to tune. */
        const val REGIONAL_NEIGHBOUR_DISTANCE_METERS = 80_000.0
    }
}

private fun SavedPlaceEntity.toPlace(): Place = Place(
    id = id,
    name = name,
    stateCode = stateCode,
    point = GeoPoint(lat, lon),
    zoneId = zoneId,
    isPreset = isPreset
)
