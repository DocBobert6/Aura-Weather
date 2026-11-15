package com.example

import com.example.core.HttpClients
import com.example.core.NwsHeaderInterceptor
import com.example.core.ProviderEndpoints
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pure tests for 3.4 (NWS User-Agent + interceptor split). They also run
 * in CI (testDebugUnitTest); run-pure-tests.sh compiles this file so they
 * run locally too (it needs the OkHttp jars in test-libs on its classpath).
 *
 * The fake chain is the logging-interceptor-capture equivalent: it records
 * the exact request the interceptor hands to proceed(), so the header
 * assertions are on what actually goes out on the wire, not on a
 * constant.
 */
class HttpClientsTest {

    private val repoUrl = "https://github.com/DocBobert6/Aura-Weather"

    /** Minimal Interceptor.Chain that captures the request proceed() receives. */
    private class CapturingChain(private val original: Request) : Interceptor.Chain {
        var captured: Request? = null
            private set

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            captured = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException("not used by this test")
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    }

    @Test
    fun testUserAgentCarriesRepoUrl() {
        assertEquals("AuraWeather/1.0 ($repoUrl)", ProviderEndpoints.NWS_USER_AGENT)
        assertFalse(
            "The placeholder contact must be gone",
            ProviderEndpoints.NWS_USER_AGENT.contains("auraweather.local")
        )
    }

    @Test
    fun testNwsClientCarriesNwsInterceptor() {
        val interceptors = HttpClients.nwsClient().interceptors
        assertTrue(
            "The NWS client must carry the NWS header interceptor",
            interceptors.any { it is NwsHeaderInterceptor }
        )
    }

    @Test
    fun testPlainClientCarriesNoNwsInterceptor() {
        val interceptors = HttpClients.plainClient().interceptors
        assertFalse(
            "The Open-Meteo client must not carry the NWS header interceptor",
            interceptors.any { it is NwsHeaderInterceptor }
        )
    }

    @Test
    fun testTileDisplayClientDispatcherLimits() {
        val dispatcher = HttpClients.tileDisplayClient().dispatcher
        assertEquals(
            "Display lane: 16 requests per host (AUDIT6 Fix 1 range 10-16)",
            HttpClients.TILE_DISPLAY_MAX_REQUESTS_PER_HOST,
            dispatcher.maxRequestsPerHost
        )
        assertEquals(16, dispatcher.maxRequestsPerHost)
        assertEquals(HttpClients.TILE_DISPLAY_MAX_REQUESTS, dispatcher.maxRequests)
        assertEquals(64, dispatcher.maxRequests)
    }

    @Test
    fun testTilePrefetchClientDispatcherLimits() {
        val dispatcher = HttpClients.tilePrefetchClient().dispatcher
        assertEquals(
            "Prefetch lane stays at the old 5-per-host level so a burst cannot starve display",
            HttpClients.TILE_PREFETCH_MAX_REQUESTS_PER_HOST,
            dispatcher.maxRequestsPerHost
        )
        assertEquals(5, dispatcher.maxRequestsPerHost)
        assertEquals(HttpClients.TILE_PREFETCH_MAX_REQUESTS, dispatcher.maxRequests)
        assertEquals(32, dispatcher.maxRequests)
    }

    @Test
    fun testTileLanesAreSeparateDispatchers() {
        val display = HttpClients.tileDisplayClient()
        val prefetch = HttpClients.tilePrefetchClient()
        assertNotSame(
            "Display and prefetch must not share a dispatcher, or prefetch could queue behind display",
            display.dispatcher,
            prefetch.dispatcher
        )
        assertNotSame(display, prefetch)
    }

    @Test
    fun testTileClientsCarryNoNwsHeadersAndNoLogging() {
        for (client in listOf(HttpClients.tileDisplayClient(), HttpClients.tilePrefetchClient())) {
            assertFalse(
                "Tile hosts are IEM / Esri / GIBS, not api.weather.gov — no NWS headers",
                client.interceptors.any { it is NwsHeaderInterceptor }
            )
            assertFalse(
                "A tile burst would flood logcat — no logging interceptor on tile lanes",
                client.interceptors.any { it is HttpLoggingInterceptor }
            )
            assertTrue("Tile lanes carry no interceptors at all", client.interceptors.isEmpty())
        }
    }

    @Test
    fun testNwsInterceptorSetsHeadersOnTheRequest() {
        val original = Request.Builder()
            .url("https://api.weather.gov/points/39.0,-84.5")
            .build()
        val chain = CapturingChain(original)
        NwsHeaderInterceptor().intercept(chain)
        val sent = chain.captured
            ?: throw AssertionError("The interceptor never called proceed()")
        assertEquals("AuraWeather/1.0 ($repoUrl)", sent.header("User-Agent"))
        assertEquals("application/geo+json", sent.header("Accept"))
        assertEquals(original.url, sent.url)
    }
}
