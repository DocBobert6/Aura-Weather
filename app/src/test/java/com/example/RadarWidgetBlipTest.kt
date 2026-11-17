package com.example

import com.example.ui.widget.RADAR_WIDGET_BLIP_DOT_RADIUS_PX
import com.example.ui.widget.RADAR_WIDGET_BLIP_RING_RADIUS_PX
import com.example.ui.widget.RADAR_WIDGET_BLIP_RING_STROKE_PX
import com.example.ui.widget.RADAR_WIDGET_HEIGHT_PX
import com.example.ui.widget.RADAR_WIDGET_WIDTH_PX
import com.example.ui.widget.RadarWidgetBlipDraw
import com.example.ui.widget.radarWidgetBlipDrawSpec
import com.example.ui.widget.radarWidgetPlaceDotPx
import com.example.ui.widget.radarWidgetTileGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Radar widget still blip (0.4.0): the place's bitmap pixel and the
 * dot/ring sizes. Pure Kotlin — runs in the local kotlinc + JUnit
 * pass. The Canvas draw itself is CI-only (RadarWidgetCompositor).
 */
class RadarWidgetBlipTest {

    @Test
    fun `dot sits at the bitmap centre for the grid centre place`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val dot = radarWidgetPlaceDotPx(grid, 39.1, -84.5)
        assertEquals(RADAR_WIDGET_WIDTH_PX / 2.0, dot.xPx, 0.001)
        assertEquals(RADAR_WIDGET_HEIGHT_PX / 2.0, dot.yPx, 0.001)
    }

    @Test
    fun `dot moves with the place, east and north`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val east = radarWidgetPlaceDotPx(grid, 39.1, -84.0)
        assertTrue(east.xPx > RADAR_WIDGET_WIDTH_PX / 2.0)
        assertEquals(RADAR_WIDGET_HEIGHT_PX / 2.0, east.yPx, 0.001)
        val north = radarWidgetPlaceDotPx(grid, 39.6, -84.5)
        assertTrue(north.yPx < RADAR_WIDGET_HEIGHT_PX / 2.0)
        assertEquals(RADAR_WIDGET_WIDTH_PX / 2.0, north.xPx, 0.001)
    }

    @Test
    fun `dot and ring fit the bitmap with room to spare`() {
        assertTrue(RADAR_WIDGET_BLIP_DOT_RADIUS_PX > 0f)
        assertTrue(RADAR_WIDGET_BLIP_RING_RADIUS_PX > RADAR_WIDGET_BLIP_DOT_RADIUS_PX)
        assertTrue(RADAR_WIDGET_BLIP_RING_STROKE_PX > 0f)
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val dot = radarWidgetPlaceDotPx(grid, 39.1, -84.5)
        assertTrue(dot.xPx - RADAR_WIDGET_BLIP_RING_RADIUS_PX > 0)
        assertTrue(dot.xPx + RADAR_WIDGET_BLIP_RING_RADIUS_PX < RADAR_WIDGET_WIDTH_PX)
        assertTrue(dot.yPx - RADAR_WIDGET_BLIP_RING_RADIUS_PX > 0)
        assertTrue(dot.yPx + RADAR_WIDGET_BLIP_RING_RADIUS_PX < RADAR_WIDGET_HEIGHT_PX)
    }

    // -- Draw spec: the exact parameters the compositor paints -------

    @Test
    fun `draw spec carries the projected centre and the fixed sizes`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val spec = radarWidgetBlipDrawSpec(grid, 39.1, -84.5)
        assertEquals(RADAR_WIDGET_WIDTH_PX / 2.0, spec.centerXPx, 0.001)
        assertEquals(RADAR_WIDGET_HEIGHT_PX / 2.0, spec.centerYPx, 0.001)
        assertEquals(RADAR_WIDGET_BLIP_DOT_RADIUS_PX, spec.dotRadiusPx, 0f)
        assertEquals(RADAR_WIDGET_BLIP_RING_RADIUS_PX, spec.ringRadiusPx, 0f)
        assertEquals(RADAR_WIDGET_BLIP_RING_STROKE_PX, spec.ringStrokePx, 0f)
    }

    @Test
    fun `draw spec centre follows the place`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val east = radarWidgetBlipDrawSpec(grid, 39.1, -84.0)
        assertTrue(east.centerXPx > RADAR_WIDGET_WIDTH_PX / 2.0)
        assertEquals(RADAR_WIDGET_HEIGHT_PX / 2.0, east.centerYPx, 0.001)
    }

    // -- Pixel coverage: which bitmap pixels the baked dot paints ----
    //
    // Mirrors Canvas.drawCircle semantics for the compositor's two
    // calls (filled disc dist <= dot radius; stroked ring
    // |dist - ring radius| <= stroke / 2), applied to the spec the
    // compositor draws verbatim. Probes stay >= 1 px clear of the
    // antialiased edges, so they assert fully-painted vs unpainted
    // pixels, not blended edge pixels. The Canvas execution itself
    // is CI-only; this pins the geometry it executes.

    /** True when the spec paints bitmap pixel ([x], [y]) white. */
    private fun specPaints(spec: RadarWidgetBlipDraw, x: Double, y: Double): Boolean {
        val dist = hypot(x - spec.centerXPx, y - spec.centerYPx)
        if (dist <= spec.dotRadiusPx) return true
        return kotlin.math.abs(dist - spec.ringRadiusPx) <= spec.ringStrokePx / 2.0
    }

    @Test
    fun `pixel coverage dot paints the centre ring paints at radius ten gap stays bare`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val spec = radarWidgetBlipDrawSpec(grid, 39.1, -84.5)
        val cx = RADAR_WIDGET_WIDTH_PX / 2.0
        val cy = RADAR_WIDGET_HEIGHT_PX / 2.0
        // Dot interior (dist 0 and 4; edge at 5 is antialiased).
        assertTrue(specPaints(spec, cx, cy))
        assertTrue(specPaints(spec, cx + 4, cy))
        // Gap between dot and ring (dist 7: past the dot, short of
        // the ring band 9..11).
        assertFalse(specPaints(spec, cx + 7, cy))
        assertFalse(specPaints(spec, cx + 4, cy + 4))
        // Ring band centre (dist 10, on the stroke; also diagonal).
        assertTrue(specPaints(spec, cx + 10, cy))
        assertTrue(specPaints(spec, cx + 7, cy + 7))
        // Outside the ring (dist 13).
        assertFalse(specPaints(spec, cx + 13, cy))
        assertFalse(specPaints(spec, cx, cy + 13))
    }
}
