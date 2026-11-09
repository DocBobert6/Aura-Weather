package com.example

import com.example.domain.model.WeatherCondition
import com.example.ui.widget.WidgetConditionIcon
import com.example.ui.widget.WidgetState
import com.example.ui.widget.conditionToWidgetIcon
import com.example.ui.widget.toDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Pure tests for the 3.2 widget model + formatting (WidgetState).
 * They also run in CI (testDebugUnitTest); run-pure-tests.sh compiles
 * this file so they run locally too.
 */
class WidgetStateTest {

    private fun state(
        tempC: Double? = 15.0,
        condition: WeatherCondition = WeatherCondition.ClearDay,
        highC: Double? = 17.2,
        lowC: Double? = 5.6,
        useMetric: Boolean = false,
        isDaytime: Boolean = true
    ) = WidgetState(
        placeName = "Crittenden, KY",
        tempC = tempC,
        condition = condition,
        highC = highC,
        lowC = lowC,
        fetchedAt = Instant.parse("2026-11-09T17:43:00Z"),
        isStale = false,
        zoneId = "America/New_York",
        useMetric = useMetric,
        isDaytime = isDaytime
    )

    @Test
    fun testDisplay_usTemperatures() {
        val display = state().toDisplay()
        assertEquals("59°", display.tempText)
        assertEquals("H 63° / L 42°", display.highLowText)
    }

    @Test
    fun testDisplay_metricTemperatures() {
        val display = state(useMetric = true).toDisplay()
        assertEquals("15°C", display.tempText)
        assertEquals("H 17°C / L 6°C", display.highLowText)
    }

    @Test
    fun testDisplay_missingTemperaturesShowDashes() {
        val display = state(tempC = null, highC = null, lowC = null).toDisplay()
        assertEquals("--", display.tempText)
        assertEquals("H -- / L --", display.highLowText)
    }

    @Test
    fun testDisplay_updatedLineInPlaceZone() {
        // 17:43 UTC is 12:43 PM in America/New_York (EST, UTC-5).
        val display = state().toDisplay()
        assertEquals("UPDATED 12:43 PM", display.updatedText)
    }

    @Test
    fun testDisplay_conditionUppercase() {
        val display = state(condition = WeatherCondition.PartlyCloudyDay).toDisplay()
        assertEquals("PARTLY CLOUDY", display.conditionText)
    }

    @Test
    fun testIconMapping_clearDayAndNight() {
        assertEquals(
            WidgetConditionIcon.Sunny,
            conditionToWidgetIcon(WeatherCondition.ClearDay, true)
        )
        assertEquals(
            WidgetConditionIcon.Moon,
            conditionToWidgetIcon(WeatherCondition.ClearNight, false)
        )
    }

    @Test
    fun testIconMapping_partlyCloudy() {
        assertEquals(
            WidgetConditionIcon.PartlyCloudy,
            conditionToWidgetIcon(WeatherCondition.PartlyCloudyDay, true)
        )
        assertEquals(
            WidgetConditionIcon.PartlyCloudy,
            conditionToWidgetIcon(WeatherCondition.PartlyCloudyNight, false)
        )
    }

    @Test
    fun testIconMapping_cloudFamily() {
        assertEquals(
            WidgetConditionIcon.Cloudy,
            conditionToWidgetIcon(WeatherCondition.Cloudy, true)
        )
        assertEquals(
            WidgetConditionIcon.Cloudy,
            conditionToWidgetIcon(WeatherCondition.Fog, true)
        )
        assertEquals(
            WidgetConditionIcon.Cloudy,
            conditionToWidgetIcon(WeatherCondition.Haze, true)
        )
        assertEquals(
            WidgetConditionIcon.Cloudy,
            conditionToWidgetIcon(WeatherCondition.Windy, true)
        )
    }

    @Test
    fun testIconMapping_precipitation() {
        assertEquals(
            WidgetConditionIcon.Rain,
            conditionToWidgetIcon(WeatherCondition.Rain, true)
        )
        assertEquals(
            WidgetConditionIcon.Rain,
            conditionToWidgetIcon(WeatherCondition.Drizzle, true)
        )
        assertEquals(
            WidgetConditionIcon.Rain,
            conditionToWidgetIcon(WeatherCondition.HeavyRain, true)
        )
        assertEquals(
            WidgetConditionIcon.Storm,
            conditionToWidgetIcon(WeatherCondition.Thunderstorm, true)
        )
        assertEquals(
            WidgetConditionIcon.Snow,
            conditionToWidgetIcon(WeatherCondition.Snow, true)
        )
        assertEquals(
            WidgetConditionIcon.Snow,
            conditionToWidgetIcon(WeatherCondition.Sleet, true)
        )
        assertEquals(
            WidgetConditionIcon.Snow,
            conditionToWidgetIcon(WeatherCondition.FreezingRain, true)
        )
    }

    @Test
    fun testIconMapping_unknownFollowsDaytime() {
        assertEquals(
            WidgetConditionIcon.Sunny,
            conditionToWidgetIcon(WeatherCondition.Unknown, true)
        )
        assertEquals(
            WidgetConditionIcon.Moon,
            conditionToWidgetIcon(WeatherCondition.Unknown, false)
        )
    }
}
