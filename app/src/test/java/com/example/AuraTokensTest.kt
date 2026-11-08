package com.example

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherCondition
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraGlass
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.AuraSky
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.WeatherPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.2 token tests (run in CI; not part of the kotlinc pure-test
 * script, which has no Compose on its classpath).
 */
class AuraTokensTest {

    @Test
    fun schemes_resolveForEveryConditionDayNightAndMode() {
        for (condition in WeatherCondition.entries) {
            for (isDaytime in listOf(true, false)) {
                val dark = WeatherPalettes.getSchemeForCondition(condition, isDaytime, true)
                val light = WeatherPalettes.getSchemeForCondition(condition, isDaytime, false)
                // A scheme is never empty, and light really is lighter.
                assertNotEquals(Color.Unspecified, dark.primary)
                assertNotEquals(Color.Unspecified, light.primary)
                assertTrue(
                    "$condition day=$isDaytime: light background must be lighter than dark",
                    light.background.luminance() > dark.background.luminance()
                )
                assertTrue(
                    "$condition day=$isDaytime: light surface must be lighter than dark",
                    light.surface.luminance() > dark.surface.luminance()
                )
            }
        }
    }

    @Test
    fun schemes_differByConditionAndDayNight() {
        val clearDay = WeatherPalettes.getSchemeForCondition(WeatherCondition.ClearDay, true, true)
        val stormNight =
            WeatherPalettes.getSchemeForCondition(WeatherCondition.Thunderstorm, false, true)
        assertNotEquals(clearDay.primary, stormNight.primary)
        assertNotEquals(clearDay.background, stormNight.background)
        val clearNight =
            WeatherPalettes.getSchemeForCondition(WeatherCondition.ClearDay, false, true)
        assertNotEquals(clearDay.background, clearNight.background)
    }

    @Test
    fun severityScale_oneColourAndLabelPerLevel() {
        val expected = mapOf(
            AlertSeverity.Extreme to (Color(0xFFD32F2F) to "EXTREME"),
            AlertSeverity.Severe to (Color(0xFFF57C00) to "SEVERE"),
            AlertSeverity.Moderate to (Color(0xFFFFA000) to "MODERATE"),
            AlertSeverity.Minor to (Color(0xFF1E88E5) to "ADVISORY"),
            AlertSeverity.Unknown to (Color(0xFF5C6BC0) to "STATEMENT")
        )
        val colours = mutableSetOf<Color>()
        for ((severity, want) in expected) {
            val got = AuraColors.severityStyle(severity)
            assertEquals(want, got)
            assertTrue(colours.add(got.first))
        }
        assertEquals(5, colours.size)
    }

    @Test
    fun aqiScale_bandsMatchAirScreen() {
        assertEquals(Color.White.copy(alpha = 0.5f), AuraColors.aqiColor(null))
        assertEquals(Color(0xFF00E676), AuraColors.aqiColor(30))
        assertEquals(Color(0xFFFFEB3B), AuraColors.aqiColor(75))
        assertEquals(Color(0xFFFF9800), AuraColors.aqiColor(125))
        assertEquals(Color(0xFFF44336), AuraColors.aqiColor(175))
        assertEquals(Color(0xFF9C27B0), AuraColors.aqiColor(250))
        assertEquals(Color(0xFF795548), AuraColors.aqiColor(400))
    }

    @Test
    fun shapes_collapseToFourTokens() {
        assertEquals(CornerSize(12.dp), AuraShapes.small.topStart)
        assertEquals(CornerSize(20.dp), AuraShapes.medium.topStart)
        assertEquals(CornerSize(28.dp), AuraShapes.large.topStart)
        assertEquals(CornerSize(50), AuraShapes.pill.topStart)
    }

    @Test
    fun glass_barIsStrongerThanCard() {
        assertTrue(AuraGlass.blurRadius(GlassLevel.Bar) > AuraGlass.blurRadius(GlassLevel.Card))
        assertTrue(AuraGlass.scrimAlpha(GlassLevel.Bar) > AuraGlass.scrimAlpha(GlassLevel.Card))
    }

    @Test
    fun sky_gradientsCoverEveryCondition() {
        for (condition in WeatherCondition.entries) {
            for (isDaytime in listOf(true, false)) {
                val gradient = AuraSky.gradientColors(condition, isDaytime)
                assertEquals(3, gradient.size)
                assertTrue(gradient.none { it == Color.Unspecified })
            }
        }
        assertNotEquals(
            AuraSky.celestialColor(true),
            AuraSky.celestialColor(false)
        )
    }
}
