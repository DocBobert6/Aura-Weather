package com.example

import com.example.spike.maplibre.SPIKE_BASEMAP_TILE_TEMPLATE
import com.example.spike.maplibre.SPIKE_CROSSFADE_MS
import com.example.spike.maplibre.SPIKE_FRAME_COUNT
import com.example.spike.maplibre.SPIKE_MRMS_SLOT_A
import com.example.spike.maplibre.SPIKE_MRMS_SLOT_B
import com.example.spike.maplibre.SPIKE_NAVY_BG
import com.example.spike.maplibre.SpikeBbox
import com.example.spike.maplibre.buildSpikeStyleJson
import com.example.spike.maplibre.expandBbox
import com.example.spike.maplibre.spikeBboxToParam
import com.example.spike.maplibre.spikeFrameLayerId
import com.example.spike.maplibre.spikeFrameSourceId
import com.example.spike.maplibre.spikeIemFrameTileTemplate
import com.example.spike.maplibre.spikeIemLayerNameFor
import com.example.spike.maplibre.spikeMrmsLayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for the Phase 0 MapLibre spike helpers.
 * Run locally with run-pure-tests-spike.sh (kotlinc + JUnit); the
 * Android activity itself is verified by CI (it compiles against the
 * real 13.7.1 SDK there).
 */
class MapLibreSpikeStyleTest {

    @Test
    fun `frame count is ten and crossfade is 150ms`() {
        assertEquals(10, SPIKE_FRAME_COUNT)
        assertEquals(150L, SPIKE_CROSSFADE_MS)
    }

    @Test
    fun `style json is a raster-only style with navy background`() {
        val json = buildSpikeStyleJson()
        assertTrue(json.contains("\"version\":8"))
        assertTrue(json.contains("\"background-color\":\"$SPIKE_NAVY_BG\""))
        assertTrue(json.contains(SPIKE_BASEMAP_TILE_TEMPLATE))
        assertTrue(json.contains("\"type\":\"raster\""))
        // No vector sources, no glyphs, no sprite: raster-only.
        assertFalse(json.contains("\"type\":\"vector\""))
        assertFalse(json.contains("glyphs"))
        assertFalse(json.contains("sprite"))
        // Radar sources are added programmatically (Kotlin volatile API),
        // so the JSON must NOT declare them or a volatile flag.
        assertFalse(json.contains("spike-radar-frame"))
        assertFalse(json.contains("volatile"))
        // Balanced braces/brackets (minimal structural sanity).
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun `basemap template uses Esri z-y-x order`() {
        assertTrue(SPIKE_BASEMAP_TILE_TEMPLATE.contains("/tile/{z}/{y}/{x}"))
    }

    @Test
    fun `IEM layer names match the canonical mapping`() {
        assertEquals("nexrad-n0q-900913", spikeIemLayerNameFor(9))
        assertEquals("nexrad-n0q-900913-m05m", spikeIemLayerNameFor(8))
        assertEquals("nexrad-n0q-900913-m45m", spikeIemLayerNameFor(0))
        assertEquals("nexrad-n0q-900913-m45m", spikeIemLayerNameFor(42))
    }

    @Test
    fun `IEM tile template uses MapLibre z-x-y order`() {
        val live = spikeIemFrameTileTemplate(9)
        assertEquals(
            "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/" +
                "nexrad-n0q-900913/{z}/{x}/{y}.png",
            live
        )
        assertTrue(spikeIemFrameTileTemplate(0).contains("nexrad-n0q-900913-m45m"))
    }

    @Test
    fun `frame source and layer ids are unique and distinct`() {
        val sourceIds = (0 until SPIKE_FRAME_COUNT).map { spikeFrameSourceId(it) }
        val layerIds = (0 until SPIKE_FRAME_COUNT).map { spikeFrameLayerId(it) }
        assertEquals(SPIKE_FRAME_COUNT, sourceIds.toSet().size)
        assertEquals(SPIKE_FRAME_COUNT, layerIds.toSet().size)
        assertTrue(sourceIds.intersect(layerIds.toSet()).isEmpty())
        assertEquals("spike-radar-frame-0", spikeFrameSourceId(0))
        assertEquals("spike-radar-frame-layer-9", spikeFrameLayerId(9))
    }

    @Test
    fun `MRMS slot layer ids are distinct per slot`() {
        assertEquals("spike-mrms-layer-spike-mrms-a", spikeMrmsLayerId(SPIKE_MRMS_SLOT_A))
        assertEquals("spike-mrms-layer-spike-mrms-b", spikeMrmsLayerId(SPIKE_MRMS_SLOT_B))
    }

    @Test
    fun `expandBbox preserves centre and scales size by factor`() {
        val bbox = SpikeBbox(westLon = -87.5, southLat = 36.6, eastLon = -81.5, northLat = 41.6)
        val expanded = expandBbox(bbox, 1.5)
        // Centre preserved.
        assertEquals(-84.5, (expanded.westLon + expanded.eastLon) / 2.0, 1e-9)
        assertEquals(39.1, (expanded.southLat + expanded.northLat) / 2.0, 1e-9)
        // Width/height x1.5 (6.0 -> 9.0 lon, 5.0 -> 7.5 lat).
        assertEquals(9.0, expanded.eastLon - expanded.westLon, 1e-9)
        assertEquals(7.5, expanded.northLat - expanded.southLat, 1e-9)
        // Factor 1.0 is the identity.
        assertEquals(bbox, expandBbox(bbox, 1.0))
    }

    @Test
    fun `bbox param formats west south east north`() {
        val bbox = SpikeBbox(westLon = -89.0, southLat = 35.35, eastLon = -80.0, northLat = 42.85)
        assertEquals("-89.0,35.35,-80.0,42.85", spikeBboxToParam(bbox))
    }
}
