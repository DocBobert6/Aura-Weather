package com.example

import com.example.data.mapper.shortConditionCode
import com.example.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyShortCodesTest {

    @Test
    fun allConditionsHaveUppercaseCodes() {
        for (condition in WeatherCondition.values()) {
            val code = shortConditionCode(condition)
            assertTrue(condition.name, code.isNotBlank())
            assertEquals(condition.name, code, code.uppercase())
        }
    }

    @Test
    fun truncatedCasesAreFixed() {
        // The two truncations on Robert's screenshots.
        assertEquals("PT CLDY", shortConditionCode(WeatherCondition.PartlyCloudyDay))
        assertEquals("PT CLDY", shortConditionCode(WeatherCondition.PartlyCloudyNight))
        assertEquals("DRIZZLE", shortConditionCode(WeatherCondition.Drizzle))
    }

    @Test
    fun widestConditionsHaveShortCodes() {
        assertEquals("T-STORMS", shortConditionCode(WeatherCondition.Thunderstorm))
        assertEquals("HVY RAIN", shortConditionCode(WeatherCondition.HeavyRain))
        assertEquals("FRZ RAIN", shortConditionCode(WeatherCondition.FreezingRain))
    }

    @Test
    fun remainingConditions() {
        assertEquals("CLEAR", shortConditionCode(WeatherCondition.ClearDay))
        assertEquals("CLEAR", shortConditionCode(WeatherCondition.ClearNight))
        assertEquals("CLOUDY", shortConditionCode(WeatherCondition.Cloudy))
        assertEquals("FOG", shortConditionCode(WeatherCondition.Fog))
        assertEquals("HAZY", shortConditionCode(WeatherCondition.Haze))
        assertEquals("RAIN", shortConditionCode(WeatherCondition.Rain))
        assertEquals("SNOW", shortConditionCode(WeatherCondition.Snow))
        assertEquals("SLEET", shortConditionCode(WeatherCondition.Sleet))
        assertEquals("WINDY", shortConditionCode(WeatherCondition.Windy))
        assertEquals("FAIR", shortConditionCode(WeatherCondition.Unknown))
    }

    @Test
    fun widestCodeFitsTheDailyColumn() {
        // star4000_small, measured with fontTools: every capital advances
        // 973/2048 em, so at 14sp one char is 973/2048 * 14 = 6.65dp, plus
        // the bodyMedium letter spacing of 0.25sp per char.
        val advanceDpPerChar = 973.0 / 2048.0 * 14.0 + 0.25
        var widest = 0
        for (condition in WeatherCondition.values()) {
            widest = maxOf(widest, shortConditionCode(condition).length)
        }
        assertEquals(8, widest)
        val widestWidthDp = widest * advanceDpPerChar
        // The condition column is 83dp on a 411dp screen (16dp list
        // padding + 18dp card padding + 252dp fixed columns). The widest
        // code must fit it with room to spare.
        assertTrue("widest code renders $widestWidthDp dp, over the 83dp column", widestWidthDp < 83.0)
        // And the full names it replaces would NOT have fit: the longest
        // full name is 13 chars ("PARTLY CLOUDY", "THUNDERSTORMS" is 13
        // before uppercasing is irrelevant — length is what matters).
        assertTrue("13 chars at 14sp must exceed the column", 13 * advanceDpPerChar > 83.0)
    }
}
