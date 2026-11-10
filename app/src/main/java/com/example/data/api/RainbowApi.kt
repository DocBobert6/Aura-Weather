package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Rainbow Weather Nowcast response (api.rainbow.ai/nowcast/v1/precip/{lon}/{lat}).
 * Minute-by-minute precipitation for up to 4 hours ahead at 1-minute
 * resolution, updated every 10 minutes. Auth is the
 * Ocp-Apim-Subscription-Key header. Free tier: 5,000 requests/month.
 *
 * Endpoint verified live (returns 401 without a key, not 404). The
 * response shape below is from the Nowcast OpenAPI spec
 * (doc.rainbow.ai): { location, forecasts: [{timestamp,
 * precipitation_type, precipitation_intensity (mm/h), probability
 * (0-1)}], updated_at }. The shape is NOT live-verified — no key
 * exists yet to fetch a real response — so every field is nullable
 * with a default and the repository treats a missing/empty forecasts
 * list as "no Rainbow data" (fallback, never a crash). Live
 * verification with a real key is owed by Robert.
 */
@JsonClass(generateAdapter = true)
data class RainbowNowcastResponse(
    @Json(name = "forecasts") val forecasts: List<RainbowForecastPoint>? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RainbowForecastPoint(
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "precipitation_type") val precipitationType: String? = null,
    @Json(name = "precipitation_intensity") val precipitationIntensity: Double? = null,
    @Json(name = "probability") val probability: Double? = null
)

interface RainbowApi {
    @GET("nowcast/v1/precip/{lon}/{lat}")
    suspend fun getNowcast(
        @Path("lon") lon: String,
        @Path("lat") lat: String,
        @Header("Ocp-Apim-Subscription-Key") apiKey: String
    ): RainbowNowcastResponse
}
