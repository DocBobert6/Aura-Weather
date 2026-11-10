package com.example.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GeoPoint(val lat: Double, val lon: Double)

data class Place(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val stateCode: String = "",
    // Resolved per place (NWS /points timeZone, else Open-Meteo UTC offset).
    // The device zone is only a transient starting point, never a place default.
    val zoneId: String = ZoneId.systemDefault().id,
    val isPreset: Boolean = false
)

/**
 * One plotted point on the Home regional map (R5): the current place
 * (isCentre) or one neighbour ~80 km away. tempC is null only for the
 * centre when the loaded state has no temperature; neighbours with no
 * temperature are omitted by the repository, never plotted with an
 * invented value. label is a real city name (NWS relativeLocation) or a
 * compass label ("NE") — never an invented city name.
 */
data class RegionalPoint(
    val point: GeoPoint,
    val label: String,
    val tempC: Double?,
    val condition: WeatherCondition,
    val isDaytime: Boolean = true,
    val isCentre: Boolean = false
)

enum class WeatherCondition(val displayName: String) {
    ClearDay("Clear"),
    ClearNight("Clear"),
    PartlyCloudyDay("Partly Cloudy"),
    PartlyCloudyNight("Partly Cloudy"),
    Cloudy("Cloudy"),
    Fog("Fog"),
    Haze("Hazy"),
    Drizzle("Drizzle"),
    Rain("Rain"),
    HeavyRain("Heavy Rain"),
    Thunderstorm("Thunderstorm"),
    Snow("Snow"),
    Sleet("Sleet"),
    FreezingRain("Freezing Rain"),
    Windy("Windy"),
    Unknown("Fair")
}

data class CurrentConditions(
    val observedAt: Instant,
    val station: String?,
    val condition: WeatherCondition,
    val description: String,
    val tempC: Double?,
    val feelsLikeC: Double?,
    val dewPointC: Double?,
    val humidityPct: Double?,
    val windMs: Double?,
    val windGustMs: Double?,
    val windDirDeg: Double?,
    val pressurePa: Double?,
    val visibilityM: Double?,
    val cloudCoverPct: Double? = null
)

data class HourlyPoint(
    val time: Instant,
    val tempC: Double?,
    val popPct: Int?,
    val precipMm: Double?,
    val condition: WeatherCondition,
    val windMs: Double?,
    // Day/night for this exact hour (from NWS isDaytime, or solar position).
    // Never assume daytime: a 2 AM entry must render a night icon.
    val isDaytime: Boolean = true,
    // Snowfall for this hour, in cm (Open-Meteo's snowfall unit).
    // Null when the source has no snowfall (e.g. NWS hourly).
    val snowfallCm: Double? = null
)

data class DailyPoint(
    val date: LocalDate,
    val highC: Double?,
    val lowC: Double?,
    val popPct: Int?,
    val condition: WeatherCondition,
    val summary: String,
    val uvIndexMax: Double? = null,
    // Precipitation sum for the day, in mm (Open-Meteo precipitation_sum).
    val precipSumMm: Double? = null,
    // Snowfall sum for the day, in cm (Open-Meteo snowfall_sum unit).
    val snowfallSumCm: Double? = null
)

data class MinutePrecip(
    val time: Instant,
    val mmPerHour: Double,
    val probabilityPct: Int?
)

enum class NowcastResolution { OneMinute, FifteenMinute }

enum class AlertSeverity { Extreme, Severe, Moderate, Minor, Unknown }

data class WeatherAlert(
    val id: String,
    val event: String,
    val severity: AlertSeverity,
    val urgency: String,
    val headline: String,
    val description: String,
    val instruction: String?,
    val effective: Instant,
    val expires: Instant,
    // When the event itself ends (NWS "ends"); null when the feed omits it.
    // The pill shows this, not "expires" (when the alert product expires).
    val ends: Instant?,
    val senderName: String?,
    val areaDesc: String
)

data class AirQuality(
    val aqi: Int,
    val category: String,
    val pm25: Double?,
    val pm10: Double?,
    val ozonePpb: Double?,
    val dominant: String?,
    val source: String,
    val healthGuidance: String
)

data class PollenData(
    val treeIndex: Int?,
    val grassIndex: Int?,
    val weedIndex: Int?,
    val source: String,
    val hasKey: Boolean
)

data class SolarTimes(
    val sunrise: Instant,
    val sunset: Instant,
    val solarNoon: Instant,
    val civilDawn: Instant,
    val civilDusk: Instant,
    val goldenHourMorningStart: Instant,
    val goldenHourMorningEnd: Instant,
    val goldenHourEveningStart: Instant,
    val goldenHourEveningEnd: Instant,
    val blueHourMorningStart: Instant,
    val blueHourMorningEnd: Instant,
    val blueHourEveningStart: Instant,
    val blueHourEveningEnd: Instant,
    val dayLengthMinutes: Long,
    val dayLengthDeltaYesterdayMinutes: Long,
    val currentSunElevation: Double,
    val currentSunAzimuth: Double,
    val isDaytime: Boolean
)

data class LunarTimes(
    val phaseName: String,
    val illuminatedFraction: Double,
    val phaseAgeDays: Double,
    val nextNewMoonDays: Int,
    val nextFullMoonDays: Int,
    val isWaxing: Boolean,
    val moonrise: Instant? = null,
    val moonset: Instant? = null
)

data class FullWeatherState(
    val place: Place,
    val current: CurrentConditions,
    val hourly48h: List<HourlyPoint>,
    val daily14d: List<DailyPoint>,
    val minutely60m: List<MinutePrecip>,
    val minuteSummary: String,
    val activeAlerts: List<WeatherAlert>,
    // Null when the air-quality provider returned nothing: the UI shows
    // "unavailable", never an invented AQI.
    val airQuality: AirQuality?,
    val pollen: PollenData,
    val solar: SolarTimes,
    val lunar: LunarTimes,
    // Per-place data resolved from NWS /points for this place (null outside
    // NWS coverage or when /points failed). Never a hardcoded station/zone.
    val radarStation: String? = null,
    val forecastZone: String? = null,
    val isStale: Boolean = false,
    val fetchedAt: Instant = Instant.now(),
    val errorMessage: String? = null
)
