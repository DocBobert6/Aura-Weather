package com.example

import com.example.ui.widget.RADAR_WIDGET_MRMS_FALLBACK_ATTEMPTS
import com.example.ui.widget.RADAR_WIDGET_UNAVAILABLE_LABEL
import com.example.ui.widget.RADAR_WIDGET_WAITING_LABEL
import com.example.ui.widget.radarWidgetEmptyStateLabel
import com.example.ui.widget.RadarWidgetFailedBakeOutcome
import com.example.ui.widget.radarWidgetFailedBakeOutcome
import com.example.ui.widget.radarWidgetLabel
import com.example.ui.widget.radarWidgetMrmsCandidateTimes
import com.example.ui.widget.radarWidgetOverlayStep
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radar widget fallbacks (Item 4 refinement + Item 5), 2026-11-16:
 * the HRRR-not-published → live-frame fallback, the MRMS 5-min
 * step-back, the failed-bake stale/unavailable decision, and the
 * waiting/unavailable empty-state wording. Pure Kotlin — runs in
 * the local kotlinc + JUnit pass. The Canvas compositing and the
 * updater's Android calls around these decisions are CI-only.
 */
class RadarWidgetFallbackTest {

    // -- HRRR-not-published → live frame ------------------------------

    @Test
    fun `failed forecast step falls back to the live frame`() {
        assertEquals(0, radarWidgetOverlayStep(1, hrrrFailed = true))
        assertEquals(0, radarWidgetOverlayStep(2, hrrrFailed = true))
    }

    @Test
    fun `served forecast step keeps its own step`() {
        assertEquals(1, radarWidgetOverlayStep(1, hrrrFailed = false))
        assertEquals(2, radarWidgetOverlayStep(2, hrrrFailed = false))
        assertEquals(0, radarWidgetOverlayStep(0, hrrrFailed = false))
    }

    @Test
    fun `fallback frame is labelled LIVE NOW with its real valid time`() {
        val frame = Instant.parse("2026-11-16T23:41:00Z")
        assertEquals(
            "LIVE NOW · 18:41",
            radarWidgetLabel(
                radarWidgetOverlayStep(1, hrrrFailed = true),
                frame,
                "America/New_York"
            )
        )
    }

    @Test
    fun `fallback to an older live frame is labelled STALE, never fresh`() {
        val frame = Instant.parse("2026-11-16T23:35:00Z")
        assertEquals(
            "LIVE NOW · 18:35 · STALE",
            radarWidgetLabel(
                radarWidgetOverlayStep(2, hrrrFailed = true),
                frame,
                "America/New_York",
                stale = true
            )
        )
    }

    // -- MRMS step-back candidates -------------------------------------

    @Test
    fun `candidates step back 5 minutes from the newest frame`() {
        val newest = Instant.parse("2026-11-17T04:40:00Z").toEpochMilli()
        val now = Instant.parse("2026-11-17T04:41:00Z")
        val candidates = radarWidgetMrmsCandidateTimes(newest, now)
        assertEquals(RADAR_WIDGET_MRMS_FALLBACK_ATTEMPTS, candidates.size)
        assertEquals(
            listOf(
                Instant.parse("2026-11-17T04:40:00Z").toEpochMilli(),
                Instant.parse("2026-11-17T04:35:00Z").toEpochMilli(),
                Instant.parse("2026-11-17T04:30:00Z").toEpochMilli(),
                Instant.parse("2026-11-17T04:25:00Z").toEpochMilli()
            ),
            candidates
        )
    }

    @Test
    fun `candidates without an extent start at now truncated to 5 minutes`() {
        // No service info: 04:41:37 truncates to 04:40, then steps back.
        val now = Instant.parse("2026-11-17T04:41:37Z")
        val candidates = radarWidgetMrmsCandidateTimes(null, now)
        assertEquals(
            Instant.parse("2026-11-17T04:40:00Z").toEpochMilli(),
            candidates.first()
        )
        assertEquals(RADAR_WIDGET_MRMS_FALLBACK_ATTEMPTS, candidates.size)
        for (i in 1 until candidates.size) {
            assertEquals(
                "candidate $i is 5 min before candidate ${i - 1}",
                candidates[i - 1] - 300_000L,
                candidates[i]
            )
        }
    }

    @Test
    fun `candidate count honours the attempts parameter`() {
        val newest = Instant.parse("2026-11-17T04:40:00Z").toEpochMilli()
        val now = Instant.parse("2026-11-17T04:41:00Z")
        assertEquals(1, radarWidgetMrmsCandidateTimes(newest, now, attempts = 1).size)
        assertEquals(2, radarWidgetMrmsCandidateTimes(newest, now, attempts = 2).size)
        // The newest candidate is always first, whatever the count.
        assertEquals(
            newest,
            radarWidgetMrmsCandidateTimes(newest, now, attempts = 2).first()
        )
    }

    @Test
    fun `step-back never invents a frame time`() {
        // Every candidate is a real 5-min boundary at or before the newest.
        val newest = Instant.parse("2026-11-17T04:40:00Z").toEpochMilli()
        val now = Instant.parse("2026-11-17T04:41:00Z")
        val candidates = radarWidgetMrmsCandidateTimes(newest, now)
        assertTrue(candidates.all { it <= newest && it % 300_000L == 0L })
    }

    // -- Failed bake: stale vs unavailable -----------------------------

    @Test
    fun `live bake fails with a cache shows the stale image`() {
        assertEquals(
            RadarWidgetFailedBakeOutcome.STALE_SHOWN,
            radarWidgetFailedBakeOutcome(hasCachedImage = true)
        )
    }

    @Test
    fun `live bake fails with no cache shows unavailable`() {
        assertEquals(
            RadarWidgetFailedBakeOutcome.UNAVAILABLE,
            radarWidgetFailedBakeOutcome(hasCachedImage = false)
        )
    }

    // -- Empty-state wording -------------------------------------------

    @Test
    fun `first failure with no image waits for radar`() {
        assertEquals(
            "WAITING FOR RADAR",
            radarWidgetEmptyStateLabel(hasFailedBefore = false)
        )
        assertEquals(
            RADAR_WIDGET_WAITING_LABEL,
            radarWidgetEmptyStateLabel(hasFailedBefore = false)
        )
    }

    @Test
    fun `repeat failure with no image is unavailable`() {
        assertEquals(
            "RADAR UNAVAILABLE",
            radarWidgetEmptyStateLabel(hasFailedBefore = true)
        )
        assertEquals(
            RADAR_WIDGET_UNAVAILABLE_LABEL,
            radarWidgetEmptyStateLabel(hasFailedBefore = true)
        )
    }

    @Test
    fun `waiting and unavailable are distinct states`() {
        assertTrue(
            radarWidgetEmptyStateLabel(false) != radarWidgetEmptyStateLabel(true)
        )
    }
}
