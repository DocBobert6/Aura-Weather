package com.example.music

import kotlin.random.Random

/**
 * Pure next-track picker for the background music. No Android imports,
 * so it is covered by the pure kotlinc tests (run-pure-tests.sh).
 *
 * The four bundled tracks play in random order, never the same track
 * twice in a row — the same rule the ws4kp emulator uses for its
 * WeatherStar-inspired music.
 */

/**
 * Picks the next track index. [currentIndex] is the track playing now,
 * or -1 when nothing is playing yet. Never returns [currentIndex] when
 * there is more than one track; with a single track there is nothing
 * else to pick, so it returns 0.
 */
fun nextTrackIndex(currentIndex: Int, trackCount: Int, random: Random): Int {
    if (trackCount <= 1) return 0
    if (currentIndex !in 0 until trackCount) return random.nextInt(trackCount)
    val pick = random.nextInt(trackCount - 1)
    return if (pick >= currentIndex) pick + 1 else pick
}
