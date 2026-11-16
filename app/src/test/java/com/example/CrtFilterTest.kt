package com.example

import com.example.ui.radar.engine.CrtFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for the 90s CRT filter parameters ([CrtFilter]).
 * Run locally with run-pure-tests-crt-filter.sh (kotlinc + JUnit);
 * the draw layer itself is verified by CI (it compiles against the
 * real Compose runtime there).
 */
class CrtFilterTest {

    @Test
    fun `filter is on by default`() {
        assertTrue(CrtFilter.DEFAULT_ENABLED)
    }

    @Test
    fun `all parameters are in their valid ranges`() {
        assertTrue(CrtFilter.isValid())
    }

    @Test
    fun `scanline pitch is 3dp with a 1dp line at 0_10 alpha`() {
        assertEquals(3f, CrtFilter.SCANLINE_SPACING_DP, 0f)
        assertEquals(1f, CrtFilter.SCANLINE_THICKNESS_DP, 0f)
        assertEquals(0.10f, CrtFilter.SCANLINE_ALPHA, 0f)
    }

    @Test
    fun `aperture grille is fainter than the scanlines`() {
        assertEquals(3f, CrtFilter.GRILLE_SPACING_DP, 0f)
        assertEquals(1f, CrtFilter.GRILLE_THICKNESS_DP, 0f)
        assertEquals(0.05f, CrtFilter.GRILLE_ALPHA, 0f)
        assertTrue(CrtFilter.GRILLE_ALPHA < CrtFilter.SCANLINE_ALPHA)
    }

    @Test
    fun `vignette peaks at 0_28 black at the corners`() {
        assertEquals(0.28f, CrtFilter.VIGNETTE_EDGE_ALPHA, 0f)
        assertEquals(0.55f, CrtFilter.VIGNETTE_INNER_FRACTION, 0f)
        assertEquals(0.75f, CrtFilter.VIGNETTE_RADIUS_FRACTION, 0f)
    }

    @Test
    fun `phosphor tint is a warm white at 0_03 alpha`() {
        assertEquals(0xFFFFF3D6L, CrtFilter.PHOSPHOR_TINT_ARGB)
        assertEquals(0.03f, CrtFilter.PHOSPHOR_TINT_ALPHA, 0f)
    }

    @Test
    fun `spacing converts with density`() {
        // At density 2.625 (a ~400 dpi phone) a 3dp pitch is ~7.9px.
        assertEquals(7.875f, CrtFilter.scanlineSpacingPx(2.625f), 0.001f)
        assertEquals(2.625f, CrtFilter.scanlineThicknessPx(2.625f), 0.001f)
        assertEquals(7.875f, CrtFilter.grilleSpacingPx(2.625f), 0.001f)
    }

    @Test
    fun `scanline and grille counts cover the layer`() {
        // 2400px tall at density 2.625: 2400 / 7.875 = ~304 lines.
        assertEquals(304, CrtFilter.scanlineCount(2400f, 2.625f))
        // 1080px wide: 1080 / 7.875 = ~137 stripes.
        assertEquals(137, CrtFilter.grilleLineCount(1080f, 2.625f))
        // Degenerate sizes draw nothing, never a negative count.
        assertEquals(0, CrtFilter.scanlineCount(0f, 2.625f))
        assertEquals(0, CrtFilter.grilleLineCount(0f, 2.625f))
    }

    @Test
    fun `vignette is transparent inside the inner fraction and full at the corner`() {
        assertEquals(0f, CrtFilter.vignetteAlphaAt(0f), 0f)
        assertEquals(0f, CrtFilter.vignetteAlphaAt(0.55f), 0f)
        assertEquals(0.28f, CrtFilter.vignetteAlphaAt(1f), 0.001f)
        // Halfway through the ramp: half the edge alpha.
        assertEquals(0.14f, CrtFilter.vignetteAlphaAt(0.775f), 0.001f)
        // Past the corner clamps at the edge alpha.
        assertEquals(0.28f, CrtFilter.vignetteAlphaAt(1.5f), 0.001f)
    }

    @Test
    fun `vignette radius is 0_75 of the centre-to-corner distance`() {
        // 1080x2400 layer: corner distance is sqrt(540^2 + 1200^2) = ~1316.2.
        val corner = CrtFilter.cornerDistancePx(1080f, 2400f)
        assertEquals(1316.2f, corner, 0.5f)
        assertEquals(0.75f * corner, CrtFilter.vignetteRadiusPx(1080f, 2400f), 0.001f)
    }

    @Test
    fun `vignette alpha never decreases toward the corner`() {
        var previous = 0f
        var f = 0f
        while (f <= 1.001f) {
            val alpha = CrtFilter.vignetteAlphaAt(f)
            assertTrue("alpha fell at $f: $alpha < $previous", alpha >= previous)
            assertTrue(alpha <= CrtFilter.VIGNETTE_EDGE_ALPHA)
            previous = alpha
            f += 0.05f
        }
    }
}
