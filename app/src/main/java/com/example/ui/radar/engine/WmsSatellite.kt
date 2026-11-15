package com.example.ui.radar.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Phase 3 — pure WMS satellite core (URL, probed timestamps, blank
 * detection, cost text).
 *
 * Pure Kotlin (JDK only): no Android, no Compose, no MapLibre, no
 * OkHttp, no coroutines — compiled and unit-tested locally with
 * kotlinc + JUnit (see `WmsSatelliteTest`).
 *
 * # Why this exists
 *
 * Phase 0 / rebuild-satellite.md §1.6: the satellite primary is a
 * WMS single-image JPEG (viewport × 1.5, ~0.85 MB VIS / ~0.33 MB IR
 * per cold switch) instead of the 77-tile WMTS grid (~6 MB, slowest
 * tile gates the frame). Three things the tile path never had to
 * solve become load-bearing here, and all three live in this file:
 *
 * 1. **The timestamp must be probed, per layer, on the WMS path
 *    itself.** The old `gibsTimestamp` (now − 60 min, one backoff
 *    for both layers) requests scans that do not exist: GeoColor
 *    lag was ~82 min on WMTS and ~92 min on WMS on 2026-11-15, and
 *    it moves day to day. Worse, WMS availability is NON-MONOTONIC
 *    in time (Phase 0 spike 3 / `wms-sampling/probe_wms.py`):
 *    18:50 real, 18:40 black, 18:30 real, 18:20 black — individual
 *    scans can be missing/black while neighbours are fine, and the
 *    WMS lag behind WMTS was 10 min at ~19:31 UTC and ~50 min at
 *    ~20:02 UTC, so NO fixed offset and NO WMTS proxy can pin the
 *    WMS time. Resolution is therefore DIRECT small-GetMap probing
 *    only: [wmsTimeCandidates] + [newestNonBlank], mirrored exactly
 *    from `probe_wms.py`. Never use GetCapabilities for freshness
 *    (WMTS caps was stale for IR; WMS caps lags real availability
 *    by 10–30 min for both layers).
 * 2. **HTTP 200 means nothing on this path.** WMS answers 200 with
 *    a fully transparent image (unpublished time), a pure black
 *    image (a scan the backend has not rendered), or an XML
 *    ServiceException body (GDAL block failure) — all probed live
 *    2026-11-15. Blank detection is mandatory: [isBlankSample]
 *    (pixel stats, exact probe rules) + [isXmlErrorBody].
 * 3. **The cost text must be honest.** The old text said "ABOUT
 *    6 MB PER SWITCH" (VIS) / "ABOUT 5 MB" (IR); the WMS numbers
 *    are ~0.9 MB / ~0.3 MB — but [satelliteCostText] formats the
 *    REAL byte count passed in, never a hardcoded estimate.
 *
 * # What the wiring agent owns (NOT in this file)
 *
 * - Network: firing the small 256×256 probe GetMaps in parallel
 *   (5 per round, ≤ 10 total across 2 rounds) and the full GetMap,
 *   through the coalescing fetcher.
 * - Bitmap decode + sampling: decode the JPEG, sample a coarse
 *   grid (every 64 px, as the probe does), and hand the RGB ints
 *   to [isBlankSample]. See the "Sampling contract" section below.
 * - Display: one image stretched over its bbox, previous image
 *   held until the new one decodes, crossfade, 10 s timeout.
 * - The per-layer, per-path timestamp cache lifecycle:
 *   [WmsTimestampCache] is the decision helper; the wiring agent
 *   holds one instance per layer and calls it before each resolve.
 */

// ---------------------------------------------------------------------------
// Endpoint + layers
// ---------------------------------------------------------------------------

/** GIBS WMS endpoint (EPSG:3857, "best" — the current best-available data). */
const val WMS_BASE_URL: String = "https://gibs.earthdata.nasa.gov/wms/epsg3857/best/wms.cgi"

/** WMS version used for every GetMap (1.1.1 behaves the same; 1.3.0 is the documented path). */
const val WMS_VERSION: String = "1.3.0"

/** Small-probe GetMap size: same bbox, 256×256 (~19 KB if real, 668 B if black, ~1–2 s each). */
const val WMS_PROBE_WIDTH_PX: Int = 256
const val WMS_PROBE_HEIGHT_PX: Int = 256

/** The two GIBS satellite layers, with their WMS layer names. */
enum class WmsSatelliteLayer(val wmsLayerName: String) {
    /** GOES-East ABI GeoColor (visible). Flaky on WMS; newest scan lags WMTS by ≥10 min. */
    VISIBLE("GOES-East_ABI_GeoColor"),

    /** GOES-East ABI Band 13 clean infrared. Comparatively stable (~32 min lag). */
    INFRARED("GOES-East_ABI_Band13_Clean_Infrared")
}

// ---------------------------------------------------------------------------
// TIME format
// ---------------------------------------------------------------------------

/** WMS TIME format: UTC ISO-8601 `yyyy-MM-dd'T'HH:mm:ss'Z'` (e.g. `2026-11-15T18:00:00Z`). */
fun formatWmsTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(epochMs))
}

// ---------------------------------------------------------------------------
// URL builder
// ---------------------------------------------------------------------------

/** A WMS bbox in EPSG:3857 metres: minX/minY = south-west, maxX/maxY = north-east. */
data class WmsBbox3857(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    /** Width in metres. */
    val widthMetres: Double get() = maxX - minX

    /** Height in metres. */
    val heightMetres: Double get() = maxY - minY
}

/**
 * WMS GetMap URL for [layer] over [bbox3857] at [widthPx] ×
 * [heightPx], for the scan at [timeEpochMs].
 *
 * Parameter order mirrors the reference probe
 * (`wms-sampling/probe_wms.py`): `SERVICE=WMS&VERSION=1.3.0&
 * REQUEST=GetMap&LAYERS=<layer>&STYLES=&CRS=EPSG:3857&BBOX=<bbox>&
 * WIDTH=<w>&HEIGHT=<h>&FORMAT=image/jpeg&TRANSPARENT=FALSE&TIME=<utc>`.
 * `FORMAT=image/jpeg` — PNG is unusable at viewport size (12.0 MB /
 * 8.0 s at 1620×2880 vs 854 KB / 3.12 s JPEG, probed live).
 * `TRANSPARENT=FALSE` — satellite is opaque, alpha is not needed,
 * and FALSE makes a missing scan render black instead of
 * transparent (either way [isBlankSample] catches it). TIME is
 * always explicit: no TIME → XML ServiceException from the server.
 * Bbox coordinates are formatted to 2 decimals, as in the probe.
 */
fun wmsGetMapUrl(
    layer: WmsSatelliteLayer,
    bbox3857: WmsBbox3857,
    widthPx: Int,
    heightPx: Int,
    timeEpochMs: Long
): String {
    val bbox = listOf(bbox3857.minX, bbox3857.minY, bbox3857.maxX, bbox3857.maxY)
        .joinToString(",") { "%.2f".format(Locale.US, it) }
    return "$WMS_BASE_URL?SERVICE=WMS&VERSION=$WMS_VERSION&REQUEST=GetMap" +
        "&LAYERS=${layer.wmsLayerName}&STYLES=&CRS=EPSG:3857&BBOX=$bbox" +
        "&WIDTH=$widthPx&HEIGHT=$heightPx&FORMAT=image/jpeg&TRANSPARENT=FALSE" +
        "&TIME=${formatWmsTime(timeEpochMs)}"
}

/**
 * Small-probe GetMap URL: the same GetMap at 256×256 over the same
 * [bbox3857], for the candidate scan at [timeEpochMs]. This is the
 * URL the timestamp resolver fires (5 in parallel per round, ≤ 10
 * total) — a full-size GetMap per candidate would cost ~0.9 MB
 * each; a 256×256 probe costs ~19 KB if real / 668 B if black.
 */
fun wmsSmallProbeUrl(
    layer: WmsSatelliteLayer,
    bbox3857: WmsBbox3857,
    timeEpochMs: Long
): String = wmsGetMapUrl(layer, bbox3857, WMS_PROBE_WIDTH_PX, WMS_PROBE_HEIGHT_PX, timeEpochMs)

// ---------------------------------------------------------------------------
// Viewport math (lon/lat → EPSG:3857, bbox × 1.5, image size)
// ---------------------------------------------------------------------------

/** WGS-84 semi-major axis (metres) — the EPSG:3857 sphere radius. */
const val EPSG3857_EARTH_RADIUS_METRES: Double = 6378137.0

/** EPSG:3857 half-world extent in metres (20037508.342789244). */
const val EPSG3857_HALF_WORLD_METRES: Double = 20037508.342789244

/** Web-Mercator latitude clamp (±85.05112878°): beyond it y → ±∞. */
const val EPSG3857_MAX_LATITUDE: Double = 85.05112878

/**
 * Pan-buffer ratio (the OpenLayers `ImageWMS ratio` idea, from the
 * rebuild research §6): the requested bbox and image are 1.5× the
 * viewport, so small pans are free — reposition the image under
 * the pan, no refetch. Refetch only when the pan would expose an
 * edge, on zoom change, on layer switch, or on timestamp refresh.
 */
const val WMS_VIEWPORT_RATIO: Double = 1.5

/** Long-edge cap for the requested image (px): very tall screens must not request absurd images. */
const val WMS_IMAGE_MAX_LONG_EDGE_PX: Int = 3000

/**
 * Longitude/latitude → EPSG:3857 metres. Latitude is clamped to
 * ±[EPSG3857_MAX_LATITUDE] (beyond it the Mercator y is infinite).
 * Returns (x, y) in metres.
 */
fun lonLatTo3857(lonDeg: Double, latDeg: Double): Pair<Double, Double> {
    val lat = max(-EPSG3857_MAX_LATITUDE, min(EPSG3857_MAX_LATITUDE, latDeg))
    val x = EPSG3857_EARTH_RADIUS_METRES * Math.toRadians(lonDeg)
    val latRad = Math.toRadians(lat)
    val y = EPSG3857_EARTH_RADIUS_METRES * ln(tan(PI / 4.0 + latRad / 2.0))
    return x to y
}

/**
 * Metres per pixel at web-Mercator [zoom]: `2πR / (256 · 2^zoom)`.
 * z6 = 2445.98 m/px, z5 = 4891.97 m/px.
 */
fun metresPerPixel(zoom: Int): Double =
    2.0 * PI * EPSG3857_EARTH_RADIUS_METRES / (256.0 * (1 shl zoom))

/**
 * The WMS bbox for a viewport of [viewWidthPx] × [viewHeightPx]
 * screen pixels at [zoom], centred on ([centerLon], [centerLat]).
 *
 * The bbox is the viewport × [WMS_VIEWPORT_RATIO] in each
 * direction — the extra 50% is the pan buffer (see
 * [WMS_VIEWPORT_RATIO]). Centre is a parameter: the engine centres
 * on Cincinnati (38.7833, −84.6027), but this function never
 * hardcodes it.
 *
 * NOTE on the probe's hardcoded bbox: `probe_wms.py` uses
 * `-11271098.44,1878958.85,-8140237.76,7514743.96`, which is the
 * 5×9-tile viewport at z6, TILE-ALIGNED (lon −101.25…−73.125,
 * lat 16.64…55.78) rather than centre-aligned — its centre is
 * (−87.19, ~38.81), not Cincinnati. This function is
 * centre-aligned by construction, so its bbox differs slightly
 * from the probe's for the same nominal viewport. That is fine:
 * WMS takes any bbox, and the probe's exact bbox is only a
 * sampling fixture, not a contract.
 */
fun wmsBbox3857(
    centerLon: Double,
    centerLat: Double,
    viewWidthPx: Int,
    viewHeightPx: Int,
    zoom: Int
): WmsBbox3857 {
    val (cx, cy) = lonLatTo3857(centerLon, centerLat)
    val mpp = metresPerPixel(zoom)
    val halfW = viewWidthPx * mpp * WMS_VIEWPORT_RATIO / 2.0
    val halfH = viewHeightPx * mpp * WMS_VIEWPORT_RATIO / 2.0
    return WmsBbox3857(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
}

/**
 * The requested image size for a [screenWidthPx] × [screenHeightPx]
 * screen: viewport × [WMS_VIEWPORT_RATIO] (e.g. 1620×2880 for a
 * 1080×1920 screen), with the long edge capped at
 * [WMS_IMAGE_MAX_LONG_EDGE_PX] px, preserving aspect ratio.
 * Returns (widthPx, heightPx).
 */
fun wmsImageSizeForViewport(screenWidthPx: Int, screenHeightPx: Int): Pair<Int, Int> {
    var w = (screenWidthPx * WMS_VIEWPORT_RATIO).roundToInt()
    var h = (screenHeightPx * WMS_VIEWPORT_RATIO).roundToInt()
    val longEdge = max(w, h)
    if (longEdge > WMS_IMAGE_MAX_LONG_EDGE_PX) {
        val scale = WMS_IMAGE_MAX_LONG_EDGE_PX.toDouble() / longEdge
        w = (w * scale).roundToInt()
        h = (h * scale).roundToInt()
    }
    return w to h
}

// ---------------------------------------------------------------------------
// Timestamp candidates + newest-non-blank selection
// ---------------------------------------------------------------------------

/** One scan step: GIBS publishes GOES scans every 10 min. */
const val WMS_SCAN_STEP_MS: Long = 10L * 60L * 1000L

/** Round 1 starts this far back from now (the newest candidate). */
const val WMS_ROUND1_START_BACK_MIN: Long = 60L

/** Round 2 starts this far back from now (only if round 1 is all blank). */
const val WMS_ROUND2_START_BACK_MIN: Long = 110L

/** Candidates per round (5). Bounded budget: at most 2 rounds = 10 small probes. */
const val WMS_CANDIDATES_PER_ROUND: Int = 5

/** Round a timestamp down to the 10-min scan boundary. */
fun roundDownTo10Min(epochMs: Long): Long = epochMs - (epochMs % WMS_SCAN_STEP_MS)

/**
 * Candidate scan times (epoch ms, newest first) for probe [round]
 * at [nowMs], mirroring `probe_wms.py` exactly:
 * - Round 1: now−60, −70, −80, −90, −100 min, each rounded down
 *   to 10 min.
 * - Round 2: now−110, −120, −130, −140, −150 min, each rounded
 *   down to 10 min.
 *
 * Round 2 is fired only when every round-1 probe came back blank
 * (or errored). If round 2 is also all blank, GIBS is down or the
 * lag grew past the budget — hold the last good image, else the
 * honest unavailable state; do NOT walk further back.
 *
 * @throws IllegalArgumentException when [round] is not 1 or 2.
 */
fun wmsTimeCandidates(nowMs: Long, round: Int): List<Long> {
    val startBackMin = when (round) {
        1 -> WMS_ROUND1_START_BACK_MIN
        2 -> WMS_ROUND2_START_BACK_MIN
        else -> throw IllegalArgumentException("wmsTimeCandidates: round must be 1 or 2, was $round")
    }
    return (0 until WMS_CANDIDATES_PER_ROUND).map { i ->
        roundDownTo10Min(nowMs - (startBackMin + 10L * i) * 60L * 1000L)
    }
}

/**
 * The newest candidate whose probe was non-blank, or null when
 * none was. [candidates] must be newest-first (as
 * [wmsTimeCandidates] returns them); [blankByTime] maps each
 * probed candidate time to whether its small GetMap was blank
 * (true = blank/error, false = real imagery).
 *
 * Non-monotonic availability is the whole point of this function:
 * a newer scan can be black while an older one is real (probed
 * 2026-11-15 ~20:02 UTC: 18:50 real, 18:40 black, 18:30 real),
 * so the answer is the newest NON-BLANK candidate — never simply
 * the newest candidate, and never a fixed backoff. A candidate
 * missing from [blankByTime] (never probed, or its probe failed to
 * report) is treated as blank: unproven is not usable.
 */
fun newestNonBlank(candidates: List<Long>, blankByTime: Map<Long, Boolean>): Long? =
    candidates.firstOrNull { blankByTime[it] == false }

// ---------------------------------------------------------------------------
// Resolved-timestamp cache (10-min TTL)
// ---------------------------------------------------------------------------

/**
 * How long a resolved timestamp stays valid: 10 min
 * (rebuild-satellite.md §1.7 rule 3). Re-resolve only when the
 * cached resolution is older than this, or when a fetch fails.
 */
const val WMS_TIMESTAMP_TTL_MS: Long = 10L * 60L * 1000L

/** One resolved timestamp: the scan time, and when it was resolved (epoch ms). */
data class ResolvedWmsTime(val scanTimeMs: Long, val resolvedAtMs: Long)

/**
 * Per-layer resolved-timestamp store with the 10-min TTL decision.
 * Pure decision logic on timestamps — no I/O, no clock reads: the
 * wiring agent passes [nowMs] (its own clock) and calls
 * [needsResolve] before each resolve.
 *
 * Lifecycle (wiring agent): one instance per layer, held for the
 * session. [recordResolved] after a successful probe; [needsResolve]
 * returns true when there is no entry, when the entry is older
 * than [WMS_TIMESTAMP_TTL_MS], or after [recordFetchFailure]
 * (a failed full GetMap invalidates the resolution — the scan may
 * have gone black since it was probed). Do NOT re-probe on every
 * pan, zoom, or layer switch: the timestamp is part of the cache
 * key, and a new timestamp is a new URL set (a re-download by
 * design), so the 10-min cadence is the bound on re-downloads.
 */
class WmsTimestampCache {
    private val resolvedByLayer = mutableMapOf<WmsSatelliteLayer, ResolvedWmsTime>()

    /** The resolved scan time for [layer], or null when nothing is resolved. */
    fun resolvedTime(layer: WmsSatelliteLayer): Long? = resolvedByLayer[layer]?.scanTimeMs

    /** Record a successful resolution of [scanTimeMs] for [layer] at [nowMs]. */
    fun recordResolved(layer: WmsSatelliteLayer, scanTimeMs: Long, nowMs: Long) {
        resolvedByLayer[layer] = ResolvedWmsTime(scanTimeMs, nowMs)
    }

    /** A fetch failed for [layer]: invalidate its resolution so the next check re-resolves. */
    fun recordFetchFailure(layer: WmsSatelliteLayer) {
        resolvedByLayer.remove(layer)
    }

    /**
     * Must [layer] be re-resolved at [nowMs]? True when there is no
     * entry, or when the entry was resolved more than
     * [WMS_TIMESTAMP_TTL_MS] ago. Exactly at the TTL it is still
     * valid (the bound is "older than 10 min").
     */
    fun needsResolve(layer: WmsSatelliteLayer, nowMs: Long): Boolean {
        val entry = resolvedByLayer[layer] ?: return true
        return nowMs - entry.resolvedAtMs > WMS_TIMESTAMP_TTL_MS
    }
}

// ---------------------------------------------------------------------------
// Blank detection (pure, on sampled RGB ints)
// ---------------------------------------------------------------------------

/**
 * Blank threshold on pooled standard deviation: below 1.0 the
 * image is zero-variance (pure black / fully uniform) — the exact
 * probe rule (`std < 1.0`).
 */
const val WMS_BLANK_MAX_STDDEV: Double = 1.0

/**
 * Blank threshold on near-black fraction: above 0.995 the image
 * is effectively empty — the exact probe rule
 * (`near_black > 0.995`).
 */
const val WMS_BLANK_MAX_NEAR_BLACK_FRACTION: Double = 0.995

/** Near-black pixel: every channel below 8 (the exact probe rule). */
const val WMS_NEAR_BLACK_CHANNEL_MAX: Int = 8

/** Pixel statistics for a sampled image: per-channel means, pooled stddev, near-black fraction. */
data class WmsSampleStats(
    val meanR: Double,
    val meanG: Double,
    val meanB: Double,
    /** Pooled standard deviation over ALL sampled channel values (as the probe's `flat.std()`). */
    val stddev: Double,
    /** Fraction of sampled pixels with every channel < [WMS_NEAR_BLACK_CHANNEL_MAX]. */
    val nearBlackFraction: Double
)

/**
 * Statistics for [samples] (RGB ints, one [Triple] per sampled
 * pixel). The stddev is POOLED over all channel values — exactly
 * the probe's `flat.std()` over the (N, 3) array — not per-channel
 * and not averaged: a single-channel gradient and a grey image
 * must score differently, and the probe's `< 1.0` threshold was
 * calibrated on the pooled value (real GeoColor at this bbox
 * scores ~60).
 *
 * Empty [samples] returns all-zero means, stddev 0.0 and
 * near-black fraction 1.0 — i.e. blank (nothing was sampled, so
 * nothing proves imagery). A single pixel's stddev is the spread
 * of its three channels (pooled), so a single grey pixel
 * (200, 200, 200) has stddev 0.0 and is blank, while a single
 * coloured pixel (200, 210, 190) has stddev ~8.2 and is not —
 * one pixel can only prove imagery if its channels disagree.
 */
fun wmsSampleStats(samples: List<Triple<Int, Int, Int>>): WmsSampleStats {
    if (samples.isEmpty()) {
        return WmsSampleStats(0.0, 0.0, 0.0, 0.0, 1.0)
    }
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    var nearBlack = 0
    for ((r, g, b) in samples) {
        sumR += r
        sumG += g
        sumB += b
        if (r < WMS_NEAR_BLACK_CHANNEL_MAX &&
            g < WMS_NEAR_BLACK_CHANNEL_MAX &&
            b < WMS_NEAR_BLACK_CHANNEL_MAX
        ) {
            nearBlack++
        }
    }
    val n = samples.size.toDouble()
    val meanR = sumR / n
    val meanG = sumG / n
    val meanB = sumB / n
    // Pooled variance over all 3N channel values, about the pooled mean.
    val pooledMean = (sumR + sumG + sumB) / (3.0 * n)
    var sqDiffSum = 0.0
    for ((r, g, b) in samples) {
        sqDiffSum += (r - pooledMean) * (r - pooledMean)
        sqDiffSum += (g - pooledMean) * (g - pooledMean)
        sqDiffSum += (b - pooledMean) * (b - pooledMean)
    }
    val stddev = kotlin.math.sqrt(sqDiffSum / (3.0 * n))
    return WmsSampleStats(meanR, meanG, meanB, stddev, nearBlack / n)
}

/**
 * Is this sampled image blank — i.e. a failed fetch, not data?
 * Exactly the probe rules: `stddev < 1.0` (pure black /
 * zero-variance) OR `near-black fraction > 0.995` (all channels
 * < 8 in more than 99.5% of sampled pixels). Either rule alone
 * blanks: a mostly-black image with a few bright pixels can have
 * stddev well above 1.0 and still be blank by the near-black rule.
 *
 * # Sampling contract (wiring agent — read this)
 *
 * The Bitmap decoding/sampling lives in Android code, NOT here:
 * this function takes already-decoded RGB ints because the
 * pure-test classpath has no `android.graphics`. The wiring agent
 * must sample the decoded JPEG on a COARSE GRID — every 64 px, as
 * the probe does (`a[::64, ::64, :]`), which is ~26×45 ≈ 1,170
 * samples for a 1620×2880 image — and pass each sampled pixel as
 * `Triple(red, green, blue)` with channels 0–255 from
 * `Color.red/green/blue(pixel)` (or `pixel shr 16 and 0xFF` etc.).
 * Do NOT pass every pixel (4.6 M samples is wasteful) and do NOT
 * pass a downscaled thumbnail's pixels without noting it (a
 * thumbnail smooths variance down and can false-blank a real
 * image). JPEG has no alpha: there is no transparent case to
 * handle — a "transparent" failure renders as black with
 * `TRANSPARENT=FALSE`, which this catches. An image that fails to
 * decode at all is a fetch failure, not a blank: report it as
 * blank to the resolver (so the timestamp steps back / falls back
 * to tiles) but do not call this function on it.
 */
fun isBlankSample(samples: List<Triple<Int, Int, Int>>): Boolean {
    val stats = wmsSampleStats(samples)
    return stats.stddev < WMS_BLANK_MAX_STDDEV ||
        stats.nearBlackFraction > WMS_BLANK_MAX_NEAR_BLACK_FRACTION
}

// ---------------------------------------------------------------------------
// XML ServiceException guard
// ---------------------------------------------------------------------------

/** How many leading bytes are scanned for a `ServiceException` marker (the exact probe window). */
const val WMS_XML_ERROR_SCAN_BYTES: Int = 2000

/**
 * Is [bytes] an XML error body rather than a JPEG? True when the
 * body starts with `<?xml` or `<Serv` (the probe's first-5-bytes
 * check), or when `ServiceException` appears in the first
 * [WMS_XML_ERROR_SCAN_BYTES] bytes. The WMS backend returns these
 * with HTTP 200 (GDAL block-fetch failure, e.g. valid time +
 * `TRANSPARENT=TRUE`), so the wiring agent must check this BEFORE
 * attempting a decode — a JPEG decoder handed XML either throws
 * or returns garbage. Mirrors `probe_wms.py` exactly.
 */
fun isXmlErrorBody(bytes: ByteArray): Boolean {
    if (bytes.size >= 5) {
        val head = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        if (head == "<?xml" || head == "<Serv") return true
    }
    val window = bytes.copyOfRange(0, min(WMS_XML_ERROR_SCAN_BYTES, bytes.size))
    return String(window, Charsets.US_ASCII).contains("ServiceException")
}

// ---------------------------------------------------------------------------
// Cost text (actual bytes only)
// ---------------------------------------------------------------------------

/**
 * Honest per-switch cost text from the ACTUAL bytes fetched for
 * this switch ([bytesFetched]) — never a hardcoded estimate. The
 * old text said "ABOUT 6 MB PER SWITCH" (VIS) / "ABOUT 5 MB" (IR);
 * the WMS probe numbers are ~0.9 MB VIS / ~0.3 MB IR, but those
 * are probe numbers, not this switch's number: the wiring agent
 * passes the real byte count of the full GetMap it just fetched
 * (e.g. 854,026 → `"854 KB PER SWITCH"`). Formatting is
 * [formatDataSize] (one decimal for KB/MB, whole bytes below 1 KB).
 * Star4000-safe: capitals, digits, spaces and periods only.
 */
fun satelliteCostText(bytesFetched: Long): String =
    "${formatDataSize(bytesFetched)} PER SWITCH"
