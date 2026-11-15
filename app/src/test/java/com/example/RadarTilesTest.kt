package com.example

import com.example.data.mapper.gibsTileUrl
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.isFrameSettled
import com.example.data.mapper.isSatelliteFrameSettled
import com.example.data.mapper.lookAheadFrameIndices
import com.example.data.mapper.loopDwellMs
import com.example.data.mapper.loopNextIndex
import com.example.data.mapper.loopPreloadCostText
import com.example.data.mapper.RADAR_LOOP_DWELL_MS
import com.example.data.mapper.RADAR_LOOP_END_PAUSE_MS
import com.example.data.mapper.FORECAST_SLOW_MIN_SETTLED_FRACTION
import com.example.data.mapper.FORECAST_SLOW_WAIT_MS
import com.example.data.mapper.RADAR_LOOP_WAIT_TIMEOUT_MS
import com.example.data.mapper.isForecastSlow
import com.example.data.mapper.settledUrlCount
import com.example.data.mapper.SATELLITE_OVERLAY_CAP_MS
import com.example.data.mapper.RadarRange
import com.example.data.mapper.hrrrLayerName
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarFrameUrls
import com.example.data.mapper.PAN_DIRECTION_THRESHOLD_PX
import com.example.data.mapper.PAN_SETTLE_DELAY_MS
import com.example.data.mapper.ParentTile
import com.example.data.mapper.TileRange
import com.example.data.mapper.VIEWPORT_MAX_TILES_X
import com.example.data.mapper.VIEWPORT_MAX_TILES_Y
import com.example.data.mapper.VIEWPORT_TILE_MARGIN
import com.example.data.mapper.centreTileRange
import com.example.data.mapper.isTileSettled
import com.example.data.mapper.panDirection
import com.example.data.mapper.parentTileFor
import com.example.data.mapper.radarFrameUrlsForRange
import com.example.data.mapper.radarPrefetchUrlsForRange
import com.example.data.mapper.satelliteCapRemainingMs
import com.example.data.mapper.satelliteCostText
import com.example.data.mapper.satelliteTileUrlsForRange
import com.example.data.mapper.viewportTileRange
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.latToTileY
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.radarPrefetchUrls
import com.example.data.mapper.satelliteTileUrls
import com.example.data.mapper.satelliteZoomLevel
import com.example.data.mapper.snappedTileSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the 3.3 radar prefetch URL building (RadarTiles).
 * They also run in CI (testDebugUnitTest); run-pure-tests.sh compiles
 * this file so they run locally too.
 *
 * The centre is Cincinnati (39.1, -84.5) at zoom 8, on a phone-like
 * grid (tilesX = 6, tilesY = 9): halfX = 3, halfY = 4, so each frame
 * covers 7 x 9 = 63 tiles. The centre tile at zoom 8 is (67, 97),
 * computed independently (not by calling the functions under test).
 */
class RadarTilesTest {

    private val centerLat = 39.1
    private val centerLon = -84.5
    private val zoom = 8
    private val tilesX = 6
    private val tilesY = 9

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
    fun testSatelliteZoomLevel_capsAtMaxMatrixNotLevelCount() {
        // Level7 holds matrices 0..6, Level6 holds 0..5: the cap is the
        // max matrix, not the set's level count (7 / 6 404s/400s).
        assertEquals(6, satelliteZoomLevel(isVisible = true, zoom = 8))
        assertEquals(5, satelliteZoomLevel(isVisible = false, zoom = 8))
        assertEquals(6, satelliteZoomLevel(isVisible = true, zoom = 7))
        assertEquals(5, satelliteZoomLevel(isVisible = false, zoom = 7))
        assertEquals(6, satelliteZoomLevel(isVisible = true, zoom = 9))
        assertEquals(5, satelliteZoomLevel(isVisible = false, zoom = 9))
    }

    @Test
    fun testIemTileUrl_format() {
        assertEquals(
            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/8/67/97.png",
            iemTileUrl("nexrad-n0q-900913", 8, 67, 97)
        )
    }

    @Test
    fun testPrefetchUrls_tenFrames() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        assertEquals(10, frames.size)
    }

    @Test
    fun testPrefetchUrls_eachFrameCoversTheGrid() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        // 7 wide (dx -3..3) x 9 tall (dy -4..4) = 63 tiles per frame.
        for ((index, urls) in frames.withIndex()) {
            assertEquals("frame $index", 63, urls.size)
        }
    }

    @Test
    fun testPrefetchUrls_allTenLayersPresent() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        val layers = frames.map { urls ->
            urls.first().substringAfter("/1.0.0/").substringBefore("/$zoom/")
        }.toSet()
        assertEquals((0..9).map { iemLayerNameFor(it) }.toSet(), layers)
    }

    @Test
    fun testPrefetchUrls_centreTilePresentInEveryFrame() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        for ((index, urls) in frames.withIndex()) {
            val expected = iemTileUrl(iemLayerNameFor(index), zoom, 67, 97)
            assertTrue("frame $index must contain its centre tile", urls.contains(expected))
        }
    }

    @Test
    fun testPrefetchUrls_noDuplicatesWithinAFrame() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        for ((index, urls) in frames.withIndex()) {
            assertEquals("frame $index", urls.size, urls.toSet().size)
        }
    }

    @Test
    fun testPrefetchUrls_iemHostAndZoom() {
        val frames = radarPrefetchUrls(centerLat, centerLon, zoom, tilesX, tilesY)
        for (urls in frames) {
            for (url in urls) {
                assertTrue(url, url.startsWith("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/"))
                assertTrue(url, url.contains("/$zoom/"))
            }
        }
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

    @Test
    fun testLookAheadFrameIndices_noWrapMidRange() {
        // Default look-ahead is 5 (radar smoothness fix, 2026-11-14).
        assertEquals(listOf(11, 12, 13, 14, 15), lookAheadFrameIndices(10, 75))
    }

    @Test
    fun testLookAheadFrameIndices_explicitAheadThree() {
        // The old 3-frame window is still available explicitly.
        assertEquals(listOf(11, 12, 13), lookAheadFrameIndices(10, 75, ahead = 3))
    }

    @Test
    fun testLookAheadFrameIndices_wrapsAtLastFrame() {
        // At the last frame the next five are 0, 1, 2, 3, 4 (L-4: frame 0
        // must be prefetched before the loop wraps onto it).
        assertEquals(listOf(0, 1, 2, 3, 4), lookAheadFrameIndices(74, 75))
    }

    @Test
    fun testLookAheadFrameIndices_wrapsAtLastMinusOne() {
        assertEquals(listOf(74, 0, 1, 2, 3), lookAheadFrameIndices(73, 75))
    }

    @Test
    fun testLookAheadFrameIndices_wrapsAtLastMinusTwo() {
        assertEquals(listOf(73, 74, 0, 1, 2), lookAheadFrameIndices(72, 75))
    }

    @Test
    fun testLookAheadFrameIndices_wrapsLiveLoop() {
        assertEquals(listOf(0, 1, 2, 3, 4), lookAheadFrameIndices(9, 10))
    }

    @Test
    fun testLookAheadFrameIndices_singleFrameIsEmpty() {
        // A 1-frame loop has no frame ahead of itself to prefetch.
        assertEquals(emptyList<Int>(), lookAheadFrameIndices(0, 1))
    }

    @Test
    fun testLookAheadFrameIndices_twoFramesAlternates() {
        assertEquals(listOf(1), lookAheadFrameIndices(0, 2))
        assertEquals(listOf(0), lookAheadFrameIndices(1, 2))
    }

    @Test
    fun testLookAheadFrameIndices_emptyRangeIsEmpty() {
        assertEquals(emptyList<Int>(), lookAheadFrameIndices(0, 0))
    }

    // AUDIT6 §9 Fixes 2-3: loop gating (hold the previous frame until
    // the next is ready), dwell, and the honest preload cost.

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
    fun testIsFrameSettled_allTiles() {
        assertTrue(isFrameSettled(77, 77))
    }

    @Test
    fun testIsFrameSettled_notYet() {
        assertFalse(isFrameSettled(76, 77))
        assertFalse(isFrameSettled(0, 77))
    }

    @Test
    fun testIsFrameSettled_noTilesDrawnIsNeverReady() {
        // The grid has not reported its size yet: 0/0 must not read
        // as ready, or the loop would advance on an empty grid.
        assertFalse(isFrameSettled(0, 0))
    }

    @Test
    fun testIsFrameSettled_overSettledStillReady() {
        // Defensive: a double-counted tile must not wedge readiness.
        assertTrue(isFrameSettled(78, 77))
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
    fun testLoopWaitTimeout_unchangedTwoSeconds() {
        // The 2 s timeout is the fallback bound, not the bottleneck:
        // it must stay 2 s (AUDIT6 §9: do not raise it).
        assertEquals(2000L, RADAR_LOOP_WAIT_TIMEOUT_MS)
        assertEquals(750L, RADAR_LOOP_DWELL_MS)
        assertEquals(2250L, RADAR_LOOP_END_PAUSE_MS)
    }

    @Test
    fun testForecastSlowWait_isTwentySeconds() {
        // Fix 12: the slow window is 20 s — a healthy cold frame
        // needs ~16 s wall for all 77 tiles, so 20 s is past a
        // healthy full load, not inside it.
        assertEquals(20000L, FORECAST_SLOW_WAIT_MS)
        assertEquals(0.25, FORECAST_SLOW_MIN_SETTLED_FRACTION, 0.0)
    }

    @Test
    fun testIsForecastSlow_firesWhenTooLittleSettledAfterWait() {
        assertTrue(isForecastSlow(0, 77, 20000L))
        assertTrue(isForecastSlow(10, 77, 20000L))
        // 19 of 77 is below the 25% line (19.25 tiles).
        assertTrue(isForecastSlow(19, 77, 20000L))
        assertTrue(isForecastSlow(0, 77, 60000L))
    }

    @Test
    fun testIsForecastSlow_doesNotFireWhileHealthy() {
        // 20 of 77 is above the 25% line; a healthy frame is ~90%
        // settled by 20 s, so none of these must fire.
        assertFalse(isForecastSlow(20, 77, 20000L))
        assertFalse(isForecastSlow(38, 77, 20000L))
        assertFalse(isForecastSlow(70, 77, 20000L))
        assertFalse(isForecastSlow(77, 77, 20000L))
    }

    @Test
    fun testIsForecastSlow_doesNotFireBeforeWait() {
        // Nothing settled yet at 5 s is normal on a cold start —
        // the burst is still draining — not an unavailable state.
        assertFalse(isForecastSlow(0, 77, 5000L))
        assertFalse(isForecastSlow(0, 77, 19999L))
    }

    @Test
    fun testIsForecastSlow_noTilesIsNeverSlow() {
        // The grid has not reported yet: 0/0 must not read as slow.
        assertFalse(isForecastSlow(0, 0, 60000L))
    }

    @Test
    fun testSettledUrlCount_countsOnlySettled() {
        val urls = listOf("a", "b", "c")
        assertEquals(2, settledUrlCount(urls, setOf("a", "c")))
        assertEquals(0, settledUrlCount(urls, emptySet()))
        assertEquals(0, settledUrlCount(emptyList(), setOf("a")))
    }

    @Test
    fun testLoopPreloadCostText_honestCosts() {
        assertEquals("FIRST LOAD ABOUT 25 MB THEN CACHED", loopPreloadCostText(RadarRange.Live))
        assertEquals("FIRST LOAD ABOUT 3.5 MB THEN CACHED", loopPreloadCostText(RadarRange.Future24h))
        assertEquals("FIRST LOAD ABOUT 5 MB THEN CACHED", loopPreloadCostText(RadarRange.Future48h))
    }

    @Test
    fun testLoopPreloadCostText_star4000SafeCharsOnly() {
        for (range in RadarRange.entries) {
            val text = loopPreloadCostText(range)
            assertTrue(text, text.matches(Regex("^[A-Z0-9 .]+$")))
        }
    }

    @Test
    fun testSatelliteOverlayCap_isTwelveSeconds() {
        // The cap value is unchanged from the old per-switch cap —
        // Fix 9 fixes only the restart, not the duration.
        assertEquals(12000L, SATELLITE_OVERLAY_CAP_MS)
    }

    @Test
    fun testSatelliteCapRemainingMs_fullAtVisitStart() {
        assertEquals(12000L, satelliteCapRemainingMs(1000L, 1000L))
    }

    @Test
    fun testSatelliteCapRemainingMs_countsDownFromVisitStart() {
        assertEquals(7000L, satelliteCapRemainingMs(1000L, 6000L))
        assertEquals(1L, satelliteCapRemainingMs(1000L, 12999L))
        assertEquals(0L, satelliteCapRemainingMs(1000L, 13000L))
    }

    @Test
    fun testSatelliteCapRemainingMs_zeroWhenElapsedOrNeverStarted() {
        // Past the cap: no delay, the overlay clears at once.
        assertEquals(0L, satelliteCapRemainingMs(1000L, 50000L))
        // No visit started: the cap effect returns early.
        assertEquals(0L, satelliteCapRemainingMs(0L, 5000L))
    }

    @Test
    fun testIsSatelliteFrameSettled_allTiles() {
        assertTrue(isSatelliteFrameSettled(77, 77))
        // Defensive: a double-counted tile must not wedge readiness.
        assertTrue(isSatelliteFrameSettled(78, 77))
    }

    @Test
    fun testIsSatelliteFrameSettled_notYet() {
        assertFalse(isSatelliteFrameSettled(76, 77))
        assertFalse(isSatelliteFrameSettled(0, 77))
        // No tiles drawn yet is never ready.
        assertFalse(isSatelliteFrameSettled(0, 0))
    }

    @Test
    fun testSatelliteCostText_honestCosts() {
        assertEquals("SATELLITE USES MORE DATA. ABOUT 6 MB PER SWITCH.", satelliteCostText(true))
        assertEquals("SATELLITE USES MORE DATA. ABOUT 5 MB PER SWITCH.", satelliteCostText(false))
    }

    @Test
    fun testSatelliteCostText_star4000SafeCharsOnly() {
        for (isVisible in listOf(true, false)) {
            val text = satelliteCostText(isVisible)
            assertTrue(text, text.matches(Regex("^[A-Z0-9 .]+$")))
        }
    }

    @Test
    fun testRadarFrameUrls_singleLayerGrid() {
        val urls = radarFrameUrls("ridge::USCOMP-N0Q-202611100040", 38.78, -84.61, 8, 3, 3)
        // 3x3 grid -> (2*1+1)^2 = 9 tiles, all for the one layer.
        assertEquals(9, urls.size)
        assertTrue(urls.all { it.contains("ridge::USCOMP-N0Q-202611100040") })
        assertTrue(urls.all { it.startsWith("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/") })
    }

    @Test
    fun testGibsTileUrl_visible() {
        // GeoColor visible: Level7 matrix set, timestamp in the path.
        assertEquals(
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/2026-11-14T02:30:00Z/GoogleMapsCompatible_Level7/6/93/64.png",
            gibsTileUrl(true, "2026-11-14T02:30:00Z", 6, 64, 93)
        )
    }

    @Test
    fun testGibsTileUrl_infrared() {
        // Band13 clean IR: Level6 matrix set.
        assertEquals(
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_Band13_Clean_Infrared/default/2026-11-14T02:30:00Z/GoogleMapsCompatible_Level6/5/93/64.png",
            gibsTileUrl(false, "2026-11-14T02:30:00Z", 5, 64, 93)
        )
    }

    @Test
    fun testSatelliteTileUrls_visibleGrid() {
        // Same 6x9 phone grid as the radar prefetch: 7 x 9 = 63 tiles,
        // centred on tile (67, 97) at zoom 8, first tile (64, 93).
        val urls = satelliteTileUrls(true, "2026-11-14T02:30:00Z", centerLat, centerLon, zoom, tilesX, tilesY)
        assertEquals(63, urls.size)
        assertTrue(urls.all { it.contains("GOES-East_ABI_GeoColor") })
        assertTrue(urls.all { it.contains("GoogleMapsCompatible_Level7") })
        assertTrue(urls.all { it.contains("2026-11-14T02:30:00Z") })
        assertEquals(
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/default/2026-11-14T02:30:00Z/GoogleMapsCompatible_Level7/8/93/64.png",
            urls.first()
        )
    }

    @Test
    fun testSatelliteTileUrls_infraredGrid() {
        val urls = satelliteTileUrls(false, "2026-11-14T02:30:00Z", centerLat, centerLon, 5, tilesX, tilesY)
        assertEquals(63, urls.size)
        assertTrue(urls.all { it.contains("GOES-East_ABI_Band13_Clean_Infrared") })
        assertTrue(urls.all { it.contains("GoogleMapsCompatible_Level6") })
        assertTrue(urls.all { it.contains("/5/") })
    }

    @Test
    fun testSnappedTileSpan_integerStepIsExact() {
        // Whole-pixel step: every tile is exactly the step wide.
        assertEquals(100 to 256, snappedTileSpan(100f, 0, 256f))
        assertEquals(356 to 256, snappedTileSpan(100f, 1, 256f))
        assertEquals(-156 to 256, snappedTileSpan(100f, -1, 256f))
    }

    @Test
    fun testSnappedTileSpan_fractionalStepSharesEdges() {
        // Pinch-zoom step (256 * 1.03): consecutive spans must share
        // edges exactly — the right edge of one is the left edge of
        // the next — with widths of 263 or 264, never a 1px gap.
        val step = 263.68f
        for (i in -5..20) {
            val (left, width) = snappedTileSpan(17.3f, i, step)
            val (nextLeft, _) = snappedTileSpan(17.3f, i + 1, step)
            assertEquals(nextLeft, left + width)
            assertTrue(width == 263 || width == 264)
        }
    }

    @Test
    fun testSnappedTileSpan_absoluteTileCoords() {
        // Absolute tile indices (zoom 8 centre tile ~68) with a
        // fractional origin: widths stay the step, no drift.
        val (left, width) = snappedTileSpan(-1234.25f, 68, 256f)
        assertEquals(16174, left)
        assertEquals(256, width)
        val (left2, width2) = snappedTileSpan(-1234.25f, 69, 256f)
        assertEquals(left + width, left2)
        assertEquals(256, width2)
    }

    // ------------------------------------------------------------------
    // AUDIT6 §9 Fix 4 — viewport-driven tile manager.
    //
    // The screen is 1080x1920 px, tile 256 px, zoom 8, centred on
    // Cincinnati (39.1, -84.5). The map origin (screen position of
    // world 0,0) is (-16845.24, -24063.05), computed independently in
    // Python from the Mercator projection — not by calling the code
    // under test. Visible tiles are X 65..70, Y 93..101; with the
    // 1-tile margin the range is X 64..71, Y 92..102 (8 x 11 = 88).
    // ------------------------------------------------------------------

    private val screenW = 1080f
    private val screenH = 1920f
    private val originX = -16845.24f
    private val originY = -24063.05f

    @Test
    fun testViewportTileRange_unpannedPhoneGrid() {
        val range = viewportTileRange(originX, originY, screenW, screenH, 256f, zoom)
        assertEquals(TileRange(64, 71, 92, 102), range)
        assertEquals(8, range.width)
        assertEquals(11, range.height)
        assertEquals(88, range.tileCount)
        assertTrue(range.contains(67, 97))
    }

    @Test
    fun testViewportTileRange_panRightShiftsRangeLeft() {
        // Panning the map 256 px right (one tile) moves the viewport
        // one tile left in world coordinates: the range must follow.
        val panned = viewportTileRange(originX + 256f, originY, screenW, screenH, 256f, zoom)
        assertEquals(TileRange(63, 70, 92, 102), panned)
    }

    @Test
    fun testViewportTileRange_panLeftShiftsRangeRight() {
        val panned = viewportTileRange(originX - 300f, originY, screenW, screenH, 256f, zoom)
        assertEquals(TileRange(65, 72, 92, 102), panned)
    }

    @Test
    fun testViewportTileRange_noMarginIsExactlyVisible() {
        val range = viewportTileRange(originX, originY, screenW, screenH, 256f, zoom, marginTiles = 0)
        assertEquals(TileRange(65, 70, 93, 101), range)
    }

    @Test
    fun testViewportTileRange_marginTwoIsCappedByGridCap() {
        // Margin 2 would be 10 wide x 13 tall; the 9 x 11 grid cap
        // trims it back around the viewport centre tile (67, 97).
        val range = viewportTileRange(originX, originY, screenW, screenH, 256f, zoom, marginTiles = 2)
        assertEquals(TileRange(63, 71, 92, 102), range)
        assertEquals(VIEWPORT_MAX_TILES_X, range.width)
        assertEquals(VIEWPORT_MAX_TILES_Y, range.height)
    }

    @Test
    fun testViewportTileRange_velocityAddsOneTileInPanDirection() {
        // Shorter screen (1080x1200) so the height is below the cap
        // and the velocity tile is visible: visible Y 93..98, margin
        // 1 gives 92..99; panning down adds one more row, 92..100.
        val base = viewportTileRange(originX, originY, screenW, 1200f, 256f, zoom)
        assertEquals(TileRange(64, 71, 92, 99), base)
        val down = viewportTileRange(originX, originY, screenW, 1200f, 256f, zoom, velocityDy = 1)
        assertEquals(TileRange(64, 71, 92, 100), down)
        val up = viewportTileRange(originX, originY, screenW, 1200f, 256f, zoom, velocityDy = -1)
        assertEquals(TileRange(64, 71, 91, 99), up)
        val right = viewportTileRange(originX, originY, screenW, 1200f, 256f, zoom, velocityDx = 1)
        assertEquals(TileRange(64, 72, 92, 99), right)
    }

    @Test
    fun testViewportTileRange_hugeScreenIsCapped() {
        // A 10000 px screen sees tiles X 65..104, Y 93..133; the cap
        // trims to 9 x 11 around the viewport centre tile (84, 113).
        val range = viewportTileRange(originX, originY, 10000f, 10000f, 256f, zoom)
        assertEquals(TileRange(80, 88, 108, 118), range)
        assertEquals(VIEWPORT_MAX_TILES_X, range.width)
        assertEquals(VIEWPORT_MAX_TILES_Y, range.height)
    }

    @Test
    fun testViewportTileRange_defaultMarginIsOne() {
        assertEquals(1, VIEWPORT_TILE_MARGIN)
        assertEquals(300L, PAN_SETTLE_DELAY_MS)
    }

    @Test
    fun testCentreTileRange_matchesOldGrid() {
        // The old centre-anchored grid: tilesX 6, tilesY 9 around
        // centre tile (67, 97) is X 64..70, Y 93..101.
        assertEquals(TileRange(64, 70, 93, 101), centreTileRange(67, 97, 6, 9))
    }

    @Test
    fun testRadarFrameUrlsForRange_matchesCentreVersion() {
        val range = centreTileRange(67, 97, tilesX, tilesY)
        val viaRange = radarFrameUrlsForRange("nexrad-n0q-900913", zoom, range)
        val viaCentre = radarFrameUrls("nexrad-n0q-900913", centerLat, centerLon, zoom, tilesX, tilesY)
        assertEquals(viaCentre, viaRange)
        assertEquals(63, viaRange.size)
        assertEquals(63, viaRange.toSet().size)
    }

    @Test
    fun testRadarPrefetchUrlsForRange_tenFramesSameGrid() {
        val range = centreTileRange(67, 97, tilesX, tilesY)
        val frames = radarPrefetchUrlsForRange(zoom, range)
        assertEquals(10, frames.size)
        for (urls in frames) assertEquals(63, urls.size)
    }

    @Test
    fun testSatelliteTileUrlsForRange_matchesCentreVersion() {
        val range = centreTileRange(67, 97, tilesX, tilesY)
        val viaRange = satelliteTileUrlsForRange(true, "2026-11-14T02:30:00Z", zoom, range)
        val viaCentre = satelliteTileUrls(true, "2026-11-14T02:30:00Z", centerLat, centerLon, zoom, tilesX, tilesY)
        assertEquals(viaCentre, viaRange)
    }

    @Test
    fun testParentTileFor_quadrants() {
        // Tile (67, 97) at zoom 8 sits in the bottom-right quadrant
        // of parent (33, 48) at zoom 7; (68, 96) is top-left of
        // parent (34, 48).
        assertEquals(ParentTile(33, 48, 7, 1, 1), parentTileFor(67, 97, 8))
        assertEquals(ParentTile(34, 48, 7, 0, 0), parentTileFor(68, 96, 8))
        assertEquals(ParentTile(0, 0, 4, 0, 0), parentTileFor(0, 0, 5))
        assertEquals(ParentTile(0, 0, 4, 1, 1), parentTileFor(1, 1, 5))
    }

    @Test
    fun testParentTileFor_noParentAtZoomZero() {
        assertEquals(null, parentTileFor(0, 0, 0))
    }

    @Test
    fun testIsTileSettled_urlSetReadiness() {
        val urls = listOf("a", "b", "c")
        assertTrue(isTileSettled(urls, setOf("a", "b", "c")))
        assertTrue(isTileSettled(urls, setOf("a", "b", "c", "d")))
        // One newly visible tile still in flight: not ready — this is
        // the pan case a per-frame count gets wrong.
        assertFalse(isTileSettled(urls, setOf("a", "b")))
        assertFalse(isTileSettled(urls, emptySet()))
        assertFalse(isTileSettled(emptyList(), setOf("a")))
    }

    @Test
    fun testPanDirection_thresholdAndSign() {
        assertEquals(0, panDirection(0f))
        assertEquals(0, panDirection(5f))
        assertEquals(0, panDirection(PAN_DIRECTION_THRESHOLD_PX))
        assertEquals(1, panDirection(9f))
        assertEquals(1, panDirection(500f))
        assertEquals(-1, panDirection(-9f))
        assertEquals(-1, panDirection(-500f))
    }
}
