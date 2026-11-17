package com.example

import com.example.domain.model.HourlyPoint
import com.example.domain.model.WeatherCondition
import com.example.ui.widget.WidgetConditionIcon
import com.example.ui.widget.WidgetHourlyPoint
import com.example.ui.widget.WidgetState
import com.example.ui.widget.conditionToWidgetIcon
import com.example.ui.widget.hourlyStripPoints
import com.example.ui.widget.stripHourCount
import com.example.ui.widget.tickerFact
import com.example.ui.widget.toDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Pure tests for the 3.2 widget model + formatting (WidgetState),
 * plus the Item 3 ticker fact and strip hour-count rules. They also
 * run in CI (testDebugUnitTest); run-pure-tests.sh compiles this file
 * so they run locally too.
 */
class WidgetStateTest {

    private fun state(
        tempC: Double? = 15.0,
        condition: WeatherCondition = WeatherCondition.ClearDay,
        highC: Double? = 17.2,
        lowC: Double? = 5.6,
        useMetric: Boolean = false,
        isDaytime: Boolean = true,
        fetchedAt: Instant = Instant.parse("2026-11-09T17:43:00Z"),
        hourly: List<WidgetHourlyPoint> = emptyList()
    ) = WidgetState(
        placeName = "Crittenden, KY",
        tempC = tempC,
        condition = condition,
        highC = highC,
        lowC = lowC,
        fetchedAt = fetchedAt,
        isStale = false,
        zoneId = "America/New_York",
        useMetric = useMetric,
        isDaytime = isDaytime,
        hourly = hourly
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
        // Partly-cloudy night is moon + small cloud (ic_widget_partly_night),
        // not the sun + cloud used by day — the old mapping showed a sun at night.
        assertEquals(
            WidgetConditionIcon.PartlyCloudyNight,
            conditionToWidgetIcon(WeatherCondition.PartlyCloudyNight, false)
        )
    }

    @Test
    fun testIconMapping_cloudFamily() {
        assertEquals(
            WidgetConditionIcon.Cloudy,
            conditionToWidgetIcon(WeatherCondition.Cloudy, true)
        )
        // Fog/haze get their own cloud-with-lines drawable (ic_widget_fog),
        // not the plain overcast cloud.
        assertEquals(
            WidgetConditionIcon.Fog,
            conditionToWidgetIcon(WeatherCondition.Fog, true)
        )
        assertEquals(
            WidgetConditionIcon.Fog,
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

    // 0.3.2: the strip's NOW cell must show the OBSERVED current temp,
    // not the hourly forecast temp for that hour. The reported case:
    // strip NOW 38 F against an observed 43 F (main widget + Home
    // strip both showed 43). The label stays "NOW"; only the temp
    // changes, and only for the first cell.
    @Test
    fun testHourlyStrip_nowUsesObservedTemp() {
        // Forecast first hour 10.0 C (50 F); observed 6.1 C (43 F).
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 6, currentTempC = 6.1)
        assertEquals("NOW", points[0].hourLabel)
        assertEquals(6.1, points[0].tempC)
        // Later hours keep their forecast temps — the observed temp
        // must not leak past the first cell.
        assertEquals(11.0, points[1].tempC)
        assertEquals(15.0, points[5].tempC)
    }

    // 0.3.2: no observation (null) falls back to the forecast temp,
    // so callers without one keep the old behaviour.
    @Test
    fun testHourlyStrip_nowFallsBackToForecastWhenObservedNull() {
        val points = hourlyStripPoints(hourlyPoints(), "America/New_York", 6, currentTempC = null)
        assertEquals("NOW", points[0].hourLabel)
        assertEquals(10.0, points[0].tempC)
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

    // Item 3: ticker fact — one fact per update, picked from the fetch
    // hour. Without strip hours there are 3 facts [high/low, updated,
    // condition]; the 17:00 UTC fetch hour lands on index 2
    // (condition), 18:00 on index 0 (high/low), 20:00 back on 2.
    @Test
    fun testTickerFact_threeFacts_rotateByFetchHour() {
        val at1700 = state(fetchedAt = Instant.parse("2026-11-09T17:00:00Z"))
        val at1800 = state(fetchedAt = Instant.parse("2026-11-09T18:00:00Z"))
        val at2000 = state(fetchedAt = Instant.parse("2026-11-09T20:00:00Z"))
        assertEquals("CLEAR", at1700.tickerFact())
        assertEquals("H 63° / L 42°", at1800.tickerFact())
        assertEquals("CLEAR", at2000.tickerFact())
    }

    // Item 3: with strip hours there are 4 facts (next-hour added
    // last). 17:00 UTC lands on index 1 (updated — noon EST),
    // 19:00 on index 3 (next hour: 8 AM, 11 C = 52 F).
    @Test
    fun testTickerFact_fourFacts_nextHourLast() {
        val hours = hourlyStripPoints(hourlyPoints(), "America/New_York", 6)
        val at1700 = state(
            fetchedAt = Instant.parse("2026-11-09T17:00:00Z"),
            hourly = hours
        )
        val at1900 = state(
            fetchedAt = Instant.parse("2026-11-09T19:00:00Z"),
            hourly = hours
        )
        assertEquals("UPDATED 12:00 PM", at1700.tickerFact())
        assertEquals("NEXT 8 AM 52°", at1900.tickerFact())
    }

    // Item 3: strip hour count by placement width.
    @Test
    fun testStripHourCount_byWidth() {
        assertEquals(6, stripHourCount(320))
        assertEquals(6, stripHourCount(300))
        assertEquals(5, stripHourCount(299))
        assertEquals(5, stripHourCount(250))
        assertEquals(4, stripHourCount(249))
        assertEquals(4, stripHourCount(180))
        assertEquals(4, stripHourCount(0))
    }
}
