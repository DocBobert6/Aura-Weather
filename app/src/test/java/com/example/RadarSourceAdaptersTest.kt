package com.example

import com.example.data.mapper.DEFAULT_RADAR_SOURCE_ORDER
import com.example.data.mapper.LibreWxrFrame
import com.example.data.mapper.LibreWxrMaps
import com.example.data.mapper.RadarFrameContent
import com.example.data.mapper.RadarSourceKind
import com.example.data.mapper.availableSources
import com.example.data.mapper.fallbackAfter
import com.example.data.mapper.libreWxrFrameTileUrls
import com.example.data.mapper.libreWxrLoopFrames
import com.example.data.mapper.libreWxrTileUrl
import com.example.data.mapper.mrmsExportImageUrl
import com.example.data.mapper.mrmsFrameTimesFromServiceInfoJson
import com.example.data.mapper.mrmsFrameTimesFromTimeExtent
import com.example.data.mapper.mrmsFramesForViewport
import com.example.data.mapper.parseLibreWxrMaps
import com.example.data.mapper.parseMrmsTimeExtent
import com.example.data.mapper.tileXToLon
import com.example.data.mapper.tileYToLat
import com.example.data.mapper.viewportBboxForTileGrid
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

    private val centerLat = 38.7833
    private val centerLon = -84.6027

    // -- Web Mercator inverse (RadarSources) -------------------------------

    @Test
    fun testTileXToLon_crittendenZoom8() {
        // lonToTileX(-84.6027, 8) = 67, so tile 67's west edge is just
        // west of Crittenden and tile 68's west edge just east of it.
        assertTrue(tileXToLon(67, 8) < centerLon)
        assertTrue(tileXToLon(68, 8) > centerLon)
        assertEquals(-85.78125, tileXToLon(67, 8), 0.00001)
    }

    @Test
    fun testTileYToLat_crittendenZoom8() {
        // latToTileY(38.7833, 8) = 98, so tile 98's north edge is just
        // north of Crittenden and tile 99's north edge just south.
        assertTrue(tileYToLat(98, 8) > centerLat)
        assertTrue(tileYToLat(99, 8) < centerLat)
    }

    @Test
    fun testTileInverse_roundTripsLonLat() {
        // tileXToLon/tileYToLat invert RadarTiles' lonToTileX/latToTileY:
        // the centre lon/lat must fall inside its own tile's edges.
        val x = com.example.data.mapper.lonToTileX(centerLon, 8)
        val y = com.example.data.mapper.latToTileY(centerLat, 8)
        assertEquals(67, x)
        assertEquals(98, y)
        assertTrue(centerLon in tileXToLon(x, 8)..tileXToLon(x + 1, 8))
        assertTrue(centerLat in tileYToLat(y + 1, 8)..tileYToLat(y, 8))
    }

    @Test
    fun testViewportBbox_containsCentreAndMargin() {
        val bbox = viewportBboxForTileGrid(centerLat, centerLon, 8, 6, 11, marginTiles = 1)
        assertTrue(bbox.contains(centerLon, centerLat))
        assertTrue(bbox.westLon < centerLon && bbox.eastLon > centerLon)
        assertTrue(bbox.southLat < centerLat && bbox.northLat > centerLat)
        // One margin tile each side: the span is the 7x11 drawn grid
        // (6/2 and 11/2 halves, +1 tile each side) plus the margin.
        val noMargin = viewportBboxForTileGrid(centerLat, centerLon, 8, 6, 11, marginTiles = 0)
        assertTrue(bbox.westLon < noMargin.westLon)
        assertTrue(bbox.eastLon > noMargin.eastLon)
    }

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

    @Test
    fun testAvailableSources_dropsUnavailable() {
        assertEquals(
            listOf(RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM),
            availableSources(setOf(RadarSourceKind.MRMS))
        )
        assertEquals(
            listOf(RadarSourceKind.MRMS),
            availableSources(setOf(RadarSourceKind.LIBRE_WXR, RadarSourceKind.IEM))
        )
    }

    // -- MRMS (MrmsTiles) ---------------------------------------------------

    @Test
    fun testMrmsExportImageUrl_probedShape() {
        val bbox = viewportBboxForTileGrid(centerLat, centerLon, 8, 6, 11, marginTiles = 0)
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

    @Test
    fun testMrmsFramesForViewport_oneImagePerFrame() {
        val json = "{\"timeInfo\":{\"timeExtent\":[1794709257000,1794715977000]}}"
        val bbox = viewportBboxForTileGrid(centerLat, centerLon, 8, 6, 11, marginTiles = 1)
        val frames = mrmsFramesForViewport(json, bbox, 1080, 1000)
        assertEquals(22, frames.size)
        assertTrue(frames.all { it.source == RadarSourceKind.MRMS })
        // MRMS shape: ONE image per frame, never a tile grid.
        val first = frames.first()
        val content = first.content as RadarFrameContent.SingleImage
        assertEquals(bbox, content.bbox)
        assertEquals(1080, content.widthPx)
        assertEquals(1000, content.heightPx)
        assertTrue(content.url.contains("time=1794709500000"))
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

    @Test
    fun testLibreWxrFrameTileUrls_gridMatchesIemGrid() {
        val maps = parseLibreWxrMaps(sampleMapsJson)!!
        val frame = maps.past.last()
        val urls = libreWxrFrameTileUrls(maps, frame, centerLat, centerLon, 8, 6, 11)
        // Same 7x11 = 77-tile grid the IEM path draws at this zoom.
        assertEquals(77, urls.size)
        assertEquals(77, urls.toSet().size)
        assertTrue(urls.contains("https://api.librewxr.net/v2/radar/1794716400/256/8/67/98/2/1_1.png"))
        assertTrue(urls.all { it.startsWith("https://api.librewxr.net/v2/radar/1794716400/256/8/") })
    }
}
