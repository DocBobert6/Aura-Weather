package com.example

import com.example.ui.widget.BitmapTextSize
import com.example.ui.widget.WIDGET_BITMAP_BINDER_BUDGET_BYTES
import com.example.ui.widget.WidgetTextFace
import com.example.ui.widget.bitmapByteSize
import com.example.ui.widget.bitmapFitsBinderBudget
import com.example.ui.widget.computeBitmapSize
import com.example.ui.widget.ellipsizeToWidth
import com.example.ui.widget.star4000Safe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the bitmap-text pipeline (Item 3, 0.3.2): the
 * sanitiser, ellipsiser, bitmap sizing, and Binder budget check in
 * WidgetBitmapText. The Android Canvas rendering itself
 * (WidgetTextBitmapRenderer) is CI-only — these tests cover every
 * branch of it that can run on the JVM.
 */
class WidgetBitmapTextTest {

    // star4000Safe: Star4000 has no en/em dash or ellipsis glyphs.

    @Test
    fun testStar4000Safe_replacesDashesAndEllipsis() {
        assertEquals("A-B-C...", star4000Safe("A\u2014B\u2013C\u2026"))
    }

    @Test
    fun testStar4000Safe_leavesPlainTextAlone() {
        assertEquals("72\u00B0F CLEAR", star4000Safe("72\u00B0F CLEAR"))
        assertEquals("CRITTENDEN, KY", star4000Safe("CRITTENDEN, KY"))
        assertEquals("H 63\u00B0 / L 42\u00B0", star4000Safe("H 63\u00B0 / L 42\u00B0"))
    }

    @Test
    fun testStar4000Safe_emptyStaysEmpty() {
        assertEquals("", star4000Safe(""))
    }

    // ellipsizeToWidth, with a fake measurer (10px per char).

    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun testEllipsize_textThatFitsIsUnchanged() {
        assertEquals("72\u00B0", ellipsizeToWidth("72\u00B0", 100f, measure))
        assertEquals("NOW", ellipsizeToWidth("NOW", 30f, measure))
    }

    @Test
    fun testEllipsize_longTextKeepsPrefixPlusDots() {
        // "CRITTENDEN, KY" is 14 chars (140px); 100px fits 7 chars + "...".
        assertEquals("CRITTEN...", ellipsizeToWidth("CRITTENDEN, KY", 100f, measure))
    }

    @Test
    fun testEllipsize_exactFitIsUnchanged() {
        assertEquals("12345", ellipsizeToWidth("12345", 50f, measure))
    }

    @Test
    fun testEllipsize_nothingFitsReturnsEmpty() {
        // "..." alone is 30px; a 20px cap cannot fit even that.
        assertEquals("", ellipsizeToWidth("CRITTENDEN", 20f, measure))
    }

    @Test
    fun testEllipsize_emptyTextReturnsEmpty() {
        assertEquals("", ellipsizeToWidth("", 100f, measure))
    }

    @Test
    fun testEllipsize_usesDotsNotUnicodeEllipsis() {
        // Star4000 has no U+2026 glyph; the suffix must be three periods.
        val result = ellipsizeToWidth("ABCDEFGHIJ", 50f, measure)
        assertTrue(result.endsWith("..."))
        assertFalse(result.contains("\u2026"))
    }

    // computeBitmapSize: width = ceil(text) + 2*pad, height from metrics.

    @Test
    fun testComputeBitmapSize_addsPaddingBothSides() {
        // text 100.2px wide, ascent -80, descent 20, pad 3:
        // width 101 + 6 = 107, height 100 + 6 = 106.
        assertEquals(
            BitmapTextSize(107, 106),
            computeBitmapSize(100.2f, -80f, 20f, 3)
        )
    }

    @Test
    fun testComputeBitmapSize_neverZero() {
        assertEquals(BitmapTextSize(1, 1), computeBitmapSize(0f, 0f, 0f, 0))
    }

    // Binder budget: ARGB_8888 = 4 bytes/px, ~1 MB per update.

    @Test
    fun testBitmapByteSize_argb8888() {
        assertEquals(20_000L, bitmapByteSize(100, 50))
        assertEquals(0L, bitmapByteSize(0, 50))
    }

    @Test
    fun testBitmapFitsBinderBudget_textRunFits() {
        // A big-temp run at Pixel density: ~200x100px is ~80 KB.
        assertTrue(bitmapFitsBinderBudget(200, 100))
        // Even a full-width ticker run fits on its own.
        assertTrue(bitmapFitsBinderBudget(800, 40))
    }

    @Test
    fun testBitmapFitsBinderBudget_hugeBitmapRefused() {
        // 600x500px is 1.2 MB — over the 1 MB budget.
        assertFalse(bitmapFitsBinderBudget(600, 500))
        assertEquals(1_048_576L, WIDGET_BITMAP_BINDER_BUDGET_BYTES)
    }

    // The face enum exists for both renderer faces.

    @Test
    fun testFaces_bothExist() {
        assertEquals(2, WidgetTextFace.values().size)
        assertEquals(WidgetTextFace.Large, WidgetTextFace.valueOf("Large"))
        assertEquals(WidgetTextFace.Small, WidgetTextFace.valueOf("Small"))
    }
}
