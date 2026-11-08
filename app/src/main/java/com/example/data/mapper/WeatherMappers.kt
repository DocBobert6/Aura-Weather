package com.example.data.mapper

import com.example.domain.model.AirQuality
import com.example.domain.model.AlertSeverity
import com.example.domain.model.MinutePrecip
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object WeatherMappers {

    fun mapWmoCodeToCondition(code: Int?, isDaytime: Boolean = true): WeatherCondition {
        if (code == null) return if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        return when (code) {
            0 -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
            1, 2 -> if (isDaytime) WeatherCondition.PartlyCloudyDay else WeatherCondition.PartlyCloudyNight
            3 -> WeatherCondition.Cloudy
            45, 48 -> WeatherCondition.Fog
            51, 53, 55 -> WeatherCondition.Drizzle
            56, 57 -> WeatherCondition.FreezingRain
            61, 63 -> WeatherCondition.Rain
            65 -> WeatherCondition.HeavyRain
            66, 67 -> WeatherCondition.FreezingRain
            71, 73, 75, 77 -> WeatherCondition.Snow
            80, 81 -> WeatherCondition.Rain
            82 -> WeatherCondition.HeavyRain
            85, 86 -> WeatherCondition.Snow
            95, 96, 99 -> WeatherCondition.Thunderstorm
            else -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        }
    }

    fun mapNwsTextToCondition(text: String?, isDaytime: Boolean = true): WeatherCondition {
        val lower = text?.lowercase(Locale.ROOT) ?: ""
        return when {
            lower.contains("thunder") || lower.contains("t-storm") -> WeatherCondition.Thunderstorm
            lower.contains("heavy rain") || lower.contains("downpour") -> WeatherCondition.HeavyRain
            lower.contains("rain") || lower.contains("showers") -> WeatherCondition.Rain
            lower.contains("drizzle") || lower.contains("sprinkles") -> WeatherCondition.Drizzle
            lower.contains("snow") || lower.contains("flurries") || lower.contains("blizzard") -> WeatherCondition.Snow
            lower.contains("sleet") || lower.contains("ice pellets") -> WeatherCondition.Sleet
            lower.contains("freezing rain") -> WeatherCondition.FreezingRain
            lower.contains("fog") || lower.contains("mist") -> WeatherCondition.Fog
            lower.contains("haze") || lower.contains("smoke") -> WeatherCondition.Haze
            lower.contains("wind") || lower.contains("breezy") -> WeatherCondition.Windy
            lower.contains("overcast") || lower.contains("cloudy") -> WeatherCondition.Cloudy
            lower.contains("partly") -> if (isDaytime) WeatherCondition.PartlyCloudyDay else WeatherCondition.PartlyCloudyNight
            lower.contains("sunny") || lower.contains("clear") || lower.contains("fair") ->
                if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
            else -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        }
    }

    fun interpolate15MinTo60Min(
        times: List<String>?,
        precip15: List<Double>?,
        now: Instant = Instant.now()
    ): Pair<List<MinutePrecip>, String> {
        val minutePoints = mutableListOf<MinutePrecip>()
        val startSec = now.epochSecond

        val rawRates = precip15 ?: emptyList()
        // Default curve points for 4 intervals (0m, 15m, 30m, 45m, 60m)
        val p0 = rawRates.getOrNull(0) ?: 0.0
        val p15 = rawRates.getOrNull(1) ?: p0
        val p30 = rawRates.getOrNull(2) ?: p15
        val p45 = rawRates.getOrNull(3) ?: p30
        val p60 = rawRates.getOrNull(4) ?: p45

        for (m in 0 until 60) {
            val pointTime = Instant.ofEpochSecond(startSec + m * 60)
            val mmPerHour: Double = when {
                m < 15 -> lerp(p0, p15, m / 15.0)
                m < 30 -> lerp(p15, p30, (m - 15) / 15.0)
                m < 45 -> lerp(p30, p45, (m - 30) / 15.0)
                else -> lerp(p45, p60, (m - 45) / 15.0)
            }
            val prob = if (mmPerHour > 0.05) (mmPerHour * 40.0).roundToInt().coerceIn(20, 100) else 0
            minutePoints.add(MinutePrecip(pointTime, mmPerHour, prob))
        }

        val sentence = generateNowcastSentence(minutePoints, now)
        return Pair(minutePoints, sentence)
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction.coerceIn(0.0, 1.0)
    }

    private fun generateNowcastSentence(points: List<MinutePrecip>, now: Instant): String {
        val anyPrecip = points.any { it.mmPerHour > 0.1 }
        if (!anyPrecip) {
            return "No precipitation expected for the next 60 minutes."
        }

        val firstPrecipIndex = points.indexOfFirst { it.mmPerHour > 0.1 }
        val maxIntensity = points.maxOf { it.mmPerHour }
        val intensityDesc = when {
            maxIntensity < 0.5 -> "Drizzle"
            maxIntensity < 2.5 -> "Light rain"
            maxIntensity < 7.6 -> "Moderate rain"
            else -> "Heavy rain"
        }

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneId.of("America/New_York"))

        return if (firstPrecipIndex <= 2) {
            val stopIndex = points.drop(firstPrecipIndex).indexOfFirst { it.mmPerHour <= 0.1 }
            if (stopIndex != -1) {
                val stopTime = points[firstPrecipIndex + stopIndex].time
                "$intensityDesc ending around ${timeFormatter.format(stopTime)}."
            } else {
                "$intensityDesc continuing for the next 60 minutes."
            }
        } else {
            val startTime = points[firstPrecipIndex].time
            "$intensityDesc starting in $firstPrecipIndex min (~${timeFormatter.format(startTime)})."
        }
    }

    fun mapAqi(usAqi: Int?, pm25: Double?, pm10: Double?, ozone: Double?): AirQuality {
        val aqi = usAqi ?: 28
        val (category, guidance) = when {
            aqi <= 50 -> "Good" to "Air quality is satisfactory and poses little or no health risk."
            aqi <= 100 -> "Moderate" to "Air quality is acceptable. Unusually sensitive individuals should limit prolonged outdoor exertion."
            aqi <= 150 -> "Unhealthy for Sensitive Groups" to "Active children, older adults, and individuals with respiratory issues should reduce outdoor exertion."
            aqi <= 200 -> "Unhealthy" to "Everyone may begin to experience health effects. Limit prolonged outdoor exertion."
            aqi <= 300 -> "Very Unhealthy" to "Health alert: The risk of health effects is increased for everyone. Avoid strenuous outdoor activities."
            else -> "Hazardous" to "Emergency warning: Entire population is likely to be affected. Remain indoors."
        }

        val dominant = when {
            pm25 != null && pm25 > 15.0 -> "PM2.5"
            ozone != null && ozone > 45.0 -> "Ozone (O₃)"
            pm10 != null && pm10 > 40.0 -> "PM10"
            else -> "PM2.5"
        }

        return AirQuality(
            aqi = aqi,
            category = category,
            pm25 = pm25 ?: 6.2,
            pm10 = pm10 ?: 12.0,
            ozonePpb = ozone ?: 32.0,
            dominant = dominant,
            source = "Open-Meteo Air Quality",
            healthGuidance = guidance
        )
    }

    fun mapAlertSeverity(severity: String?): AlertSeverity {
        return when (severity?.lowercase(Locale.ROOT)) {
            "extreme" -> AlertSeverity.Extreme
            "severe" -> AlertSeverity.Severe
            "moderate" -> AlertSeverity.Moderate
            "minor" -> AlertSeverity.Minor
            else -> AlertSeverity.Unknown
        }
    }
}
