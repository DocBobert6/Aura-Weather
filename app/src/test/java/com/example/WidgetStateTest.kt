package com.example

import com.example.domain.model.HourlyPoint
import com.example.domain.model.WeatherCondition
import com.example.ui.widget.WidgetConditionIcon
import com.example.ui.widget.WidgetState
import com.example.ui.widget.conditionToWidgetIcon
import com.example.ui.widget.hourlyStripPoints
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

    // Build E: hourly-strip points.
    private fun hourlyPoints(): List<HourlyPoint> {
        // 6 hours from noon UTC (= 7 AM New York — November is EST,
        // UTC-5, after DST ends on Nov 1).
        val base = Instant.parse("2026-11-10T12:00:00Z")
        return (0..5).map { h ->
            HourlyPoint(
                time = base.plusSeconds(h * 3600L),
                tempC = 10.0 + h,
                popPct = null,
                precipMm = null,
                condition = if (h < 3) WeatherCondition.ClearDay else WeatherCondition.Rain,
                windMs = null,
                isDaytime = h < 5
            )
        }
    }

    @Test
    fun testHourlyStrip_firstIsNow_restAreHours() {
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 6)
        assertEquals(6, points.size)
        assertEquals("NOW", points[0].hourLabel)
        assertEquals("8 AM", points[1].hourLabel)
        assertEquals("9 AM", points[2].hourLabel)
        assertEquals("12 PM", points[5].hourLabel)
    }

    @Test
    fun testHourlyStrip_countCapsHours() {
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 4)
        assertEquals(4, points.size)
        assertEquals("NOW", points[0].hourLabel)
        assertEquals("10 AM", points[3].hourLabel)
    }

    @Test
    fun testHourlyStrip_tempsAndConditionsCarried() {
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 6)
        assertEquals(10.0, points[0].tempC)
        assertEquals(WeatherCondition.ClearDay, points[0].condition)
        assertEquals(WeatherCondition.Rain, points[4].condition)
        assertEquals(15.0, points[5].tempC)
    }

    @Test
    fun testHourlyStrip_dayNightCarriedForIcons() {
        // Hour 5 is night (isDaytime false) — the strip must render a
        // night icon for it, not a sun.
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 6)
        assertEquals(true, points[0].isDaytime)
        assertEquals(false, points[5].isDaytime)
    }

    @Test
    fun testHourlyStrip_emptyHourly_returnsEmpty() {
        assertEquals(0, hourlyStripPoints(emptyList(), "America/New_York", 6).size)
    }
}
