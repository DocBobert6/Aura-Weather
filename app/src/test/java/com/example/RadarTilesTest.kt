package com.example

import com.example.data.mapper.iemLayerNameFor
import com.example.data.mapper.iemTileUrl
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
}
