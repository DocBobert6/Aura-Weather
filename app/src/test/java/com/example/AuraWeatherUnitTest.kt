package com.example

import com.example.core.UnitFormatter
import com.example.data.mapper.AirNowReading
import com.example.data.mapper.TomorrowMinutePoint
import com.example.data.mapper.WeatherMappers
import com.example.data.mapper.fillDailyHighs
import com.example.domain.model.DailyPoint
import com.example.data.mapper.destinationPoint
import com.example.data.mapper.distanceMeters
import com.example.data.mapper.parseOpenMeteoUtcInstant
import com.example.data.mapper.regionalCompassLabel
import com.example.data.mapper.gibsTimestamp
import com.example.data.mapper.shortMapLabel
import com.example.data.mapper.stableAlertId
import com.example.domain.astro.LunarCalculator
import com.example.domain.astro.SolarCalculator
import com.example.domain.model.GeoPoint
import com.example.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class AuraWeatherUnitTest {

    @Test
    fun testSolarCalculator_outputsRealisticSunTimesForDenver() {
        // Denver, CO: 39.74 N, 104.99 W — a non-Eastern zone, on purpose.
        // The app works anywhere; the solar math must not be tested only in
        // the developer's home timezone.
        val testInstant = Instant.parse("2026-06-21T16:00:00Z") // Summer solstice
        val solarTimes = SolarCalculator.calculate(
            point = GeoPoint(39.74, -104.99),
            instant = testInstant,
            zoneId = ZoneId.of("America/Denver")
        )

        assertNotNull(solarTimes.sunrise)
        assertNotNull(solarTimes.sunset)
        assertTrue("Sunset must be after sunrise", solarTimes.sunset.isAfter(solarTimes.sunrise))
        assertTrue("Summer day length in Denver should be around 14 to 15.5 hours", solarTimes.dayLengthMinutes in (14 * 60)..(15 * 60 + 30))
    }

    @Test
    fun testSolarCalculator_LondonNovemberSunriseSunset() {
        // London: 51.50 N, 0.12 W in November (GMT, UTC+0)
        val testInstant = Instant.parse("2026-11-07T12:00:00Z")
        val zone = ZoneId.of("Europe/London")
        val solar = SolarCalculator.calculate(
            point = GeoPoint(51.50, -0.12),
            instant = testInstant,
            zoneId = zone
        )

        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US).withZone(zone)
        val sunrise = formatter.format(solar.sunrise)
        val sunset = formatter.format(solar.sunset)
        val noon = formatter.format(solar.solarNoon)

        // In London in November, sunrise is ~7 AM, solar noon ~11:40 AM, sunset ~4:20 PM
        assertTrue("Sunrise should be in the morning ~7 AM, was $sunrise", sunrise.startsWith("7:") && sunrise.endsWith("AM"))
        assertTrue("Sunset should be in the afternoon ~4 PM, was $sunset", sunset.startsWith("4:") && sunset.endsWith("PM"))
        assertTrue("Solar noon should be late morning ~11 AM, was $noon", noon.startsWith("11:") && noon.endsWith("AM"))
        assertTrue("Day length in Nov should be ~9.3h (540-580 min), was ${solar.dayLengthMinutes}", solar.dayLengthMinutes in 540..580)
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
    fun testLunarCalculator_moonriseMoonsetNearNewMoon() {
        // 2026-11-08: waning crescent, 1 day before new moon. The moon rises
        // near sunrise and sets near sunset (it tracks the sun when new).
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 11, 8)
        val (rise, set) = LunarCalculator.calculateRiseSet(date, zone, 38.77, -84.56)
        assertNotNull("Moonrise should exist on 2026-11-08", rise)
        assertNotNull("Moonset should exist on 2026-11-08", set)
        val riseHour = rise!!.atZone(zone).hour
        val setHour = set!!.atZone(zone).hour
        assertTrue("Moonrise near new moon should be early morning (4-9 AM), was $riseHour", riseHour in 4..9)
        assertTrue("Moonset near new moon should be late afternoon (3-8 PM), was $setHour", setHour in 15..20)
        assertTrue("Moonrise should precede moonset", rise.isBefore(set))
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

        // Missing values render as a dash, never an invented number.
        assertEquals("--", imperial.formatTemperature(null))
        assertEquals("--", imperial.formatWindSpeed(null))
        assertEquals("--", imperial.formatPressure(null))
        assertEquals("--", imperial.formatVisibility(null))
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
        assertEquals("Good", goodAqi?.category)
        // Ozone: Open-Meteo reports µg/m³; the UI labels it ppb, so the
        // mapper converts (30 µg/m³ ≈ 15.3 ppb). The raw value must never
        // be shown as ppb.
        assertEquals(15.28, goodAqi?.ozonePpb ?: 0.0, 0.01)
        // 70 µg/m³ is ≈ 35.6 ppb — below the 54 ppb "Good" boundary, so
        // ozone is not the dominant pollutant (the old code compared the
        // raw µg/m³ against a ppb threshold and got this wrong).
        val ozoneHighUg = WeatherMappers.mapAqi(40, 5.0, 10.0, 70.0)
        assertEquals(35.66, ozoneHighUg?.ozonePpb ?: 0.0, 0.01)
        assertEquals("PM2.5", ozoneHighUg?.dominant)

        val modAqi = WeatherMappers.mapAqi(85, 25.0, 35.0, 50.0)
        assertEquals("Moderate", modAqi?.category)

        // No AQI from the provider means no AQI object — never a made-up 28.
        assertNull(WeatherMappers.mapAqi(null, null, null, null))
        // Present AQI with missing pollutants keeps the AQI and null pollutants.
        val sparseAqi = WeatherMappers.mapAqi(40, null, null, null)
        assertEquals(40, sparseAqi?.aqi)
        assertNull(sparseAqi?.pm25)
        assertNull(sparseAqi?.dominant)
    }

    @Test
    fun testMapAirNowAqi_worstPollutantWinsAndConcentrationsKept() {
        // Open-Meteo says AQI 35 (Good) with real concentrations.
        val openMeteo = WeatherMappers.mapAqi(35, 8.0, 15.0, 30.0)
        // AirNow: PM2.5 AQI 42, O3 AQI 68 (worst), PM10 AQI 30.
        val result = WeatherMappers.mapAirNowAqi(
            readings = listOf(
                AirNowReading("PM2.5", 42),
                AirNowReading("O3", 68),
                AirNowReading("PM10", 30)
            ),
            openMeteoAqi = openMeteo
        )
        assertEquals(68, result?.aqi)
        assertEquals("Moderate", result?.category)
        assertEquals("Ozone (O₃)", result?.dominant)
        assertEquals("AirNow", result?.source)
        // Concentrations stay Open-Meteo's (AirNow reports AQI only).
        assertEquals(8.0, result?.pm25 ?: 0.0, 0.001)
        assertEquals(15.0, result?.pm10 ?: 0.0, 0.001)
        assertEquals(15.28, result?.ozonePpb ?: 0.0, 0.01)
    }

    @Test
    fun testMapAirNowAqi_emptyFallsBackToOpenMeteo() {
        val openMeteo = WeatherMappers.mapAqi(35, 8.0, 15.0, 30.0)
        // Empty list (outside US coverage, or no station nearby).
        assertEquals(openMeteo, WeatherMappers.mapAirNowAqi(emptyList(), openMeteo))
        // Rows with no AQI at all are ignored the same way.
        assertEquals(
            openMeteo,
            WeatherMappers.mapAirNowAqi(listOf(AirNowReading("PM2.5", null)), openMeteo)
        )
    }

    @Test
    fun testMapAirNowAqi_airNowOnlyWhenOpenMeteoHasNone() {
        // No Open-Meteo AQI, but AirNow has one: headline from AirNow,
        // concentrations null (nothing else to take them from).
        val result = WeatherMappers.mapAirNowAqi(
            readings = listOf(AirNowReading("PM2.5", 120)),
            openMeteoAqi = null
        )
        assertEquals(120, result?.aqi)
        assertEquals("Unhealthy for Sensitive Groups", result?.category)
        assertEquals("PM2.5", result?.dominant)
        assertEquals("AirNow", result?.source)
        assertNull(result?.pm25)
        // Neither source has an AQI: null, never invented.
        assertNull(WeatherMappers.mapAirNowAqi(emptyList(), null))
    }

    @Test
    fun testOpenMeteoTime_parsedAsUtcNotLocal() {
        // Open-Meteo returns UTC wall times. "2026-11-07T14:00" must be
        // 14:00 UTC — the old code read local times as UTC ("${t}:00Z"),
        // shifting every hour 4-5 hours.
        assertEquals(Instant.parse("2026-11-07T14:00:00Z"), parseOpenMeteoUtcInstant("2026-11-07T14:00"))
        assertNull(parseOpenMeteoUtcInstant("not-a-time"))
    }

    /** A full day of 15-min timestamps starting at midnight UTC. */
    private fun dayTimesUtc(): List<String> {
        val times = mutableListOf<String>()
        for (h in 0 until 24) {
            for (m in listOf(0, 15, 30, 45)) {
                times.add("2026-11-07T%02d:%02d".format(h, m))
            }
        }
        return times
    }

    @Test
    fun testMinuteCastInterpolation_anchorsOnNowAndConvertsToMmPerHour() {
        // Rain in the 14:15 and 14:30 intervals only: 0.5 and 1.0 mm per
        // 15 minutes. At 14:20 the curve must use those — and the rates must
        // be mm/h (x4): 2.0 at 14:15, 4.0 at 14:30.
        val times = dayTimesUtc()
        val precip = MutableList(96) { 0.0 }
        precip[57] = 0.5 // 14:15
        precip[58] = 1.0 // 14:30
        val now = Instant.parse("2026-11-07T14:20:00Z")
        val zone = ZoneId.of("America/New_York")

        val (points, sentence) = WeatherMappers.interpolate15MinTo60Min(
            times = times,
            precip15 = precip,
            now = now,
            zoneId = zone
        )

        assertEquals("Must produce 60 minutes of interpolated resolution", 60, points.size)
        // Minute 0 is 14:20, 5 minutes into the 14:15 interval: it lerps
        // between the 14:15 (2.0 mm/h) and 14:30 (4.0 mm/h) rates at 5/15:
        // 2.0 + 2.0 * 5/15 = 2.67.
        assertTrue("Minute 0 should be ~2.67 mm/h, was ${points[0].mmPerHour}", abs(points[0].mmPerHour - 2.67) < 0.01)
        // Minute 10 is 14:30, the peak of the 14:30 interval: 4.0 mm/h.
        assertTrue("Minute 10 should be the 14:30 rate, 4.0 mm/h, was ${points[10].mmPerHour}", abs(points[10].mmPerHour - 4.0) < 0.01)
        assertTrue("Minute 45 should be dry, was ${points[45].mmPerHour}", points[45].mmPerHour < 0.05)
        // The rain ends at 14:45 UTC, which is 9:45 AM Eastern; the sentence
        // must use the place zone.
        assertTrue("Sentence should describe rain, was: $sentence", sentence.contains("rain", ignoreCase = true))
        assertTrue("Sentence time should be in Eastern time (9:45 AM), was: $sentence", sentence.contains("9:45 AM"))
    }

    @Test
    fun testMinuteCastInterpolation_midnightRainDoesNotLeakIntoAfternoon() {
        // Regression test for the original bug: rain only at 00:00-00:45
        // must NOT appear in a 14:20 nowcast. The old code took the first
        // five (midnight) values regardless of the time.
        val times = dayTimesUtc()
        val precip = MutableList(96) { 0.0 }
        precip[0] = 2.0
        precip[1] = 1.0
        precip[2] = 0.5
        val now = Instant.parse("2026-11-07T14:20:00Z")

        val (points, sentence) = WeatherMappers.interpolate15MinTo60Min(
            times = times,
            precip15 = precip,
            now = now
        )

        assertEquals(60, points.size)
        assertTrue("Afternoon curve must be dry when only midnight rained", points.all { it.mmPerHour < 0.05 })
        assertTrue("Sentence must say no precipitation, was: $sentence", sentence.contains("No precipitation", ignoreCase = true))
    }

    @Test
    fun testMinuteCastInterpolation_missingTimesIsUnavailableNotInvented() {
        val (points, sentence) = WeatherMappers.interpolate15MinTo60Min(
            times = null,
            precip15 = listOf(0.5, 1.0, 0.0, 0.0, 0.0),
            now = Instant.parse("2026-11-07T14:20:00Z")
        )
        assertTrue(points.isEmpty())
        assertTrue("Sentence must say unavailable, was: $sentence", sentence.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun testDestinationPoint_movesNorth80km() {
        // Covington, KY area: 39.10 N, 84.51 W. 80 km due north is
        // ~0.72 degrees of latitude, same longitude.
        val from = GeoPoint(39.10, -84.51)
        val dest = destinationPoint(from, 0.0, 80_000.0)
        assertEquals(39.82, dest.lat, 0.01)
        assertEquals(-84.51, dest.lon, 0.01)
        assertEquals(80_000.0, distanceMeters(from, dest), 1.0)
    }

    @Test
    fun testDestinationPoint_movesEast80km() {
        val from = GeoPoint(39.10, -84.51)
        val dest = destinationPoint(from, 90.0, 80_000.0)
        assertEquals(39.10, dest.lat, 0.01)
        assertTrue("East destination must be east of the start", dest.lon > -84.51)
        assertEquals(80_000.0, distanceMeters(from, dest), 1.0)
    }

    @Test
    fun testDestinationPoint_allEightBearingsAre80kmAway() {
        val from = GeoPoint(39.10, -84.51)
        for (bearing in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
            val dest = destinationPoint(from, bearing, 80_000.0)
            assertEquals("Bearing $bearing must land 80 km away", 80_000.0, distanceMeters(from, dest), 1.0)
        }
    }

    @Test
    fun testRegionalCompassLabel_eightPoints() {
        assertEquals("N", regionalCompassLabel(0.0))
        assertEquals("NE", regionalCompassLabel(45.0))
        assertEquals("E", regionalCompassLabel(90.0))
        assertEquals("SE", regionalCompassLabel(135.0))
        assertEquals("S", regionalCompassLabel(180.0))
        assertEquals("SW", regionalCompassLabel(225.0))
        assertEquals("W", regionalCompassLabel(270.0))
        assertEquals("NW", regionalCompassLabel(315.0))
        assertEquals("N", regionalCompassLabel(360.0))
    }

    @Test
    fun testShortMapLabel_dropsStateAndHyphenatedTail() {
        assertEquals("LEXINGTON", shortMapLabel("LEXINGTON-FAYETTE, KY"))
        assertEquals("SEVEN MILE", shortMapLabel("SEVEN MILE, OH"))
        assertEquals("CRITTENDEN", shortMapLabel("CRITTENDEN, KY"))
        assertEquals("BATESVILLE", shortMapLabel("BATESVILLE, IN"))
    }

    @Test
    fun testShortMapLabel_compassFallbackUnchanged() {
        assertEquals("NE", shortMapLabel("NE"))
        assertEquals("SW", shortMapLabel("SW"))
    }

    @Test
    fun testGibsTimestamp_backsOffSixtyMinutesThenRoundsDown() {
        // GIBS publishes GOES scans behind real time (GeoColor ~41-51 min,
        // Band13 ~21-31 min, measured 2026-11-09), so the latest 10-minute
        // boundary 404s. Back off 60 minutes first, then round down.
        assertEquals("2026-11-09T11:40:00Z", gibsTimestamp(Instant.parse("2026-11-09T12:43:27Z")))
        assertEquals("2026-11-09T11:40:00Z", gibsTimestamp(Instant.parse("2026-11-09T12:40:00Z")))
        assertEquals("2026-11-09T11:30:00Z", gibsTimestamp(Instant.parse("2026-11-09T12:39:59Z")))
        assertEquals("2026-11-08T23:00:00Z", gibsTimestamp(Instant.parse("2026-11-09T00:04:59Z")))
    }

    @Test
    fun testMapTomorrowMinutely_buildsSixtyPointsAndSentence() {
        // 1-minute intervals, dry then a rain band from minute 20.
        val now = Instant.parse("2026-11-09T12:00:00Z")
        val zone = ZoneId.of("America/New_York")
        val points = (0 until 60).map { m ->
            val mm = if (m >= 20 && m < 40) 2.5 else 0.0
            val prob = if (m >= 20 && m < 40) 80 else 0
            TomorrowMinutePoint(now.plusSeconds(m.toLong() * 60), mm, prob)
        }
        val (minutely, sentence) = WeatherMappers.mapTomorrowMinutely(points, now, zone)
        assertEquals(60, minutely.size)
        assertEquals(0.0, minutely[0].mmPerHour, 0.001)
        assertEquals(2.5, minutely[25].mmPerHour, 0.001)
        assertEquals(80, minutely[25].probabilityPct)
        assertEquals(0.0, minutely[50].mmPerHour, 0.001)
        assertTrue("Sentence must describe the rain", sentence.contains("rain", ignoreCase = true))
    }

    @Test
    fun testMapTomorrowMinutely_emptyListIsUnavailable() {
        val now = Instant.parse("2026-11-09T12:00:00Z")
        val (minutely, sentence) = WeatherMappers.mapTomorrowMinutely(
            emptyList(), now, ZoneId.of("America/New_York")
        )
        assertTrue(minutely.isEmpty())
        assertTrue(sentence.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun testMapTomorrowMinutely_missingMinutesCarryForward() {
        // Only minutes 0 and 30 present; the gap carries minute 0's rate.
        val now = Instant.parse("2026-11-09T12:00:00Z")
        val points = listOf(
            TomorrowMinutePoint(now, 1.0, 50),
            TomorrowMinutePoint(now.plusSeconds(30 * 60), 3.0, 90)
        )
        val (minutely, _) = WeatherMappers.mapTomorrowMinutely(
            points, now, ZoneId.of("America/New_York")
        )
        assertEquals(60, minutely.size)
        assertEquals(1.0, minutely[15].mmPerHour, 0.001)
        assertEquals(3.0, minutely[45].mmPerHour, 0.001)
    }

    @Test
    fun testMapTomorrowPollen_keepsIndicesAndKeyFlag() {
        val pollen = WeatherMappers.mapTomorrowPollen(2, 1, 0, true)
        assertEquals(2, pollen.treeIndex)
        assertEquals(1, pollen.grassIndex)
        assertEquals(0, pollen.weedIndex)
        assertEquals("Tomorrow.io", pollen.source)
        assertTrue(pollen.hasKey)
    }

    @Test
    fun testMapTomorrowPollen_nullIndicesStayNull() {
        // A key with no pollen data is not invented: indices stay null.
        val pollen = WeatherMappers.mapTomorrowPollen(null, null, null, true)
        assertNull(pollen.treeIndex)
        assertNull(pollen.grassIndex)
        assertNull(pollen.weedIndex)
        assertTrue(pollen.hasKey)
    }

    @Test
    fun testResolveLocationPermission_fineGrantsHighAccuracy() {
        val outcome = WeatherMappers.resolveLocationPermission(
            fineGranted = true, coarseGranted = true
        )
        assertTrue(outcome.granted)
        assertTrue(outcome.highAccuracy)
    }

    @Test
    fun testResolveLocationPermission_coarseOnlyGrantsBalanced() {
        // Approximate-only grant: location still works, just not precise.
        val outcome = WeatherMappers.resolveLocationPermission(
            fineGranted = false, coarseGranted = true
        )
        assertTrue(outcome.granted)
        assertTrue("Coarse-only must not claim high accuracy", !outcome.highAccuracy)
    }

    @Test
    fun testResolveLocationPermission_neitherGrantsNothing() {
        val outcome = WeatherMappers.resolveLocationPermission(
            fineGranted = false, coarseGranted = false
        )
        assertTrue(!outcome.granted)
        assertTrue(!outcome.highAccuracy)
    }

    @Test
    fun testStableAlertId_sameInputsSameId() {
        val effective = Instant.parse("2026-11-09T12:00:00Z")
        val first = stableAlertId("Tornado Warning", "Boone County", effective)
        val second = stableAlertId("Tornado Warning", "Boone County", effective)
        assertEquals("A refetch must produce the same id", first, second)
        assertTrue("Derived ids carry the alert_ prefix", first.startsWith("alert_"))
    }

    @Test
    fun testStableAlertId_differentFieldsDifferentIds() {
        val effective = Instant.parse("2026-11-09T12:00:00Z")
        val base = stableAlertId("Tornado Warning", "Boone County", effective)
        assertTrue("Different event", base != stableAlertId("Severe Thunderstorm Warning", "Boone County", effective))
        assertTrue("Different area", base != stableAlertId("Tornado Warning", "Kenton County", effective))
        assertTrue("Different effective time", base != stableAlertId("Tornado Warning", "Boone County", Instant.parse("2026-11-09T13:00:00Z")))
        assertTrue("Null fields still produce a stable id", stableAlertId(null, null, effective) == stableAlertId(null, null, effective))
    }

    @Test
    fun testFillDailyHighs_fillsTodayNullHighFromOpenMeteo() {
        // Evening case: NWS gave today only a nighttime period, so
        // highC is null but lowC is set. Open-Meteo has the day's
        // true max — it must fill the high, and leave the low alone.
        val today = LocalDate.of(2026, 11, 9)
        val tomorrow = LocalDate.of(2026, 11, 10)
        val nws = listOf(
            DailyPoint(today, null, 5.6, 20, WeatherCondition.ClearNight, "Clear", null),
            DailyPoint(tomorrow, 12.0, 3.0, 10, WeatherCondition.ClearDay, "Sunny", null)
        )
        val om = listOf(
            DailyPoint(today, 11.1, 5.0, 20, WeatherCondition.ClearDay, "Clear", 2.0),
            DailyPoint(tomorrow, 12.0, 3.0, 10, WeatherCondition.ClearDay, "Sunny", 1.5)
        )
        val filled = fillDailyHighs(nws, om)
        assertEquals(11.1, filled[0].highC!!, 0.001)
        assertEquals(5.6, filled[0].lowC!!, 0.001)
        // Tomorrow already had both values: untouched.
        assertEquals(12.0, filled[1].highC!!, 0.001)
        assertEquals(3.0, filled[1].lowC!!, 0.001)
    }

    @Test
    fun testFillDailyHighs_completeListReturnedUnchanged() {
        val today = LocalDate.of(2026, 11, 9)
        val nws = listOf(
            DailyPoint(today, 11.1, 5.6, 20, WeatherCondition.ClearDay, "Clear", null)
        )
        val om = listOf(
            DailyPoint(today, 12.0, 4.0, 20, WeatherCondition.ClearDay, "Clear", 2.0)
        )
        val filled = fillDailyHighs(nws, om)
        assertEquals(11.1, filled[0].highC!!, 0.001)
        assertEquals(5.6, filled[0].lowC!!, 0.001)
    }

    @Test
    fun testFillDailyHighs_dateMissingFromOpenMeteoLeftAlone() {
        val today = LocalDate.of(2026, 11, 9)
        val nws = listOf(
            DailyPoint(today, null, 5.6, 20, WeatherCondition.ClearNight, "Clear", null)
        )
        val filled = fillDailyHighs(nws, emptyList())
        assertNull(filled[0].highC)
        assertEquals(5.6, filled[0].lowC!!, 0.001)
    }
}
