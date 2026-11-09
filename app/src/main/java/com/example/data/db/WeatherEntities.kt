package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cache row per place. The JSON columns hold real Moshi JSON for one
 * section each (see WeatherRepository) — never pipe/comma-delimited strings,
 * which silently dropped fields and invented defaults on restore.
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val placeId: String,
    val cachedAt: Long,
    val currentJson: String,
    val hourlyJson: String,
    val dailyJson: String,
    val minutelyJson: String,
    val alertsJson: String,
    val aqiJson: String,
    val pollenJson: String,
    val radarStation: String?,
    val forecastZone: String?,
    val minuteSummary: String
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stateCode: String,
    val lat: Double,
    val lon: Double,
    val isPreset: Boolean,
    val isSelected: Boolean,
    // IANA zone resolved for this place (NWS /points timeZone, else the
    // Open-Meteo UTC offset). Empty until the first successful fetch.
    val zoneId: String = ""
)

/**
 * One row per alert the 3.1 worker has already notified about (or seen
 * and decided not to notify). The seen-set is what makes "two polls with
 * the same active alert → one notification" work, and it must survive a
 * reboot — so it lives in Room, never only in memory. firstSeenAt is the
 * epoch-millis of the first poll that saw the alert; rows older than 7
 * days are pruned by the worker so the table cannot grow without bound.
 * severity is the AlertSeverity name at the last poll, so a later
 * escalation (Moderate → Severe) on the same id can be detected.
 */
@Entity(tableName = "seen_alerts")
data class SeenAlertEntity(
    @PrimaryKey val alertId: String,
    val firstSeenAt: Long,
    val severity: String
)
