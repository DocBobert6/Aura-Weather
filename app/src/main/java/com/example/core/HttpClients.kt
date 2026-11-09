package com.example.core

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
}
