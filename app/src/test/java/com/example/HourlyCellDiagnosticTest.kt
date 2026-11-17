package com.example

import com.example.data.mapper.WeatherMappers
import com.example.domain.model.HourlyPoint
import com.example.domain.model.HourlySource
import com.example.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Widget-audit 2 AM / 3 AM diagnostic (0.3.2, Worker E): per-cell hourly
 * source tracking. Pure Kotlin — no Compose, no Android — so it runs in
 * the kotlinc + JUnit harness. The repository parsers themselves
 * (parseNwsHourly / parseOpenMeteoHourly) need the Android toolchain and
 * are CI-only; what is pinned here is everything pure underneath the
 * diagnostic: the model defaults, the per-cell log line, and the icon
 * name mirror of getWeatherIcon.
 *
 * Background: Robert's screenshots (2026-11-16, 11:52–11:53 PM) still
 * showed plain-cloud icons at 2 AM and 3 AM while a live Open-Meteo
 * probe said WMO code 0 / 0% cloud for those hours. Worker D fixed the
 * NWS text mapping order ("partly" before "cloudy") and added parse-time
 * logging, but a mapping-only check cannot explain the strip: the model
 * dropped both the source and the WMO code before the cell was built.
 * These tests pin that a cell now carries its source and its code (or,
 * for NWS, its raw text), and that a cell with no recorded source says
 * UNKNOWN instead of inventing one.
 */
class HourlyCellDiagnosticTest {

    private val zone = ZoneId.of("America/New_York")
    private val twoAm = Instant.parse("2026-11-17T07:00:00Z") // 02:00 EST

    private fun basePoint(
        condition: WeatherCondition = WeatherCondition.PartlyCloudyNight,
        isDaytime: Boolean = false,
        source: HourlySource = HourlySource.Unknown,
        wmoCode: Int? = null,
        rawShortForecast: String? = null
    ) = HourlyPoint(
        time = twoAm,
        tempC = 5.0,
        popPct = null,
        precipMm = null,
        condition = condition,
        windMs = null,
        isDaytime = isDaytime,
        source = source,
        wmoCode = wmoCode,
        rawShortForecast = rawShortForecast
    )

    // --- Source tracking on the model -----------------------------------

    @Test
    fun testDefaultCellReportsUnknownSourceNotAnInventedOne() {
        // A point built without source tracking (e.g. deserialised from an
        // old cache row) must say UNKNOWN — never guess NWS or Open-Meteo.
        val point = HourlyPoint(
            time = twoAm, tempC = 5.0, popPct = null, precipMm = null,
            condition = WeatherCondition.ClearNight, windMs = null, isDaytime = false
        )
        assertEquals(HourlySource.Unknown, point.source)
        assertEquals(null, point.wmoCode)
        assertEquals(null, point.rawShortForecast)
        val line = WeatherMappers.hourlyCellDiagnostic(point, zone)
        assertTrue(line.contains("source=UNKNOWN"))
        assertTrue(line.contains("wmo=n/a"))
        assertTrue(!line.contains("raw="))
    }

    @Test
    fun testNwsFedCellReportsNwsAndRawTextWithNoWmo() {
        // NWS has no WMO code: the line must carry the raw shortForecast
        // and read wmo=n/a, not a made-up code.
        val point = basePoint(
            source = HourlySource.Nws,
            rawShortForecast = "Partly Cloudy"
        )
        val line = WeatherMappers.hourlyCellDiagnostic(point, zone)
        assertEquals(
            "hourly cell hour=2026-11-17 02:00 source=NWS wmo=n/a raw=\"Partly Cloudy\" " +
                "condition=PartlyCloudyNight icon=PartlyCloudyNightIcon",
            line
        )
    }

    @Test
    fun testOpenMeteoFedCellReportsOmAndItsWmoCode() {
        // The 2 AM case itself: WMO 0 must survive onto the point and into
        // the log, so a clear-code hour can never silently render cloudy.
        val point = basePoint(
            condition = WeatherCondition.ClearNight,
            source = HourlySource.OpenMeteo,
            wmoCode = 0
        )
        val line = WeatherMappers.hourlyCellDiagnostic(point, zone)
        assertEquals(
            "hourly cell hour=2026-11-17 02:00 source=OpenMeteo wmo=0 " +
                "condition=ClearNight icon=DarkMode",
            line
        )
    }

    @Test
    fun testDiagnosticCarriesMappedConditionAndIcon() {
        // A cloudy NWS hour logs the cloud condition AND the plain-cloud
        // icon name — the pair that decides whether the strip is lying.
        val point = basePoint(
            condition = WeatherCondition.Cloudy,
            source = HourlySource.Nws,
            rawShortForecast = "Mostly Cloudy"
        )
        val line = WeatherMappers.hourlyCellDiagnostic(point, zone)
        assertTrue(line.contains("condition=Cloudy"))
        assertTrue(line.contains("icon=Cloud"))
        assertTrue(line.contains("raw=\"Mostly Cloudy\""))
    }

    // --- Icon-name mirror of getWeatherIcon ------------------------------
    // Every branch pinned: if getWeatherIcon gains or changes a branch,
    // this mirror (and these tests) must change with it.

    @Test
    fun testIconNameClearDayAndNight() {
        assertEquals("WbSunny", WeatherMappers.hourlyCellIconName(WeatherCondition.ClearDay, true))
        // ClearDay at night is DarkMode in the app (not Sunny).
        assertEquals("DarkMode", WeatherMappers.hourlyCellIconName(WeatherCondition.ClearDay, false))
        assertEquals("DarkMode", WeatherMappers.hourlyCellIconName(WeatherCondition.ClearNight, false))
        assertEquals("DarkMode", WeatherMappers.hourlyCellIconName(WeatherCondition.ClearNight, true))
    }

    @Test
    fun testIconNamePartlyCloudyDayAndNight() {
        assertEquals(
            "PartlyCloudyDayIcon",
            WeatherMappers.hourlyCellIconName(WeatherCondition.PartlyCloudyDay, true)
        )
        assertEquals(
            "PartlyCloudyNightIcon",
            WeatherMappers.hourlyCellIconName(WeatherCondition.PartlyCloudyNight, false)
        )
        // The variants share the display name; the icon follows day/night,
        // not the variant — so a night cell never gets the day glyph.
        assertEquals(
            "PartlyCloudyNightIcon",
            WeatherMappers.hourlyCellIconName(WeatherCondition.PartlyCloudyDay, false)
        )
        assertEquals(
            "PartlyCloudyDayIcon",
            WeatherMappers.hourlyCellIconName(WeatherCondition.PartlyCloudyNight, true)
        )
    }

    @Test
    fun testIconNameCloudFogAndWind() {
        assertEquals("Cloud", WeatherMappers.hourlyCellIconName(WeatherCondition.Cloudy, true))
        assertEquals("Cloud", WeatherMappers.hourlyCellIconName(WeatherCondition.Cloudy, false))
        assertEquals("FogIcon", WeatherMappers.hourlyCellIconName(WeatherCondition.Fog, true))
        assertEquals("FogIcon", WeatherMappers.hourlyCellIconName(WeatherCondition.Haze, false))
        assertEquals("Air", WeatherMappers.hourlyCellIconName(WeatherCondition.Windy, true))
    }

    @Test
    fun testIconNamePrecipAndStorm() {
        assertEquals("WaterDrop", WeatherMappers.hourlyCellIconName(WeatherCondition.Drizzle, true))
        assertEquals("WaterDrop", WeatherMappers.hourlyCellIconName(WeatherCondition.Rain, true))
        assertEquals("WaterDrop", WeatherMappers.hourlyCellIconName(WeatherCondition.HeavyRain, true))
        assertEquals("Thunderstorm", WeatherMappers.hourlyCellIconName(WeatherCondition.Thunderstorm, true))
        assertEquals("AcUnit", WeatherMappers.hourlyCellIconName(WeatherCondition.Snow, true))
        assertEquals("AcUnit", WeatherMappers.hourlyCellIconName(WeatherCondition.Sleet, true))
        assertEquals("AcUnit", WeatherMappers.hourlyCellIconName(WeatherCondition.FreezingRain, true))
    }

    @Test
    fun testIconNameUnknownFallsBackToSunOrMoon() {
        assertEquals("WbSunny", WeatherMappers.hourlyCellIconName(WeatherCondition.Unknown, true))
        assertEquals("NightsStay", WeatherMappers.hourlyCellIconName(WeatherCondition.Unknown, false))
    }
}
