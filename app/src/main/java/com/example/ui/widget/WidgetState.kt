package com.example.ui.widget

import com.example.core.UnitFormatter
import com.example.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The widget's data (3.2), pure Kotlin — no Android, no Glance, no Room.
 * The repository builds it from the in-memory state or the Room cache;
 * the Glance widget only renders it. A null WidgetState (no selected
 * place, no cache) is the empty prompt, never invented weather.
 */
data class WidgetState(
    val placeName: String,
    val tempC: Double?,
    val condition: WeatherCondition,
    val highC: Double?,
    val lowC: Double?,
    val fetchedAt: Instant,
    val isStale: Boolean,
    val zoneId: String,
    val useMetric: Boolean,
    val isDaytime: Boolean,
    // Build E: the next hours for the hourly-strip widget (empty for the
    // main widget's state — it never reads this). Default empty so
    // existing construction sites keep working.
    val hourly: List<WidgetHourlyPoint> = emptyList()
)

/** One hour in the strip: label, temp, condition, day/night. */
data class WidgetHourlyPoint(
    val hourLabel: String,
    val tempC: Double?,
    val condition: WeatherCondition,
    val isDaytime: Boolean
)

/**
 * Build E: the strip's hours, pure. The first point is the current hour
 * ("NOW", like the Home hourly strip); the rest are labelled in the
 * place's zone ("2 PM"). Capped at [count] so a narrow widget takes
 * fewer hours than a wide one.
 */
fun hourlyStripPoints(
    hourly: List<com.example.domain.model.HourlyPoint>,
    zoneId: String,
    count: Int
): List<WidgetHourlyPoint> {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("h a", Locale.US).withZone(zone)
    return hourly.take(count).mapIndexed { index, point ->
        WidgetHourlyPoint(
            hourLabel = if (index == 0) "NOW" else formatter.format(point.time).uppercase(Locale.US),
            tempC = point.tempC,
            condition = point.condition,
            isDaytime = point.isDaytime
        )
    }
}

/** Display strings for one widget render. All text is uppercase (retro). */
data class WidgetDisplay(
    val tempText: String,
    val conditionText: String,
    val highLowText: String,
    val updatedText: String
)

fun WidgetState.toDisplay(): WidgetDisplay {
    val formatter = UnitFormatter(useMetric)
    val tempText = formatter.formatTemperature(tempC)
    val highText = formatter.formatTemperature(highC)
    val lowText = formatter.formatTemperature(lowC)
    val highLowText = "H $highText / L $lowText"
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val timeText = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        .withZone(zone)
        .format(fetchedAt)
    val updatedText = "UPDATED $timeText"
    val conditionText = condition.displayName.uppercase(Locale.US)
    return WidgetDisplay(
        tempText = tempText,
        conditionText = conditionText,
        highLowText = highLowText,
        updatedText = updatedText
    )
}

/**
 * Which condition picture the widget shows. Pure (no drawable id) so it
 * is unit-testable; the Glance widget maps each value to its drawable.
 */
enum class WidgetConditionIcon {
    Sunny, Moon, PartlyCloudy, Cloudy, Rain, Snow, Storm
}

fun conditionToWidgetIcon(condition: WeatherCondition, isDaytime: Boolean): WidgetConditionIcon =
    when (condition) {
        WeatherCondition.ClearDay -> WidgetConditionIcon.Sunny
        WeatherCondition.ClearNight -> WidgetConditionIcon.Moon
        WeatherCondition.PartlyCloudyDay,
        WeatherCondition.PartlyCloudyNight -> WidgetConditionIcon.PartlyCloudy
        WeatherCondition.Cloudy,
        WeatherCondition.Fog,
        WeatherCondition.Haze,
        WeatherCondition.Windy -> WidgetConditionIcon.Cloudy
        WeatherCondition.Drizzle,
        WeatherCondition.Rain,
        WeatherCondition.HeavyRain -> WidgetConditionIcon.Rain
        WeatherCondition.Thunderstorm -> WidgetConditionIcon.Storm
        WeatherCondition.Snow,
        WeatherCondition.Sleet,
        WeatherCondition.FreezingRain -> WidgetConditionIcon.Snow
        WeatherCondition.Unknown ->
            if (isDaytime) WidgetConditionIcon.Sunny else WidgetConditionIcon.Moon
    }
