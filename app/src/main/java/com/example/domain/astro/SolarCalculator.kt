package com.example.domain.astro

import com.example.domain.model.GeoPoint
import com.example.domain.model.SolarTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure Kotlin astronomical calculations based on the NOAA Solar Position Algorithm.
 * Solar events are calculated in UTC and can be formatted into any local ZoneId.
 */
object SolarCalculator {

    private const val DEG2RAD = Math.PI / 180.0
    private const val RAD2DEG = 180.0 / Math.PI

    /**
     * Calculates solar events for the given point, date and zone.
     */
    fun calculate(
        point: GeoPoint,
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.of("America/New_York")
    ): SolarTimes {
        val zonedDateTime = instant.atZone(zoneId)
        val date = zonedDateTime.toLocalDate()

        val todayTimes = calculateDay(point, date)
        val yesterdayTimes = calculateDay(point, date.minusDays(1))

        val dayLengthMinutes = (todayTimes.sunset.epochSecond - todayTimes.sunrise.epochSecond) / 60
        val yesterdayLengthMinutes = (yesterdayTimes.sunset.epochSecond - yesterdayTimes.sunrise.epochSecond) / 60
        val deltaMinutes = dayLengthMinutes - yesterdayLengthMinutes

        val position = calculatePosition(point, instant)
        val isDaytime = instant.isAfter(todayTimes.sunrise) && instant.isBefore(todayTimes.sunset)

        return SolarTimes(
            sunrise = todayTimes.sunrise,
            sunset = todayTimes.sunset,
            solarNoon = todayTimes.solarNoon,
            civilDawn = todayTimes.civilDawn,
            civilDusk = todayTimes.civilDusk,
            goldenHourMorningStart = todayTimes.goldenMorningStart,
            goldenHourMorningEnd = todayTimes.goldenMorningEnd,
            goldenHourEveningStart = todayTimes.goldenEveningStart,
            goldenHourEveningEnd = todayTimes.goldenEveningEnd,
            blueHourMorningStart = todayTimes.blueMorningStart,
            blueHourMorningEnd = todayTimes.blueMorningEnd,
            blueHourEveningStart = todayTimes.blueEveningStart,
            blueHourEveningEnd = todayTimes.blueEveningEnd,
            dayLengthMinutes = dayLengthMinutes,
            dayLengthDeltaYesterdayMinutes = deltaMinutes,
            currentSunElevation = position.first,
            currentSunAzimuth = position.second,
            isDaytime = isDaytime
        )
    }

    private data class DaySolarEvents(
        val sunrise: Instant,
        val sunset: Instant,
        val solarNoon: Instant,
        val civilDawn: Instant,
        val civilDusk: Instant,
        val goldenMorningStart: Instant,
        val goldenMorningEnd: Instant,
        val goldenEveningStart: Instant,
        val goldenEveningEnd: Instant,
        val blueMorningStart: Instant,
        val blueMorningEnd: Instant,
        val blueEveningStart: Instant,
        val blueEveningEnd: Instant
    )

    private fun calculateDay(point: GeoPoint, date: LocalDate): DaySolarEvents {
        // Julian date at 12:00:00 UTC of the target date
        val noonUtc = date.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant()
        val jd = toJulianDate(noonUtc)

        // Days since Jan 1, 2000, 12:00 UTC
        val n = jd - 2451545.0 + 0.0008
        // Mean solar noon approximation
        val jStar = n - (point.lon / 360.0)
        // Solar mean anomaly
        val m = (357.5291 + 0.98560028 * jStar) % 360.0
        val mRad = m * DEG2RAD

        // Equation of the center
        val c = 1.9148 * sin(mRad) + 0.0200 * sin(2.0 * mRad) + 0.0003 * sin(3.0 * mRad)
        // Ecliptic longitude
        val lambda = ((m + c + 180.0 + 102.9372) % 360.0) * DEG2RAD

        // Solar transit (Solar Noon in Julian Date)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2.0 * lambda)

        // Sun declination
        val sinDec = sin(lambda) * sin(23.44 * DEG2RAD)
        val cosDec = cos(asin(sinDec))
        val latRad = point.lat * DEG2RAD

        fun hourAngle(altitudeDeg: Double): Double {
            val altRad = altitudeDeg * DEG2RAD
            val cosH = (sin(altRad) - sin(latRad) * sinDec) / (cos(latRad) * cosDec)
            val clamped = cosH.coerceIn(-1.0, 1.0)
            return acos(clamped) * RAD2DEG
        }

        // Standard refraction altitude is -0.833°
        val h0 = hourAngle(-0.833)
        val jSet = jTransit + (h0 / 360.0)
        val jRise = jTransit - (h0 / 360.0)

        // Civil twilight (-6.0°)
        val hCivil = hourAngle(-6.0)
        val jCivilDusk = jTransit + (hCivil / 360.0)
        val jCivilDawn = jTransit - (hCivil / 360.0)

        // Golden hour: -4.0° to +6.0°
        val hGoldenLow = hourAngle(-4.0)
        val hGoldenHigh = hourAngle(6.0)
        val jGoldenMorningStart = jTransit - (hGoldenLow / 360.0)
        val jGoldenMorningEnd = jTransit - (hGoldenHigh / 360.0)
        val jGoldenEveningStart = jTransit + (hGoldenHigh / 360.0)
        val jGoldenEveningEnd = jTransit + (hGoldenLow / 360.0)

        // Blue hour: -8.0° to -4.0°
        val hBlueLow = hourAngle(-8.0)
        val jBlueMorningStart = jTransit - (hBlueLow / 360.0)
        val jBlueMorningEnd = jTransit - (hGoldenLow / 360.0)
        val jBlueEveningStart = jTransit + (hGoldenLow / 360.0)
        val jBlueEveningEnd = jTransit + (hBlueLow / 360.0)

        return DaySolarEvents(
            sunrise = fromJulianDate(jRise),
            sunset = fromJulianDate(jSet),
            solarNoon = fromJulianDate(jTransit),
            civilDawn = fromJulianDate(jCivilDawn),
            civilDusk = fromJulianDate(jCivilDusk),
            goldenMorningStart = fromJulianDate(jGoldenMorningStart),
            goldenMorningEnd = fromJulianDate(jGoldenMorningEnd),
            goldenEveningStart = fromJulianDate(jGoldenEveningStart),
            goldenEveningEnd = fromJulianDate(jGoldenEveningEnd),
            blueMorningStart = fromJulianDate(jBlueMorningStart),
            blueMorningEnd = fromJulianDate(jBlueMorningEnd),
            blueEveningStart = fromJulianDate(jBlueEveningStart),
            blueEveningEnd = fromJulianDate(jBlueEveningEnd)
        )
    }

    private fun calculatePosition(point: GeoPoint, instant: Instant): Pair<Double, Double> {
        val jd = toJulianDate(instant)
        val t = (jd - 2451545.0) / 36525.0

        val l0 = (280.46646 + 36000.76983 * t) % 360.0
        val m = (357.52911 + 35999.05029 * t) % 360.0
        val mRad = m * DEG2RAD

        val c = (1.914602 - 0.004817 * t) * sin(mRad) + (0.019993 - 0.000101 * t) * sin(2.0 * mRad)
        val sunTrueLon = (l0 + c) % 360.0

        val epsilon = (23.439291 - 0.0130042 * t) * DEG2RAD
        val sunRad = sunTrueLon * DEG2RAD
        val sinDec = sin(epsilon) * sin(sunRad)
        val decRad = asin(sinDec)

        val raRad = atan2(cos(epsilon) * sin(sunRad), cos(sunRad))

        // Greenwich Mean Sidereal Time
        val d = jd - 2451545.0
        val gmst = (280.46061837 + 360.98564736629 * d) % 360.0
        val lmst = (gmst + point.lon) % 360.0
        val lmstRad = lmst * DEG2RAD

        val hourAngle = lmstRad - raRad
        val latRad = point.lat * DEG2RAD

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(hourAngle)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
        val elevationDeg = altRad * RAD2DEG

        val cosAz = (sin(decRad) - sin(latRad) * sinAlt) / (cos(latRad) * cos(altRad))
        var azDeg = acos(cosAz.coerceIn(-1.0, 1.0)) * RAD2DEG
        if (sin(hourAngle) > 0) {
            azDeg = 360.0 - azDeg
        }

        return Pair(elevationDeg, azDeg)
    }

    private fun toJulianDate(instant: Instant): Double {
        return (instant.toEpochMilli() / 86400000.0) + 2440587.5
    }

    private fun fromJulianDate(jd: Double): Instant {
        val millis = ((jd - 2440587.5) * 86400000.0).toLong()
        return Instant.ofEpochMilli(millis)
    }
}
