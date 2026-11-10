package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pirate Weather forecast response (api.pirateweather.net/forecast).
 * A drop-in Dark Sky replacement: the `minutely` block carries true
 * 1-minute precipitation for the next 60 minutes. Only `minutely` is
 * used — the rest of the forecast (currently/hourly/daily) is ignored
 * by the app, so those fields are not modelled here.
 *
 * Auth: the API key travels as the `apikey` request header, NOT in
 * the URL path (a key in the path appears in logs). The docs require
 * *some* key-shaped segment in the path even when the header is used,
 * so the caller passes the literal dummy "key" there. Units are
 * requested as `si` so precipIntensity is in mm/h (the default `us`
 * units report it in inches per hour, which would be read 25x low).
 *
 * Endpoint verified live (returns 401 without a key, not 404). The
 * response shape below is from the Pirate Weather API docs
 * (pirateweather.net/en/latest/API) and the Dark Sky format it
 * mirrors: { minutely: { summary, data: [{ time (UNIX epoch
 * seconds), precipIntensity (mm/h), precipProbability (0-1),
 * precipType }] } }. The shape is NOT live-verified — no key exists
 * yet to fetch a real response — so every field is nullable with a
 * default and the repository treats a missing/empty minutely block
 * as "no Pirate Weather data" (fallback, never a crash). Free tier:
 * 10,000 calls/month, no card. A new key can take ~20 minutes to go
 * live. Community project, best-effort, no SLA.
 */
@JsonClass(generateAdapter = true)
data class PirateWeatherForecastResponse(
    @Json(name = "minutely") val minutely: PirateWeatherMinutely? = null
)

@JsonClass(generateAdapter = true)
data class PirateWeatherMinutely(
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "data") val data: List<PirateWeatherMinuteData>? = null
)

@JsonClass(generateAdapter = true)
data class PirateWeatherMinuteData(
    @Json(name = "time") val time: Long? = null,
    @Json(name = "precipIntensity") val precipIntensity: Double? = null,
    @Json(name = "precipProbability") val precipProbability: Double? = null,
    @Json(name = "precipType") val precipType: String? = null
)

interface PirateWeatherApi {
    @GET("forecast/{dummy}/{latlon}")
    suspend fun getForecast(
        @Path("dummy") dummy: String,
        @Path(value = "latlon", encoded = true) latlon: String,
        @Header("apikey") apiKey: String,
        @Query("units") units: String = "si"
    ): PirateWeatherForecastResponse
}
