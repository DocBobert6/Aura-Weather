package com.example.data.mapper

/**
 * LibreWXR adapter — AUDIT6 §9 fix 7, PAIRED second source (never sole).
 *
 * LibreWXR is a RainViewer-v2-compatible API serving NCEP MRMS data as
 * XYZ tiles: `GET https://api.librewxr.net/public/weather-maps.json`
 * returns `{host, radar: {past: [...], nowcast: [...]}}`, each frame
 * `{time, path}`, and tiles are
 * `{host}{path}/256/{z}/{x}/{y}/{color}/{options}.png`.
 *
 * Verified live 2026-11-15 ~04:30 UTC (fix-8 re-probe note) and again
 * by this adapter's author 2026-11-15 ~04:35 UTC:
 * - maps JSON 200 — 12 past frames + 6 nowcast frames;
 * - tile with the CORRECT prefix, e.g.
 *   `https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png`
 *   → 200. NOTE: the tile path is `/v2/radar/{time}` — it comes from
 *   the maps JSON `path` field, NOT `/public/radar/{time}`; a probe
 *   with the wrong prefix 404'd. Always build tile URLs from the
 *   `path` the service returns, never by guessing the prefix.
 * - Frames PRUNE: a tile fetched with a 2-hour-old frame timestamp
 *   404'd. Always use the newest timestamps from a fresh maps fetch.
 *
 * PAIRED, NEVER SOLE: single operator, no SLA, commercial-scale
 * traffic on the public instance not allowed (operator's Terms of Use
 * 2026-06-20). The adapter models this in [DEFAULT_RADAR_SOURCE_ORDER]
 * (MRMS primary, LibreWXR second, IEM fallback) — if LibreWXR fails
 * or a frame prunes, the loop falls through instead of going blank.
 *
 * Pure Kotlin — no Android, no Compose, no Moshi, no Retrofit.
 * Moshi/Retrofit models for the same endpoint live in
 * data/api/RadarSourceApi.kt (CI-covered, not pure-compiled).
 */

// ---------------------------------------------------------------------------
// Endpoints + tile format
// ---------------------------------------------------------------------------

/** LibreWXR public instance (no trailing slash; `host` in maps JSON agrees). */
const val LIBRE_WXR_BASE_URL: String = "https://api.librewxr.net"

/** The maps document: frame lists + the tile URL prefix per frame. */
const val LIBRE_WXR_MAPS_URL: String = "$LIBRE_WXR_BASE_URL/public/weather-maps.json"

/** Tile size in the URL path (LibreWXR also serves 512; 256 matches the app grid). */
const val LIBRE_WXR_TILE_SIZE: Int = 256

/**
 * 512 px tile size (Phase 2 fallback source, Phase 0 spike 5): one
 * 512 tile at zoom−1 covers exactly the 2×2 256 tiles at zoom
 * (pixel-compared MAD 0.0), so a frame drops from ~77 requests to
 * ~20 at roughly equal total bytes.
 */
const val LIBRE_WXR_TILE_SIZE_512: Int = 512

/**
 * Colour scheme 2 = NEXRAD Level III (RainViewer scheme numbering).
 * Chosen because it is the US NEXRAD palette — the same family as the
 * fixed NWS scale MRMS serves — so the loop looks consistent when it
 * falls back between MRMS and LibreWXR, and because the live probe's
 * example tile used scheme 2. The other schemes (0 Universal Blue,
 * 1 Original, …) would recolour the same data for no benefit.
 */
const val LIBRE_WXR_COLOR_SCHEME: Int = 2

/**
 * Tile options `1_1` = `{smooth}_{snow}`: smoothing ON, snow shown.
 * Smoothing on matches the loop's look (the probe's example tile used
 * `1_1`); snow on keeps winter precipitation visible instead of
 * dropping it. Both are part of the URL, so a prefetch is a cache hit
 * for the display request only when they match exactly.
 */
const val LIBRE_WXR_TILE_OPTIONS: String = "1_1"

/**
 * Frames older than this before the newest past frame are treated as
 * pruned: the server 404s them (a 2-hour-old frame 404'd in the live
 * probe), so the adapter drops them instead of queueing doomed tile
 * requests. Nowcast frames are kept only up to 60 min past the newest
 * past frame (the live nowcast window: 6 frames × 10 min).
 */
const val LIBRE_WXR_PAST_KEEP_SECONDS: Long = 2 * 60 * 60
const val LIBRE_WXR_NOWCAST_KEEP_SECONDS: Long = 60 * 60

// ---------------------------------------------------------------------------
// Models (mirrored by the Moshi models in RadarSourceApi.kt)
// ---------------------------------------------------------------------------

/** One LibreWXR frame: epoch SECONDS (not ms) + its tile URL prefix. */
data class LibreWxrFrame(
    val timeEpochSec: Long,
    val path: String,
    val isNowcast: Boolean
)

/** A parsed weather-maps document: host + past and nowcast frame lists. */
data class LibreWxrMaps(
    val host: String,
    val past: List<LibreWxrFrame>,
    val nowcast: List<LibreWxrFrame>
) {
    /** All frames, oldest first — the loop order. */
    fun allFramesSorted(): List<LibreWxrFrame> =
        (past + nowcast).sortedBy { it.timeEpochSec }

    /** Newest observed (past) frame time, or null when there is none. */
    fun newestPastTime(): Long? = past.maxOfOrNull { it.timeEpochSec }
}

// ---------------------------------------------------------------------------
// Tile URL builder
// ---------------------------------------------------------------------------

/**
 * One LibreWXR tile URL. [host] and [path] come from the maps JSON —
 * never hardcode the `/v2/radar/{time}` prefix; the service owns it.
 * Pattern: `{host}{path}/{tileSizePx}/{z}/{x}/{y}/{color}/{options}.png`.
 * [tileSizePx] is 256 by default (the app grid); 512 requests the
 * zoom−1 parent tile (Phase 2 fallback source — see
 * [LIBRE_WXR_TILE_SIZE_512]).
 *
 * Example (verified live): `libreWxrTileUrl("https://api.librewxr.net",
 * "/v2/radar/1794716400", 6, 16, 24)` =
 * `https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png`.
 */
fun libreWxrTileUrl(
    host: String,
    path: String,
    zoom: Int,
    tileX: Int,
    tileY: Int,
    colorScheme: Int = LIBRE_WXR_COLOR_SCHEME,
    options: String = LIBRE_WXR_TILE_OPTIONS,
    tileSizePx: Int = LIBRE_WXR_TILE_SIZE
): String =
    "$host$path/$tileSizePx/$zoom/$tileX/$tileY/$colorScheme/$options.png"

/**
 * MapLibre tile-URL template for one LibreWXR frame: the same URL
 * shape as [libreWxrTileUrl], with MapLibre's `{z}` / `{x}` / `{y}`
 * placeholders in place of the tile coordinates (MapLibre substitutes
 * them per tile at request time). Default [tileSizePx] is 512: the
 * Phase 2 fallback source requests 512 px tiles at zoom−1 (see
 * [LIBRE_WXR_TILE_SIZE_512]) and caps the source's maxzoom at 7, so
 * MapLibre overzooms the z7 tiles at map zoom 8–9 instead of ever
 * requesting z8/z9 tiles from LibreWXR.
 */
fun libreWxrTileTemplate(
    host: String,
    path: String,
    tileSizePx: Int = LIBRE_WXR_TILE_SIZE_512
): String =
    "$host$path/$tileSizePx/{z}/{x}/{y}/$LIBRE_WXR_COLOR_SCHEME/$LIBRE_WXR_TILE_OPTIONS.png"

private val LIBRE_WXR_HOST_REGEX = Regex("\"host\"\\s*:\\s*\"([^\"]+)\"")
private val LIBRE_WXR_FRAME_REGEX =
    Regex("\\{[^{}]*\"time\"\\s*:\\s*(\\d+)[^{}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}")

/** All `{time, path}` entries inside one JSON array span. */
private fun parseLibreWxrFrameList(arrayJson: String, isNowcast: Boolean): List<LibreWxrFrame> =
    LIBRE_WXR_FRAME_REGEX.findAll(arrayJson).map { match ->
        val (time, path) = match.destructured
        LibreWxrFrame(time.toLong(), path, isNowcast)
    }.toList()

/** The `[...]` array following `"key":` in [json], or null when absent. */
private fun jsonArrayAfter(json: String, key: String): String? {
    val keyIndex = json.indexOf("\"$key\"") ?: return null
    val open = json.indexOf('[', keyIndex)
    if (open < 0) return null
    // Entries are flat {time, path} objects — no nested arrays, so the
    // first closing bracket ends the array.
    val close = json.indexOf(']', open)
    if (close < 0) return null
    return json.substring(open, close + 1)
}

/**
 * Parse a weather-maps JSON document into [LibreWxrMaps], or null when
 * it has no host or no past frames (unusable as a source — the caller
 * falls back to IEM). Only `host`, `radar.past[]` and
 * `radar.nowcast[]` are read; satellite/alert sections, if present,
 * are ignored.
 */
fun parseLibreWxrMaps(mapsJson: String): LibreWxrMaps? {
    val host = LIBRE_WXR_HOST_REGEX.find(mapsJson)?.groupValues?.get(1)
        ?: return null
    val pastJson = jsonArrayAfter(mapsJson, "past") ?: return null
    val past = parseLibreWxrFrameList(pastJson, isNowcast = false)
    if (past.isEmpty()) return null
    val nowcastJson = jsonArrayAfter(mapsJson, "nowcast")
    val nowcast = nowcastJson?.let { parseLibreWxrFrameList(it, isNowcast = true) }.orEmpty()
    return LibreWxrMaps(host = host, past = past, nowcast = nowcast)
}

// ---------------------------------------------------------------------------
// Loop frames: merge past + nowcast, drop pruned frames
// ---------------------------------------------------------------------------

/**
 * The playable loop frames from a parsed maps document: past +
 * nowcast merged, oldest first, with pruned frames dropped —
 * past frames older than [LIBRE_WXR_PAST_KEEP_SECONDS] before the
 * newest past frame, and nowcast frames more than
 * [LIBRE_WXR_NOWCAST_KEEP_SECONDS] past it. The nowcast frames are
 * the cheap honest "interpolation" (server-side advected frames,
 * AUDIT6 §9 fix 7) — they extend the loop 60 min into the future,
 * they are NOT model reflectivity and must never be labelled as such.
 *
 * Callers must re-fetch the maps document each loop refresh and
 * rebuild this list: frame timestamps move and old frames prune, so
 * a cached list goes stale within minutes.
 */
fun libreWxrLoopFrames(maps: LibreWxrMaps): List<LibreWxrFrame> {
    val newestPast = maps.newestPastTime() ?: return emptyList()
    return maps.allFramesSorted().filter { frame ->
        if (frame.isNowcast) {
            frame.timeEpochSec <= newestPast + LIBRE_WXR_NOWCAST_KEEP_SECONDS
        } else {
            frame.timeEpochSec >= newestPast - LIBRE_WXR_PAST_KEEP_SECONDS
        }
    }
}
