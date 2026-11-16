package com.example.data.mapper

/**
 * NWS ArcGIS MRMS ImageServer adapter — AUDIT6 §9 fix 6, PRIMARY
 * observed-radar source.
 *
 * Service: `radar_base_reflectivity_time` ImageServer — MRMS composite
 * reflectivity, CONUS + AK + Caribbean + Guam + Hawaii, 5-min updates,
 * time-enabled (`time` = epoch ms). Official NWS infrastructure,
 * public domain, keyless, no tracking. QC'd at the source (clutter /
 * biology scrubbed), which attacks the AUDIT6 §1 speckle flicker at
 * its root instead of in the renderer.
 *
 * Base URL (verified live 2026-11-15 ~04:30 UTC, and again by this
 * adapter's author 2026-11-15 ~04:35 UTC):
 * https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity_time/ImageServer
 * - Service info `?f=json` → 200, `timeInfo.timeExtent` present.
 * - `exportImage` for the Crittenden KY bbox → 200.
 *
 * It is a REST/WMS bbox service, NOT XYZ tiles: consumption is
 * `exportImage` (bbox + size + time → ONE image for the viewport).
 * The engine draws one image per frame over its bbox, alongside
 * (not through) the XYZ tile path.
 *
 * COLOUR SCALE: MRMS serves its own FIXED NWS colour scale. There is
 * no palette parameter and no custom palette to build — the wiring
 * agent must not try to recolour the image; it displays as served.
 *
 * WINDOW: the service reports its own moving window in
 * `timeInfo.timeExtent` (epoch ms). The documented window is ~4 h,
 * but the live probe's extent spanned only ~112 min
 * ([1794709257000, 1794715977000] on 2026-11-15), so this adapter
 * NEVER assumes 4 h — frame times are always derived from the
 * reported extent. The loop window for MRMS is therefore whatever
 * the service reports, not IEM's 45-min LIVE archive.
 *
 * Pure Kotlin — no Android, no Compose, no Moshi, no Retrofit.
 * Moshi/Retrofit models for the same endpoints live in
 * data/api/RadarSourceApi.kt (CI-covered, not pure-compiled).
 */

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------

/** MRMS `radar_base_reflectivity_time` ImageServer base (no trailing slash). */
const val MRMS_BASE_URL: String =
    "https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity_time/ImageServer"

/** Service-info document: carries `timeInfo.timeExtent` (epoch ms). */
const val MRMS_SERVICE_INFO_URL: String = "$MRMS_BASE_URL?f=json"

/** MRMS update cadence: one frame every 5 minutes. */
const val MRMS_FRAME_STEP_MS: Long = 5 * 60 * 1000L

// ---------------------------------------------------------------------------
// exportImage URL builder (one image per frame for the viewport)
// ---------------------------------------------------------------------------

/**
 * One MRMS `exportImage` URL for [bbox] at [timeEpochMs], rendered
 * [widthPx] x [heightPx] pixels.
 *
 * Parameters (all verified against the live probe):
 * - `bbox` in lon/lat degrees, `bboxSR=4326`;
 * - `size` is the viewport size in pixels — the wiring agent passes
 *   the radar viewport size (e.g. ~1080x1000 on Robert's phone), NOT
 *   a fixed 512: the image is drawn stretched over the bbox, so its
 *   pixel size should match the display size;
 * - `imageSR=3857` (Web Mercator output, matching the basemap);
 * - `format=png32` (32-bit PNG with alpha — transparent where no echo);
 * - `time` = epoch milliseconds (the service is time-enabled);
 * - `f=image` (raw image bytes, not JSON).
 *
 * No API key, no tracking parameters.
 */
fun mrmsExportImageUrl(
    bbox: LonLatBbox,
    timeEpochMs: Long,
    widthPx: Int,
    heightPx: Int
): String {
    val bboxParam = "${bbox.westLon},${bbox.southLat},${bbox.eastLon},${bbox.northLat}"
    return "$MRMS_BASE_URL/exportImage" +
        "?bbox=$bboxParam&bboxSR=4326" +
        "&size=$widthPx,$heightPx&imageSR=3857" +
        "&format=png32&time=$timeEpochMs&f=image"
}

// ---------------------------------------------------------------------------
// Frame times from the service timeExtent
// ---------------------------------------------------------------------------

/**
 * Frame times (epoch ms) inside [startMs]..[endMs], aligned to the
 * 5-min cadence: first frame is the first 5-min boundary at or after
 * [startMs], then every [MRMS_FRAME_STEP_MS] up to [endMs].
 * Empty when the extent is inverted or shorter than one step.
 *
 * Probed extent [1794709257000, 1794715977000] yields 22 frames:
 * first 1794709500000, last 1794715800000.
 */
fun mrmsFrameTimesFromTimeExtent(startMs: Long, endMs: Long): List<Long> {
    if (endMs < startMs) return emptyList()
    val step = MRMS_FRAME_STEP_MS
    // Ceiling division to the next 5-min boundary (start itself counts if aligned).
    val first = ((startMs + step - 1) / step) * step
    if (first > endMs) return emptyList()
    val frames = mutableListOf<Long>()
    var t = first
    while (t <= endMs) {
        frames.add(t)
        t += step
    }
    return frames
}

private val MRMS_TIME_EXTENT_REGEX =
    Regex("\"timeExtent\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*]")

/**
 * The `[startMs, endMs]` timeExtent from a service-info JSON
 * document, or null when the document has none (service changed shape
 * or the fetch failed). Minimal hand-rolled parse on purpose: the
 * pure-test classpath has no Moshi, and only these two numbers are
 * needed here. The Retrofit/Moshi model in RadarSourceApi.kt parses
 * the same document in production.
 */
fun parseMrmsTimeExtent(serviceInfoJson: String): Pair<Long, Long>? {
    val match = MRMS_TIME_EXTENT_REGEX.find(serviceInfoJson) ?: return null
    val (start, end) = match.destructured
    return start.toLong() to end.toLong()
}

/**
 * Frame times (epoch ms) straight from a service-info JSON document:
 * parse the timeExtent, then align to the 5-min cadence. Empty when
 * the document has no usable extent — the caller then falls back to
 * the next source in [DEFAULT_RADAR_SOURCE_ORDER] instead of
 * synthesising frames.
 */
fun mrmsFrameTimesFromServiceInfoJson(serviceInfoJson: String): List<Long> {
    val extent = parseMrmsTimeExtent(serviceInfoJson) ?: return emptyList()
    return mrmsFrameTimesFromTimeExtent(extent.first, extent.second)
}

// ---------------------------------------------------------------------------
// One frame, ready for the loop
// ---------------------------------------------------------------------------
