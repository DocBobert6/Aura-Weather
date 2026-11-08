package com.example.domain.model

import java.time.Instant
import java.time.LocalDate

data class GeoPoint(val lat: Double, val lon: Double)

data class Place(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val stateCode: String = "KY",
    val zoneId: String = "America/New_York",
    val isPreset: Boolean = false
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
    val visibilityM: Double?
)

data class HourlyPoint(
    val time: Instant,
    val tempC: Double?,
    val popPct: Int?,
    val precipMm: Double?,
    val condition: WeatherCondition,
    val windMs: Double?
)

data class DailyPoint(
    val date: LocalDate,
    val highC: Double?,
    val lowC: Double?,
    val popPct: Int?,
    val condition: WeatherCondition,
    val summary: String,
    val uvIndexMax: Double? = null
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
    val isWaxing: Boolean
)

data class FullWeatherState(
    val place: Place,
    val current: CurrentConditions,
    val hourly48h: List<HourlyPoint>,
    val daily7d: List<DailyPoint>,
    val minutely60m: List<MinutePrecip>,
    val minuteSummary: String,
    val activeAlerts: List<WeatherAlert>,
    val airQuality: AirQuality,
    val pollen: PollenData,
    val solar: SolarTimes,
    val lunar: LunarTimes,
    val isStale: Boolean = false,
    val fetchedAt: Instant = Instant.now(),
    val errorMessage: String? = null
)
