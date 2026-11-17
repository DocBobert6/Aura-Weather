package com.example

import com.example.data.mapper.UvCardState
import com.example.data.mapper.uvCardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvDisplayTest {

    @Test
    fun nightWinsOverPresentMax() {
        // The 11:53 PM bug: today's max was 3, the card showed
        // "3 MODERATE" instead of NIGHT.
        assertEquals(UvCardState.Night, uvCardState(3.0, isNight = true))
        assertEquals(UvCardState.Night, uvCardState(11.0, isNight = true))
    }

    @Test
    fun nightWithNoMaxIsStillNight() {
        assertEquals(UvCardState.Night, uvCardState(null, isNight = true))
    }

    @Test
    fun daylightShowsTheMax() {
        val state = uvCardState(3.0, isNight = false)
        assertTrue(state is UvCardState.Daytime)
        assertEquals(3.0, (state as UvCardState.Daytime).uvIndexMax, 0.0)
    }

    @Test
    fun daylightWithNoMaxIsUnknown() {
        assertEquals(UvCardState.Unknown, uvCardState(null, isNight = false))
    }

    @Test
    fun zeroMaxInDaylightIsStillDaytime() {
        // A real 0.0 max is a value, not missing data.
        val state = uvCardState(0.0, isNight = false)
        assertTrue(state is UvCardState.Daytime)
        assertEquals(0.0, (state as UvCardState.Daytime).uvIndexMax, 0.0)
    }
}
