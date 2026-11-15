package com.example

import com.example.data.mapper.LIBRE_WXR_TILE_SIZE
import com.example.data.mapper.centreFirstTileOffsets
import com.example.data.mapper.libreWxrTileTemplate
import com.example.data.mapper.libreWxrTileUrl
import com.example.ui.radar.engine.RADAR_ENGINE_CENTER_LAT
import com.example.ui.radar.engine.RADAR_ENGINE_CENTER_LON
import com.example.ui.radar.engine.RADAR_ENGINE_LIVE_SLOW_BUDGET_MS
import com.example.ui.radar.engine.RADAR_ENGINE_MRMS_BBOX_1X
import com.example.ui.radar.engine.RADAR_ENGINE_PAN_MARGIN_DEGREES
import com.example.ui.radar.engine.mrmsBboxForCentre
import com.example.ui.radar.engine.panPastMargin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the Phase 2 wiring helpers (the activity/fetcher
 * consume them; the fetcher and MapLibre wiring themselves are
 * CI-verified, not pure-tested).
 *
 * Ground truth:
 * - The LibreWXR 256 example tile URL is the one verified 200 live
 *   2026-11-15: https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png
 * - The 512-at-zoom−1 equivalence (one 512 z7 tile = the four 256 z8
 *   children, MAD 0.0) is Phase 0 spike 5 (RADAR_PHASE0_RESULTS.md).
 * - The pan margin (0.5°) sits inside the tightest slack the 1.5×
 *   MRMS image gives over the chrome-adjusted viewport (~0.65°
 *   vertically at zoom 7) — see RADAR_ENGINE_PAN_MARGIN_DEGREES.
 */
class RadarLoopWiringTest {

    // -- LibreWXR 512 tile URLs ------------------------------------------

    @Test
    fun testLibreWxrTileUrl_defaultIs256() {
        val url = libreWxrTileUrl("https://api.librewxr.net", "/v2/radar/1794716400", 6, 16, 24)
        assertEquals(
            "https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png",
            url
        )
    }

    @Test
    fun testLibreWxrTileUrl_512Size() {
        val url = libreWxrTileUrl(
            "https://api.librewxr.net", "/v2/radar/1794716400", 7, 16, 24,
            tileSizePx = 512
        )
        assertEquals(
            "https://api.librewxr.net/v2/radar/1794716400/512/7/16/24/2/1_1.png",
            url
        )
    }

    @Test
    fun testLibreWxrTileUrl_explicit256MatchesDefault() {
        val host = "https://api.librewxr.net"
        val path = "/v2/radar/1794716400"
        assertEquals(
            libreWxrTileUrl(host, path, 8, 67, 98),
            libreWxrTileUrl(host, path, 8, 67, 98, tileSizePx = LIBRE_WXR_TILE_SIZE)
        )
    }

    @Test
    fun testLibreWxrTileTemplate_defaultIs512() {
        assertEquals(
            "https://api.librewxr.net/v2/radar/1794716400/512/{z}/{x}/{y}/2/1_1.png",
            libreWxrTileTemplate("https://api.librewxr.net", "/v2/radar/1794716400")
        )
    }

    @Test
    fun testLibreWxrTileTemplate_explicit256() {
        assertEquals(
            "https://api.librewxr.net/v2/radar/1794716400/256/{z}/{x}/{y}/2/1_1.png",
            libreWxrTileTemplate(
                "https://api.librewxr.net", "/v2/radar/1794716400",
                tileSizePx = LIBRE_WXR_TILE_SIZE
            )
        )
    }

    // -- Centre-first tile ordering ---------------------------------------

    @Test
    fun testCentreFirstTileOffsets_9x11SizeAndCentreFirst() {
        val offsets = centreFirstTileOffsets(9, 11)
        assertEquals(9 * 11, offsets.size)
        assertEquals(0 to 0, offsets[0])
        // The 3x3 centre block (Chebyshev distance <= 1) comes first:
        // (0,0) then the 8 neighbours, in a deterministic order.
        val firstNine = offsets.take(9).toSet()
        val expectedCentreBlock = setOf(
            0 to 0, -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1
        )
        assertEquals(expectedCentreBlock, firstNine)
    }

    @Test
    fun testCentreFirstTileOffsets_ringsAreNonDecreasing() {
        val offsets = centreFirstTileOffsets(9, 11)
        val rings = offsets.map { maxOf(kotlin.math.abs(it.first), kotlin.math.abs(it.second)) }
        for (i in 1 until rings.size) {
            assertTrue(rings[i] >= rings[i - 1])
        }
        // The last entries are the grid's corners (Chebyshev distance 5).
        assertEquals(5, rings.last())
        assertTrue(offsets.takeLast(4).toSet().contains(4 to 5))
    }

    @Test
    fun testCentreFirstTileOffsets_5x6LibreWxrGrid() {
        // The LibreWXR 512 proving grid: 5 wide x 7 tall = 35 tiles.
        val offsets = centreFirstTileOffsets(5, 6)
        assertEquals(35, offsets.size)
        assertEquals(0 to 0, offsets[0])
        assertTrue(offsets.contains(2 to 3))
        assertTrue(offsets.contains(-2 to -3))
    }

    @Test
    fun testCentreFirstTileOffsets_coversEveryCellOnce() {
        val offsets = centreFirstTileOffsets(5, 5)
        assertEquals(25, offsets.toSet().size)
        for (dx in -2..2) {
            for (dy in -2..2) {
                assertTrue(offsets.contains(dx to dy))
            }
        }
    }

    // -- Pan margin --------------------------------------------------------

    @Test
    fun testPanPastMargin_atCentreIsFalse() {
        assertFalse(panPastMargin(39.1, -84.5, 39.1, -84.5))
    }

    @Test
    fun testPanPastMargin_smallPanIsFalse() {
        assertFalse(panPastMargin(39.1, -84.5, 39.5, -84.5))
        assertFalse(panPastMargin(39.1, -84.5, 39.1, -84.1))
        assertFalse(panPastMargin(39.1, -84.5, 38.7, -84.9))
    }

    @Test
    fun testPanPastMargin_exactlyAtMarginIsFalse() {
        // Strictly past the margin counts; exactly on it does not.
        assertFalse(panPastMargin(39.1, -84.5, 39.1 + RADAR_ENGINE_PAN_MARGIN_DEGREES, -84.5))
    }

    @Test
    fun testPanPastMargin_pastMarginIsTrue() {
        assertTrue(panPastMargin(39.1, -84.5, 39.1 + 0.6, -84.5))
        assertTrue(panPastMargin(39.1, -84.5, 39.1 - 0.6, -84.5))
        assertTrue(panPastMargin(39.1, -84.5, 39.1, -84.5 + 0.6))
        assertTrue(panPastMargin(39.1, -84.5, 39.1, -84.5 - 0.6))
    }

    // -- MRMS bbox around a centre -----------------------------------------

    @Test
    fun testMrmsBboxForCentre_cincinnatiMatchesPhase1Constant() {
        assertEquals(
            RADAR_ENGINE_MRMS_BBOX_1X,
            mrmsBboxForCentre(RADAR_ENGINE_CENTER_LAT, RADAR_ENGINE_CENTER_LON)
        )
    }

    @Test
    fun testMrmsBboxForCentre_is6x5AroundCentre() {
        val bbox = mrmsBboxForCentre(38.0, -85.0)
        assertEquals(-88.0, bbox.westLon, 0.00001)
        assertEquals(35.5, bbox.southLat, 0.00001)
        assertEquals(-82.0, bbox.eastLon, 0.00001)
        assertEquals(40.5, bbox.northLat, 0.00001)
    }

    // -- Live slow budget --------------------------------------------------

    @Test
    fun testLiveSlowBudget_is20s() {
        // 20 s: 3x past a healthy full MRMS loop load (~6 s wall),
        // matching the forecast trigger's 20 s precedent.
        assertEquals(20_000L, RADAR_ENGINE_LIVE_SLOW_BUDGET_MS)
    }
}
