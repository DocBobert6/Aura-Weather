package com.example

import com.example.ui.radar.engine.RadarBlipDisplay
import com.example.ui.radar.engine.radarBlipDisplay
import com.example.ui.radar.engine.radarBlipVisibleForFrameIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radar blip (0.4.0): the blink phase and display rule. Pure Kotlin
 * — runs in the local kotlinc + JUnit pass.
 */
class RadarBlipTest {

    // -- Blink phase -----------------------------------------------------

    @Test
    fun `blink flips on every frame step, even visible odd hidden`() {
        assertTrue(radarBlipVisibleForFrameIndex(0))
        assertFalse(radarBlipVisibleForFrameIndex(1))
        assertTrue(radarBlipVisibleForFrameIndex(2))
        assertFalse(radarBlipVisibleForFrameIndex(3))
        assertTrue(radarBlipVisibleForFrameIndex(8))
        assertFalse(radarBlipVisibleForFrameIndex(9))
    }

    // -- Display rule ------------------------------------------------------

    @Test
    fun `satellite layers show the blip like radar`() {
        // Robert, 2026-11-17: the blip shows on satellite too.
        // The rule takes no layer input at all — satellite
        // follows the same steady/blink rule as radar: steady
        // VISIBLE while the loop is not playing, blinking with
        // the playhead parity while it plays.
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(false, 0))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(false, 1))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, 0))
        assertEquals(RadarBlipDisplay.HIDDEN, radarBlipDisplay(true, 1))
    }

    @Test
    fun `paused or loading radar holds the dot steady visible`() {
        // Not playing: the beat has stopped, so the dot holds — on
        // either parity, never a hidden dot while the user looks at
        // a still radar.
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(false, 0))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(false, 1))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(false, 9))
    }

    @Test
    fun `playing radar blinks with the playhead parity`() {
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, 0))
        assertEquals(RadarBlipDisplay.HIDDEN, radarBlipDisplay(true, 1))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, 8))
        assertEquals(RadarBlipDisplay.HIDDEN, radarBlipDisplay(true, 9))
    }
}
