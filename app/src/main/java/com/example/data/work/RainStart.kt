package com.example.data.work

import com.example.domain.model.MinutePrecip
import java.time.Duration
import java.time.Instant

/**
 * The pure rain-starting decision for Build C (audit item 13). No Android,
 * no Room, no WorkManager — plain JUnit tests cover it (RainStartTest),
 * and the worker calls it through [AlertCheckWorker].
 *
 * The minute-cast is the same data the Home "Next 60 minutes" card shows:
 * the Open-Meteo 15-minute interpolation, or Pirate Weather 1-minute
 * data when that provider is available. The resolution does not matter
 * here — every comparison is on the point's *time*, never its index.
 *
 * Rain means mmPerHour above [RAIN_THRESHOLD_MM_PER_H] — the same 0.1
 * threshold the nowcast sentence uses, so the alert and the card never
 * disagree about whether it is raining.
 *
 * - Rain already falling (a wet point within the next
 *   [ALREADY_RAINING_WINDOW_MINUTES] minutes) → already raining: no
 *   alert. The rain is here; there is nothing to warn about.
 * - First wet point after that window but within
 *   [RAIN_LOOKAHEAD_MINUTES] minutes → rain starting: notify, unless
 *   this event was already notified (see below).
 * - No wet point in the next 60 minutes → no rain: the event, if any,
 *   is over.
 *
 * Dedupe (once per rain event): the worker persists the epoch millis of
 * the last rain notification as `rainAlertActiveSince` (0 = no event
 * in progress). A rain-starting forecast while that flag is set is the
 * SAME event — the start time drifts as the forecast updates, so the
 * flag, not the start time, is the dedupe key. The flag is cleared only
 * when a check finds no rain in the next 60 minutes (the event has
 * passed), so the next rain event can notify again. Turning the toggle
 * off does not clear the flag; the next check after re-enabling sees
 * the same event and stays silent, which is the correct behaviour.
 */
const val RAIN_THRESHOLD_MM_PER_H = 0.1
const val ALREADY_RAINING_WINDOW_MINUTES = 5L
const val RAIN_LOOKAHEAD_MINUTES = 60L

/** Intensity words, the same bands the nowcast sentence uses. */
fun rainIntensityDescription(maxMmPerHour: Double): String = when {
    maxMmPerHour < 0.5 -> "Drizzle"
    maxMmPerHour < 2.5 -> "Light rain"
    maxMmPerHour < 7.6 -> "Moderate rain"
    else -> "Heavy rain"
}

sealed interface RainStartDecision {
    /** No rain in the next 60 minutes — clear the active-event flag. */
    data object NoRain : RainStartDecision

    /** Rain is already falling (or about to, within 5 minutes). */
    data object AlreadyRaining : RainStartDecision

    /** Rain is starting, but this event was already notified. */
    data object AlreadyNotified : RainStartDecision

    /** Rain is starting and this event has not been notified yet. */
    data class Notify(
        val startTime: Instant,
        val minutesUntil: Int,
        val intensity: String
    ) : RainStartDecision
}

fun decideRainStart(
    minutely: List<MinutePrecip>,
    now: Instant,
    activeSinceEpochMs: Long
): RainStartDecision {
    val horizon = now.plus(Duration.ofMinutes(RAIN_LOOKAHEAD_MINUTES))
    val wet = minutely.filter { it.mmPerHour > RAIN_THRESHOLD_MM_PER_H }
        .filter { !it.time.isBefore(now) && !it.time.isAfter(horizon) }
        .sortedBy { it.time }
    if (wet.isEmpty()) return RainStartDecision.NoRain

    val first = wet.first()
    val alreadyRainingUntil = now.plus(Duration.ofMinutes(ALREADY_RAINING_WINDOW_MINUTES))
    if (!first.time.isAfter(alreadyRainingUntil)) {
        return RainStartDecision.AlreadyRaining
    }

    if (activeSinceEpochMs != 0L) return RainStartDecision.AlreadyNotified

    val minutesUntil = Duration.between(now, first.time).toMinutes().toInt()
    val intensity = rainIntensityDescription(wet.maxOf { it.mmPerHour })
    return RainStartDecision.Notify(
        startTime = first.time,
        minutesUntil = minutesUntil,
        intensity = intensity
    )
}
