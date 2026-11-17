package com.example

import com.example.data.mapper.RadarRange
import com.example.data.mapper.radarRangeFrames
import com.example.ui.widget.RADAR_WIDGET_HEIGHT_PX
import com.example.ui.widget.RADAR_WIDGET_STEP_COUNT
import com.example.ui.widget.RADAR_WIDGET_WIDTH_PX
import com.example.ui.widget.RADAR_WIDGET_ZOOM
import com.example.ui.widget.esriBasemapTileUrl
import com.example.ui.widget.radarWidgetBitmapByteCount
import com.example.ui.widget.radarWidgetFallbackFrameTimeMs
import com.example.ui.widget.radarWidgetFutureFrame
import com.example.ui.widget.radarWidgetLabel
import com.example.ui.widget.radarWidgetNewestMrmsTimeMs
import com.example.ui.widget.radarWidgetStepIndex
import com.example.ui.widget.radarWidgetStepOffsetMinutes
import com.example.ui.widget.radarWidgetTileGrid
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radar widget model (Item 4): stepping, labels, bitmap budget,
 * tile grid, frame selection. Pure Kotlin — runs in the local
 * kotlinc + JUnit pass.
 */
class RadarWidgetModelTest {

    // -- Stepping ------------------------------------------------------

    @Test
    fun `step cycles 0 1 2 across successive 15-min refreshes`() {
        // 20:15 UTC sits on a step-0 boundary (epoch/900 % 3 == 0).
        val base = Instant.parse("2026-11-16T20:15:00Z")
        val steps = (0..5).map { radarWidgetStepIndex(base.plusSeconds(it * 900L)) }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), steps)
    }

    @Test
    fun `step advances exactly once for any 15-min-apart pair`() {
        // The refresh is not wall-clock aligned (WorkManager starts its
        // period at enqueue), so the step must advance by one whatever
        // the phase: t and t+15min always cross exactly one boundary.
        for (phaseMin in 0..14) {
            val t = Instant.parse("2026-11-16T20:00:00Z").plusSeconds(phaseMin * 60L)
            val a = radarWidgetStepIndex(t)
            val b = radarWidgetStepIndex(t.plusSeconds(900L))
            assertEquals("phase $phaseMin", (a + 1) % RADAR_WIDGET_STEP_COUNT, b)
        }
    }

    @Test
    fun `step offsets are 0 15 30`() {
        assertEquals(0, radarWidgetStepOffsetMinutes(0))
        assertEquals(15, radarWidgetStepOffsetMinutes(1))
        assertEquals(30, radarWidgetStepOffsetMinutes(2))
        assertEquals(30, radarWidgetStepOffsetMinutes(99))
        assertEquals(0, radarWidgetStepOffsetMinutes(-1))
    }

    // -- Labels ----------------------------------------------------------

    @Test
    fun `label states the frame and its valid time`() {
        val frame = Instant.parse("2026-11-16T20:42:00Z")
        assertEquals(
            "LIVE NOW · 15:42",
            radarWidgetLabel(0, frame, "America/New_York")
        )
        assertEquals(
            "IN 15M · 15:57",
            radarWidgetLabel(1, Instant.parse("2026-11-16T20:57:00Z"), "America/New_York")
        )
        assertEquals(
            "IN 30M · 16:12",
            radarWidgetLabel(2, Instant.parse("2026-11-16T21:12:00Z"), "America/New_York")
        )
    }

    @Test
    fun `stale label appends STALE, never looks fresh`() {
        val frame = Instant.parse("2026-11-16T20:42:00Z")
        assertEquals(
            "LIVE NOW · 15:42 · STALE",
            radarWidgetLabel(0, frame, "America/New_York", stale = true)
        )
        assertEquals(
            "IN 15M · 15:57 · STALE",
            radarWidgetLabel(1, Instant.parse("2026-11-16T20:57:00Z"), "America/New_York", stale = true)
        )
    }

    @Test
    fun `label falls back to the device zone for a bad zone id`() {
        val frame = Instant.parse("2026-11-16T20:42:00Z")
        val label = radarWidgetLabel(0, frame, "Not/AZone")
        assertTrue(label.startsWith("LIVE NOW · "))
        assertTrue(label.contains(":"))
    }

    // -- Bitmap budget ---------------------------------------------------

    @Test
    fun `composited bitmap is 830x290 RGB_565, well inside the RemoteViews limit`() {
        assertEquals(830, RADAR_WIDGET_WIDTH_PX)
        assertEquals(290, RADAR_WIDGET_HEIGHT_PX)
        assertEquals(481_400L, radarWidgetBitmapByteCount())
        assertTrue(radarWidgetBitmapByteCount() < 1_000_000L)
    }

    // -- Tile grid -------------------------------------------------------

    @Test
    fun `grid over Cincinnati at zoom 7 is 4 by 2 tiles`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        assertEquals(4, grid.tileX1 - grid.tileX0 + 1)
        assertEquals(2, grid.tileY1 - grid.tileY0 + 1)
        assertEquals(8, grid.tileCount)
    }

    @Test
    fun `grid bbox contains the centre and the centre tile holds it`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        assertTrue(grid.westLon < -84.5 && -84.5 < grid.eastLon)
        assertTrue(grid.southLat < 39.1 && 39.1 < grid.northLat)
        assertTrue(grid.centreTileX in grid.tileX0..grid.tileX1)
        assertTrue(grid.centreTileY in grid.tileY0..grid.tileY1)
    }

    @Test
    fun `tile draw offsets place the centre tile over the bitmap centre`() {
        val grid = radarWidgetTileGrid(39.1, -84.5)
        val x = grid.tileDrawX(grid.centreTileX)
        val y = grid.tileDrawY(grid.centreTileY)
        // The centre tile's left edge is at most one tile left of the
        // bitmap centre, and its span covers the centre.
        assertTrue(x <= RADAR_WIDGET_WIDTH_PX / 2.0)
        assertTrue(x + 256.0 > RADAR_WIDGET_WIDTH_PX / 2.0)
        assertTrue(y <= RADAR_WIDGET_HEIGHT_PX / 2.0)
        assertTrue(y + 256.0 > RADAR_WIDGET_HEIGHT_PX / 2.0)
    }

    @Test
    fun `esri url keeps the z y x order`() {
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/7/55/34",
            esriBasemapTileUrl(7, 34, 55)
        )
    }

    // -- Frame selection -------------------------------------------------

    @Test
    fun `newest MRMS time comes from the service-info extent`() {
        // Extent [start, end] aligned to the 5-min cadence; the newest
        // frame is the last aligned time at or before the end.
        val json = "{\"timeInfo\":{\"timeExtent\":[1794709257000,1794715977000]}}"
        assertEquals(1794715800000L, radarWidgetNewestMrmsTimeMs(json))
    }

    @Test
    fun `newest MRMS time is null without an extent`() {
        assertNull(radarWidgetNewestMrmsTimeMs("{}"))
        assertNull(radarWidgetNewestMrmsTimeMs(null))
    }

    @Test
    fun `fallback frame time truncates now to the 5-min cadence`() {
        val now = Instant.parse("2026-11-16T20:42:37Z")
        assertEquals(
            Instant.parse("2026-11-16T20:40:00Z").toEpochMilli(),
            radarWidgetFallbackFrameTimeMs(now)
        )
    }

    @Test
    fun `future frame is the first at or after now plus offset`() {
        val now = Instant.parse("2026-11-16T20:07:00Z")
        val frames = radarRangeFrames(RadarRange.Future6h, now)
        assertTrue(frames.isNotEmpty())
        val plus15 = radarWidgetFutureFrame(now, 15)
        assertNotNull(plus15)
        assertTrue(!plus15!!.validTime.isBefore(now.plusSeconds(15 * 60L)))
        // It is the FIRST such frame: the previous one is before target.
        val index = frames.indexOf(plus15)
        if (index > 0) {
            assertTrue(frames[index - 1].validTime.isBefore(now.plusSeconds(15 * 60L)))
        }
        val plus30 = radarWidgetFutureFrame(now, 30)
        assertNotNull(plus30)
        assertTrue(!plus30!!.validTime.isBefore(now.plusSeconds(30 * 60L)))
        assertTrue(plus30.validTime >= plus15.validTime)
    }

    @Test
    fun `future frame never leaves the 6h range`() {
        val now = Instant.parse("2026-11-16T20:07:00Z")
        val frame = radarWidgetFutureFrame(now, 30)
        assertNotNull(frame)
        assertTrue(!frame!!.validTime.isAfter(now.plusSeconds(6 * 3600L)))
        assertTrue(frame.layerName.startsWith("hrrr::REFD-F"))
    }
}
