package com.example.ui.radar.engine

/**
 * Phase 2 — pure policy helpers for the loop fetcher (the wiring
 * agent builds the fetcher itself; this file is the policy it must
 * follow, with no network, no Android, no coroutines).
 *
 * Pure Kotlin: no Android, no Compose, no MapLibre, no OkHttp, no
 * coroutines — compiled and unit-tested locally with kotlinc + JUnit
 * (see `CoalescingPolicyTest`).
 *
 * Three policies, from the rebuild research (§3.2.4) and the Phase 0
 * findings — each is a rule the fetcher applies, not a suggestion:
 * 1. [Retry-on-truncation](#retry-on-truncation): a short read is
 *    retried; a 404 is never retried within a generation.
 * 2. [Per-generation negative caching](#per-generation-negative-caching):
 *    a 404 is remembered until the generation ends.
 * 3. [Explicit TTLs by URL](#explicit-ttls-by-url): the app owns
 *    cache lifetimes per host instead of inheriting server headers.
 * 4. [Cost text from actual bytes](#cost-text-from-actual-bytes):
 *    never a fixed estimate (the research §3.3 fix 5: fixed numbers
 *    were wrong and weather-dependent).
 */

// ---------------------------------------------------------------------------
// Retry-on-truncation
// ---------------------------------------------------------------------------

/** Total fetch attempts for one URL: 1 initial try + 2 retries. */
const val FETCH_MAX_ATTEMPTS: Int = 3

/**
 * How a fetch failed, as the fetcher reports it. Only
 * [TRUNCATED_READ] is retried under this policy: a short read is
 * the one failure mode Phase 0 proved recoverable (LibreWXR echo
 * tiles repeatedly failed under urllib with `IncompleteRead` and
 * `curl --retry` recovered all of them). A 404 is a fact about the
 * URL, not a transient failure — it goes to the negative cache and
 * is never retried within the generation. A 5xx / network error /
 * timeout is left to the fetcher's own backoff, if any; this policy
 * does not bless retrying it, because an unseen retry loop against
 * a down government service is a data-budget hit with no evidence
 * it helps.
 */
enum class FetchFailureKind {
    /** Fewer bytes arrived than the response promised (short read). */
    TRUNCATED_READ,

    /** HTTP 404: the URL does not exist (pruned frame, bad time). */
    HTTP_NOT_FOUND,

    /** HTTP 5xx: the server failed. */
    HTTP_SERVER_ERROR,

    /** Any other HTTP status (400, 429, …). */
    HTTP_OTHER,

    /** Transport failure before any status (DNS, connect, reset). */
    NETWORK_ERROR,

    /** The request exceeded its timeout. */
    TIMEOUT
}

/**
 * Classify a fetch outcome from what the fetcher observed:
 * [statusCode] null means no HTTP response arrived. [bytesReceived]
 * vs [contentLength] detects the short read; [exceptionName] is the
 * simple class name of any transport exception (e.g.
 * `"SocketTimeoutException"`, `"IncompleteRead"`) and is only read
 * when there is no status code.
 */
fun classifyFetchFailure(
    statusCode: Int?,
    bytesReceived: Long,
    contentLength: Long?,
    exceptionName: String? = null
): FetchFailureKind? {
    if (statusCode == null) {
        if (isTruncatedRead(bytesReceived, contentLength)) return FetchFailureKind.TRUNCATED_READ
        if (exceptionName != null && "Timeout" in exceptionName) return FetchFailureKind.TIMEOUT
        return FetchFailureKind.NETWORK_ERROR
    }
    return when (statusCode) {
        404 -> FetchFailureKind.HTTP_NOT_FOUND
        in 500..599 -> FetchFailureKind.HTTP_SERVER_ERROR
        in 200..299 -> if (isTruncatedRead(bytesReceived, contentLength)) {
            FetchFailureKind.TRUNCATED_READ
        } else {
            null // success: not a failure at all
        }
        else -> FetchFailureKind.HTTP_OTHER
    }
}

/**
 * Is this a short read — fewer bytes arrived than the response's
 * `Content-Length` promised? Null or non-positive lengths cannot
 * prove truncation (chunked / unknown-length bodies), so they
 * return false: only a positive promised length the body did not
 * reach counts. A zero-byte body against a positive length is a
 * truncation, not an empty success.
 */
fun isTruncatedRead(bytesReceived: Long, contentLength: Long?): Boolean =
    contentLength != null && contentLength > 0 && bytesReceived < contentLength

/**
 * May [failure] be retried after [attemptsMade] attempts? Only
 * [FetchFailureKind.TRUNCATED_READ] is retryable, and only while
 * fewer than [FETCH_MAX_ATTEMPTS] attempts have been made (so the
 * worst case is 1 initial try + 2 retries = 3 requests for one
 * URL, bounding the data cost of a persistently short read).
 */
fun isTruncationRetryable(failure: FetchFailureKind, attemptsMade: Int): Boolean =
    failure == FetchFailureKind.TRUNCATED_READ && attemptsMade < FETCH_MAX_ATTEMPTS

// ---------------------------------------------------------------------------
// Per-generation negative caching
// ---------------------------------------------------------------------------

/**
 * A 404 is never retried within a generation. The cache is keyed by
 * (generation, URL): a URL that 404s is remembered until the
 * generation ends, then forgotten — a new generation re-probes it,
 * because a pruned frame can backfill and a bad timestamp can
 * become good (Phase 0 spike 3: GIBS black-hole scans backfilled
 * within minutes; the same applies to MRMS times after an extent
 * refresh). Only 404s are recorded: any other failure kind is not
 * a fact about the URL and must not suppress a retry.
 *
 * The wiring agent owns the lifecycle: it calls [endGeneration]
 * with the OLD generation id exactly when [RadarLoopStore] resolves
 * a new generation (fallback or refresh that replaces the frame
 * list), and [clearAll] only on a full loop reset. It must never
 * call `endGeneration` per frame — that would defeat the cache.
 */
class NegativeCache {
    private val blockedByGeneration = mutableMapOf<Long, MutableSet<String>>()

    /**
     * Record a 404 for [url] in [generationId]. Returns true when
     * recorded; false when [statusCode] is not 404 (nothing is
     * recorded for any other status — see the class doc).
     */
    fun record(url: String, generationId: Long, statusCode: Int): Boolean {
        if (statusCode != 404) return false
        blockedByGeneration.getOrPut(generationId) { mutableSetOf() }.add(url)
        return true
    }

    /** Is [url] negatively cached in [generationId]? */
    fun isBlocked(url: String, generationId: Long): Boolean =
        blockedByGeneration[generationId]?.contains(url) == true

    /** How many URLs are blocked in [generationId]? (Tests / logging.) */
    fun blockedCount(generationId: Long): Int =
        blockedByGeneration[generationId]?.size ?: 0

    /**
     * End [generationId]: forget its 404s. A generation ends when
     * the loop re-resolves (fallback to another source, or a refresh
     * that replaces the frame list) — never per frame.
     */
    fun endGeneration(generationId: Long) {
        blockedByGeneration.remove(generationId)
    }

    /** Forget every generation (full loop reset only). */
    fun clearAll() {
        blockedByGeneration.clear()
    }
}

// ---------------------------------------------------------------------------
// Explicit TTLs by URL
// ---------------------------------------------------------------------------

/** MRMS disk-cache TTL: 12 h (`max-age=43200`, probed live). */
const val MRMS_TTL_SECONDS: Long = 43200L

/** IEM / HRRR disk-cache TTL: 5 min (`max-age=300`, probed live). */
const val IEM_TTL_SECONDS: Long = 300L

/** LibreWXR tile TTL: 5 min (`max-age=300` via Cloudflare, probed live). */
const val LIBRE_WXR_TILE_TTL_SECONDS: Long = 300L

/** Esri basemap disk-cache TTL: 24 h (`max-age=86400`, probed live). */
const val ESRI_TTL_SECONDS: Long = 86400L

/**
 * How long a fetched URL may be served from disk. The app owns
 * these lifetimes per host instead of inheriting server headers:
 * GIBS sends `no-store` (which would defeat caching entirely) and
 * IEM/LibreWXR send 5-min headers that would churn the disk cache
 * on every loop — the research (§3.2.4) fixes the lifetimes here.
 */
sealed class CacheTtl {
    /** Serve from disk for [seconds] after fetch, then revalidate. */
    data class Seconds(val seconds: Long) : CacheTtl()

    /**
     * The URL embeds its own version (the GIBS timestamp): the
     * bytes for that URL never change, so the entry never expires
     * while the URL is identical. A new timestamp is a new URL and
     * therefore a new entry — the TTL is the URL, not a clock.
     */
    object TimestampIsVersion : CacheTtl()

    /** Never serve from disk; always re-fetch (LibreWXR maps doc). */
    object NoCache : CacheTtl()

    /** Host is not in the table below: do not assume a lifetime. */
    object Unknown : CacheTtl()
}

/**
 * The disk-cache TTL for [url], by host (and path where it
 * matters). Host matching is by substring on the URL's authority,
 * so subdomains and scheme changes do not silently change policy:
 * - `mapservices.weather.noaa.gov` (MRMS): 12 h.
 * - `mesonet.agron.iastate.edu` (IEM tiles, HRRR tiles): 5 min.
 * - `server.arcgisonline.com` (Esri basemap): 24 h.
 * - `gibs.earthdata.nasa.gov` (GIBS WMTS/WMS): timestamp-is-version.
 * - `api.librewxr.net` maps document (`/public/weather-maps.json`):
 *   no cache — the frame list moves every few minutes and the
 *   document carries no cache-control header. LibreWXR TILES
 *   (any other path on that host): 5 min.
 * - Anything else: [CacheTtl.Unknown].
 */
fun cacheTtlForUrl(url: String): CacheTtl {
    val lower = url.lowercase()
    return when {
        "mapservices.weather.noaa.gov" in lower -> CacheTtl.Seconds(MRMS_TTL_SECONDS)
        "mesonet.agron.iastate.edu" in lower -> CacheTtl.Seconds(IEM_TTL_SECONDS)
        "server.arcgisonline.com" in lower -> CacheTtl.Seconds(ESRI_TTL_SECONDS)
        "gibs.earthdata.nasa.gov" in lower -> CacheTtl.TimestampIsVersion
        "api.librewxr.net" in lower -> if ("/public/weather-maps.json" in lower) {
            CacheTtl.NoCache
        } else {
            CacheTtl.Seconds(LIBRE_WXR_TILE_TTL_SECONDS)
        }
        else -> CacheTtl.Unknown
    }
}

// ---------------------------------------------------------------------------
// Cost text from actual bytes
// ---------------------------------------------------------------------------

/**
 * Human-readable data size from an ACTUAL fetched byte count —
 * never a fixed estimate. The research (§3.3 fix 5) proved fixed
 * numbers wrong and weather-dependent (IEM swung 49× between two
 * probes 15 h apart), so the loop's cost text is computed from the
 * bytes the fetcher really downloaded. One decimal for KB/MB
 * (`"2.6 MB"`, `"428 KB"`); whole bytes below 1 KB (`"96 B"`).
 * Whole KB/MB values drop the `.0` (`"428 KB"`, not `"428.0 KB"`).
 */
fun formatDataSize(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> "${oneDecimal(b / 1024.0)} KB"
        else -> "${oneDecimal(b / (1024.0 * 1024.0))} MB"
    }
}

/** One decimal place, with a trailing `.0` dropped (`2.6`, `428`). */
private fun oneDecimal(value: Double): String {
    val s = "%.1f".format(java.util.Locale.US, value)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/**
 * Honest first-load cost text for the loop, from the ACTUAL bytes
 * fetched for this generation ([totalBytesFetched]). Same shape as
 * the old text (`"FIRST LOAD ABOUT 100 KB THEN CACHED"`) but with
 * the measured number in place of the estimate — the number is
 * only ever written after the bytes exist. Star4000-safe: capitals,
 * digits, spaces and periods only.
 */
fun loopLoadCostText(totalBytesFetched: Long): String =
    "FIRST LOAD ${formatDataSize(totalBytesFetched)} THEN CACHED"
