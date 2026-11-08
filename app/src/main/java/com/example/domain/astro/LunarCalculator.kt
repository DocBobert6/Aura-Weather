package com.example.domain.astro

import com.example.domain.model.LunarTimes
import java.time.Instant
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Pure Kotlin astronomical algorithm for lunar phase and illumination.
 */
object LunarCalculator {

    // Known reference new moon: January 11, 2024 at 11:57 UTC (Epoch ms: 1704974220000)
    private const val REFERENCE_NEW_MOON_EPOCH_MS = 1704974220000L
    private const val SYNODIC_MONTH_MS = 29.53058867 * 86400000.0

    fun calculate(instant: Instant = Instant.now()): LunarTimes {
        val deltaMs = instant.toEpochMilli() - REFERENCE_NEW_MOON_EPOCH_MS
        val cycles = deltaMs / SYNODIC_MONTH_MS
        val currentCycleFraction = cycles - cycles.toInt()
        val normalizedFraction = if (currentCycleFraction < 0) currentCycleFraction + 1.0 else currentCycleFraction

        val ageDays = normalizedFraction * 29.53058867
        val phaseAngleRad = normalizedFraction * 2.0 * Math.PI

        // Illuminated fraction of the disk: (1 - cos(phaseAngle)) / 2
        val illuminatedFraction = (1.0 - cos(phaseAngleRad)) / 2.0
        val isWaxing = normalizedFraction < 0.5

        val phaseName = when {
            ageDays < 1.0 || ageDays >= 28.5 -> "New Moon"
            ageDays < 6.5 -> "Waxing Crescent"
            ageDays < 8.5 -> "First Quarter"
            ageDays < 13.5 -> "Waxing Gibbous"
            ageDays < 15.5 -> "Full Moon"
            ageDays < 21.5 -> "Waning Gibbous"
            ageDays < 23.5 -> "Last Quarter"
            else -> "Waning Crescent"
        }

        // Days to next new moon and full moon
        val nextNewDays = ((1.0 - normalizedFraction) * 29.53058867).roundToInt().coerceAtLeast(0)
        val fullMoonFraction = 0.5
        val diffToFull = if (normalizedFraction <= fullMoonFraction) {
            fullMoonFraction - normalizedFraction
        } else {
            1.0 + fullMoonFraction - normalizedFraction
        }
        val nextFullDays = (diffToFull * 29.53058867).roundToInt().coerceAtLeast(0)

        return LunarTimes(
            phaseName = phaseName,
            illuminatedFraction = (illuminatedFraction * 100.0).roundToInt() / 100.0,
            phaseAgeDays = (ageDays * 10.0).roundToInt() / 10.0,
            nextNewMoonDays = nextNewDays,
            nextFullMoonDays = nextFullDays,
            isWaxing = isWaxing
        )
    }
}
