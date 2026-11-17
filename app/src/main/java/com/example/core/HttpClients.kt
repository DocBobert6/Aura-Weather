package com.example.core

import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * The NWS header interceptor (3.4). Adds the NWS User-Agent (with the real
 * contact — the GitHub repo URL) and the geo+json Accept header to every
 * request it sees. It is a named class (not an anonymous lambda) so tests
 * can assert which clients carry it: the NWS client must, the Open-Meteo
 * clients must not.
 */
class NwsHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", ProviderEndpoints.NWS_USER_AGENT)
            .header("Accept", ProviderEndpoints.NWS_ACCEPT_HEADER)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Sets a fixed User-Agent on every request it sees (0.4.0). Nominatim's
 * usage policy requires a valid User-Agent identifying the app; the
 * NWS interceptor is not reused here because it also adds the
 * geo+json Accept header, which is wrong for a non-NWS host.
 */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}

/**
 * The two OkHttp clients (3.4). Both share the timeouts and the BASIC
 * logging interceptor; only the NWS client carries the NWS headers.
 * Open-Meteo must never receive NWS headers, so it gets its own plain
 * client instead of sharing the NWS one.
 */
object HttpClients {
    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })

    fun nwsClient(): OkHttpClient =
        baseBuilder()
            .addInterceptor(NwsHeaderInterceptor())
            .build()

    fun plainClient(): OkHttpClient =
        baseBuilder()
            .build()

    /**
     * Nominatim client (0.4.0): the plain client plus the app
     * User-Agent Nominatim's usage policy requires. No NWS headers
     * (it is not an NWS endpoint) and no geo+json Accept.
     */
    fun nominatimClient(): OkHttpClient =
        baseBuilder()
            .addInterceptor(UserAgentInterceptor(ProviderEndpoints.NOMINATIM_USER_AGENT))
            .build()

    /**
     * Tile client (AUDIT6 section 9 Fix 1). The app had no custom
     * ImageLoader, so Coil used a plain OkHttpClient with the default
     * dispatcher: 5 requests per host. One radar frame is 77 tiles to
     * one host, so a frame took ~16 s to arrive while the loop steps
     * every 2.75 s at most.
     *
     * Display lane: 16 requests per host (audit range 10-16), 64
     * total. This is the Coil singleton's client; every AsyncImage
     * on screen uses it. (The old prefetch lane — a second
     * ImageLoader for the retired tile screen — was deleted at the
     * Phase 5 cutover; the engine fetches through its own client.)
     *
     * It shares the same timeouts as the API clients, but carries
     * neither the NWS header interceptor (tile hosts are
     * IEM / Esri / GIBS, not api.weather.gov) nor the logging
     * interceptor (a tile burst would flood logcat). Same tiles,
     * faster — no change in data volume.
     */
    const val TILE_DISPLAY_MAX_REQUESTS_PER_HOST = 16
    const val TILE_DISPLAY_MAX_REQUESTS = 64

    private fun tileBuilder(maxRequests: Int, maxRequestsPerHost: Int): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .dispatcher(
                Dispatcher().apply {
                    this.maxRequests = maxRequests
                    this.maxRequestsPerHost = maxRequestsPerHost
                }
            )

    fun tileDisplayClient(): OkHttpClient =
        tileBuilder(
            maxRequests = TILE_DISPLAY_MAX_REQUESTS,
            maxRequestsPerHost = TILE_DISPLAY_MAX_REQUESTS_PER_HOST
        ).build()

}
