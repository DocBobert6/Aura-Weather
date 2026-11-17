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
    fun `satellite is always off, playing or not`() {
        assertEquals(RadarBlipDisplay.OFF, radarBlipDisplay(false, true, 0))
        assertEquals(RadarBlipDisplay.OFF, radarBlipDisplay(false, true, 1))
        assertEquals(RadarBlipDisplay.OFF, radarBlipDisplay(false, false, 0))
    }

    @Test
    fun `paused or loading radar holds the dot steady visible`() {
        // Not playing: the beat has stopped, so the dot holds — on
        // either parity, never a hidden dot while the user looks at
        // a still radar.
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, false, 0))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, false, 1))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, false, 9))
    }

    @Test
    fun `playing radar blinks with the playhead parity`() {
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, true, 0))
        assertEquals(RadarBlipDisplay.HIDDEN, radarBlipDisplay(true, true, 1))
        assertEquals(RadarBlipDisplay.VISIBLE, radarBlipDisplay(true, true, 8))
        assertEquals(RadarBlipDisplay.HIDDEN, radarBlipDisplay(true, true, 9))
    }
}
