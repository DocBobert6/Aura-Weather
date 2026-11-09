package com.example

import com.example.music.nextTrackIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pure tests for the background-music track picker (no Android imports).
 * The MediaPlayer itself is not pure-testable — only the pick logic is.
 */
class MusicPickerTest {

    @Test
    fun testNextTrackNeverRepeatsCurrent() {
        val random = Random(42)
        repeat(1000) { i ->
            val current = i % 4
            val next = nextTrackIndex(current, 4, random)
            assertNotEquals("pick $i repeated track $current", current, next)
            assertTrue("pick $i out of range: $next", next in 0 until 4)
        }
    }

    @Test
    fun testNextTrackCoversAllTracks() {
        val random = Random(7)
        val seen = mutableSetOf<Int>()
        var current = 0
        repeat(200) {
            current = nextTrackIndex(current, 4, random)
            seen.add(current)
        }
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun testFirstPickHasNoCurrentTrack() {
        val random = Random(123)
        val seen = mutableSetOf<Int>()
        repeat(200) {
            seen.add(nextTrackIndex(-1, 4, random))
        }
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun testSingleTrackAlwaysReturnsZero() {
        val random = Random(99)
        repeat(10) {
            assertEquals(0, nextTrackIndex(0, 1, random))
            assertEquals(0, nextTrackIndex(-1, 1, random))
        }
    }
}
