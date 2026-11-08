package com.example

import com.example.core.UnitFormatter
import com.example.data.mapper.WeatherMappers
import com.example.domain.astro.LunarCalculator
import com.example.domain.astro.SolarCalculator
import com.example.domain.model.RegionDefaults
import com.example.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AuraWeatherUnitTest {

    @Test
    fun testSolarCalculator_outputsRealisticSunTimesForNKY() {
        val testInstant = Instant.parse("2026-06-21T16:00:00Z") // Summer solstice
        val solarTimes = SolarCalculator.calculate(
            point = RegionDefaults.HOME_POINT,
            instant = testInstant,
            zoneId = ZoneId.of("America/New_York")
        )

        assertNotNull(solarTimes.sunrise)
        assertNotNull(solarTimes.sunset)
        assertTrue("Sunset must be after sunrise", solarTimes.sunset.isAfter(solarTimes.sunrise))
        assertTrue("Summer day length in NKY should be around 14 to 15 hours", solarTimes.dayLengthMinutes in (14 * 60)..(15 * 60 + 30))
    }

    @Test
    fun testSolarCalculator_CrittendenKY_NovemberSunriseSunset() {
        // Crittenden, KY: 38.79 N, 84.60 W in November (Eastern Time UTC-5)
        val testInstant = Instant.parse("2026-11-07T17:00:00Z")
        val zone = ZoneId.of("America/New_York")
        val solar = SolarCalculator.calculate(
            point = com.example.domain.model.GeoPoint(38.79, -84.60),
            instant = testInstant,
            zoneId = zone
        )

        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US).withZone(zone)
        val sunrise = formatter.format(solar.sunrise)
        val sunset = formatter.format(solar.sunset)
        val noon = formatter.format(solar.solarNoon)

        // In Eastern Time (EST), sunrise is ~7:13 AM, solar noon is ~12:23 PM, sunset is ~5:33 PM
        assertTrue("Sunrise should be in the morning ~7 AM, was $sunrise", sunrise.startsWith("7:") && sunrise.endsWith("AM"))
        assertTrue("Sunset should be in the evening ~5 PM, was $sunset", sunset.startsWith("5:") && sunset.endsWith("PM"))
        assertTrue("Solar noon should be midday ~12 PM, was $noon", noon.startsWith("12:") && noon.endsWith("PM"))
        assertTrue("Day length in Nov should be ~10h (600-640 min), was ${solar.dayLengthMinutes}", solar.dayLengthMinutes in 600..640)
    }

    @Test
    fun testLunarCalculator_calculatesIlluminationProperly() {
        val testInstant = Instant.parse("2026-11-06T12:00:00Z")
        val lunar = LunarCalculator.calculate(testInstant)

        assertTrue("Illumination fraction should be between 0 and 1", lunar.illuminatedFraction in 0.0..1.0)
        assertTrue("Phase age should be between 0 and 30 days", lunar.phaseAgeDays in 0.0..30.0)
        assertTrue("Phase name should not be blank", lunar.phaseName.isNotBlank())
        assertTrue("Days to full moon should be non-negative", lunar.nextFullMoonDays >= 0)
    }

    @Test
    fun testUnitFormatter_conversionsAreAccurate() {
        val imperial = UnitFormatter(useMetric = false)
        val metric = UnitFormatter(useMetric = true)

        // 20°C is 68°F
        assertEquals("68°", imperial.formatTemperature(20.0))
        assertEquals("20°C", metric.formatTemperature(20.0))

        // 10 m/s is ~22 mph or 36 km/h
        assertEquals("22 mph", imperial.formatWindSpeed(10.0))
        assertEquals("36.0 km/h", metric.formatWindSpeed(10.0))

        // 101325 Pa is ~29.92 inHg or 1013 hPa
        assertEquals("29.92 inHg", imperial.formatPressure(101325.0))
        assertEquals("1013 hPa", metric.formatPressure(101325.0))
    }

    @Test
    fun testWeatherMappers_wmoCodesAndAqi() {
        // WMO code 0 -> ClearDay
        assertEquals(WeatherCondition.ClearDay, WeatherMappers.mapWmoCodeToCondition(0, isDaytime = true))
        // WMO code 95 -> Thunderstorm
        assertEquals(WeatherCondition.Thunderstorm, WeatherMappers.mapWmoCodeToCondition(95, isDaytime = true))
        // WMO code 71 -> Snow
        assertEquals(WeatherCondition.Snow, WeatherMappers.mapWmoCodeToCondition(71, isDaytime = true))

        // AQI mapping
        val goodAqi = WeatherMappers.mapAqi(35, 8.0, 15.0, 30.0)
        assertEquals("Good", goodAqi.category)

        val modAqi = WeatherMappers.mapAqi(85, 25.0, 35.0, 50.0)
        assertEquals("Moderate", modAqi.category)
    }

    @Test
    fun testMinuteCastInterpolation() {
        val raw15 = listOf(0.0, 1.2, 3.5, 0.4, 0.0)
        val (points, sentence) = WeatherMappers.interpolate15MinTo60Min(
            times = listOf("0", "15", "30", "45", "60"),
            precip15 = raw15,
            now = Instant.now()
        )

        assertEquals("Must produce 60 minutes of interpolated resolution", 60, points.size)
        assertTrue("Sentence should describe rain starting/continuing", sentence.contains("rain", ignoreCase = true))
    }
}
