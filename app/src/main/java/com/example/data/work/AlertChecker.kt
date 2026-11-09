package com.example.data.work

import com.example.data.db.SeenAlertDao
import com.example.data.db.SeenAlertEntity
import com.example.data.repo.WeatherRepository
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherAlert
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * One alert-check pass (3.1), separated from WorkManager so it can be
 * unit-tested with a fake API and an in-memory database (AlertCheckerTest).
 * The worker ([AlertCheckWorker]) is a thin shell around this: it resolves
 * the collaborators from the app container and maps a thrown exception to
 * Result.retry().
 *
 * - No selected place → nothing to check, no notification.
 * - The fetch throws on a network failure (the repository never invents
 *   an empty list), so the caller retries with backoff.
 * - Every currently active alert is recorded in the seen-set with its
 *   current severity, whether or not it was notified — so a later
 *   escalation on the same id is detected, and a repeat is not.
 * - Seen rows older than 7 days are pruned, so the table cannot grow
 *   without bound. Active alerts keep being refreshed, so they are
 *   never pruned while still active.
 */
class AlertChecker(
    private val repository: WeatherRepository,
    private val seenAlertDao: SeenAlertDao,
    private val onNotify: (WeatherAlert, String) -> Unit
) {
    sealed interface Outcome {
        data object NoPlace : Outcome
        data class Checked(val notified: Int, val active: Int) : Outcome
    }

    suspend fun check(now: Instant = Instant.now()): Outcome {
        val place = repository.getSelectedPlace() ?: return Outcome.NoPlace
        val alerts = repository.getActiveAlertsForPlace(place)
        val seenRows = seenAlertDao.getAll()
        val seen = seenRows.associate { row ->
            row.alertId to SeenAlert(
                id = row.alertId,
                severity = runCatching { AlertSeverity.valueOf(row.severity) }
                    .getOrDefault(AlertSeverity.Unknown),
                firstSeenAt = Instant.ofEpochMilli(row.firstSeenAt)
            )
        }
        val toNotify = decideNotifications(alerts, seen, now)
        toNotify.forEach { alert -> onNotify(alert, place.name) }
        val active = alerts.filter { !it.expires.isBefore(now) }
        active.forEach { alert ->
            seenAlertDao.insert(
                SeenAlertEntity(
                    alertId = alert.id,
                    firstSeenAt = now.toEpochMilli(),
                    severity = alert.severity.name
                )
            )
        }
        seenAlertDao.deleteOlderThan(now.minus(7, ChronoUnit.DAYS).toEpochMilli())
        return Outcome.Checked(notified = toNotify.size, active = active.size)
    }
}
