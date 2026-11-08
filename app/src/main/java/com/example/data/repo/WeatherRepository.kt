package com.example.data.repo

import com.example.data.api.NwsApi
import com.example.data.api.NwsForecastPeriod
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.api.OpenMeteoWeatherResponse
import com.example.data.db.SavedPlaceDao
import com.example.data.db.SavedPlaceEntity
import com.example.data.db.WeatherCacheDao
import com.example.data.db.WeatherCacheEntity
import com.example.data.mapper.InstantJsonAdapter
import com.example.data.mapper.LocalDateJsonAdapter
import com.example.data.mapper.WeatherMappers
import com.example.data.mapper.distanceMeters
import com.example.data.mapper.isDaytimeAt
import com.example.data.mapper.parseOpenMeteoUtcInstant
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
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
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

    suspend fun loadWeather(place: Place, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        _isRefreshing.value = true

        // 1. Read Room cache first (offline-first). A cache row in the old
        // pipe-delimited format cannot be parsed and is treated as absent.
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
        val resolvedPlace = place.copy(zoneId = zoneId.id)
        if (resolvedPlace.zoneId != place.zoneId) {
            runCatching { savedPlaceDao.updateZoneId(place.id, zoneId.id) }
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
        val dailyList = parseNwsDaily(nwsForecastResult?.properties?.periods, zoneId)
            .ifEmpty { parseOpenMeteoDaily(openMeteoResult, zoneId) }

        // --- 60-minute nowcast (Open-Meteo minutely_15, anchored on now).
        val (minutely, minuteSentence) = WeatherMappers.interpolate15MinTo60Min(
            openMeteoResult?.minutely15?.time,
            openMeteoResult?.minutely15?.precipitation,
            now,
            zoneId
        )

        // --- Alerts. An alert with no usable id or times is dropped, not
        // given a made-up id or expiry; expired alerts are dropped too.
        val alerts = alertsResult?.features?.mapNotNull { feature ->
            val props = feature.properties ?: return@mapNotNull null
            val id = props.id ?: return@mapNotNull null
            val effective = props.effective?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val expires = props.expires?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val ends = props.ends?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (expires.isBefore(now)) return@mapNotNull null
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
                areaDesc = props.areaDesc ?: ""
            )
        } ?: emptyList()

        // --- Air quality. Null when the provider returned no AQI.
        val aqCurrent = aqResult?.current
        val airQuality = WeatherMappers.mapAqi(
            usAqi = aqCurrent?.usAqi,
            pm25 = aqCurrent?.pm25,
            pm10 = aqCurrent?.pm10,
            ozone = aqCurrent?.ozone
        )

        // --- Pollen. There is no live pollen provider wired up (the
        // Tomorrow.io key is stored but no API is ever called), so the
        // honest value is empty — never the old hardcoded 1/1/2.
        val pollen = PollenData(
            treeIndex = null,
            grassIndex = null,
            weedIndex = null,
            source = "Tomorrow.io (not yet connected)",
            hasKey = false
        )

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
    // parse (including any old pipe-delimited row) is treated as absent.
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
                ?: PollenData(null, null, null, "Tomorrow.io (not yet connected)", false)

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
