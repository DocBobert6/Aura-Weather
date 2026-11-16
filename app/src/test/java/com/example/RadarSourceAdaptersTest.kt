package com.example

import com.example.data.mapper.DEFAULT_RADAR_SOURCE_ORDER
import com.example.data.mapper.LibreWxrFrame
import com.example.data.mapper.LibreWxrMaps
import com.example.data.mapper.LonLatBbox
import com.example.data.mapper.RadarSourceKind
import com.example.data.mapper.fallbackAfter
import com.example.data.mapper.libreWxrLoopFrames
import com.example.data.mapper.libreWxrTileUrl
import com.example.data.mapper.mrmsExportImageUrl
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.mrmsFrameTimesFromTimeExtent
import com.example.data.mapper.parseLibreWxrMaps
import com.example.data.mapper.parseMrmsTimeExtent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the AUDIT6 §9 fix 6 / fix 7 adapters (MrmsTiles,
 * LibreWxrTiles, RadarSources). No Android, no Compose, no Moshi, no
 * Retrofit — plain JUnit, same style as RadarTilesTest.
 *
 * NOTE: run-pure-tests-audit6.sh has a hardcoded file list and does
 * NOT pick this class up yet — the coordinator must extend the script
 * (see the adapter report). Run this class directly with kotlinc +
 * JUnitCore until then.
 *
 * Ground truth for the numbers below:
 * - MRMS timeExtent [1794709257000, 1794715977000] and the
 *   exportImage probe come from hrrr-reprobe-20261115.json
 *   (2026-11-15 ~04:30 UTC) and the fix-8 re-probe note.
 * - The LibreWXR example tile URL is the one verified 200 in that
 *   note: https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png
 * - Crittenden KY (38.7833, -84.6027) is the probe centre: tile
 *   (67, 98) at zoom 8, (16, 24) at zoom 6.
 */
class RadarSourceAdaptersTest {

    // -- Fallback ordering (RadarSources) ----------------------------------

    @Test
    fun testDefaultOrder_mrmsFirstLibreWxrSecondIemLast() {
        assertEquals(
            listOf(RadarSourceKind.MRMS, RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM),
            DEFAULT_RADAR_SOURCE_ORDER
        )
    }

    @Test
    fun testFallbackAfter_walksTheOrder() {
        assertEquals(RadarSourceKind.LIBRE_WXR, fallbackAfter(RadarSourceKind.MRMS))
        assertEquals(RadarSourceKind.IEM, fallbackAfter(RadarSourceKind.LIBRE_WXR))
        assertNull(fallbackAfter(RadarSourceKind.IEM))
    }

    // -- MRMS (MrmsTiles) ---------------------------------------------------

    @Test
    fun testMrmsExportImageUrl_probedShape() {
        // The Crittenden KY bbox at zoom 8, 6x11 grid, no margin
        // (tiles X 64..71, Y 93..104) — the same bbox the deleted
        // viewportBboxForTileGrid helper built for this test.
        val bbox = LonLatBbox(
            westLon = -90.0,
            southLat = 31.952162238024968,
            eastLon = -80.15625,
            northLat = 44.087585028245165
        )
        val url = mrmsExportImageUrl(bbox, 1794715977000L, 512, 512)
        assertTrue(url.startsWith("https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity_time/ImageServer/exportImage?"))
        assertTrue(url.contains("bboxSR=4326"))
        assertTrue(url.contains("size=512,512"))
        assertTrue(url.contains("imageSR=3857"))
        assertTrue(url.contains("format=png32"))
        assertTrue(url.contains("time=1794715977000"))
        assertTrue(url.contains("f=image"))
        // The bbox in the URL is the one built above.
        assertTrue(url.contains("bbox=${bbox.westLon},${bbox.southLat},${bbox.eastLon},${bbox.northLat}"))
    }

    @Test
    fun testParseMrmsTimeExtent_probedDocument() {
        val json = "{\"timeInfo\":{\"timeExtent\":[1794709257000,1794715977000]}}"
        assertEquals(1794709257000L to 1794715977000L, parseMrmsTimeExtent(json))
    }

    @Test
    fun testParseMrmsTimeExtent_missingExtentIsNull() {
        assertNull(parseMrmsTimeExtent("{\"timeInfo\":{}}"))
        assertNull(parseMrmsTimeExtent("{}"))
    }

    @Test
    fun testMrmsFrameTimes_probedExtentIs22FiveMinuteFrames() {
        val frames = mrmsFrameTimesFromTimeExtent(1794709257000L, 1794715977000L)
        assertEquals(22, frames.size)
        assertEquals(1794709500000L, frames.first())
        assertEquals(1794715800000L, frames.last())
        for (i in 1 until frames.size) {
            assertEquals(300000L, frames[i] - frames[i - 1])
        }
    }

    @Test
    fun testMrmsFrameTimes_alignedStartCounts() {
        // Start exactly on a 5-min boundary: first frame is the start
        // itself, then every 300,000 ms up to the end, inclusive.
        assertEquals(
            listOf(300000000L, 300300000L, 300600000L),
            mrmsFrameTimesFromTimeExtent(300000000L, 300600000L)
        )
    }

    @Test
    fun testMrmsFrameTimes_invertedExtentIsEmpty() {
        assertTrue(mrmsFrameTimesFromTimeExtent(2000L, 1000L).isEmpty())
        assertTrue(mrmsFrameTimesFromServiceInfoJson("{\"nope\":true}").isEmpty())
    }

    // -- LibreWXR (LibreWxrTiles) --------------------------------------------

    private val sampleMapsJson = """
        {"host":"https://api.librewxr.net","radar":{
        "past":[{"time":1794715800,"path":"/v2/radar/1794715800"},
        {"time":1794716400,"path":"/v2/radar/1794716400"}],
        "nowcast":[{"time":1794717000,"path":"/v2/radar/1794717000"},
        {"time":1794720000,"path":"/v2/radar/1794720000"}]}}
    """.trimIndent()

    @Test
    fun testLibreWxrTileUrl_matchesProbedExampleExactly() {
        assertEquals(
            "https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png",
            libreWxrTileUrl("https://api.librewxr.net", "/v2/radar/1794716400", 6, 16, 24)
        )
    }

    @Test
    fun testLibreWxrTileUrl_neverUsesPublicPrefix() {
        // The /public/radar/{time} prefix 404'd live; the builder only
        // ever uses the path the maps JSON returns.
        val url = libreWxrTileUrl("https://api.librewxr.net", "/v2/radar/1794716400", 8, 67, 98)
        assertTrue(url.contains("/v2/radar/1794716400/"))
        assertTrue(!url.contains("/public/radar/"))
    }

    @Test
    fun testParseLibreWxrMaps_sampleDocument() {
        val maps = parseLibreWxrMaps(sampleMapsJson)!!
        assertEquals("https://api.librewxr.net", maps.host)
        assertEquals(2, maps.past.size)
        assertEquals(2, maps.nowcast.size)
        assertEquals(1794716400L, maps.newestPastTime())
        assertTrue(maps.past.all { !it.isNowcast })
        assertTrue(maps.nowcast.all { it.isNowcast })
        assertEquals("/v2/radar/1794716400", maps.past.last().path)
    }

    @Test
    fun testParseLibreWxrMaps_noPastIsNull() {
        assertNull(parseLibreWxrMaps("{\"host\":\"https://api.librewxr.net\",\"radar\":{\"past\":[]}}"))
        assertNull(parseLibreWxrMaps("{}"))
    }

    @Test
    fun testLibreWxrLoopFrames_mergesPastAndNowcastSorted() {
        val maps = parseLibreWxrMaps(sampleMapsJson)!!
        val frames = libreWxrLoopFrames(maps)
        assertEquals(
            listOf(1794715800L, 1794716400L, 1794717000L, 1794720000L),
            frames.map { it.timeEpochSec }
        )
    }

    @Test
    fun testLibreWxrLoopFrames_dropsPrunedPastFrames() {
        // A past frame >2 h older than the newest past frame is pruned
        // server-side (a 2-hour-old frame 404'd live) — drop it here.
        val maps = LibreWxrMaps(
            host = "https://api.librewxr.net",
            past = listOf(
                LibreWxrFrame(1794716400L - 3 * 3600, "/v2/radar/old", false),
                LibreWxrFrame(1794716400L, "/v2/radar/1794716400", false)
            ),
            nowcast = emptyList()
        )
        val frames = libreWxrLoopFrames(maps)
        assertEquals(listOf(1794716400L), frames.map { it.timeEpochSec })
    }

    @Test
    fun testLibreWxrLoopFrames_dropsFarNowcast() {
        // Nowcast beyond 60 min past the newest past frame is out of
        // the live nowcast window — drop it rather than queueing it.
        val maps = LibreWxrMaps(
            host = "https://api.librewxr.net",
            past = listOf(LibreWxrFrame(1794716400L, "/v2/radar/1794716400", false)),
            nowcast = listOf(
                LibreWxrFrame(1794716400L + 3600, "/v2/radar/1794720000", true),
                LibreWxrFrame(1794716400L + 7200, "/v2/radar/1794723600", true)
            )
        )
        val frames = libreWxrLoopFrames(maps)
        assertEquals(
            listOf(1794716400L, 1794716400L + 3600),
            frames.map { it.timeEpochSec }
        )
    }

}
