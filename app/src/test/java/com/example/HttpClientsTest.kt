package com.example

import com.example.core.HttpClients
import com.example.core.NwsHeaderInterceptor
import com.example.core.ProviderEndpoints
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

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

        // OkHttp 5.x Chain members. The interceptor under test only calls
        // request() and proceed(); these are stubs so the fake satisfies the
        // interface. with* returns this (the fake never reconfigures).
        override val followSslRedirects: Boolean = true
        override val followRedirects: Boolean = true
        override val dns: Dns = Dns.SYSTEM
        override val socketFactory: SocketFactory = SocketFactory.getDefault()
        override val retryOnConnectionFailure: Boolean = true
        override val authenticator: Authenticator = Authenticator.NONE
        override val cookieJar: CookieJar = CookieJar.NO_COOKIES
        override val cache: Cache? = null
        override val proxy: Proxy? = null
        override val proxySelector: ProxySelector = object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)
            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {}
        }
        override val proxyAuthenticator: Authenticator = Authenticator.NONE
        override val sslSocketFactoryOrNull: SSLSocketFactory? = null
        override val x509TrustManagerOrNull: X509TrustManager? = null
        override val hostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }
        override val certificatePinner: CertificatePinner = CertificatePinner.DEFAULT
        override val connectionPool: ConnectionPool = ConnectionPool()
        override val eventListener: EventListener = EventListener.NONE

        override fun withDns(dns: Dns): Interceptor.Chain = this
        override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain = this
        override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this
        override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this
        override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain = this
        override fun withCache(cache: Cache?): Interceptor.Chain = this
        override fun withProxy(proxy: Proxy?): Interceptor.Chain = this
        override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this
        override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain = this
        override fun withSslSocketFactory(sslSocketFactory: SSLSocketFactory?, x509TrustManager: X509TrustManager?): Interceptor.Chain = this
        override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain = this
        override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain = this
        override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain = this
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
