package com.example

import com.example.data.mapper.gibsTileUrl
import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.lookAheadFrameIndices
import com.example.data.mapper.RadarRange
import com.example.data.mapper.hrrrLayerName
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarFrameUrls
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.latToTileY
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.radarPrefetchUrls
import com.example.data.mapper.satelliteTileUrls
import com.example.data.mapper.satelliteZoomLevel
import com.example.data.mapper.snappedTileSpan
import org.junit.Assert.assertEquals
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
}
