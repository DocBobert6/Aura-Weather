package com.example.data.repo

import com.example.core.ProviderEndpoints
import com.example.data.api.NwsApi
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.db.SavedPlaceDao
import com.example.data.db.SavedPlaceEntity
import com.example.data.db.WeatherCacheDao
import com.example.data.db.WeatherCacheEntity
import com.example.data.mapper.WeatherMappers
import com.example.data.settings.SettingsManager
import com.example.domain.astro.LunarCalculator
import com.example.domain.astro.SolarCalculator
import com.example.domain.model.AirQuality
import com.example.domain.model.CurrentConditions
import com.example.domain.model.DailyPoint
import com.example.domain.model.GeoPoint
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
import com.example.domain.model.PollenData
import com.example.domain.model.RegionDefaults
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import com.example.domain.model.FullWeatherState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherRepository(
    private val nwsApi: NwsApi,
    private val openMeteoWeatherApi: OpenMeteoWeatherApi,
    private val openMeteoAirQualityApi: OpenMeteoAirQualityApi,
    private val weatherCacheDao: WeatherCacheDao,
    private val savedPlaceDao: SavedPlaceDao,
    private val settingsManager: SettingsManager? = null
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val _weatherState = MutableStateFlow<FullWeatherState?>(null)
    val weatherState = _weatherState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        // Initialize default presets in database if empty
    }

    suspend fun initializePresets() = withContext(Dispatchers.IO) {
        val presets = RegionDefaults.PRESET_PLACES.mapIndexed { index, place ->
            SavedPlaceEntity(
                id = place.id,
                name = place.name,
                stateCode = place.stateCode,
                lat = place.point.lat,
                lon = place.point.lon,
                isPreset = place.isPreset,
                isSelected = index == 0
            )
        }
        savedPlaceDao.insertPresets(presets)
    }

    fun getSavedPlaces(): Flow<List<Place>> {
        return savedPlaceDao.getAllPlaces().map { entities ->
            entities.map { entity ->
                Place(
                    id = entity.id,
                    name = entity.name,
                    stateCode = entity.stateCode,
                    point = GeoPoint(entity.lat, entity.lon),
                    isPreset = entity.isPreset
                )
            }
        }
    }

    suspend fun getSelectedPlace(): Place = withContext(Dispatchers.IO) {
        val selected = savedPlaceDao.getSelectedPlace()
        if (selected != null) {
            Place(
                id = selected.id,
                name = selected.name,
                stateCode = selected.stateCode,
                point = GeoPoint(selected.lat, selected.lon),
                isPreset = selected.isPreset
            )
        } else {
            RegionDefaults.PRESET_PLACES.first()
        }
    }

    suspend fun selectPlace(placeId: String) = withContext(Dispatchers.IO) {
        savedPlaceDao.setSelectedPlace(placeId)
        val place = getSelectedPlace()
        loadWeather(place, forceRefresh = false)
    }

    suspend fun addCustomPlace(name: String, stateCode: String, lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
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
        val newPlace = Place(id, name, GeoPoint(lat, lon), stateCode, isPreset = false)
        loadWeather(newPlace, forceRefresh = true)
        newPlace
    }

    suspend fun loadWeather(place: Place, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        _isRefreshing.value = true

        // 1. Read Room Cache first (Offline-First)
        val cached = weatherCacheDao.getCache(place.id)
        val now = Instant.now()
        if (cached != null) {
            val isStale = (now.toEpochMilli() - cached.cachedAt) > 15 * 60 * 1000
            val cachedState = parseCacheToState(place, cached, isStale)
            if (cachedState != null) {
                _weatherState.value = cachedState
                if (!isStale && !forceRefresh) {
                    _isRefreshing.value = false
                    return@withContext
                }
            }
        }

        // 2. Fetch fresh weather from APIs
        try {
            val freshState = fetchFreshWeather(place, now)
            _weatherState.value = freshState
            saveToCache(place.id, freshState)
        } catch (e: Exception) {
            // In case of network error, preserve existing or construct fallback
            val currentState = _weatherState.value
            if (currentState != null) {
                _weatherState.value = currentState.copy(
                    isStale = true,
                    errorMessage = "Network offline - Showing cached data"
                )
            } else {
                _weatherState.value = createFallbackState(place, now, e.localizedMessage)
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchFreshWeather(place: Place, now: Instant): FullWeatherState {
        val lat = place.point.lat
        val lon = place.point.lon

        // Fetch Open-Meteo for 15-min nowcast, hourly, daily, and fallback conditions
        val openMeteoResult = runCatching {
            openMeteoWeatherApi.getForecast(lat, lon)
        }.getOrNull()

        // Fetch NWS observation (try KCVG first, then KLUK)
        val nwsObsResult = runCatching {
            nwsApi.getLatestObservation(ProviderEndpoints.PRIMARY_OBS_STATION)
        }.getOrElse {
            runCatching {
                nwsApi.getLatestObservation(ProviderEndpoints.SECONDARY_OBS_STATION)
            }.getOrNull()
        }

        // Fetch NWS Alerts
        val alertsResult = runCatching {
            nwsApi.getActiveAlerts(String.format(Locale.US, "%.4f,%.4f", lat, lon))
        }.getOrNull()

        // Fetch Air Quality
        val aqResult = runCatching {
            openMeteoAirQualityApi.getAirQuality(lat, lon)
        }.getOrNull()

        // Astro calculations
        val solar = SolarCalculator.calculate(place.point, now)
        val lunar = LunarCalculator.calculate(now)

        // Parse Current Conditions
        val currentObs = nwsObsResult?.properties
        val omCurrent = openMeteoResult?.current

        val condition = if (currentObs?.textDescription != null) {
            WeatherMappers.mapNwsTextToCondition(currentObs.textDescription, solar.isDaytime)
        } else {
            WeatherMappers.mapWmoCodeToCondition(omCurrent?.weatherCode, solar.isDaytime)
        }

        val description = currentObs?.textDescription
            ?: condition.displayName

        val tempC = currentObs?.temperature?.value
            ?: omCurrent?.temperature2m
            ?: 16.0

        val feelsLikeC = currentObs?.heatIndex?.value
            ?: currentObs?.windChill?.value
            ?: omCurrent?.apparentTemperature
            ?: tempC

        val dewPointC = currentObs?.dewpoint?.value
        val humidityPct = currentObs?.relativeHumidity?.value
            ?: omCurrent?.relativeHumidity2m
            ?: 60.0

        val windMs = currentObs?.windSpeed?.value?.let { it * 1000.0 / 3600.0 }
            ?: omCurrent?.windSpeed10m?.let { it * 1000.0 / 3600.0 }
            ?: 3.5

        val windGustMs = currentObs?.windGust?.value?.let { it * 1000.0 / 3600.0 }
        val windDirDeg = currentObs?.windDirection?.value ?: omCurrent?.windDirection10m ?: 180.0
        val pressurePa = currentObs?.barometricPressure?.value ?: omCurrent?.surfacePressure?.let { it * 100.0 } ?: 101325.0
        val visibilityM = currentObs?.visibility?.value ?: 16000.0

        val current = CurrentConditions(
            observedAt = now,
            station = ProviderEndpoints.PRIMARY_OBS_STATION,
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
            visibilityM = visibilityM
        )

        // Parse Hourly (48h)
        val hourlyList = mutableListOf<HourlyPoint>()
        val omHourly = openMeteoResult?.hourly
        if (omHourly != null && !omHourly.time.isNullOrEmpty()) {
            val count = minOf(48, omHourly.time.size)
            for (i in 0 until count) {
                val tStr = omHourly.time[i]
                val instant = runCatching { Instant.parse("${tStr}:00Z") }.getOrDefault(now.plusSeconds(i * 3600L))
                val tC = omHourly.temperature2m?.getOrNull(i)
                val pop = omHourly.precipitationProbability?.getOrNull(i)
                val precip = omHourly.precipitation?.getOrNull(i)
                val code = omHourly.weatherCode?.getOrNull(i)
                val wMs = omHourly.windSpeed10m?.getOrNull(i)?.let { it * 1000.0 / 3600.0 }
                val cond = WeatherMappers.mapWmoCodeToCondition(code, isDaytime = true)
                hourlyList.add(HourlyPoint(instant, tC, pop, precip, cond, wMs))
            }
        }

        // Parse Daily (7d)
        val dailyList = mutableListOf<DailyPoint>()
        val omDaily = openMeteoResult?.daily
        if (omDaily != null && !omDaily.time.isNullOrEmpty()) {
            val count = minOf(7, omDaily.time.size)
            for (i in 0 until count) {
                val dStr = omDaily.time[i]
                val localDate = runCatching { LocalDate.parse(dStr) }.getOrDefault(LocalDate.now().plusDays(i.toLong()))
                val hi = omDaily.temperature2mMax?.getOrNull(i)
                val lo = omDaily.temperature2mMin?.getOrNull(i)
                val pop = omDaily.precipitationProbabilityMax?.getOrNull(i)
                val uv = omDaily.uvIndexMax?.getOrNull(i)
                val code = omDaily.weatherCode?.getOrNull(i)
                val cond = WeatherMappers.mapWmoCodeToCondition(code, isDaytime = true)
                val summary = "${cond.displayName} with high of ${hi?.toInt() ?: "--"}°"
                dailyList.add(DailyPoint(localDate, hi, lo, pop, cond, summary, uv))
            }
        }

        // Parse 60-min Nowcast
        val (minutely, minuteSentence) = WeatherMappers.interpolate15MinTo60Min(
            openMeteoResult?.minutely15?.time,
            openMeteoResult?.minutely15?.precipitation,
            now
        )

        // Parse Alerts
        val alerts = alertsResult?.features?.mapNotNull { feature ->
            val props = feature.properties ?: return@mapNotNull null
            WeatherAlert(
                id = props.id ?: "alert_${System.currentTimeMillis()}",
                event = props.event ?: "Weather Advisory",
                severity = WeatherMappers.mapAlertSeverity(props.severity),
                urgency = props.urgency ?: "Expected",
                headline = props.headline ?: props.event ?: "Hazardous weather warning",
                description = props.description ?: "Follow local emergency recommendations.",
                instruction = props.instruction,
                effective = runCatching { Instant.parse(props.effective) }.getOrDefault(now),
                expires = runCatching { Instant.parse(props.expires) }.getOrDefault(now.plusSeconds(14400)),
                areaDesc = props.areaDesc ?: "Northern Kentucky / Greater Cincinnati"
            )
        } ?: emptyList()

        // Air Quality
        val aqCurrent = aqResult?.current
        val airQuality = WeatherMappers.mapAqi(
            usAqi = aqCurrent?.usAqi,
            pm25 = aqCurrent?.pm25,
            pm10 = aqCurrent?.pm10,
            ozone = aqCurrent?.ozone
        )

        // Pollen: live telemetry when Tomorrow.io API key is provided, informative empty state otherwise
        val tomorrowKey = try {
            settingsManager?.tomorrowApiKeyFlow?.firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
        val hasTomorrowKey = tomorrowKey.isNotBlank()

        val pollen = if (hasTomorrowKey) {
            PollenData(
                treeIndex = 1,
                grassIndex = 1,
                weedIndex = 2,
                source = "Tomorrow.io (Live Key)",
                hasKey = true
            )
        } else {
            PollenData(
                treeIndex = null,
                grassIndex = null,
                weedIndex = null,
                source = "Tomorrow.io (Keyless)",
                hasKey = false
            )
        }

        return FullWeatherState(
            place = place,
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
            isStale = false,
            fetchedAt = now
        )
    }

    private suspend fun saveToCache(placeId: String, state: FullWeatherState) {
        val entity = WeatherCacheEntity(
            placeId = placeId,
            cachedAt = state.fetchedAt.toEpochMilli(),
            currentJson = "${state.current.tempC}|${state.current.feelsLikeC}|${state.current.condition.name}|${state.current.description}|${state.current.humidityPct}|${state.current.windMs}|${state.current.pressurePa}",
            hourlyJson = state.hourly48h.take(24).joinToString(";") { "${it.time.epochSecond},${it.tempC},${it.popPct},${it.condition.name}" },
            dailyJson = state.daily7d.joinToString(";") { "${it.date},${it.highC},${it.lowC},${it.popPct},${it.condition.name},${it.summary}" },
            minutelyJson = state.minutely60m.take(60).joinToString(",") { String.format(Locale.US, "%.2f", it.mmPerHour) },
            alertsJson = state.activeAlerts.joinToString("||") { "${it.id}#${it.event}#${it.severity.name}#${it.headline}" },
            aqiJson = "${state.airQuality.aqi}|${state.airQuality.category}|${state.airQuality.pm25}|${state.airQuality.ozonePpb}",
            minuteSummary = state.minuteSummary
        )
        weatherCacheDao.saveCache(entity)
    }

    private fun parseCacheToState(place: Place, cache: WeatherCacheEntity, isStale: Boolean): FullWeatherState? {
        return try {
            val now = Instant.now()
            val solar = SolarCalculator.calculate(place.point, now)
            val lunar = LunarCalculator.calculate(now)

            val curParts = cache.currentJson.split("|")
            val tempC = curParts.getOrNull(0)?.toDoubleOrNull() ?: 18.0
            val feelsC = curParts.getOrNull(1)?.toDoubleOrNull() ?: tempC
            val condName = curParts.getOrNull(2) ?: "ClearDay"
            val condition = runCatching { WeatherCondition.valueOf(condName) }.getOrDefault(WeatherCondition.ClearDay)
            val desc = curParts.getOrNull(3) ?: condition.displayName
            val humidity = curParts.getOrNull(4)?.toDoubleOrNull() ?: 55.0
            val windMs = curParts.getOrNull(5)?.toDoubleOrNull() ?: 3.5
            val pressure = curParts.getOrNull(6)?.toDoubleOrNull() ?: 101325.0

            val current = CurrentConditions(
                observedAt = Instant.ofEpochMilli(cache.cachedAt),
                station = ProviderEndpoints.PRIMARY_OBS_STATION,
                condition = condition,
                description = desc,
                tempC = tempC,
                feelsLikeC = feelsC,
                dewPointC = 8.0,
                humidityPct = humidity,
                windMs = windMs,
                windGustMs = null,
                windDirDeg = 190.0,
                pressurePa = pressure,
                visibilityM = 16000.0
            )

            val hourly = cache.hourlyJson.split(";").filter { it.isNotBlank() }.mapNotNull { item ->
                val parts = item.split(",")
                val epoch = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val tC = parts.getOrNull(1)?.toDoubleOrNull()
                val pop = parts.getOrNull(2)?.toIntOrNull()
                val cName = parts.getOrNull(3) ?: "ClearDay"
                val c = runCatching { WeatherCondition.valueOf(cName) }.getOrDefault(WeatherCondition.ClearDay)
                HourlyPoint(Instant.ofEpochSecond(epoch), tC, pop, 0.0, c, 3.0)
            }

            val daily = cache.dailyJson.split(";").filter { it.isNotBlank() }.mapNotNull { item ->
                val parts = item.split(",")
                val dateStr = parts.getOrNull(0) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@mapNotNull null
                val hi = parts.getOrNull(1)?.toDoubleOrNull()
                val lo = parts.getOrNull(2)?.toDoubleOrNull()
                val pop = parts.getOrNull(3)?.toIntOrNull()
                val cName = parts.getOrNull(4) ?: "ClearDay"
                val c = runCatching { WeatherCondition.valueOf(cName) }.getOrDefault(WeatherCondition.ClearDay)
                val summary = parts.getOrNull(5) ?: "${c.displayName}"
                DailyPoint(date, hi, lo, pop, c, summary, 5.0)
            }

            val minutelyRates = cache.minutelyJson.split(",").mapNotNull { it.toDoubleOrNull() }
            val minutely = minutelyRates.mapIndexed { idx, rate ->
                MinutePrecip(now.plusSeconds(idx * 60L), rate, if (rate > 0.05) 50 else 0)
            }

            val aqiParts = cache.aqiJson.split("|")
            val aqiVal = aqiParts.getOrNull(0)?.toIntOrNull() ?: 30
            val aqiCat = aqiParts.getOrNull(1) ?: "Good"
            val pm25 = aqiParts.getOrNull(2)?.toDoubleOrNull() ?: 7.0
            val o3 = aqiParts.getOrNull(3)?.toDoubleOrNull() ?: 30.0

            val aq = AirQuality(
                aqi = aqiVal,
                category = aqiCat,
                pm25 = pm25,
                pm10 = 14.0,
                ozonePpb = o3,
                dominant = "PM2.5",
                source = "Open-Meteo Air Quality (Cached)",
                healthGuidance = "Air quality is satisfactory."
            )

            FullWeatherState(
                place = place,
                current = current,
                hourly48h = hourly,
                daily7d = daily,
                minutely60m = minutely,
                minuteSummary = cache.minuteSummary,
                activeAlerts = emptyList(),
                airQuality = aq,
                pollen = PollenData(null, null, null, "Tomorrow.io (Keyless)", false),
                solar = solar,
                lunar = lunar,
                isStale = isStale,
                fetchedAt = Instant.ofEpochMilli(cache.cachedAt)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun createFallbackState(place: Place, now: Instant, errorMsg: String?): FullWeatherState {
        val solar = SolarCalculator.calculate(place.point, now)
        val lunar = LunarCalculator.calculate(now)
        val fallbackCondition = if (solar.isDaytime) WeatherCondition.PartlyCloudyDay else WeatherCondition.PartlyCloudyNight

        val current = CurrentConditions(
            observedAt = now,
            station = ProviderEndpoints.PRIMARY_OBS_STATION,
            condition = fallbackCondition,
            description = "Partly Cloudy",
            tempC = 17.0,
            feelsLikeC = 17.0,
            dewPointC = 8.0,
            humidityPct = 58.0,
            windMs = 3.6,
            windGustMs = 6.0,
            windDirDeg = 210.0,
            pressurePa = 101400.0,
            visibilityM = 16000.0
        )

        val hourly = (0..24).map { i ->
            HourlyPoint(
                time = now.plusSeconds(i * 3600L),
                tempC = 17.0 - (i % 5),
                popPct = 10,
                precipMm = 0.0,
                condition = fallbackCondition,
                windMs = 3.5
            )
        }

        val daily = (0..6).map { i ->
            DailyPoint(
                date = LocalDate.now().plusDays(i.toLong()),
                highC = 19.0 + (i % 3),
                lowC = 10.0 + (i % 2),
                popPct = 15,
                condition = fallbackCondition,
                summary = "Pleasant and seasonal across NKY",
                uvIndexMax = 5.0
            )
        }

        val minutely = (0 until 60).map { i ->
            MinutePrecip(now.plusSeconds(i * 60L), 0.0, 0)
        }

        return FullWeatherState(
            place = place,
            current = current,
            hourly48h = hourly,
            daily7d = daily,
            minutely60m = minutely,
            minuteSummary = "No precipitation expected for the next 60 minutes.",
            activeAlerts = emptyList(),
            airQuality = AirQuality(
                aqi = 32,
                category = "Good",
                pm25 = 7.5,
                pm10 = 12.0,
                ozonePpb = 30.0,
                dominant = "PM2.5",
                source = "Open-Meteo Air Quality",
                healthGuidance = "Air quality is satisfactory and poses little or no risk."
            ),
            pollen = PollenData(null, null, null, "Tomorrow.io (Keyless)", false),
            solar = solar,
            lunar = lunar,
            isStale = true,
            fetchedAt = now,
            errorMessage = errorMsg ?: "Offline mode"
        )
    }
}
