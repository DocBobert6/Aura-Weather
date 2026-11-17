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
 * the classic RemoteViews widget only renders it. A null WidgetState (no selected
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
 *
 * 0.3.2: [currentTempC] is the observed current temperature — the same
 * value the main widget and the Home strip's NOW cell show. The first
 * point carries it instead of the hourly forecast's own temp for that
 * hour (which can sit a few degrees apart — the reported 38° strip
 * NOW against a 43° observed). Single source of truth for "now", the
 * way HomeScreen's HourlyForecastCard does it. Null falls back to the
 * forecast temp, so callers without an observation keep working.
 */
fun hourlyStripPoints(
    hourly: List<com.example.domain.model.HourlyPoint>,
    zoneId: String,
    count: Int,
    currentTempC: Double? = null
): List<WidgetHourlyPoint> {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("h a", Locale.US).withZone(zone)
    return hourly.take(count).mapIndexed { index, point ->
        WidgetHourlyPoint(
            hourLabel = if (index == 0) "NOW" else formatter.format(point.time).uppercase(Locale.US),
            tempC = if (index == 0) currentTempC ?: point.tempC else point.tempC,
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
 * How many hours the hourly strip shows for a placement [minWidthDp]
 * wide (classic RemoteViews: the receiver reads the width from the
 * platform options and hides the extra columns). Mirrors the old
 * Glance sizes: a wide 4x1 strip shows 6 hours, the middle size 5,
 * a narrower placement 4. Pure, so it is unit-testable.
 */
fun stripHourCount(minWidthDp: Int): Int = when {
    minWidthDp >= 300 -> 6
    minWidthDp >= 250 -> 5
    else -> 4
}

/**
 * The ticker-strip fact (Item 3): the main widget's blue strip shows
 * ONE fact per update — the widget host cannot animate or rotate, so
 * the fact is picked deterministically from the fetch hour and swaps
 * on the next refresh. The facts are, in rotation order, high/low,
 * the updated line, the condition, and (when the strip hours exist)
 * the next hour's temp. Pure, so it is unit-testable.
 */
fun WidgetState.tickerFact(): String {
    val display = toDisplay()
    val facts = mutableListOf(
        display.highLowText,
        display.updatedText,
        display.conditionText
    )
    nextHourFact()?.let { facts.add(it) }
    val index = ((fetchedAt.epochSecond / 3600) % facts.size).toInt()
    return facts[index]
}

/** "NEXT 2 PM 61°" — the hour after the current one, or null. */
private fun WidgetState.nextHourFact(): String? {
    val next = hourly.getOrNull(1) ?: return null
    val formatter = UnitFormatter(useMetric)
    return "NEXT ${next.hourLabel} ${formatter.formatTemperature(next.tempC)}"
}

/**
 * Which condition picture the widget shows. Pure (no drawable id) so it
 * is unit-testable; the widget maps each value to its drawable.
 */
enum class WidgetConditionIcon {
    Sunny, Moon, PartlyCloudy, PartlyCloudyNight, Cloudy, Fog, Rain, Snow, Storm
}

fun conditionToWidgetIcon(condition: WeatherCondition, isDaytime: Boolean): WidgetConditionIcon =
    when (condition) {
        WeatherCondition.ClearDay -> WidgetConditionIcon.Sunny
        WeatherCondition.ClearNight -> WidgetConditionIcon.Moon
        WeatherCondition.PartlyCloudyDay -> WidgetConditionIcon.PartlyCloudy
        WeatherCondition.PartlyCloudyNight -> WidgetConditionIcon.PartlyCloudyNight
        WeatherCondition.Cloudy,
        WeatherCondition.Windy -> WidgetConditionIcon.Cloudy
        WeatherCondition.Fog,
        WeatherCondition.Haze -> WidgetConditionIcon.Fog
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
