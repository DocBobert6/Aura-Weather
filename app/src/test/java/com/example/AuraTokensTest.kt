package com.example

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Token tests (run in CI; not part of the kotlinc pure-test script, which
 * has no Compose on its classpath). R1: the condition palettes, the glass
 * levels, and the sky gradients are gone with the palette/glass/sky
 * removal — the tests below cover what remains: the data colour scales,
 * the legacy shapes, and the retro tokens.
 */
class AuraTokensTest {

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
    fun retroTokens_navyScaleAndAccents() {
        assertEquals(Color(0xFF0A1E5C), RetroTokens.Background)
        assertEquals(Color(0xFF14307E), RetroTokens.Panel)
        assertEquals(Color(0xFF1E3F9E), RetroTokens.PanelLight)
        assertEquals(Color(0xFF4A6AC9), RetroTokens.Border)
        assertEquals(Color(0xFFD93A2B), RetroTokens.HeaderRed)
        assertEquals(Color(0xFFF26522), RetroTokens.HeaderOrange)
        assertEquals(Color(0xFFFFD600), RetroTokens.Yellow)
        assertEquals(Color(0xFFFFFFFF), RetroTokens.TextPrimary)
        assertEquals(Color(0xFF0D2B7A), RetroTokens.TickerBlue)
        // The panel is a lighter navy than the background; the yellow is
        // bright enough to read as a heading colour on navy.
        assertTrue(RetroTokens.Panel.luminance() > RetroTokens.Background.luminance())
        assertTrue(RetroTokens.Yellow.luminance() > 0.5f)
    }

    @Test
    fun retroShapes_classicRadii() {
        assertEquals(CornerSize(4.dp), RetroShapes.small.topStart)
        assertEquals(CornerSize(6.dp), RetroShapes.medium.topStart)
        assertEquals(CornerSize(8.dp), RetroShapes.large.topStart)
    }
}
