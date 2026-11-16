package com.example

import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.loopDwellMs
import com.example.data.mapper.loopNextIndex
import com.example.data.mapper.RADAR_LOOP_DWELL_MS
import com.example.data.mapper.RADAR_LOOP_END_PAUSE_MS
import com.example.data.mapper.RadarRange
import com.example.data.mapper.hrrrLayerName
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.latToTileY
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.mrmsLiveFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the radar helpers kept for the engine (RadarTiles).
 * They also run in CI (testDebugUnitTest); run-pure-tests.sh compiles
 * this file so they run locally too.
 *
 * The centre is Cincinnati (39.1, -84.5) at zoom 8. The centre
 * tile at zoom 8 is (67, 97), computed independently (not by
 * calling the functions under test).
 */
class RadarTilesTest {

    private val centerLat = 39.1
    private val centerLon = -84.5
    private val zoom = 8

    @Test
    fun testLonToTileX_cincinnatiZoom8() {
        assertEquals(67, lonToTileX(centerLon, zoom))
    }

    @Test
    fun testLatToTileY_cincinnatiZoom8() {
        assertEquals(97, latToTileY(centerLat, zoom))
    }

    @Test
    fun testIemLayerNameFor_liveAndOldest() {
        assertEquals("nexrad-n0q-900913", iemLayerNameFor(9))
        assertEquals("nexrad-n0q-900913-m45m", iemLayerNameFor(0))
        assertEquals("nexrad-n0q-900913-m05m", iemLayerNameFor(8))
    }

    @Test
    fun testIemLayerNameFor_allTenDistinct() {
        val names = (0..9).map { iemLayerNameFor(it) }.toSet()
        assertEquals(10, names.size)
    }

    @Test
    fun testIemTileUrl_format() {
        assertEquals(
            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/8/67/97.png",
            iemTileUrl("nexrad-n0q-900913", 8, 67, 97)
        )
    }

    @Test
    fun testHrrrLayerName_forecastMinutesNotHours() {
        // FXXXX is the forecast MINUTE: 6 hours out is F0360, not F006.
        // Live form (-0 = latest run): the timestamped archived form
        // 503s for the latest run, so it must never be built.
        assertEquals("hrrr::REFD-F0000-0", hrrrLayerName(0))
        assertEquals("hrrr::REFD-F0360-0", hrrrLayerName(360))
        assertEquals("hrrr::REFD-F2880-0", hrrrLayerName(2880))
    }

    @Test
    fun testHrrrLayerName_neverTimestamped() {
        // The layer name must not embed a run timestamp: that is the
        // archived form, which IEM cannot serve for the latest run.
        val frames = radarRangeFrames(RadarRange.Future24h, java.time.Instant.parse("2026-11-10T01:00:00Z"))
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.layerName.endsWith("-0") })
        assertTrue(frames.all { it.layerName.startsWith("hrrr::REFD-F") })
    }

    @Test
    fun testRadarFrameLabel_relative() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        assertEquals("NOW", radarFrameLabel(now, now))
        assertEquals("45M AGO", radarFrameLabel(now.minusSeconds(45 * 60), now))
        assertEquals("24H AGO", radarFrameLabel(now.minusSeconds(24 * 3600), now))
        assertEquals("IN 15M", radarFrameLabel(now.plusSeconds(15 * 60), now))
        assertEquals("IN 6H", radarFrameLabel(now.plusSeconds(6 * 3600), now))
        assertEquals("IN 48H", radarFrameLabel(now.plusSeconds(48 * 3600), now))
    }

    @Test
    fun testRadarFrameLabel_minuteGranularFuture() {
        // AUDIT6 §9 Fix 11: the old "IN 2H" label covered four
        // consecutive 15-min frames (120-179 min), so it sat still
        // ~9 s while the loop advanced. Every frame now differs.
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        assertEquals("IN 59M", radarFrameLabel(now.plusSeconds(59 * 60), now))
        assertEquals("IN 1H", radarFrameLabel(now.plusSeconds(60 * 60), now))
        assertEquals("IN 1H 15M", radarFrameLabel(now.plusSeconds(75 * 60), now))
        assertEquals("IN 2H", radarFrameLabel(now.plusSeconds(120 * 60), now))
        assertEquals("IN 2H 15M", radarFrameLabel(now.plusSeconds(135 * 60), now))
        assertEquals("IN 2H 30M", radarFrameLabel(now.plusSeconds(150 * 60), now))
        assertEquals("IN 2H 45M", radarFrameLabel(now.plusSeconds(165 * 60), now))
        assertEquals("IN 3H", radarFrameLabel(now.plusSeconds(180 * 60), now))
        assertEquals("IN 24H 15M", radarFrameLabel(now.plusSeconds((24 * 60 + 15) * 60), now))
    }

    @Test
    fun testRadarFrameLabel_minuteGranularPast() {
        // The same collapse existed on the past side ("2H AGO"
        // covered 120-179 min ago); it gets the same fix.
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        assertEquals("59M AGO", radarFrameLabel(now.minusSeconds(59 * 60), now))
        assertEquals("1H AGO", radarFrameLabel(now.minusSeconds(60 * 60), now))
        assertEquals("1H 15M AGO", radarFrameLabel(now.minusSeconds(75 * 60), now))
        assertEquals("2H AGO", radarFrameLabel(now.minusSeconds(120 * 60), now))
        assertEquals("2H 15M AGO", radarFrameLabel(now.minusSeconds(135 * 60), now))
        assertEquals("2H 30M AGO", radarFrameLabel(now.minusSeconds(150 * 60), now))
    }

    @Test
    fun testRadarFrameLabel_each15MinFrameIn2hBandIsDistinct() {
        // The video's stall: four frames, one label. All four (plus
        // the next hour boundary) must now be distinct.
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val labels = (120..180 step 15).map { radarFrameLabel(now.plusSeconds(it * 60L), now) }
        assertEquals(listOf("IN 2H", "IN 2H 15M", "IN 2H 30M", "IN 2H 45M", "IN 3H"), labels)
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun testRadarFrameLabel_liveAndNow() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        assertEquals("NOW", radarFrameLabel(now, now))
        assertEquals("IN 1M", radarFrameLabel(now.plusSeconds(60), now))
        assertEquals("1M AGO", radarFrameLabel(now.minusSeconds(60), now))
    }

    @Test
    fun testRadarFrameLabel_star4000SafeCharsOnly() {
        // Labels render in the Star4000 font: no en/em dash or
        // ellipsis — only A-Z, 0-9 and spaces, over the full range.
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val labels = (-48 * 60..48 * 60 step 15).map { radarFrameLabel(now.plusSeconds(it * 60L), now) }
        assertTrue(labels.isNotEmpty())
        for (label in labels) {
            assertTrue(label, label.matches(Regex("^[A-Z0-9 ]+$")))
        }
    }

    @Test
    fun testRadarRangeFrames_liveIsTenFrames() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val frames = radarRangeFrames(RadarRange.Live, now)
        assertEquals(10, frames.size)
        assertEquals("LIVE NOW", frames[9].label)
        assertEquals("45M AGO", frames[0].label)
        assertEquals(iemLayerNameFor(9), frames[9].layerName)
    }

    @Test
    fun testRadarRangeFrames_future24hStepsAndCap() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val frames = radarRangeFrames(RadarRange.Future24h, now)
        // Starts in the future, ends at or before now+24h.
        assertTrue(frames.isNotEmpty())
        assertTrue(frames[0].validTime.isAfter(now))
        assertTrue(!frames.last().validTime.isAfter(now.plusSeconds(24 * 3600)))
        // First steps are 15 min (HRRR cadence for the first 18h).
        val step0 = java.time.Duration.between(frames[0].validTime, frames[1].validTime).toMinutes()
        assertEquals(15L, step0)
        // All layers are HRRR REFD with a 4-digit forecast minute.
        assertTrue(frames.all { it.layerName.startsWith("hrrr::REFD-F") })
        assertTrue(frames.all { it.label.startsWith("IN ") })
        // No frame exceeds 48h from its run (the HRRR cap).
        val run = java.time.Instant.parse("2026-11-10T00:00:00Z")
        assertTrue(frames.all { !it.validTime.isAfter(run.plusSeconds(48 * 3600)) })
    }

    @Test
    fun testRadarRangeFrames_future48hEndsWithin48hOfRun() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val frames = radarRangeFrames(RadarRange.Future48h, now)
        assertTrue(frames.isNotEmpty())
        // The run is ~1h before now, so the last frame is ~47h out,
        // capped at 48h from the run — never beyond it.
        val run = java.time.Instant.parse("2026-11-10T00:00:00Z")
        assertTrue(!frames.last().validTime.isAfter(run.plusSeconds(48 * 3600)))
        assertTrue(frames.last().validTime.isAfter(now.plusSeconds(46 * 3600)))
    }

    // AUDIT6 §9 Fixes 2-3: loop gating (hold the previous frame until
    // the next is ready) and dwell.

    @Test
    fun testLoopNextIndex_midRange() {
        assertEquals(11, loopNextIndex(10, 75))
    }

    @Test
    fun testLoopNextIndex_wrapsAtLastFrame() {
        assertEquals(0, loopNextIndex(74, 75))
        assertEquals(0, loopNextIndex(9, 10))
    }

    @Test
    fun testLoopNextIndex_singleFrameStays() {
        assertEquals(0, loopNextIndex(0, 1))
    }

    @Test
    fun testLoopNextIndex_emptyLoopIsZero() {
        assertEquals(0, loopNextIndex(0, 0))
    }

    @Test
    fun testLoopNextIndex_coercesOutOfRangeCurrent() {
        // A stale index past the end still wraps to 0 instead of
        // crashing the loop; a negative one coerces to frame 0's next.
        assertEquals(0, loopNextIndex(99, 10))
        assertEquals(1, loopNextIndex(-3, 10))
    }

    @Test
    fun testLoopDwellMs_normalFrame() {
        assertEquals(750L, loopDwellMs(0, 10))
        assertEquals(750L, loopDwellMs(8, 10))
    }

    @Test
    fun testLoopDwellMs_newestFrameGetsEndPause() {
        // End pause before the loop wraps (AUDIT6 §9 Fix 2).
        assertEquals(2250L, loopDwellMs(9, 10))
        assertEquals(2250L, loopDwellMs(74, 75))
    }

    @Test
    fun testLoopDwellMs_singleFrameIsNewest() {
        assertEquals(2250L, loopDwellMs(0, 1))
    }

    @Test
    fun testLoopDwellMs_emptyLoopIsNormal() {
        assertEquals(750L, loopDwellMs(0, 0))
    }

    @Test
    fun testLoopDwell_unchanged() {
        // The loop dwell consts are pinned: 750 ms per frame,
        // 2,250 ms end pause on the newest frame. (The old 2 s
        // wait timeout RADAR_LOOP_WAIT_TIMEOUT_MS was deleted at
        // the Phase 5 cutover.)
        assertEquals(750L, RADAR_LOOP_DWELL_MS)
        assertEquals(2250L, RADAR_LOOP_END_PAUSE_MS)
    }

    // -- Multi-source observed radar (AUDIT6 §9 Fixes 6+7) ---------------
    // Ground truth: the probed MRMS timeExtent [1794709257000,
    // 1794715977000] (22 five-minute frames).

    private val mrmsProbedTimes: List<Long> =
        (0 until 22).map { 1794709500000L + it * 300000L }

    @Test
    fun testMrmsLiveFrames_probedExtentIsTenFrames() {
        val now = java.time.Instant.ofEpochMilli(1794715977000L)
        val frames = mrmsLiveFrames(mrmsProbedTimes, now)
        // The loop keeps its 10-frame shape: the newest 10 of 22.
        assertEquals(10, frames.size)
        assertEquals(1794713100000L, frames.first().validTime.toEpochMilli())
        assertEquals(1794715800000L, frames.last().validTime.toEpochMilli())
        assertEquals("47M AGO", frames.first().label)
        assertEquals("LIVE NOW", frames.last().label)
        // Layer names map from the end: newest is the live layer.
        assertEquals("nexrad-n0q-900913-m45m", frames.first().layerName)
        assertEquals("nexrad-n0q-900913", frames.last().layerName)
        assertTrue(mrmsLiveFrames(emptyList(), now).isEmpty())
    }

}
