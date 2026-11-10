package com.example

import com.example.data.work.ALREADY_RAINING_WINDOW_MINUTES
import com.example.data.work.RAIN_LOOKAHEAD_MINUTES
import com.example.data.work.RainStartDecision
import com.example.data.work.decideRainStart
import com.example.data.work.rainIntensityDescription
import com.example.domain.model.MinutePrecip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Pure tests for the Build C rain-starting decision. The minute-cast is
 * the 15-minute (Open-Meteo) interpolation — every assertion is on the
 * point's time, never its index.
 */
class RainStartTest {

    private val now = Instant.parse("2026-11-10T12:00:00Z")

    /** One point per minute for 60 minutes, wet from [wetFromMin] onwards. */
    private fun minutely(wetFromMin: Int?, wetMmPerHour: Double = 1.5): List<MinutePrecip> {
        return (0..60).map { m ->
            MinutePrecip(
                time = now.plus(Duration.ofMinutes(m.toLong())),
                mmPerHour = if (wetFromMin != null && m >= wetFromMin) wetMmPerHour else 0.0,
                probabilityPct = null
            )
        }
    }

    /** 15-minute points (Open-Meteo interpolation), wet from [wetFromMin]. */
    private fun fifteenMinutely(wetFromMin: Int): List<MinutePrecip> {
        return (0..60 step 15).map { m ->
            MinutePrecip(
                time = now.plus(Duration.ofMinutes(m.toLong())),
                mmPerHour = if (m >= wetFromMin) 1.5 else 0.0,
                probabilityPct = null
            )
        }
    }

    @Test
    fun noRain_anywhere_returnsNoRain() {
        assertEquals(
            RainStartDecision.NoRain,
            decideRainStart(minutely(null), now, 0L)
        )
    }

    @Test
    fun rainAlreadyFalling_returnsAlreadyRaining() {
        // Wet from minute 0: it is raining now.
        assertEquals(
            RainStartDecision.AlreadyRaining,
            decideRainStart(minutely(0), now, 0L)
        )
    }

    @Test
    fun rainWithinFiveMinutes_returnsAlreadyRaining() {
        // Wet from minute 3: inside the already-raining window.
        assertEquals(
            RainStartDecision.AlreadyRaining,
            decideRainStart(minutely(3), now, 0L)
        )
    }

    @Test
    fun rainStartingAt25Min_returnsNotify() {
        val decision = decideRainStart(minutely(25), now, 0L)
        assertTrue(decision is RainStartDecision.Notify)
        val notify = decision as RainStartDecision.Notify
        assertEquals(25, notify.minutesUntil)
        assertEquals("Light rain", notify.intensity)
        assertEquals(now.plus(Duration.ofMinutes(25)), notify.startTime)
    }

    @Test
    fun rainStarting_alreadyNotified_returnsAlreadyNotified() {
        // Same forecast, but the event was already notified (flag set).
        assertEquals(
            RainStartDecision.AlreadyNotified,
            decideRainStart(minutely(25), now, now.toEpochMilli())
        )
    }

    @Test
    fun rainStarting_noActiveFlag_notifiesAgainAfterEventClears() {
        // A check that finds no rain clears the flag (flag = 0); the next
        // rain event then notifies again.
        assertEquals(
            RainStartDecision.NoRain,
            decideRainStart(minutely(null), now, now.toEpochMilli())
        )
        val decision = decideRainStart(minutely(40), now, 0L)
        assertTrue(decision is RainStartDecision.Notify)
    }

    @Test
    fun rainAtExactly60Min_notifies() {
        val decision = decideRainStart(minutely(60), now, 0L)
        assertTrue(decision is RainStartDecision.Notify)
        assertEquals(60, (decision as RainStartDecision.Notify).minutesUntil)
    }

    @Test
    fun rainBeyond60Min_returnsNoRain() {
        // Wet only at minute 61 — outside the 60-minute lookahead.
        val points = (0..61).map { m ->
            MinutePrecip(
                time = now.plus(Duration.ofMinutes(m.toLong())),
                mmPerHour = if (m == 61) 1.5 else 0.0,
                probabilityPct = null
            )
        }
        assertEquals(RainStartDecision.NoRain, decideRainStart(points, now, 0L))
    }

    @Test
    fun fifteenMinutePoints_rainAt15Min_notifies() {
        // Open-Meteo 15-minute resolution: first point dry, +15 wet.
        val decision = decideRainStart(fifteenMinutely(15), now, 0L)
        assertTrue(decision is RainStartDecision.Notify)
        assertEquals(15, (decision as RainStartDecision.Notify).minutesUntil)
    }

    @Test
    fun fifteenMinutePoints_firstPointWet_isAlreadyRaining() {
        val decision = decideRainStart(fifteenMinutely(0), now, 0L)
        assertEquals(RainStartDecision.AlreadyRaining, decision)
    }

    @Test
    fun intensityBands_matchNowcastSentence() {
        assertEquals("Drizzle", rainIntensityDescription(0.2))
        assertEquals("Light rain", rainIntensityDescription(1.5))
        assertEquals("Moderate rain", rainIntensityDescription(5.0))
        assertEquals("Heavy rain", rainIntensityDescription(10.0))
    }

    @Test
    fun drizzleStarting_notifiesWithDrizzle() {
        val decision = decideRainStart(minutely(30, wetMmPerHour = 0.3), now, 0L)
        assertTrue(decision is RainStartDecision.Notify)
        assertEquals("Drizzle", (decision as RainStartDecision.Notify).intensity)
    }

    @Test
    fun constants_areAsDocumented() {
        assertEquals(5L, ALREADY_RAINING_WINDOW_MINUTES)
        assertEquals(60L, RAIN_LOOKAHEAD_MINUTES)
    }
}
