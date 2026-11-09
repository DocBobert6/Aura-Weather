package com.example

import com.example.data.work.SeenAlert
import com.example.data.work.decideNotifications
import com.example.data.work.severityRank
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pure tests for the 3.1 dedupe decision (AlertDedupe). They also run in
 * CI (testDebugUnitTest); run-pure-tests.sh compiles this file so they
 * run locally too.
 */
class AlertDedupeTest {

    private val now = Instant.parse("2026-11-09T12:00:00Z")

    private fun alert(
        id: String,
        severity: AlertSeverity = AlertSeverity.Severe,
        expires: Instant = Instant.parse("2026-11-09T20:00:00Z"),
        effective: Instant = Instant.parse("2026-11-09T10:00:00Z")
    ) = WeatherAlert(
        id = id,
        event = "Severe Thunderstorm Warning",
        severity = severity,
        urgency = "Immediate",
        headline = "Severe thunderstorm warning",
        description = "Damaging winds expected.",
        instruction = "Seek shelter.",
        effective = effective,
        expires = expires,
        ends = null,
        senderName = "NWS",
        areaDesc = "Boone County"
    )

    @Test
    fun testDecide_unseenActiveAlertNotified() {
        val result = decideNotifications(listOf(alert("a1")), emptyMap(), now)
        assertEquals(listOf("a1"), result.map { it.id })
    }

    @Test
    fun testDecide_sameAlertTwiceNotifiedOnce() {
        val seen = mapOf("a1" to SeenAlert("a1", AlertSeverity.Severe, now.minusSeconds(900)))
        val result = decideNotifications(listOf(alert("a1")), seen, now)
        assertTrue("A repeat of the same alert at the same severity must not notify again", result.isEmpty())
    }

    @Test
    fun testDecide_newIdNotified() {
        val seen = mapOf("a1" to SeenAlert("a1", AlertSeverity.Severe, now.minusSeconds(900)))
        val result = decideNotifications(
            listOf(alert("a1"), alert("a2", AlertSeverity.Moderate)),
            seen,
            now
        )
        assertEquals(listOf("a2"), result.map { it.id })
    }

    @Test
    fun testDecide_expiredAlertNeverNotified() {
        val expired = alert("a1", expires = Instant.parse("2026-11-09T11:00:00Z"))
        val result = decideNotifications(listOf(expired), emptyMap(), now)
        assertTrue("An expired alert must never notify, even unseen", result.isEmpty())
    }

    @Test
    fun testDecide_severityEscalationNotified() {
        val seen = mapOf("a1" to SeenAlert("a1", AlertSeverity.Moderate, now.minusSeconds(900)))
        val escalated = alert("a1", severity = AlertSeverity.Severe)
        val result = decideNotifications(listOf(escalated), seen, now)
        assertEquals(listOf("a1"), result.map { it.id })
    }

    @Test
    fun testDecide_severityDowngradeNotNotified() {
        val seen = mapOf("a1" to SeenAlert("a1", AlertSeverity.Severe, now.minusSeconds(900)))
        val downgraded = alert("a1", severity = AlertSeverity.Moderate)
        val result = decideNotifications(listOf(downgraded), seen, now)
        assertTrue("A downgrade on a known alert is not a new situation", result.isEmpty())
    }

    @Test
    fun testSeverityRank_extremeOutranksAll() {
        assertTrue(severityRank(AlertSeverity.Extreme) < severityRank(AlertSeverity.Severe))
        assertTrue(severityRank(AlertSeverity.Severe) < severityRank(AlertSeverity.Moderate))
        assertTrue(severityRank(AlertSeverity.Moderate) < severityRank(AlertSeverity.Minor))
        assertTrue(severityRank(AlertSeverity.Minor) < severityRank(AlertSeverity.Unknown))
    }
}
