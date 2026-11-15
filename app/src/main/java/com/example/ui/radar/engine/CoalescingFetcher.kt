package com.example.ui.radar.engine

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * Phase 2 — the single fetcher for all radar single-image / proving
 * traffic (rebuild §3.2.4 / §3.5).
 *
 * One dedicated OkHttp client, one in-flight map, one negative cache,
 * one byte counter — shared by every radar fetch the engine issues:
 * MRMS exportImage bytes, MRMS + LibreWXR metadata, and the tile
 * bodies that PROVE a tiled frame ready. MapLibre's own tile manager
 * still fetches the tiles it DISPLAYS (there is no API to hand it
 * bytes); the fetcher proves readiness, the map draws.
 *
 * Rules, from [CoalescingPolicy] (the policy file owns the numbers):
 * - **Single flight.** A second fetch for an in-flight URL awaits the
 *   SAME [Deferred] — never a duplicate request. This is what makes
 *   the store's [RadarLoopStore.retry] safe: `retry()` marks frames
 *   IN_FLIGHT immediately, so a second retry before settlement
 *   re-requests the same URLs, and the map collapses them to the one
 *   in-flight request.
 * - **Retry-on-truncation only.** A short read ([FetchFailureKind.TRUNCATED_READ])
 *   is retried, up to [FETCH_MAX_ATTEMPTS] attempts total; every other
 *   failure kind is returned as-is. A 404 is a fact about the URL: it
 *   goes to the per-generation [NegativeCache] and is never retried
 *   within the generation.
 * - **Byte counting.** Every byte actually received (including the
 *   partial bytes of a truncated attempt) is counted per generation,
 *   for the honest cost text ([loopLoadCostText]) — never a fixed
 *   estimate.
 * - **Timeouts that fail fast.** Connect 10 s, read 20 s: the read
 *   bound covers the ~2.8 s median MRMS server render plus slow
 *   mobile data; the loop's 20 s slow budget (not the socket) is the
 *   real stall bound. Metadata calls get their own tighter bound in
 *   the activity (`withTimeout`, 12 s) and run in PARALLEL, never
 *   sequential.
 *
 * The client is DEDICATED, not [com.example.core.HttpClients.tileDisplayClient]:
 * that client has the same 16/host + 64 limits but IS the Coil display
 * lane — sharing it would share its queue with the display loader,
 * the exact split this fetcher exists to end.
 *
 * Lifecycle: the activity owns one fetcher and calls [close] in
 * `onDestroy`. Fetching is `suspend` and safe to call from any
 * coroutine; a caller's cancellation never cancels the shared
 * in-flight request (the [Deferred] lives in the fetcher's own scope).
 */
sealed class FetchResult {
    /** Bytes in hand. */
    data class Success(val bytes: ByteArray) : FetchResult()

    /** HTTP 404 — recorded in the per-generation negative cache. */
    object NotFound : FetchResult()

    /** Settled failure, classified (only TRUNCATED_READ is retried). */
    data class Failed(val kind: FetchFailureKind) : FetchResult()
}

class CoalescingFetcher(
    private val client: OkHttpClient = newClient()
) {

    companion object {
        /**
         * The dedicated radar client: 16 requests/host, 64 total
         * (the rebuild's single-lane numbers), connect 10 s, read
         * 20 s. No interceptors: MRMS / LibreWXR / IEM are not
         * api.weather.gov, so the NWS headers do not apply, and the
         * tile hosts must never receive them.
         */
        fun newClient(): OkHttpClient = OkHttpClient.Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                }
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<FetchResult>>()
    private val negativeCache = NegativeCache()
    private val bytesByGeneration = mutableMapOf<Long, Long>()

    /**
     * Fetch [url] once, coalesced: a second caller for an in-flight
     * URL awaits the same [Deferred] and receives the same result.
     * Returns [FetchResult.NotFound] immediately (no network) when
     * the URL is negatively cached in [generationId]. A completed
     * entry is removed from the in-flight map on completion, so a
     * later generation re-probes the URL (a pruned frame can
     * backfill; a bad timestamp can become good).
     */
    suspend fun fetch(url: String, generationId: Long): FetchResult {
        if (negativeCache.isBlocked(url, generationId)) return FetchResult.NotFound
        val deferred = mutex.withLock {
            inFlight.getOrPut(url) {
                scope.async { fetchWithRetries(url, generationId) }.also { created ->
                    created.invokeOnCompletion {
                        scope.launch {
                            mutex.withLock {
                                if (inFlight[url] === created) inFlight.remove(url)
                            }
                        }
                    }
                }
            }
        }
        return deferred.await()
    }

    /**
     * Fetch [url] as text (MRMS service info, LibreWXR maps JSON),
     * or null on any failure (404, network error, timeout). The bytes
     * still count toward the generation's cost total.
     */
    suspend fun fetchText(url: String, generationId: Long): String? {
        val result = fetch(url, generationId)
        val bytes = (result as? FetchResult.Success)?.bytes ?: return null
        return bytes.decodeToString()
    }

    /** Actual bytes fetched for [generationId] (all attempts, all URLs). */
    fun totalBytesFetched(generationId: Long): Long =
        synchronized(bytesByGeneration) { bytesByGeneration[generationId] ?: 0L }

    /**
     * End [generationId]: forget its 404s and drop its byte total.
     * Call exactly when the store resolves a new generation — never
     * per frame.
     */
    fun endGeneration(generationId: Long) {
        negativeCache.endGeneration(generationId)
        synchronized(bytesByGeneration) { bytesByGeneration.remove(generationId) }
    }

    /** Cancel all in-flight fetches (activity teardown). */
    fun close() {
        scope.cancel()
    }

    // -- Internals ---------------------------------------------------------

    private suspend fun fetchWithRetries(url: String, generationId: Long): FetchResult {
        var attempts = 0
        while (true) {
            attempts += 1
            when (val outcome = fetchOnce(url, generationId)) {
                is FetchResult.Success -> return outcome
                is FetchResult.NotFound -> return outcome
                is FetchResult.Failed -> {
                    if (!isTruncationRetryable(outcome.kind, attempts)) return outcome
                    // TRUNCATED_READ with attempts left: retry the same URL.
                }
            }
        }
    }

    /**
     * One attempt at [url]. The body is read in chunks through its
     * [okio.BufferedSource] with a running byte count, so a short read
     * is DETECTED (fewer bytes than `Content-Length` promised) even
     * when the transport reports the failure only as an [IOException]
     * on the stream — `ResponseBody.bytes()` would throw without
     * saying how much arrived. Chunked / unknown-length bodies
     * (`Content-Length` -1) cannot prove truncation, so an [IOException]
     * there classifies as a network error, not a truncation.
     */
    private fun fetchOnce(url: String, generationId: Long): FetchResult {
        val request = Request.Builder().url(url).build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            val kind = classifyFetchFailure(
                statusCode = null,
                bytesReceived = 0L,
                contentLength = null,
                exceptionName = e.javaClass.simpleName
            ) ?: FetchFailureKind.NETWORK_ERROR
            return FetchResult.Failed(kind)
        }
        response.use { resp ->
            val status = resp.code
            val contentLength = resp.body?.contentLength()?.takeIf { it >= 0 }
            if (status == 404) {
                negativeCache.record(url, generationId, 404)
                return FetchResult.NotFound
            }
            if (!resp.isSuccessful) {
                val kind = classifyFetchFailure(status, 0L, contentLength)
                    ?: FetchFailureKind.HTTP_OTHER
                return FetchResult.Failed(kind)
            }
            val source = resp.body?.source()
                ?: return FetchResult.Failed(FetchFailureKind.NETWORK_ERROR)
            val sink = Buffer()
            var received = 0L
            try {
                while (true) {
                    val read = source.read(sink, 8192)
                    if (read == -1L) break
                    received += read
                }
            } catch (e: IOException) {
                countBytes(generationId, received)
                val kind = classifyFetchFailure(
                    status, received, contentLength, e.javaClass.simpleName
                ) ?: FetchFailureKind.NETWORK_ERROR
                return FetchResult.Failed(kind)
            }
            countBytes(generationId, received)
            if (isTruncatedRead(received, contentLength)) {
                return FetchResult.Failed(FetchFailureKind.TRUNCATED_READ)
            }
            return FetchResult.Success(sink.readByteArray())
        }
    }

    private fun countBytes(generationId: Long, bytes: Long) {
        if (bytes <= 0) return
        synchronized(bytesByGeneration) {
            bytesByGeneration[generationId] =
                (bytesByGeneration[generationId] ?: 0L) + bytes
        }
    }
}
