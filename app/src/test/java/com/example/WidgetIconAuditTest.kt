package com.example

import com.example.data.mapper.WeatherMappers
import com.example.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Widget-audit item 7 (0.3.2, Worker D): weather icon fixes + 2 AM
 * diagnosis. Pure Kotlin — no Compose, no Android — so it runs in the
 * kotlinc + JUnit harness. getWeatherIcon itself is Compose (CI-only);
 * what is pinned here is every pure decision underneath it: the text
 * that reaches the icon, and the daily row's single display condition.
 */
class WidgetIconAuditTest {

    // --- 2 AM diagnosis (item 7a) -------------------------------------
    // 2026-11-16 23:45, Crittenden KY: the 48h strip showed a plain
    // overcast-cloud icon at 2 AM. The strip is NWS-hourly PRIMARY
    // (Open-Meteo is fallback only), and the live probe said NWS ILN
    // hourly 02:00 was "Partly Cloudy" while Open-Meteo said WMO code 0
    // with 1% cloud. The cause was the check order in
    // mapNwsTextToCondition: "cloudy" was tested before "partly", and
    // "Partly Cloudy" contains "cloudy" — so it mapped to Cloudy.
    // These tests pin the corrected order.

    @Test
    fun testNwsPartlyCloudyMapsToPartlyNotCloudy() {
        assertEquals(
            WeatherCondition.PartlyCloudyNight,
            WeatherMappers.mapNwsTextToCondition("Partly Cloudy", isDaytime = false)
        )
        assertEquals(
            WeatherCondition.PartlyCloudyDay,
            WeatherMappers.mapNwsTextToCondition("Partly Cloudy", isDaytime = true)
        )
        // "Partly Sunny" is the same condition by another name.
        assertEquals(
            WeatherCondition.PartlyCloudyDay,
            WeatherMappers.mapNwsTextToCondition("Partly Sunny", isDaytime = true)
        )
    }

    @Test
    fun testNwsOvercastAndCloudyStillMapToCloudy() {
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.mapNwsTextToCondition("Cloudy", isDaytime = false)
        )
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.mapNwsTextToCondition("Overcast", isDaytime = true)
        )
        // Mostly Cloudy has no "partly": still Cloudy, honestly.
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.mapNwsTextToCondition("Mostly Cloudy", isDaytime = false)
        )
    }

    @Test
    fun testNwsClearVariantsMapToClear() {
        assertEquals(
            WeatherCondition.ClearNight,
            WeatherMappers.mapNwsTextToCondition("Clear", isDaytime = false)
        )
        assertEquals(
            WeatherCondition.ClearNight,
            WeatherMappers.mapNwsTextToCondition("Mostly Clear", isDaytime = false)
        )
        // "Fair" is the NWS night-clear word (and Unknown's display
        // name); it is clear, never cloudy.
        assertEquals(
            WeatherCondition.ClearNight,
            WeatherMappers.mapNwsTextToCondition("Fair", isDaytime = false)
        )
    }

    @Test
    fun testNwsFogSnowSleetFreezingRainMapHonestly() {
        assertEquals(
            WeatherCondition.Fog,
            WeatherMappers.mapNwsTextToCondition("Patchy Fog", isDaytime = false)
        )
        assertEquals(
            WeatherCondition.Haze,
            WeatherMappers.mapNwsTextToCondition("Haze", isDaytime = true)
        )
        assertEquals(
            WeatherCondition.Snow,
            WeatherMappers.mapNwsTextToCondition("Snow", isDaytime = true)
        )
        // "Snow Showers" contains "showers": snow must win over rain.
        assertEquals(
            WeatherCondition.Snow,
            WeatherMappers.mapNwsTextToCondition("Snow Showers", isDaytime = true)
        )
        // Plain rain showers still read as rain.
        assertEquals(
            WeatherCondition.Rain,
            WeatherMappers.mapNwsTextToCondition("Rain Showers", isDaytime = true)
        )
        assertEquals(
            WeatherCondition.Sleet,
            WeatherMappers.mapNwsTextToCondition("Sleet", isDaytime = true)
        )
        assertEquals(
            WeatherCondition.FreezingRain,
            WeatherMappers.mapNwsTextToCondition("Freezing Rain", isDaytime = true)
        )
    }

    // --- WMO mapping (item 7a): the plain Cloud icon must require an
    // actually-overcast condition ---------------------------------------

    @Test
    fun testWmoClearIsClearNotCloudy() {
        // WMO 0, night: the 2 AM Open-Meteo reading. Clear, not Cloudy.
        assertEquals(
            WeatherCondition.ClearNight,
            WeatherMappers.mapWmoCodeToCondition(0, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.ClearDay,
            WeatherMappers.mapWmoCodeToCondition(0, isDaytime = true)
        )
    }

    @Test
    fun testWmoPartlyAndOvercast() {
        assertEquals(
            WeatherCondition.PartlyCloudyNight,
            WeatherMappers.mapWmoCodeToCondition(1, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.PartlyCloudyDay,
            WeatherMappers.mapWmoCodeToCondition(2, isDaytime = true)
        )
        // Only WMO 3 is actually overcast.
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.mapWmoCodeToCondition(3, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.mapWmoCodeToCondition(3, isDaytime = true)
        )
    }

    @Test
    fun testWmoFogAndSnow() {
        assertEquals(
            WeatherCondition.Fog,
            WeatherMappers.mapWmoCodeToCondition(45, isDaytime = true)
        )
        assertEquals(
            WeatherCondition.Snow,
            WeatherMappers.mapWmoCodeToCondition(71, isDaytime = true)
        )
        assertEquals(
            WeatherCondition.Snow,
            WeatherMappers.mapWmoCodeToCondition(85, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.FreezingRain,
            WeatherMappers.mapWmoCodeToCondition(66, isDaytime = true)
        )
    }

    // --- Daily row, single-source consistency (item 7b) ----------------
    // The row's text AND icon both read dailyDisplayCondition's one
    // return value, so the pop override changes text+icon together or
    // neither. These tests pin the override itself.

    @Test
    fun testDailyDisplayHighPopBecomesRain() {
        assertEquals(
            WeatherCondition.Rain,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearDay, 50, isDaytime = true)
        )
        assertEquals(
            WeatherCondition.Rain,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.Cloudy, 80, isDaytime = true)
        )
        // Rain stays rain even at night.
        assertEquals(
            WeatherCondition.Rain,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearNight, 90, isDaytime = false)
        )
    }

    @Test
    fun testDailyDisplayModeratePopOnClearBecomesPartlyDayAndNight() {
        assertEquals(
            WeatherCondition.PartlyCloudyDay,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearDay, 30, isDaytime = true)
        )
        // The night case: the old inline override always emitted the
        // DAY variant for a TODAY row at night. It must be Night.
        assertEquals(
            WeatherCondition.PartlyCloudyNight,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearNight, 30, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.PartlyCloudyNight,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearDay, 49, isDaytime = false)
        )
    }

    @Test
    fun testDailyDisplayNoOverrideBelowThresholdOrWhenNotClear() {
        // Below 30%: the condition stands, text and icon agree on it.
        assertEquals(
            WeatherCondition.ClearNight,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearNight, 29, isDaytime = false)
        )
        assertEquals(
            WeatherCondition.ClearDay,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.ClearDay, 0, isDaytime = true)
        )
        // Moderate chance on an already-cloudy day: no override — the
        // condition already says cloudy, nothing to add.
        assertEquals(
            WeatherCondition.Cloudy,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.Cloudy, 40, isDaytime = true)
        )
        // "Fair" (Unknown) is not Clear: no override, no invented
        // partly-cloudy.
        assertEquals(
            WeatherCondition.Unknown,
            WeatherMappers.dailyDisplayCondition(WeatherCondition.Unknown, 40, isDaytime = false)
        )
    }
}
