package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit + Moshi models for the AUDIT6 §9 fix 6 / fix 7 radar
 * sources (MRMS primary, LibreWXR paired). NOT pure-compiled: the
 * pure-test classpath has no Retrofit/Moshi, so this file is covered
 * by CI (`testDebugUnitTest`) only — the pure URL/time/bbox helpers
 * for the same endpoints live in data/mapper/MrmsTiles.kt and
 * data/mapper/LibreWxrTiles.kt and mirror these models field for field.
 *
 * Wiring notes for the later wiring agent:
 * - Build one Retrofit with baseUrl =
 *   `MRMS_BASE_URL + "/"` for [MrmsImageServerApi], and one with
 *   baseUrl = `LIBRE_WXR_BASE_URL + "/"` for [LibreWxrApi] (or a
 *   single Retrofit per host — never one Retrofit for both hosts).
 * - `exportImage` needs NO Retrofit call: the pure
 *   [com.example.data.mapper.mrmsExportImageUrl] builder produces a
 *   plain image URL, and Coil (or the tile client) fetches its bytes
 *   exactly like an IEM tile URL. Same for LibreWXR tile URLs from
 *   [com.example.data.mapper.libreWxrTileUrl].
 * - No API keys anywhere on either service.
 */

// ---------------------------------------------------------------------------
// MRMS ImageServer service info (`?f=json` → timeInfo.timeExtent)
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class MrmsServiceInfoResponse(
    @Json(name = "timeInfo") val timeInfo: MrmsTimeInfo?
)

@JsonClass(generateAdapter = true)
data class MrmsTimeInfo(
    /** Moving window as [startMs, endMs], epoch milliseconds. */
    @Json(name = "timeExtent") val timeExtent: List<Long>?
)

interface MrmsImageServerApi {
    /**
     * Service-info document. `f=json` returns JSON (default is HTML).
     * The wiring agent reads `timeInfo.timeExtent` and feeds it to
     * [com.example.data.mapper.mrmsFrameTimesFromTimeExtent].
     */
    @GET(".")
    suspend fun serviceInfo(
        @Query("f") format: String = "json"
    ): MrmsServiceInfoResponse
}

// ---------------------------------------------------------------------------
// LibreWXR weather-maps (`/public/weather-maps.json`)
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class LibreWxrMapsResponse(
    @Json(name = "host") val host: String?,
    @Json(name = "radar") val radar: LibreWxrRadarSection?
)

@JsonClass(generateAdapter = true)
data class LibreWxrRadarSection(
    @Json(name = "past") val past: List<LibreWxrFrameEntry>?,
    @Json(name = "nowcast") val nowcast: List<LibreWxrFrameEntry>?
)

@JsonClass(generateAdapter = true)
data class LibreWxrFrameEntry(
    /** Frame time, epoch SECONDS (not ms). */
    @Json(name = "time") val time: Long?,
    /**
     * Tile URL prefix for this frame, e.g. `/v2/radar/1794716400`.
     * Build tile URLs from this — never guess the prefix (a probe
     * with `/public/radar/{time}` 404'd).
     */
    @Json(name = "path") val path: String?
)

interface LibreWxrApi {
    /**
     * The maps document. Re-fetch on every loop refresh: frame
     * timestamps move and old frames prune within minutes. Feed the
     * response into the pure LibreWXR helpers (parse → loop frames →
     * tile URLs) via [com.example.data.mapper.LibreWxrMaps].
     */
    @GET("public/weather-maps.json")
    suspend fun weatherMaps(): LibreWxrMapsResponse
}
