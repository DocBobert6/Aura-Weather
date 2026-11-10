package com.example

import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.RadarRange
import com.example.data.mapper.hrrrLayerName
import com.example.data.mapper.iemTileUrl
import com.example.data.mapper.pastMosaicLayerName
import com.example.data.mapper.radarFrameLabel
import com.example.data.mapper.radarFrameUrls
import com.example.data.mapper.radarRangeFrames
import com.example.data.mapper.latToTileY
import com.example.data.mapper.lonToTileX
import com.example.data.mapper.radarPrefetchUrls
import com.example.data.mapper.satelliteZoomLevel
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
    fun testPastMosaicLayerName_format() {
        val t = java.time.Instant.parse("2026-11-10T00:40:00Z")
        assertEquals("ridge::USCOMP-N0Q-202611100040", pastMosaicLayerName(t))
    }

    @Test
    fun testHrrrLayerName_forecastMinutesNotHours() {
        // FXXXX is the forecast MINUTE: 6 hours out is F0360, not F006.
        val run = java.time.Instant.parse("2026-11-09T22:00:00Z")
        assertEquals("hrrr::REFD-F0000-202611092200", hrrrLayerName(run, 0))
        assertEquals("hrrr::REFD-F0360-202611092200", hrrrLayerName(run, 360))
        assertEquals("hrrr::REFD-F2880-202611092200", hrrrLayerName(run, 2880))
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
    fun testRadarRangeFrames_past24hIs49FramesEvery30Min() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        val frames = radarRangeFrames(RadarRange.Past24h, now)
        assertEquals(49, frames.size)
        assertEquals("NOW", frames[48].label)
        assertEquals("24H AGO", frames[0].label)
        // Every frame is on a 5-minute mosaic boundary and 30 min apart.
        assertTrue(frames.all { it.layerName.startsWith("ridge::USCOMP-N0Q-") })
        val step = java.time.Duration.between(frames[0].validTime, frames[1].validTime).toMinutes()
        assertEquals(30L, step)
        val lastMinute = frames[48].validTime.atZone(java.time.ZoneOffset.UTC).minute
        assertEquals(0, lastMinute % 5)
    }

    @Test
    fun testRadarRangeFrames_past48hAnd72hCounts() {
        val now = java.time.Instant.parse("2026-11-10T01:00:00Z")
        assertEquals(49, radarRangeFrames(RadarRange.Past48h, now).size)
        assertEquals(73, radarRangeFrames(RadarRange.Past72h, now).size)
        assertEquals("48H AGO", radarRangeFrames(RadarRange.Past48h, now)[0].label)
        assertEquals("72H AGO", radarRangeFrames(RadarRange.Past72h, now)[0].label)
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
    fun testRadarFrameUrls_singleLayerGrid() {
        val urls = radarFrameUrls("ridge::USCOMP-N0Q-202611100040", 38.78, -84.61, 8, 3, 3)
        // 3x3 grid -> (2*1+1)^2 = 9 tiles, all for the one layer.
        assertEquals(9, urls.size)
        assertTrue(urls.all { it.contains("ridge::USCOMP-N0Q-202611100040") })
        assertTrue(urls.all { it.startsWith("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/") })
    }
}
