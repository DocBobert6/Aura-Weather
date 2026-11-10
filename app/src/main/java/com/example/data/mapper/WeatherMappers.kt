package com.example.data.mapper

import com.example.domain.model.AirQuality
import com.example.domain.model.PollenData
import com.example.domain.model.AlertSeverity
import com.example.domain.model.GeoPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import com.example.domain.astro.SolarCalculator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Open-Meteo is requested with timezone=UTC, so every timestamp it returns
 * ("2026-11-07T19:00") is a UTC wall time. Parse it as UTC — never append
 * "Z" to a local time and never interpret it in a hardcoded zone.
 */
fun parseOpenMeteoUtcInstant(timeStr: String): Instant? =
    runCatching { LocalDateTime.parse(timeStr).toInstant(ZoneOffset.UTC) }.getOrNull()

/** Day/night for one exact instant at a place (solar position, place zone). */
fun isDaytimeAt(point: GeoPoint, instant: Instant, zoneId: ZoneId): Boolean =
    SolarCalculator.calculate(point, instant, zoneId).isDaytime

/** Great-circle distance in metres (haversine). Used to dedupe places: a
 * "new" place within 500 m of a saved one is the same place. */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * earthRadiusM * Math.asin(Math.sqrt(h.coerceIn(0.0, 1.0)))
}

/**
 * Great-circle destination point (R5): the point [distanceMeters] away
 * from [from] along [bearingDeg] (0 = north, clockwise). Used to place
 * the regional map's neighbour points at fixed compass bearings.
 */
fun destinationPoint(from: GeoPoint, bearingDeg: Double, distanceMeters: Double): GeoPoint {
    val earthRadiusM = 6_371_000.0
    val angular = distanceMeters / earthRadiusM
    val bearing = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(from.lat)
    val lon1 = Math.toRadians(from.lon)
    val lat2 = Math.asin(
        Math.sin(lat1) * Math.cos(angular) +
            Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing)
    )
    val lon2 = lon1 + Math.atan2(
        Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
        Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2)
    )
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/** 8-point compass label for a bearing in degrees (0 = N). R5 fallback
 * label for a neighbour point whose city name is unavailable. */
fun regionalCompassLabel(bearingDeg: Double): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((bearingDeg % 360.0 + 360.0) % 360.0 / 45.0).roundToInt() % 8
    return points[idx]
}

/** Short display label for a regional-map point (audit fix C): drop the
 * ", ST" suffix and, for hyphenated city names, keep the first part, so
 * "LEXINGTON-FAYETTE, KY" -> "LEXINGTON" and "SEVEN MILE, OH" ->
 * "SEVEN MILE". The full "CITY, ST" label is unchanged everywhere else;
 * this only shortens the map overlay so it cannot collide with the
 * map's own place names. Compass fallback labels pass through
 * unchanged. */
fun shortMapLabel(label: String): String {
    val city = label.substringBefore(",").trim()
    val first = city.substringBefore("-").trim()
    return first.ifEmpty { city }
}

/** GIBS satellite timestamp (audit fix E, backoff added in the
 * post-Phase-3 batch): the full UTC time of a GOES scan GIBS has
 * actually published, as ISO-8601 ("2026-11-09T11:40:00Z").
 * GIBS resolves a date-only time to 00:00 UTC (7 PM EST the prior
 * evening), so a date-only value shows a nighttime city-lights image
 * at noon. Rounding down to the latest 10-minute boundary is not
 * enough on its own: GIBS publishes GOES scans behind real time
 * (measured 2026-11-09, GeoColor lags ~41-51 min and Band13
 * ~21-31 min), so the latest boundary 404s. Back off 60 minutes
 * first, then round down, so the timestamp lands on a scan GIBS
 * has actually published. A ~1-hour-old image beats a blank map. */
fun gibsTimestamp(now: java.time.Instant): String {
    val backedOff = now.epochSecond - 3600
    val rounded = backedOff - (backedOff % 600)
    return java.time.Instant.ofEpochSecond(rounded).toString()
}

/**
 * Stable id for an NWS alert that arrives without one (3.1 prerequisite).
 * The old fallback minted "alert_${System.currentTimeMillis()}", so two
 * alerts in the same millisecond shared an id and a refetch minted a new
 * one — dedupe-by-id could never work. This is a SHA-256 of the alert's
 * own fields (event + area + effective time), so the same alert always
 * produces the same id across refetches, and two different alerts
 * produce different ones. It is a derived identifier, not invented data:
 * every input is a real field from the feed.
 */
fun stableAlertId(event: String?, areaDesc: String?, effective: Instant): String {
    val input = "${event ?: ""}|${areaDesc ?: ""}|$effective"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return "alert_$hex"
}

/** One AirNow current-observation row, stripped to what the AQI mapping
 * needs (pollutant + AQI). The Retrofit model (AirNowObservation,
 * Moshi-annotated) lives in data.api and cannot be compiled into the
 * pure tests (no Moshi jar on that classpath), so the repository
 * converts to this plain type first. Keeping the mapping pure and
 * Moshi-free is what lets the unit tests cover it. */
data class AirNowReading(val parameterName: String?, val aqi: Int?)

/** One Tomorrow.io Timelines "1m" interval, stripped to what the
 * minute-cast mapping needs (time + intensity + probability). The
 * Retrofit model (TomorrowInterval, Moshi-annotated) lives in data.api
 * and cannot be compiled into the pure tests (no Moshi jar on that
 * classpath), so the repository converts to this plain type first. */
data class TomorrowMinutePoint(
    val time: Instant,
    val mmPerHour: Double?,
    val probabilityPct: Int?
)

object WeatherMappers {

    fun mapWmoCodeToCondition(code: Int?, isDaytime: Boolean = true): WeatherCondition {
        if (code == null) return if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        return when (code) {
            0 -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
            1, 2 -> if (isDaytime) WeatherCondition.PartlyCloudyDay else WeatherCondition.PartlyCloudyNight
            3 -> WeatherCondition.Cloudy
            45, 48 -> WeatherCondition.Fog
            51, 53, 55 -> WeatherCondition.Drizzle
            56, 57 -> WeatherCondition.FreezingRain
            61, 63 -> WeatherCondition.Rain
            65 -> WeatherCondition.HeavyRain
            66, 67 -> WeatherCondition.FreezingRain
            71, 73, 75, 77 -> WeatherCondition.Snow
            80, 81 -> WeatherCondition.Rain
            82 -> WeatherCondition.HeavyRain
            85, 86 -> WeatherCondition.Snow
            95, 96, 99 -> WeatherCondition.Thunderstorm
            else -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        }
    }

    fun mapNwsTextToCondition(text: String?, isDaytime: Boolean = true): WeatherCondition {
        val lower = text?.lowercase(Locale.ROOT) ?: ""
        return when {
            lower.contains("thunder") || lower.contains("t-storm") -> WeatherCondition.Thunderstorm
            lower.contains("heavy rain") || lower.contains("downpour") -> WeatherCondition.HeavyRain
            lower.contains("rain") || lower.contains("showers") -> WeatherCondition.Rain
            lower.contains("drizzle") || lower.contains("sprinkles") -> WeatherCondition.Drizzle
            lower.contains("snow") || lower.contains("flurries") || lower.contains("blizzard") -> WeatherCondition.Snow
            lower.contains("sleet") || lower.contains("ice pellets") -> WeatherCondition.Sleet
            lower.contains("freezing rain") -> WeatherCondition.FreezingRain
            lower.contains("fog") || lower.contains("mist") -> WeatherCondition.Fog
            lower.contains("haze") || lower.contains("smoke") -> WeatherCondition.Haze
            lower.contains("wind") || lower.contains("breezy") -> WeatherCondition.Windy
            lower.contains("overcast") || lower.contains("cloudy") -> WeatherCondition.Cloudy
            lower.contains("partly") -> if (isDaytime) WeatherCondition.PartlyCloudyDay else WeatherCondition.PartlyCloudyNight
            lower.contains("sunny") || lower.contains("clear") || lower.contains("fair") ->
                if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
            else -> if (isDaytime) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        }
    }

    /**
     * Builds the 60-minute precipitation curve starting at [now].
     *
     * The 15-minute series starts at midnight, so its first values are the
     * 12–1 AM rates — taking them blindly made "rain in 15 min" describe the
     * middle of the night. This anchors on the timestamps: it finds the
     * 15-min interval that contains [now] and interpolates forward from there.
     *
     * Units: Open-Meteo `precipitation` in the `minutely_15` block is **mm
     * per 15 minutes**, not mm/h. Each rate is multiplied by 4 before it is
     * stored as `mmPerHour` (the old code stored the raw value, 4x too low).
     *
     * If the timestamps are missing, mismatched, or [now] is past the end of
     * the series, there is no honest curve to draw: the caller gets an empty
     * list and a sentence that says so, instead of a midnight curve.
     */
    fun interpolate15MinTo60Min(
        times: List<String>?,
        precip15: List<Double>?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<List<MinutePrecip>, String> {
        if (precip15.isNullOrEmpty()) {
            return Pair(emptyList(), "Precipitation data is unavailable for the next hour.")
        }

        val parsedTimes = times?.mapNotNull { parseOpenMeteoUtcInstant(it) }
        if (parsedTimes.isNullOrEmpty() || parsedTimes.size != precip15.size) {
            return Pair(emptyList(), "Precipitation data is unavailable for the next hour.")
        }

        // Last interval that starts at or before now; if now is before the
        // first timestamp, anchor on the first interval rather than inventing data.
        val anchorIndex = parsedTimes.indexOfLast { !it.isAfter(now) }.coerceAtLeast(0)
        val anchorTime = parsedTimes[anchorIndex]
        if (anchorTime.plusSeconds(15 * 60).isBefore(now)) {
            return Pair(emptyList(), "Precipitation data is unavailable for the next hour.")
        }

        // Rates in mm/h (mm per 15 min x 4), carrying the last known rate
        // forward when the series ends mid-hour instead of dropping to zero.
        val rawRates = mutableListOf<Double>()
        var lastKnown = precip15.getOrNull(anchorIndex) ?: 0.0
        for (k in 0..4) {
            val v = precip15.getOrNull(anchorIndex + k)
            if (v != null) lastKnown = v
            rawRates.add(lastKnown)
        }
        val ratesMmPerHour = rawRates.map { it * 4.0 }

        // `now` sits part-way through the anchor interval (up to 15 min in),
        // so each curve minute is offset by that amount within the intervals.
        val offsetMinutes = java.time.Duration.between(anchorTime, now).toMinutes().toDouble()

        val minutePoints = mutableListOf<MinutePrecip>()
        val startSec = now.epochSecond

        for (m in 0 until 60) {
            val pos = m + offsetMinutes
            val intervalIndex = (pos / 15.0).toInt()
            val progress = (pos % 15.0) / 15.0
            val p0 = ratesMmPerHour.getOrElse(intervalIndex) { ratesMmPerHour.last() }
            val p1 = ratesMmPerHour.getOrElse(intervalIndex + 1) { p0 }
            val mmPerHour = lerp(p0, p1, progress)
            // Rough likelihood band from the rate — an estimate, not a measured
            // probability, and only defined when there is actually rain.
            val prob = if (mmPerHour > 0.05) (mmPerHour * 40.0).roundToInt().coerceIn(20, 100) else 0
            minutePoints.add(MinutePrecip(Instant.ofEpochSecond(startSec + m * 60), mmPerHour, prob))
        }

        val sentence = generateNowcastSentence(minutePoints, now, zoneId)
        return Pair(minutePoints, sentence)
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction.coerceIn(0.0, 1.0)
    }

    private fun generateNowcastSentence(points: List<MinutePrecip>, now: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val anyPrecip = points.any { it.mmPerHour > 0.1 }
        if (!anyPrecip) {
            return "No precipitation expected for the next 60 minutes."
        }

        val firstPrecipIndex = points.indexOfFirst { it.mmPerHour > 0.1 }
        val maxIntensity = points.maxOf { it.mmPerHour }
        val intensityDesc = when {
            maxIntensity < 0.5 -> "Drizzle"
            maxIntensity < 2.5 -> "Light rain"
            maxIntensity < 7.6 -> "Moderate rain"
            else -> "Heavy rain"
        }

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zoneId)

        return if (firstPrecipIndex <= 2) {
            val stopIndex = points.drop(firstPrecipIndex).indexOfFirst { it.mmPerHour <= 0.1 }
            if (stopIndex != -1) {
                val stopTime = points[firstPrecipIndex + stopIndex].time
                "$intensityDesc ending around ${timeFormatter.format(stopTime)}."
            } else {
                "$intensityDesc continuing for the next 60 minutes."
            }
        } else {
            val startTime = points[firstPrecipIndex].time
            "$intensityDesc starting in $firstPrecipIndex min (~${timeFormatter.format(startTime)})."
        }
    }

    /**
     * Returns null when the provider returned no AQI at all. A missing AQI is
     * "unavailable", not a made-up 28 with made-up PM2.5/PM10/ozone defaults.
     */
    /** EPA AQI bands (category + health guidance). Shared by the
     * Open-Meteo and AirNow paths so the two never disagree about what
     * an AQI number means. */
    fun aqiCategoryAndGuidance(aqi: Int): Pair<String, String> = when {
        aqi <= 50 -> "Good" to "Air quality is satisfactory and poses little or no health risk."
        aqi <= 100 -> "Moderate" to "Air quality is acceptable. Unusually sensitive individuals should limit prolonged outdoor exertion."
        aqi <= 150 -> "Unhealthy for Sensitive Groups" to "Active children, older adults, and individuals with respiratory issues should reduce outdoor exertion."
        aqi <= 200 -> "Unhealthy" to "Everyone may begin to experience health effects. Limit prolonged outdoor exertion."
        aqi <= 300 -> "Very Unhealthy" to "Health alert: The risk of health effects is increased for everyone. Avoid strenuous outdoor activities."
        else -> "Hazardous" to "Emergency warning: Entire population is likely to be affected. Remain indoors."
    }

    fun mapAqi(usAqi: Int?, pm25: Double?, pm10: Double?, ozone: Double?): AirQuality? {
        val aqi = usAqi ?: return null
        val (category, guidance) = aqiCategoryAndGuidance(aqi)

        // Open-Meteo reports ozone in µg/m³; the UI labels it ppb, so convert
        // (µg/m³ × 24.45 / 48.00 ≈ ×0.509). The 54 ppb cutoff is the EPA
        // "Good" boundary in ppb — it must be applied to the converted value,
        // not the raw µg/m³.
        val ozonePpb = ozone?.let { it * 24.45 / 48.0 }

        val dominant = when {
            pm25 != null && pm25 > 15.0 -> "PM2.5"
            ozonePpb != null && ozonePpb > 54.0 -> "Ozone (O₃)"
            pm10 != null && pm10 > 40.0 -> "PM10"
            pm25 != null -> "PM2.5"
            ozonePpb != null -> "Ozone (O₃)"
            pm10 != null -> "PM10"
            else -> null
        }

        return AirQuality(
            aqi = aqi,
            category = category,
            pm25 = pm25,
            pm10 = pm10,
            ozonePpb = ozonePpb,
            dominant = dominant,
            source = "Open-Meteo Air Quality",
            healthGuidance = guidance
        )
    }

    /**
     * AirNow AQI (post-Phase-3 batch, item 2): when an AirNow key is set,
     * the headline AQI comes from the nearest EPA monitoring station
     * instead of the Open-Meteo model. AirNow reports AQI + category per
     * pollutant but no concentrations, so the pollutant concentrations
     * (PM2.5 / PM10 / ozone) stay Open-Meteo's — only the headline AQI,
     * its category, and the dominant pollutant are replaced, and the
     * source becomes "AirNow". Returns the Open-Meteo value unchanged
     * when AirNow returned no usable observation (empty list, or outside
     * US coverage), and null only when neither source has an AQI.
     */
    fun mapAirNowAqi(readings: List<AirNowReading>, openMeteoAqi: AirQuality?): AirQuality? {
        val withAqi = readings.filter { it.aqi != null }
        if (withAqi.isEmpty()) return openMeteoAqi
        val worst = withAqi.maxByOrNull { it.aqi!! }!!
        val aqi = worst.aqi!!
        val (category, guidance) = aqiCategoryAndGuidance(aqi)
        val dominant = when (worst.parameterName) {
            "PM2.5" -> "PM2.5"
            "PM10" -> "PM10"
            "O3" -> "Ozone (O₃)"
            else -> openMeteoAqi?.dominant
        }
        return AirQuality(
            aqi = aqi,
            category = category,
            pm25 = openMeteoAqi?.pm25,
            pm10 = openMeteoAqi?.pm10,
            ozonePpb = openMeteoAqi?.ozonePpb,
            dominant = dominant,
            source = "AirNow",
            healthGuidance = guidance
        )
    }

    /**
     * Tomorrow.io "1m" timeline intervals -> a 60-minute minute-cast.
     * Anchors on the current minute and builds one MinutePrecip per
     * minute (0..59); a missing interval carries the last known rate
     * forward, exactly like the 15-minute interpolation. The sentence
     * is the same nowcast sentence the interpolated path uses, so the
     * Home minute-cast reads identically — only the data is real.
     * An empty interval list means the "1m" timeline was unavailable
     * (Enterprise on a free key): the caller keeps the interpolated
     * fallback instead of using this.
     */
    fun mapTomorrowMinutely(
        points: List<TomorrowMinutePoint>,
        now: Instant,
        zoneId: ZoneId
    ): Pair<List<MinutePrecip>, String> {
        if (points.isEmpty()) {
            return Pair(emptyList(), "Minutely precipitation is unavailable right now.")
        }
        val anchor = now.truncatedTo(ChronoUnit.MINUTES)
        val byOffset = points.mapNotNull { p ->
            val offset = ChronoUnit.MINUTES.between(anchor, p.time).toInt()
            if (offset in 0..59) offset to p else null
        }.toMap()
        val minutePoints = mutableListOf<MinutePrecip>()
        var lastMm = 0.0
        var lastProb: Int? = null
        for (m in 0 until 60) {
            val p = byOffset[m]
            if (p != null) {
                lastMm = p.mmPerHour ?: lastMm
                lastProb = p.probabilityPct ?: lastProb
            }
            minutePoints += MinutePrecip(
                time = anchor.plus(m.toLong(), ChronoUnit.MINUTES),
                mmPerHour = lastMm,
                probabilityPct = lastProb
            )
        }
        return Pair(minutePoints, generateNowcastSentence(minutePoints, now, zoneId))
    }

    /** Tomorrow.io pollen indices -> PollenData. hasKey is true when a
     * Tomorrow.io key is configured (the repository passes it), so the
     * Air & Pollen screen shows the gauges; a missing index stays null
     * and renders "--" rather than an invented value. */
    fun mapTomorrowPollen(
        treeIndex: Int?,
        grassIndex: Int?,
        weedIndex: Int?,
        hasKey: Boolean
    ): PollenData {
        return PollenData(
            treeIndex = treeIndex,
            grassIndex = grassIndex,
            weedIndex = weedIndex,
            source = "Tomorrow.io",
            hasKey = hasKey
        )
    }

    /** Outcome of a location-permission request: whether any location
     * is usable, and whether the precise (fine) grant allows a
     * high-accuracy fix. Fine wins; coarse alone still grants a
     * location, just at balanced accuracy; neither grants nothing. */
    data class LocationPermissionOutcome(val granted: Boolean, val highAccuracy: Boolean)

    fun resolveLocationPermission(
        fineGranted: Boolean,
        coarseGranted: Boolean
    ): LocationPermissionOutcome {
        return when {
            fineGranted -> LocationPermissionOutcome(granted = true, highAccuracy = true)
            coarseGranted -> LocationPermissionOutcome(granted = true, highAccuracy = false)
            else -> LocationPermissionOutcome(granted = false, highAccuracy = false)
        }
    }

    fun mapAlertSeverity(severity: String?): AlertSeverity {
        return when (severity?.lowercase(Locale.ROOT)) {
            "extreme" -> AlertSeverity.Extreme
            "severe" -> AlertSeverity.Severe
            "moderate" -> AlertSeverity.Moderate
            "minor" -> AlertSeverity.Minor
            else -> AlertSeverity.Unknown
        }
    }
}
