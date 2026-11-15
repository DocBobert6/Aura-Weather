package com.example

import com.example.ui.radar.engine.CacheTtl
import com.example.ui.radar.engine.FetchFailureKind
import com.example.ui.radar.engine.NegativeCache
import com.example.ui.radar.engine.classifyFetchFailure
import com.example.ui.radar.engine.cacheTtlForUrl
import com.example.ui.radar.engine.formatDataSize
import com.example.ui.radar.engine.isTruncatedRead
import com.example.ui.radar.engine.isTruncationRetryable
import com.example.ui.radar.engine.loopLoadCostText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for CoalescingPolicy. */
class CoalescingPolicyTest {

    // -- Retry-on-truncation -------------------------------------------

    @Test fun `short read against a positive length is a truncation`() {
        assertTrue(isTruncatedRead(100, 1000))
        assertTrue(isTruncatedRead(0, 4280))
        assertFalse(isTruncatedRead(1000, 1000))
        assertFalse(isTruncatedRead(1500, 1000))
        // Unknown / chunked length cannot prove truncation.
        assertFalse(isTruncatedRead(100, null))
        assertFalse(isTruncatedRead(100, 0))
        assertFalse(isTruncatedRead(100, -1))
    }

    @Test fun `only truncation is retryable, and only up to 3 attempts`() {
        assertTrue(isTruncationRetryable(FetchFailureKind.TRUNCATED_READ, 1))
        assertTrue(isTruncationRetryable(FetchFailureKind.TRUNCATED_READ, 2))
        assertFalse(isTruncationRetryable(FetchFailureKind.TRUNCATED_READ, 3))
        assertFalse(isTruncationRetryable(FetchFailureKind.TRUNCATED_READ, 4))
        assertFalse(isTruncationRetryable(FetchFailureKind.HTTP_NOT_FOUND, 1))
        assertFalse(isTruncationRetryable(FetchFailureKind.HTTP_SERVER_ERROR, 1))
        assertFalse(isTruncationRetryable(FetchFailureKind.NETWORK_ERROR, 1))
        assertFalse(isTruncationRetryable(FetchFailureKind.TIMEOUT, 1))
        assertFalse(isTruncationRetryable(FetchFailureKind.HTTP_OTHER, 1))
    }

    @Test fun `classifyFetchFailure distinguishes the kinds`() {
        assertEquals(
            FetchFailureKind.TRUNCATED_READ,
            classifyFetchFailure(200, 100, 1000)
        )
        assertEquals(null, classifyFetchFailure(200, 1000, 1000))
        assertEquals(
            FetchFailureKind.HTTP_NOT_FOUND,
            classifyFetchFailure(404, 0, null)
        )
        assertEquals(
            FetchFailureKind.HTTP_SERVER_ERROR,
            classifyFetchFailure(503, 0, null)
        )
        assertEquals(
            FetchFailureKind.HTTP_OTHER,
            classifyFetchFailure(429, 0, null)
        )
        assertEquals(
            FetchFailureKind.TIMEOUT,
            classifyFetchFailure(null, 0, null, "SocketTimeoutException")
        )
        assertEquals(
            FetchFailureKind.TRUNCATED_READ,
            classifyFetchFailure(null, 50, 500, "IncompleteRead")
        )
        assertEquals(
            FetchFailureKind.NETWORK_ERROR,
            classifyFetchFailure(null, 0, null, "UnknownHostException")
        )
    }

    // -- Per-generation negative caching --------------------------------

    @Test fun `a 404 is blocked within its generation only`() {
        val cache = NegativeCache()
        val url = "https://api.librewxr.net/v2/radar/1794716400/256/7/33/48/2/1_1.png"
        assertTrue(cache.record(url, 1L, 404))
        assertTrue(cache.isBlocked(url, 1L))
        assertEquals(1, cache.blockedCount(1L))
        // Other generations are unaffected.
        assertFalse(cache.isBlocked(url, 2L))
        // Ending the generation forgets the 404 (pruned frames backfill).
        cache.endGeneration(1L)
        assertFalse(cache.isBlocked(url, 1L))
        assertEquals(0, cache.blockedCount(1L))
    }

    @Test fun `only 404s are recorded`() {
        val cache = NegativeCache()
        val url = "https://example.com/tile.png"
        assertFalse(cache.record(url, 1L, 500))
        assertFalse(cache.record(url, 1L, 200))
        assertFalse(cache.record(url, 1L, 429))
        assertFalse(cache.isBlocked(url, 1L))
    }

    @Test fun `clearAll forgets every generation`() {
        val cache = NegativeCache()
        cache.record("https://a.example/x", 1L, 404)
        cache.record("https://b.example/y", 2L, 404)
        cache.clearAll()
        assertFalse(cache.isBlocked("https://a.example/x", 1L))
        assertFalse(cache.isBlocked("https://b.example/y", 2L))
    }

    // -- Explicit TTLs by URL -------------------------------------------

    @Test fun `MRMS gets 12 hours`() {
        val ttl = cacheTtlForUrl(
            "https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/" +
                "radar_base_reflectivity_time/ImageServer/exportImage?bbox=-87.5,36.6,-81.5,41.6" +
                "&bboxSR=4326&size=1080,1000&imageSR=3857&format=png32&time=1794781497000&f=image"
        )
        assertEquals(CacheTtl.Seconds(43200L), ttl)
    }

    @Test fun `IEM and HRRR get 5 minutes`() {
        assertEquals(
            CacheTtl.Seconds(300L),
            cacheTtlForUrl("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/7/33/48.png")
        )
        assertEquals(
            CacheTtl.Seconds(300L),
            cacheTtlForUrl("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/hrrr::REFD-F0075-0/7/33/48.png")
        )
    }

    @Test fun `Esri basemap gets 24 hours`() {
        assertEquals(
            CacheTtl.Seconds(86400L),
            cacheTtlForUrl("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/7/48/33")
        )
    }

    @Test fun `GIBS is timestamp-is-version`() {
        assertEquals(
            CacheTtl.TimestampIsVersion,
            cacheTtlForUrl(
                "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/GOES-East_ABI_GeoColor/" +
                    "default/2026-11-15T19:00:00Z/GoogleMapsCompatible_Level7/6/24/16.png"
            )
        )
    }

    @Test fun `LibreWXR maps never cache, tiles get 5 minutes`() {
        assertEquals(
            CacheTtl.NoCache,
            cacheTtlForUrl("https://api.librewxr.net/public/weather-maps.json")
        )
        assertEquals(
            CacheTtl.Seconds(300L),
            cacheTtlForUrl("https://api.librewxr.net/v2/radar/1794716400/256/7/33/48/2/1_1.png")
        )
    }

    @Test fun `unknown hosts get Unknown`() {
        assertEquals(CacheTtl.Unknown, cacheTtlForUrl("https://example.com/tiles/1/2/3.png"))
        assertEquals(CacheTtl.Unknown, cacheTtlForUrl("not a url"))
    }

    // -- Cost text from actual bytes ------------------------------------

    @Test fun `formatDataSize uses real byte counts`() {
        assertEquals("96 B", formatDataSize(96))
        assertEquals("0 B", formatDataSize(0))
        assertEquals("0 B", formatDataSize(-5))
        assertEquals("428 KB", formatDataSize(438272))
        assertEquals("2.6 MB", formatDataSize(2726297))
        assertEquals("1 KB", formatDataSize(1024))
        assertEquals("1 MB", formatDataSize(1024 * 1024))
    }

    @Test fun `loop cost text carries the measured bytes, never an estimate`() {
        // The old fixed text said 100 KB for an MRMS loop; the measured
        // 10-frame loop is ~2.6 MB. The text must say the measured number.
        assertEquals(
            "FIRST LOAD 2.6 MB THEN CACHED",
            loopLoadCostText(2726297)
        )
        assertEquals(
            "FIRST LOAD 160 KB THEN CACHED",
            loopLoadCostText(163840)
        )
    }
}
