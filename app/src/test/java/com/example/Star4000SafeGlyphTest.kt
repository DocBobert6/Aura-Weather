package com.example

import com.example.ui.widget.star4000Safe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pure tests for the extended star4000Safe coverage (Item 4, 0.3.3):
 * every character the PIL ink probe found empty in BOTH Star4000
 * fonts (0 ink pixels at 40pt in star4000_large.ttf and
 * star4000_small.ttf) must be replaced, and the characters the probe
 * found WITH ink (degree U+00B0, bullet U+2022, curly quotes) must
 * survive untouched. The original dash/ellipsis cases live in
 * WidgetBitmapTextTest; this file covers the new mappings only.
 */
class Star4000SafeGlyphTest {

    // The proven bug: micro + superscript 3 in the pollutant unit.

    @Test
    fun testStar4000Safe_microAndSuperscript3() {
        assertEquals("12.3 Ug/m3", star4000Safe("12.3 µg/m³"))
    }

    @Test
    fun testStar4000Safe_superscripts() {
        assertEquals("1 2 3", star4000Safe("¹ ² ³"))
    }

    @Test
    fun testStar4000Safe_subscripts() {
        // Subscript 3 renders as the missing-glyph box, not blank.
        assertEquals("O3 Level", star4000Safe("O₃ Level"))
        assertEquals("H2O", star4000Safe("H₂O"))
        assertEquals("0123456789", star4000Safe("₀₁₂₃₄₅₆₇₈₉"))
    }

    @Test
    fun testStar4000Safe_middot() {
        // The widget "LIVE NOW · 14:00" separator (RadarWidgetModel).
        assertEquals("LIVE NOW - 14:00", star4000Safe("LIVE NOW · 14:00"))
    }

    @Test
    fun testStar4000Safe_multiplyAndMinus() {
        assertEquals("2x2", star4000Safe("2×2"))
        assertEquals("5-3", star4000Safe("5−3"))
    }

    @Test
    fun testStar4000Safe_allFiveNamedCharsTogether() {
        // µ U+00B5, ³ U+00B3, en dash U+2013, em dash U+2014,
        // ellipsis U+2026 — the five from the audit brief.
        assertEquals("Ug/m3 - A-B...", star4000Safe("µg/m³ – A—B…"))
    }

    @Test
    fun testStar4000Safe_noInvisibleGlyphRemains() {
        val result = star4000Safe("µg/m³ – — … · × − ¹²³ ₀₃")
        val invisible = listOf(
            '\u00B5', '\u00B3', '\u00B2', '\u00B9',
            '\u2013', '\u2014', '\u2026',
            '\u00B7', '\u00D7', '\u2212',
            '\u2080', '\u2081', '\u2082', '\u2083'
        )
        invisible.forEach { ch ->
            assertFalse("U+${ch.code.toString(16).uppercase()} survived", result.contains(ch))
        }
        assertEquals("Ug/m3 - - ... - x - 123 03", result)
    }

    @Test
    fun testStar4000Safe_inkedCharsSurvive() {
        // Degree, bullet, and curly quotes have ink in both fonts.
        assertEquals("72°F", star4000Safe("72°F"))
        assertEquals("Cached • 5m ago", star4000Safe("Cached • 5m ago"))
        assertEquals("COULDN’T LOAD", star4000Safe("COULDN’T LOAD"))
        assertEquals("“QUOTED”", star4000Safe("“QUOTED”"))
    }
}
