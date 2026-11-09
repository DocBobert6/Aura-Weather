package com.example.data.work

import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherAlert
import java.time.Instant

/**
 * The pure dedupe decision for 3.1 alert notifications. No Android, no
 * Room, no WorkManager — plain JUnit tests cover it (AlertDedupeTest),
 * and the worker calls it through [AlertChecker].
 *
 * An alert is notified when it is not expired and either:
 * - it has never been seen before, or
 * - its severity escalated since it was last seen (e.g. Moderate became
 *   Severe — the same NWS id, a worse situation).
 * A repeat of the same alert at the same (or a lower) severity is not
 * notified again, and an expired alert is never notified.
 */
data class SeenAlert(
    val id: String,
    val severity: AlertSeverity,
    val firstSeenAt: Instant
)

/** Lower is more severe: Extreme outranks Severe outranks Moderate. */
fun severityRank(severity: AlertSeverity): Int = when (severity) {
    AlertSeverity.Extreme -> 0
    AlertSeverity.Severe -> 1
    AlertSeverity.Moderate -> 2
    AlertSeverity.Minor -> 3
    AlertSeverity.Unknown -> 4
}

fun decideNotifications(
    alerts: List<WeatherAlert>,
    seen: Map<String, SeenAlert>,
    now: Instant
): List<WeatherAlert> {
    return alerts.filter { alert ->
        if (alert.expires.isBefore(now)) return@filter false
        val previous = seen[alert.id] ?: return@filter true
        severityRank(alert.severity) < severityRank(previous.severity)
    }
}
