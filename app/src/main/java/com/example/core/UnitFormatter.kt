package com.example.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formats SI internal values into display units (US Customary or SI Metric).
 */
class UnitFormatter(private val useMetric: Boolean = false) {

    fun formatTemperature(tempC: Double?): String {
        if (tempC == null) return "--"
        return if (useMetric) {
            "${tempC.roundToInt()}°C"
        } else {
            val tempF = (tempC * 9.0 / 5.0) + 32.0
            "${tempF.roundToInt()}°"
        }
    }

    fun formatTemperatureValue(tempC: Double?): Int? {
        if (tempC == null) return null
        return if (useMetric) {
            tempC.roundToInt()
        } else {
            ((tempC * 9.0 / 5.0) + 32.0).roundToInt()
        }
    }

    fun formatWindSpeed(windMs: Double?): String {
        if (windMs == null) return "--"
        return if (useMetric) {
            val kmh = windMs * 3.6
            String.format(Locale.US, "%.1f km/h", kmh)
        } else {
            val mph = windMs * 2.23694
            String.format(Locale.US, "%.0f mph", mph)
        }
    }

    fun formatPressure(pressurePa: Double?): String {
        if (pressurePa == null) return "--"
        return if (useMetric) {
            val hPa = pressurePa / 100.0
            String.format(Locale.US, "%.0f hPa", hPa)
        } else {
            val inHg = pressurePa * 0.0002953
            String.format(Locale.US, "%.2f inHg", inHg)
        }
    }

    fun formatPrecipitation(precipMm: Double?): String {
        if (precipMm == null || precipMm <= 0.0) return if (useMetric) "0 mm" else "0 in"
        return if (useMetric) {
            String.format(Locale.US, "%.1f mm", precipMm)
        } else {
            val inches = precipMm * 0.0393701
            String.format(Locale.US, "%.2f in", inches)
        }
    }

    /**
     * Snowfall in cm (Open-Meteo's snowfall unit). Metric shows cm;
     * imperial shows inches. Null or zero is "0" — the daily row hides
     * the amount entirely on dry days, so this is only the fallback.
     */
    fun formatSnowfall(snowCm: Double?): String {
        if (snowCm == null || snowCm <= 0.0) return if (useMetric) "0 cm" else "0 in"
        return if (useMetric) {
            String.format(Locale.US, "%.1f cm", snowCm)
        } else {
            val inches = snowCm * 0.393701
            String.format(Locale.US, "%.2f in", inches)
        }
    }

    fun formatVisibility(visMeters: Double?): String {
        if (visMeters == null) return "--"
        return if (useMetric) {
            val km = visMeters / 1000.0
            String.format(Locale.US, "%.1f km", km)
        } else {
            val miles = visMeters / 1609.34
            String.format(Locale.US, "%.1f mi", miles)
        }
    }

    fun formatPrecipRate(mmPerHour: Double): String {
        return if (useMetric) {
            String.format(Locale.US, "%.1f mm/h", mmPerHour)
        } else {
            val inPerHour = mmPerHour * 0.0393701
            String.format(Locale.US, "%.2f in/h", inPerHour)
        }
    }
}
