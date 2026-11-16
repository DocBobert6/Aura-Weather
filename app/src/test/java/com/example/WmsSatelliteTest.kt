package com.example

import com.example.ui.radar.engine.WMS_IMAGE_MAX_LONG_EDGE_PX
import com.example.ui.radar.engine.WmsBbox3857
import com.example.ui.radar.engine.WmsSatelliteLayer
import com.example.ui.radar.engine.WmsTimestampCache
import com.example.ui.radar.engine.formatWmsTime
import com.example.ui.radar.engine.isBlankSample
import com.example.ui.radar.engine.isXmlErrorBody
import com.example.ui.radar.engine.lonLatTo3857
import com.example.ui.radar.engine.metresPerPixel
import com.example.ui.radar.engine.newestNonBlank
import com.example.ui.radar.engine.roundDownTo10Min
import com.example.ui.radar.engine.satelliteCostText
import com.example.ui.radar.engine.wmsBbox3857
import com.example.ui.radar.engine.wmsGetMapUrl
import com.example.ui.radar.engine.wmsImageSizeForViewport
import com.example.ui.radar.engine.wmsSmallProbeUrl
import com.example.ui.radar.engine.wmsSampleStats
import com.example.ui.radar.engine.wmsTimeCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for WmsSatellite (Phase 3). Reference numbers are
 * from the live probes in rebuild-satellite.md §1.3 and the
 * reference implementation `wms-sampling/probe_wms.py` — the
 * candidate times, blank rules and URL shape are mirrored from
 * the probe, not re-derived.
 */
class WmsSatelliteTest {

    // 2026-11-15T18:00:00Z, the probed real GeoColor scan.
    private val t1800Ms = 1794765600000L
    // 2026-11-15T20:02:30Z — now for the candidate tests (probe run time, ~20:02 UTC).
    private val nowMs = 1794772950000L

    // ------------------------------------------------------------------
    // TIME format
    // ------------------------------------------------------------------

    @Test fun `formatWmsTime is UTC ISO-8601 with Z`() {
        assertEquals("2026-11-15T18:00:00Z", formatWmsTime(t1800Ms))
        assertEquals("2026-11-15T19:00:00Z", formatWmsTime(1794769200000L))
    }

    @Test fun `roundDownTo10Min floors to the scan boundary`() {
        assertEquals(1794769200000L, roundDownTo10Min(nowMs - 60L * 60L * 1000L)) // 19:02:30 -> 19:00
        assertEquals(t1800Ms, roundDownTo10Min(t1800Ms)) // already on a boundary
        assertEquals(t1800Ms, roundDownTo10Min(t1800Ms + 9L * 60L * 1000L + 59000L)) // 18:09:59 -> 18:00
    }

    // ------------------------------------------------------------------
    // URL shape per layer
    // ------------------------------------------------------------------

    private val cincyBbox = wmsBbox3857(-84.6027, 38.7833, 1080, 1920, 6)

    @Test fun `visible GetMap URL shape is exact`() {
        assertEquals(
            "https://gibs.earthdata.nasa.gov/wms/epsg3857/best/wms.cgi" +
                "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap" +
                "&LAYERS=GOES-East_ABI_GeoColor&STYLES=&CRS=EPSG:3857" +
                "&BBOX=-11399177.26,1168460.26,-7436681.71,8212896.78" +
                "&WIDTH=1620&HEIGHT=2880&FORMAT=image/jpeg&TRANSPARENT=FALSE" +
                "&TIME=2026-11-15T18:00:00Z",
            wmsGetMapUrl(WmsSatelliteLayer.VISIBLE, cincyBbox, 1620, 2880, t1800Ms)
        )
    }

    @Test fun `infrared GetMap URL uses the Band 13 layer`() {
        val url = wmsGetMapUrl(WmsSatelliteLayer.INFRARED, cincyBbox, 1620, 2880, t1800Ms)
        assertTrue(url.contains("&LAYERS=GOES-East_ABI_Band13_Clean_Infrared&"))
        assertTrue(url.startsWith("https://gibs.earthdata.nasa.gov/wms/epsg3857/best/wms.cgi?"))
        assertTrue(url.contains("&FORMAT=image/jpeg&TRANSPARENT=FALSE&"))
        assertTrue(url.endsWith("&TIME=2026-11-15T18:00:00Z"))
    }

    @Test fun `small probe URL is the same GetMap at 256x256`() {
        val url = wmsSmallProbeUrl(WmsSatelliteLayer.VISIBLE, cincyBbox, t1800Ms)
        assertTrue(url.contains("&WIDTH=256&HEIGHT=256&"))
        assertTrue(url.contains("&LAYERS=GOES-East_ABI_GeoColor&"))
        assertEquals(
            wmsGetMapUrl(WmsSatelliteLayer.VISIBLE, cincyBbox, 256, 256, t1800Ms),
            url
        )
    }

    // ------------------------------------------------------------------
    // Candidate generation
    // ------------------------------------------------------------------

    @Test fun `round 1 candidates are now-60 to now-100 rounded down`() {
        // now = 20:02:30Z -> 19:00, 18:50, 18:40, 18:30, 18:20
        val cands = wmsTimeCandidates(nowMs, 1)
        assertEquals(5, cands.size)
        assertEquals(
            listOf(1794769200000L, 1794768600000L, 1794768000000L, 1794767400000L, 1794766800000L),
            cands
        )
        assertEquals("2026-11-15T19:00:00Z", formatWmsTime(cands.first()))
        assertEquals("2026-11-15T18:20:00Z", formatWmsTime(cands.last()))
    }

    @Test fun `round 2 candidates are now-110 to now-150 rounded down`() {
        // now = 20:02:30Z -> 18:10, 18:00, 17:50, 17:40, 17:30
        val cands = wmsTimeCandidates(nowMs, 2)
        assertEquals(5, cands.size)
        assertEquals(
            listOf(1794766200000L, 1794765600000L, 1794765000000L, 1794764400000L, 1794763800000L),
            cands
        )
        assertEquals("2026-11-15T18:10:00Z", formatWmsTime(cands.first()))
        assertEquals("2026-11-15T17:30:00Z", formatWmsTime(cands.last()))
    }

    @Test fun `candidates are 10 minutes apart and on scan boundaries`() {
        for (round in 1..2) {
            val cands = wmsTimeCandidates(nowMs, round)
            for (i in 0 until cands.size - 1) {
                assertEquals(600000L, cands[i] - cands[i + 1])
            }
            for (c in cands) {
                assertEquals(c, roundDownTo10Min(c))
            }
        }
    }

    @Test fun `candidates on an exact 10-minute boundary now`() {
        // now = 20:00:00Z exactly -> round 1 starts at 19:00, no rounding shift
        val cands = wmsTimeCandidates(1794772800000L, 1)
        assertEquals("2026-11-15T19:00:00Z", formatWmsTime(cands.first()))
        assertEquals("2026-11-15T18:20:00Z", formatWmsTime(cands.last()))
    }

    @Test fun `invalid round throws`() {
        try {
            wmsTimeCandidates(nowMs, 3)
            throw AssertionError("expected IllegalArgumentException for round 3")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ------------------------------------------------------------------
    // Newest-non-blank selection (incl. non-monotonic availability)
    // ------------------------------------------------------------------

    @Test fun `newest non-blank wins when availability is non-monotonic`() {
        // Probed 2026-11-15 ~20:02 UTC: 18:50 real, 18:40 black, 18:30 real.
        // The answer is 18:50 — the NEWEST non-blank — not the newest candidate,
        // and a naive "step back from the newest blank" would wrongly land on 18:30
        // only after wasting a step; a "newest candidate" rule would take the black 18:40.
        val cands = listOf(1794768600000L, 1794768000000L, 1794767400000L, 1794766800000L, 1794766200000L)
        val blankByTime = mapOf(
            1794768600000L to false, // 18:50 real
            1794768000000L to true,  // 18:40 black
            1794767400000L to false, // 18:30 real
            1794766800000L to true,  // 18:20 black
            1794766200000L to false  // 18:10 real
        )
        assertEquals(1794768600000L, newestNonBlank(cands, blankByTime))
    }

    @Test fun `newest non-blank skips blank newest candidates`() {
        val cands = listOf(1794768600000L, 1794768000000L, 1794767400000L)
        val blankByTime = mapOf(
            1794768600000L to true,  // 18:50 blank
            1794768000000L to true,  // 18:40 blank
            1794767400000L to false  // 18:30 real
        )
        assertEquals(1794767400000L, newestNonBlank(cands, blankByTime))
    }

    @Test fun `all blank returns null`() {
        val cands = wmsTimeCandidates(nowMs, 1)
        val blankByTime = cands.associateWith { true }
        assertNull(newestNonBlank(cands, blankByTime))
    }

    @Test fun `empty candidates returns null`() {
        assertNull(newestNonBlank(emptyList(), emptyMap()))
    }

    @Test fun `unprobed candidate is treated as blank`() {
        val cands = listOf(1794768600000L, 1794768000000L)
        // Only the older candidate was probed (and is real); the newer one is unknown.
        val blankByTime = mapOf(1794768000000L to false)
        assertEquals(1794768000000L, newestNonBlank(cands, blankByTime))
    }

    // ------------------------------------------------------------------
    // Resolved-timestamp cache (10-min TTL)
    // ------------------------------------------------------------------

    @Test fun `timestamp cache starts unresolved`() {
        val cache = WmsTimestampCache()
        assertNull(cache.resolvedTime(WmsSatelliteLayer.VISIBLE))
        assertTrue(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs))
    }

    @Test fun `timestamp cache is valid within the 10-minute TTL`() {
        val cache = WmsTimestampCache()
        cache.recordResolved(WmsSatelliteLayer.VISIBLE, t1800Ms, nowMs)
        assertEquals(t1800Ms, cache.resolvedTime(WmsSatelliteLayer.VISIBLE))
        assertFalse(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs))
        assertFalse(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs + 9L * 60L * 1000L))
        // Exactly at the TTL it is still valid ("older than 10 min" is the bound).
        assertFalse(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs + 10L * 60L * 1000L))
    }

    @Test fun `timestamp cache expires past the 10-minute TTL`() {
        val cache = WmsTimestampCache()
        cache.recordResolved(WmsSatelliteLayer.VISIBLE, t1800Ms, nowMs)
        assertTrue(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs + 10L * 60L * 1000L + 1L))
        assertTrue(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs + 20L * 60L * 1000L))
    }

    @Test fun `fetch failure invalidates the resolution`() {
        val cache = WmsTimestampCache()
        cache.recordResolved(WmsSatelliteLayer.VISIBLE, t1800Ms, nowMs)
        assertFalse(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs))
        cache.recordFetchFailure(WmsSatelliteLayer.VISIBLE)
        assertNull(cache.resolvedTime(WmsSatelliteLayer.VISIBLE))
        assertTrue(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs))
    }

    @Test fun `timestamp cache is per layer`() {
        val cache = WmsTimestampCache()
        cache.recordResolved(WmsSatelliteLayer.VISIBLE, t1800Ms, nowMs)
        assertNull(cache.resolvedTime(WmsSatelliteLayer.INFRARED))
        assertTrue(cache.needsResolve(WmsSatelliteLayer.INFRARED, nowMs))
        assertFalse(cache.needsResolve(WmsSatelliteLayer.VISIBLE, nowMs))
    }

    // ------------------------------------------------------------------
    // Blank detection thresholds
    // ------------------------------------------------------------------

    @Test fun `pure black is blank`() {
        val samples = List(1000) { Triple(0, 0, 0) }
        val stats = wmsSampleStats(samples)
        assertEquals(0.0, stats.stddev, 0.0)
        assertEquals(0.0, stats.meanR, 0.0)
        assertEquals(1.0, stats.nearBlackFraction, 0.0)
        assertTrue(isBlankSample(samples))
    }

    @Test fun `uniform near-black is blank`() {
        // All channels 5 (< 8): zero variance AND 100% near-black.
        val samples = List(1000) { Triple(5, 5, 5) }
        assertTrue(isBlankSample(samples))
    }

    @Test fun `mostly black with a few bright pixels is blank by the near-black rule`() {
        // 1996 black + 4 white: pooled stddev ~11.4 (well above 1.0) but
        // near-black fraction 0.998 > 0.995, so blank by the second rule alone.
        val samples = List(1996) { Triple(0, 0, 0) } + List(4) { Triple(255, 255, 255) }
        val stats = wmsSampleStats(samples)
        assertTrue(stats.stddev > 1.0)
        assertEquals(0.998, stats.nearBlackFraction, 0.0005)
        assertTrue(isBlankSample(samples))
    }

    @Test fun `real imagery stats are not blank`() {
        // Half dark cloud-shadow, half bright cloud: pooled stddev ~60,
        // matching the probed real GeoColor image (std ~60, mean ~(108, 119, 117)).
        val samples = List(100) { Triple(50, 60, 55) } + List(100) { Triple(170, 180, 175) }
        val stats = wmsSampleStats(samples)
        assertEquals(110.0, stats.meanR, 0.001)
        assertEquals(120.0, stats.meanG, 0.001)
        assertEquals(115.0, stats.meanB, 0.001)
        assertEquals(60.14, stats.stddev, 0.01)
        assertEquals(0.0, stats.nearBlackFraction, 0.0)
        assertFalse(isBlankSample(samples))
    }

    @Test fun `near-black fraction boundary is strict`() {
        // Exactly 99.5% near-black (199 of 200): 0.995 is NOT > 0.995,
        // and the single bright pixel lifts stddev above 1.0 — not blank.
        // (In practice the probe's grid never lands exactly on the boundary;
        // this pins the strictness of the rule.)
        val samples = List(199) { Triple(0, 0, 0) } + listOf(Triple(255, 255, 255))
        val stats = wmsSampleStats(samples)
        assertEquals(0.995, stats.nearBlackFraction, 0.0005)
        assertTrue(stats.stddev > 1.0)
        assertFalse(isBlankSample(samples))
    }

    @Test fun `empty sample list is blank`() {
        val stats = wmsSampleStats(emptyList())
        assertEquals(0.0, stats.stddev, 0.0)
        assertEquals(1.0, stats.nearBlackFraction, 0.0)
        assertTrue(isBlankSample(emptyList()))
    }

    @Test fun `single grey pixel is blank (zero variance)`() {
        assertTrue(isBlankSample(listOf(Triple(200, 200, 200))))
    }

    @Test fun `single coloured pixel is not blank (channel spread is variance)`() {
        // Pooled stddev of (200, 210, 190) is ~8.2, not 0: the pooled
        // stddev measures spread across channels too, exactly as the
        // probe's flat.std() does. One pixel proves imagery only when
        // its channels disagree.
        val stats = wmsSampleStats(listOf(Triple(200, 210, 190)))
        assertEquals(8.16, stats.stddev, 0.01)
        assertFalse(isBlankSample(listOf(Triple(200, 210, 190))))
    }

    // ------------------------------------------------------------------
    // XML-body guard
    // ------------------------------------------------------------------

    @Test fun `xml prolog body is an error`() {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ServiceExceptionReport>"
            .toByteArray(Charsets.US_ASCII)
        assertTrue(isXmlErrorBody(body))
    }

    @Test fun `ServiceException root body is an error`() {
        val body = "<ServiceExceptionReport><ServiceException>GDAL: IReadBlock failed</ServiceException>"
            .toByteArray(Charsets.US_ASCII)
        assertTrue(isXmlErrorBody(body))
    }

    @Test fun `ServiceException later in the first 2000 bytes is an error`() {
        val body = ByteArray(500) { ' '.code.toByte() } +
            "ServiceException: Unable to download block".toByteArray(Charsets.US_ASCII)
        assertTrue(isXmlErrorBody(body))
    }

    @Test fun `ServiceException beyond 2000 bytes is not caught by the guard`() {
        // The probe only scans the first 2000 bytes; a marker past that is out of scope.
        val body = ByteArray(2500) { ' '.code.toByte() } +
            "ServiceException".toByteArray(Charsets.US_ASCII)
        assertFalse(isXmlErrorBody(body))
    }

    @Test fun `jpeg magic bytes are not an error`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
            ByteArray(100) { 0 }
        assertFalse(isXmlErrorBody(jpeg))
    }

    @Test fun `empty body is not an error`() {
        assertFalse(isXmlErrorBody(ByteArray(0)))
    }

    // ------------------------------------------------------------------
    // Image-size cap
    // ------------------------------------------------------------------

    @Test fun `image size is viewport times 1_5 uncapped`() {
        // 1080x1920 screen -> 1620x2880 (the probe's full GetMap size).
        assertEquals(1620 to 2880, wmsImageSizeForViewport(1080, 1920))
        assertEquals(1200 to 1200, wmsImageSizeForViewport(800, 800))
    }

    @Test fun `image size long edge is capped at 3000 preserving aspect`() {
        // 1080x2400 screen -> 1620x3600 uncapped -> scaled to 1350x3000.
        assertEquals(1350 to 3000, wmsImageSizeForViewport(1080, 2400))
        // Wide screen: 2560x1440 -> 3840x2160 uncapped -> scaled to 3000x1688.
        assertEquals(3000 to 1688, wmsImageSizeForViewport(2560, 1440))
    }

    @Test fun `image size at exactly the cap is unchanged`() {
        assertEquals(2000 to 3000, wmsImageSizeForViewport(1333, 2000))
    }

    // ------------------------------------------------------------------
    // Bbox ratio 1.5 math
    // ------------------------------------------------------------------

    @Test fun `metres per pixel at z6 and z5`() {
        assertEquals(2445.98490512564, metresPerPixel(6), 0.000001)
        assertEquals(4891.96981025128, metresPerPixel(5), 0.000001)
    }

    @Test fun `lon lat to 3857 matches the probe centre`() {
        val (x, y) = lonLatTo3857(-84.6027, 38.7833)
        assertEquals(-9417929.48, x, 0.01)
        assertEquals(4690678.52, y, 0.01)
        val (ox, oy) = lonLatTo3857(0.0, 0.0)
        assertEquals(0.0, ox, 0.0)
        // ln(tan(pi/4)) is not exactly 0.0 in floating point (~-7e-10).
        assertEquals(0.0, oy, 1e-6)
    }

    @Test fun `bbox is viewport times 1_5 centred on the centre`() {
        // 1080x1920 at z6: viewport 1080*2445.98 = 2,641,663.71 m wide;
        // bbox = x1.5 = 3,962,495.55 m wide, 7,044,436.53 m tall.
        val bbox = wmsBbox3857(-84.6027, 38.7833, 1080, 1920, 6)
        assertEquals(3962495.55, bbox.widthMetres, 0.01)
        assertEquals(7044436.53, bbox.heightMetres, 0.01)
        val (cx, cy) = lonLatTo3857(-84.6027, 38.7833)
        assertEquals(cx, (bbox.minX + bbox.maxX) / 2.0, 0.01)
        assertEquals(cy, (bbox.minY + bbox.maxY) / 2.0, 0.01)
        // And the bbox is exactly 1.5x the un-buffered viewport in each axis.
        val mpp = metresPerPixel(6)
        assertEquals(1080 * mpp * 1.5, bbox.widthMetres, 0.01)
        assertEquals(1920 * mpp * 1.5, bbox.heightMetres, 0.01)
    }

    @Test fun `bbox scales with zoom`() {
        // Same screen at z5 covers twice the ground per pixel as z6.
        val z6 = wmsBbox3857(-84.6027, 38.7833, 1080, 1920, 6)
        val z5 = wmsBbox3857(-84.6027, 38.7833, 1080, 1920, 5)
        assertEquals(2.0 * z6.widthMetres, z5.widthMetres, 0.01)
        assertEquals(2.0 * z6.heightMetres, z5.heightMetres, 0.01)
    }

    @Test fun `bbox is a data class with sane values`() {
        val bbox = WmsBbox3857(-1.0, -2.0, 3.0, 4.0)
        assertEquals(4.0, bbox.widthMetres, 0.0)
        assertEquals(6.0, bbox.heightMetres, 0.0)
    }

    // ------------------------------------------------------------------
    // Cost text (actual bytes only)
    // ------------------------------------------------------------------

    @Test fun `satellite cost text formats the real byte count`() {
        // Probed full GetMap sizes: VIS 854,026 B, IR 332,976 B.
        assertEquals("834 KB PER SWITCH", satelliteCostText(854026L))
        assertEquals("325.2 KB PER SWITCH", satelliteCostText(332976L))
        assertEquals("96 B PER SWITCH", satelliteCostText(96L))
        assertEquals("2.6 MB PER SWITCH", satelliteCostText(2726297L))
    }

    @Test fun `satellite cost text never hardcodes the old estimates`() {
        // The old text said ABOUT 6 MB / ABOUT 5 MB regardless of reality.
        // A 0-byte fetch must not print 6 MB, and a real fetch must print its own size.
        assertEquals("0 B PER SWITCH", satelliteCostText(0L))
        assertFalse(satelliteCostText(854026L).contains("6 MB"))
    }
}
