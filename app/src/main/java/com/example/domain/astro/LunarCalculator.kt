package com.example.domain.astro

import com.example.domain.model.LunarTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

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

    // --- Moonrise / moonset (low-precision lunar position, Meeus) ---

    /** Moon's ecliptic longitude / latitude (degrees) at a Julian date. */
    private fun moonEcliptic(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val meanLongitude = (218.316 + 13.176396 * d).mod(360.0)
        val meanAnomaly = (134.963 + 13.064993 * d).mod(360.0)
        val meanDistance = (93.272 + 13.229350 * d).mod(360.0)
        val longitude = meanLongitude + 6.289 * sin(Math.toRadians(meanAnomaly))
        val latitude = 5.128 * sin(Math.toRadians(meanDistance))
        return longitude to latitude
    }

    /** Moon's right ascension / declination (degrees) at a Julian date. */
    private fun moonRaDec(jd: Double): Pair<Double, Double> {
        val (lambda, beta) = moonEcliptic(jd)
        val eps = Math.toRadians(23.4393)
        val lam = Math.toRadians(lambda)
        val bet = Math.toRadians(beta)
        val ra = Math.toDegrees(
            atan2(sin(lam) * cos(eps) - tan(bet) * sin(eps), cos(lam))
        ).mod(360.0)
        val dec = Math.toDegrees(
            asin(sin(bet) * cos(eps) + cos(bet) * sin(eps) * sin(lam))
        )
        return ra to dec
    }

    /** Greenwich Mean Sidereal Time (hours) at 0h UT on the given Julian date. */
    private fun gmstAt0h(jd0: Double): Double {
        val d = jd0 - 2451545.0
        return (18.697374558 + 24.06570982441908 * d).mod(24.0)
    }

    /**
     * Moonrise and moonset for a local date. Returns (rise, set); either is
     * null when the moon does not rise/set that day. Low-precision position,
     * so the times are rounded to the nearest 5 minutes — accurate to roughly
     * ±10 min at mid-latitudes.
     */
    fun calculateRiseSet(
        date: LocalDate,
        zoneId: ZoneId,
        latitude: Double,
        longitude: Double
    ): Pair<Instant?, Instant?> {
        // Reference: local noon — the transit nearest to it is today's.
        val noon = date.atTime(12, 0).atZone(zoneId).toInstant()
        val jdNoon = noon.toEpochMilli() / 86400000.0 + 2440587.5
        // JD at 0h UT of the UTC day containing local noon.
        val jd0 = floor(jdNoon - 0.5) + 0.5
        val jd0EpochMs = ((jd0 - 2440587.5) * 86400000.0).toLong()
        val gmst0 = gmstAt0h(jd0)
        val lonHours = longitude / 15.0
        val noonHoursFromJd0 = (noon.toEpochMilli() - jd0EpochMs) / 3600000.0

        // Transit (hours from 0h UT), iterated so the moon's motion during
        // the day is accounted for.
        var transitHours = (moonRaDec(jd0).first / 15.0 - gmst0 - lonHours).mod(24.0)
        repeat(2) {
            val jdTransit = jd0 + transitHours / 24.0
            transitHours = (moonRaDec(jdTransit).first / 15.0 - gmst0 - lonHours).mod(24.0)
        }
        // The transit nearest to local noon is today's (the mod-24 value can
        // land on the neighbouring day).
        var transit = transitHours
        while (transit - noonHoursFromJd0 > 12.0) transit -= 24.0
        while (noonHoursFromJd0 - transit > 12.0) transit += 24.0

        // Hour angle at rise/set, from the declination at transit.
        val decDeg = moonRaDec(jd0 + transit / 24.0).second
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(decDeg)
        // Upper limb: the moon's centre is 0.125° below the horizon at
        // rise/set (parallax + refraction).
        val h0 = Math.toRadians(0.125)
        val cosH = (sin(h0) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosH > 1.0 || cosH < -1.0) return null to null
        val halfDayHours = Math.toDegrees(acos(cosH)) / 15.0

        fun toInstant(hours: Double): Instant {
            val ms = jd0EpochMs + (hours * 3600000.0).roundToLong()
            val fiveMin = 5 * 60 * 1000L
            val rounded = ((ms + fiveMin / 2) / fiveMin) * fiveMin
            return Instant.ofEpochMilli(rounded)
        }
        return toInstant(transit - halfDayHours) to toInstant(transit + halfDayHours)
    }
}
