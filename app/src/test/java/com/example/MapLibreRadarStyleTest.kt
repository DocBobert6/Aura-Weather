package com.example

import com.example.ui.radar.engine.RADAR_ENGINE_BASEMAP_LAYER_ID
import com.example.ui.radar.engine.RADAR_ENGINE_BASEMAP_TILE_TEMPLATE
import com.example.ui.radar.engine.RADAR_ENGINE_CROSSFADE_MS
import com.example.ui.radar.engine.RADAR_ENGINE_FRAME_COUNT
import com.example.ui.radar.engine.RADAR_ENGINE_MRMS_SLOT_A
import com.example.ui.radar.engine.RADAR_ENGINE_MRMS_SLOT_B
import com.example.ui.radar.engine.RADAR_ENGINE_MRMS_BBOX_1X
import com.example.ui.radar.engine.RADAR_ENGINE_MRMS_BBOX_FACTOR
import com.example.ui.radar.engine.RADAR_ENGINE_NAVY_BG
import com.example.ui.radar.engine.RADAR_ENGINE_SAT_IR_LAYER_ID
import com.example.ui.radar.engine.RADAR_ENGINE_SAT_VIS_LAYER_ID
import com.example.ui.radar.engine.RadarBbox
import com.example.ui.radar.engine.buildRadarStyleJson
import com.example.ui.radar.engine.expandBbox
import com.example.ui.radar.engine.gibsTileTemplate
import com.example.ui.radar.engine.hrrrFrameTileTemplate
import com.example.ui.radar.engine.iemFrameTileTemplate
import com.example.ui.radar.engine.layerDrawOrder
import com.example.ui.radar.engine.radarBboxToParam
import com.example.ui.radar.engine.radarFutureFrameLayerId
import com.example.ui.radar.engine.radarFutureFrameSourceId
import com.example.ui.radar.engine.radarLiveFrameLayerId
import com.example.ui.radar.engine.radarLiveFrameSourceId
import com.example.ui.radar.engine.radarMrmsLayerId
import com.example.ui.radar.engine.satelliteMaxZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for the Phase 1 MapLibre engine helpers.
 * Run locally with run-pure-tests-phase1.sh (kotlinc + JUnit); the
 * engine activity itself is verified by CI (it compiles against the
 * real 13.7.1 SDK there).
 */
class MapLibreRadarStyleTest {

    private val gibsTime = "2026-11-15T14:20:00Z"

    @Test
    fun `frame count is ten and crossfade is 150ms`() {
        assertEquals(10, RADAR_ENGINE_FRAME_COUNT)
        assertEquals(150L, RADAR_ENGINE_CROSSFADE_MS)
        // Phase 4: no window bound any more — the full future ranges
        // (~73 / ~97 frames) each get one source per frame.
    }

    @Test
    fun `style json is a raster-only style with navy background`() {
        val json = buildRadarStyleJson(gibsTime, gibsTime)
        assertTrue(json.contains("\"version\":8"))
        assertTrue(json.contains("\"background-color\":\"$RADAR_ENGINE_NAVY_BG\""))
        assertTrue(json.contains(RADAR_ENGINE_BASEMAP_TILE_TEMPLATE))
        assertTrue(json.contains("\"type\":\"raster\""))
        // No vector sources, no glyphs, no sprite: raster-only.
        assertFalse(json.contains("\"type\":\"vector\""))
        assertFalse(json.contains("glyphs"))
        assertFalse(json.contains("sprite"))
        // Radar sources are added programmatically (Kotlin volatile API),
        // so the JSON must NOT declare them or a volatile flag.
        assertFalse(json.contains("radar-live-frame"))
        assertFalse(json.contains("radar-future-frame"))
        assertFalse(json.contains("radar-mrms"))
        assertFalse(json.contains("volatile"))
        // Balanced braces/brackets (minimal structural sanity).
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun `style json carries both satellite sources with the gibs time`() {
        val json = buildRadarStyleJson(gibsTime, gibsTime)
        assertTrue(json.contains("GOES-East_ABI_GeoColor"))
        assertTrue(json.contains("GOES-East_ABI_Band13_Clean_Infrared"))
        assertTrue(json.contains("GoogleMapsCompatible_Level7"))
        assertTrue(json.contains("GoogleMapsCompatible_Level6"))
        assertTrue(json.contains(gibsTime))
        // Satellite layers start hidden (opacity 0); radar is the default view.
        assertTrue(json.contains("\"$RADAR_ENGINE_SAT_VIS_LAYER_ID\""))
        assertTrue(json.contains("\"$RADAR_ENGINE_SAT_IR_LAYER_ID\""))
        assertTrue(json.contains("\"raster-opacity\":0"))
    }

    @Test
    fun `basemap template uses Esri z-y-x order`() {
        assertTrue(RADAR_ENGINE_BASEMAP_TILE_TEMPLATE.contains("/tile/{z}/{y}/{x}"))
    }

    @Test
    fun `iem and hrrr templates use z-x-y order`() {
        val iem = iemFrameTileTemplate("nexrad-n0q-900913")
        assertTrue(iem.startsWith("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/"))
        assertTrue(iem.contains("nexrad-n0q-900913/{z}/{x}/{y}.png"))
        val hrrr = hrrrFrameTileTemplate("hrrr::REFD-F0075-0")
        assertTrue(hrrr.contains("hrrr::REFD-F0075-0/{z}/{x}/{y}.png"))
    }

    @Test
    fun `gibs template uses z-y-x order and the right level per layer`() {
        val vis = gibsTileTemplate(true, gibsTime)
        assertTrue(vis.contains("GoogleMapsCompatible_Level7/{z}/{y}/{x}.png"))
        val ir = gibsTileTemplate(false, gibsTime)
        assertTrue(ir.contains("GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"))
    }

    @Test
    fun `satellite native max zoom is 6 visible and 5 IR`() {
        assertEquals(6, satelliteMaxZoom(true))
        assertEquals(5, satelliteMaxZoom(false))
    }

    @Test
    fun `frame source and layer ids are distinct per index`() {
        val sourceIds = (0 until RADAR_ENGINE_FRAME_COUNT).map { radarLiveFrameSourceId(it) }
        assertEquals(RADAR_ENGINE_FRAME_COUNT, sourceIds.toSet().size)
        val layerIds = (0 until RADAR_ENGINE_FRAME_COUNT).map { radarLiveFrameLayerId(it) }
        assertEquals(RADAR_ENGINE_FRAME_COUNT, layerIds.toSet().size)
        assertTrue(sourceIds.none { it in layerIds })
        val futureSources = (0 until 97).map { radarFutureFrameSourceId(it) }
        assertEquals(97, futureSources.toSet().size)
        val futureLayers = (0 until 97).map { radarFutureFrameLayerId(it) }
        assertEquals(97, futureLayers.toSet().size)
        assertTrue(futureSources.none { it in sourceIds })
    }

    @Test
    fun `mrms layer ids follow the slot ids`() {
        assertEquals(
            "radar-mrms-layer-$RADAR_ENGINE_MRMS_SLOT_A",
            radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_A)
        )
        assertEquals(
            "radar-mrms-layer-$RADAR_ENGINE_MRMS_SLOT_B",
            radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_B)
        )
    }

    @Test
    fun `layer draw order is background basemap satellite live future mrms`() {
        // Full 48 h range: 97 future layers between the live layers
        // and the MRMS slots.
        val order = layerDrawOrder(97)
        assertEquals(4 + RADAR_ENGINE_FRAME_COUNT + 97 + 2, order.size)
        assertEquals("radar-background", order.first())
        assertEquals(radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_B), order.last())
        assertEquals(RADAR_ENGINE_BASEMAP_LAYER_ID, order[1])
        assertEquals(RADAR_ENGINE_SAT_VIS_LAYER_ID, order[2])
        assertEquals(RADAR_ENGINE_SAT_IR_LAYER_ID, order[3])
        assertEquals(radarLiveFrameLayerId(0), order[4])
        assertEquals(radarLiveFrameLayerId(9), order[13])
        assertEquals(radarFutureFrameLayerId(0), order[14])
        assertEquals(radarFutureFrameLayerId(96), order[110])
        assertEquals(radarMrmsLayerId(RADAR_ENGINE_MRMS_SLOT_A), order[111])
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun `expand bbox by 1_5x preserves centre and widens by the factor`() {
        val expanded = expandBbox(RADAR_ENGINE_MRMS_BBOX_1X, RADAR_ENGINE_MRMS_BBOX_FACTOR)
        val centreLonBefore = (RADAR_ENGINE_MRMS_BBOX_1X.westLon + RADAR_ENGINE_MRMS_BBOX_1X.eastLon) / 2.0
        val centreLonAfter = (expanded.westLon + expanded.eastLon) / 2.0
        assertEquals(centreLonBefore, centreLonAfter, 1e-9)
        val widthBefore = RADAR_ENGINE_MRMS_BBOX_1X.eastLon - RADAR_ENGINE_MRMS_BBOX_1X.westLon
        val widthAfter = expanded.eastLon - expanded.westLon
        assertEquals(widthBefore * 1.5, widthAfter, 1e-9)
        // The spike's concrete 1.5x bbox, for the record.
        assertEquals(-89.0, expanded.westLon, 1e-9)
        assertEquals(-80.0, expanded.eastLon, 1e-9)
        assertEquals(35.35, expanded.southLat, 1e-9)
        assertEquals(42.85, expanded.northLat, 1e-9)
    }

    @Test
    fun `bbox to param is west south east north`() {
        val bbox = RadarBbox(-90.5, 34.1, -78.5, 44.1)
        assertEquals("-90.5,34.1,-78.5,44.1", radarBboxToParam(bbox))
    }
}
