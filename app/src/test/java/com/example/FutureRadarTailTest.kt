package com.example

import com.example.data.mapper.FutureFrameAvailability
import com.example.data.mapper.RadarFrame
import com.example.data.mapper.RadarRange
import com.example.data.mapper.capFutureFrames
import com.example.data.mapper.formatDataBytes
import com.example.data.mapper.formatForecastEndNote
import com.example.data.mapper.futureCostTextFromBytes
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarRangeFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FutureRadarTailTest {

    private fun frameAt(validTime: Instant): RadarFrame =
        RadarFrame(validTime, "hrrr::REFD-F0000-0", "NOW")

    private fun framesAt(vararg validTimes: Instant): List<RadarFrame> =
        validTimes.map { frameAt(it) }

    private val t0 = Instant.parse("2026-11-16T00:30:00Z")
    private fun at(minutes: Long): Instant = t0.plusSeconds(minutes * 60)

    // -- Tail cap ----------------------------------------------------------

    @Test
    fun allServedIsNotCapped() {
        val frames = framesAt(at(15), at(30), at(45))
        val result = capFutureFrames(frames, List(3) { FutureFrameAvailability.SERVED })
        assertFalse(result.capped)
        assertEquals(frames, result.playableFrames)
        assertNull(result.endNote)
        assertNull(result.firstFailedIndex)
        assertEquals(0, result.droppedCount)
        assertEquals(at(45), result.lastServedValidTime)
    }

    @Test
    fun firstFailureCapsTheTail() {
        val frames = framesAt(at(15), at(30), at(45), at(60), at(75))
        val availability = listOf(
            FutureFrameAvailability.SERVED,
            FutureFrameAvailability.SERVED,
            FutureFrameAvailability.FAILED,
            FutureFrameAvailability.SERVED,
            FutureFrameAvailability.SERVED
        )
        val result = capFutureFrames(frames, availability)
        assertTrue(result.capped)
        assertEquals(frames.take(2), result.playableFrames)
        assertEquals(3, result.droppedCount)
        assertEquals(2, result.firstFailedIndex)
        assertEquals(at(30), result.lastServedValidTime)
        assertEquals("FORECAST ENDS ABOUT 01:00 UTC", result.endNote)
    }

    @Test
    fun laterServedFramesAfterAFailureAreStillDropped() {
        // Monotonic cutoff: a SERVED after a FAILED does not reopen the tail.
        val frames = framesAt(at(15), at(30), at(45))
        val availability = listOf(
            FutureFrameAvailability.SERVED,
            FutureFrameAvailability.FAILED,
            FutureFrameAvailability.SERVED
        )
        val result = capFutureFrames(frames, availability)
        assertEquals(1, result.playableFrames.size)
        assertEquals(2, result.droppedCount)
    }

    @Test
    fun firstFrameFailedMeansNothingPlayable() {
        val frames = framesAt(at(15), at(30))
        val result = capFutureFrames(
            frames,
            listOf(FutureFrameAvailability.FAILED, FutureFrameAvailability.FAILED)
        )
        assertTrue(result.capped)
        assertTrue(result.playableFrames.isEmpty())
        assertNull(result.lastServedValidTime)
        assertEquals("NO FORECAST FRAMES AVAILABLE", result.endNote)
        assertEquals(2, result.droppedCount)
        assertEquals(0, result.firstFailedIndex)
    }

    @Test
    fun unknownBeforeFailureIsKeptProvisionally() {
        val frames = framesAt(at(15), at(30), at(45))
        val availability = listOf(
            FutureFrameAvailability.SERVED,
            FutureFrameAvailability.UNKNOWN,
            FutureFrameAvailability.FAILED
        )
        val result = capFutureFrames(frames, availability)
        assertEquals(frames.take(2), result.playableFrames)
        assertTrue(result.capped)
    }

    @Test
    fun emptyListIsUncappedAndEmpty() {
        val result = capFutureFrames(emptyList(), emptyList())
        assertFalse(result.capped)
        assertTrue(result.playableFrames.isEmpty())
        assertNull(result.endNote)
        assertEquals(0, result.droppedCount)
        assertNull(result.firstFailedIndex)
    }

    @Test
    fun shortAvailabilityListReadsMissingAsUnknown() {
        val frames = framesAt(at(15), at(30), at(45))
        val result = capFutureFrames(frames, listOf(FutureFrameAvailability.SERVED))
        assertFalse(result.capped)
        assertEquals(3, result.playableFrames.size)
    }

    @Test
    fun liveProbeShape48hCapsAtF2160() {
        // Mirrors the live probe 2026-11-16 ~00:30 UTC (run 23:00 UTC,
        // non-synoptic): 97 frames F0090–F2880, served through F2160,
        // first 503 at F2220, 503 for every lead after.
        val now = Instant.parse("2026-11-16T00:29:50Z")
        val frames = radarRangeFrames(RadarRange.Future48h, now)
        assertEquals(97, frames.size)
        val availability = frames.map { frame ->
            val leadMin = frame.layerName.substringAfter("REFD-F").substringBefore("-").toInt()
            if (leadMin <= 2160) FutureFrameAvailability.SERVED else FutureFrameAvailability.FAILED
        }
        val result = capFutureFrames(frames, availability)
        assertTrue(result.capped)
        assertEquals(85, result.playableFrames.size)
        assertEquals(12, result.droppedCount)
        assertEquals(85, result.firstFailedIndex)
        assertEquals(Instant.parse("2026-11-17T11:00:00Z"), result.lastServedValidTime)
        assertEquals("FORECAST ENDS ABOUT 11:00 UTC", result.endNote)
    }

    // -- Tail note -----------------------------------------------------------

    @Test
    fun endNoteFormatsUtcHourMinute() {
        assertEquals(
            "FORECAST ENDS ABOUT 11:00 UTC",
            formatForecastEndNote(Instant.parse("2026-11-17T11:00:00Z"))
        )
        assertEquals(
            "FORECAST ENDS ABOUT 06:05 UTC",
            formatForecastEndNote(Instant.parse("2026-11-17T06:05:00Z"))
        )
    }

    // -- Future labels (audit pin: minute-granular labels already exist
    // in radarFrameLabel and are correct — these pin them for future
    // frames so a later change cannot silently reintroduce hourly
    // labels) ---------------------------------------------------------------

    @Test
    fun futureLabelsAreMinuteGranular() {
        val now = Instant.parse("2026-11-16T00:30:00Z")
        assertEquals("IN 15M", radarFrameLabel(now.plusSeconds(15 * 60), now))
        assertEquals("IN 2H 15M", radarFrameLabel(now.plusSeconds(135 * 60), now))
        assertEquals("IN 6H", radarFrameLabel(now.plusSeconds(6 * 3600), now))
        assertEquals("IN 48H", radarFrameLabel(now.plusSeconds(48 * 3600), now))
        assertEquals("IN 1H 1M", radarFrameLabel(now.plusSeconds(61 * 60), now))
    }

    // -- Cost text from actual bytes -----------------------------------------

    @Test
    fun formatDataBytesUnits() {
        assertEquals("0 B", formatDataBytes(0))
        assertEquals("334 B", formatDataBytes(334))
        assertEquals("1023 B", formatDataBytes(1023))
        assertEquals("1 KB", formatDataBytes(1024))
        assertEquals("28 KB", formatDataBytes(28 * 1024))
        assertEquals("3.5 MB", formatDataBytes(3670016))
        assertEquals("5 MB", formatDataBytes(5 * 1024 * 1024))
        assertEquals("0 B", formatDataBytes(-50))
    }

    @Test
    fun futureCostTextUsesActualBytes() {
        // Tonight's probe: 85 served centre tiles totalled 28,390 B.
        // No "THEN CACHED": nothing caches a future visit's bytes
        // (no fetcher disk cache, volatile MapLibre sources,
        // IEM max-age=300) — a revisit re-fetches.
        assertEquals("FIRST LOAD 27.7 KB", futureCostTextFromBytes(28390))
        assertEquals("FIRST LOAD 0 B", futureCostTextFromBytes(0))
        assertEquals("FIRST LOAD 3.5 MB", futureCostTextFromBytes(3670016))
    }

    // -- Full-range shape (Phase 4 wiring pins) -----------------------------

    @Test
    fun future24hFrameCountAtProbeTime() {
        // 2026-11-16 00:29:50Z: run 23:00, first frame 00:30, 15-min
        // steps to 17:00 (18h from the run), then hourly to 00:00.
        val now = Instant.parse("2026-11-16T00:29:50Z")
        assertEquals(74, radarRangeFrames(RadarRange.Future24h, now).size)
    }

    @Test
    fun future6hIsAPrefixOfFuture24hForTheSameNow() {
        // The wiring relies on this: the 6h list is the 24h list's
        // head (same run, same step rule), so a 6h -> 24h switch
        // only extends the sources instead of rebuilding them.
        val now = Instant.parse("2026-11-16T00:29:50Z")
        val h6 = radarRangeFrames(RadarRange.Future6h, now)
        val h24 = radarRangeFrames(RadarRange.Future24h, now)
        assertEquals(24, h6.size)
        assertEquals(74, h24.size)
        assertEquals(h6, h24.take(h6.size))
    }

    @Test
    fun future24hIsAPrefixOfFuture48hForTheSameNow() {
        // The wiring relies on this: the 24h list is the 48h list's
        // head (same run, same step rule), so a 24h -> 48h switch
        // only extends the sources instead of rebuilding them.
        val now = Instant.parse("2026-11-16T00:29:50Z")
        val h24 = radarRangeFrames(RadarRange.Future24h, now)
        val h48 = radarRangeFrames(RadarRange.Future48h, now)
        assertEquals(74, h24.size)
        assertEquals(97, h48.size)
        assertEquals(h24, h48.take(h24.size))
    }
}
