package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Tomorrow.io Timelines response (api.tomorrow.io/v4/timelines).
 * One timeline per requested timestep; each interval carries the
 * requested fields as values (null when a field is unavailable for
 * that timestep or location). The app requests two timesteps:
 * "1m" for minute precipitation and "current" for the live pollen
 * index. Sub-hourly timesteps (1m/5m/15m/30m) are Enterprise in
 * Tomorrow.io's docs, so a free key may return no 1m timeline — the
 * minute-cast then falls back to the 15-minute Open-Meteo
 * interpolation, and only the pollen (current index, available on
 * free keys) flows. A missing timeline is a fallback, never a crash.
 */
@JsonClass(generateAdapter = true)
data class TomorrowTimelinesResponse(
    @Json(name = "data") val data: TomorrowTimelinesData?
)

@JsonClass(generateAdapter = true)
data class TomorrowTimelinesData(
    @Json(name = "timelines") val timelines: List<TomorrowTimeline>?
)

@JsonClass(generateAdapter = true)
data class TomorrowTimeline(
    @Json(name = "timestep") val timestep: String?,
    @Json(name = "intervals") val intervals: List<TomorrowInterval>?
)

@JsonClass(generateAdapter = true)
data class TomorrowInterval(
    @Json(name = "time") val time: String?,
    @Json(name = "values") val values: TomorrowValues?
)

@JsonClass(generateAdapter = true)
data class TomorrowValues(
    @Json(name = "precipitationIntensity") val precipitationIntensity: Double?,
    @Json(name = "precipitationProbability") val precipitationProbability: Double?,
    @Json(name = "treeIndex") val treeIndex: Int?,
    @Json(name = "grassIndex") val grassIndex: Int?,
    @Json(name = "weedIndex") val weedIndex: Int?
)

interface TomorrowApi {
    @GET("v4/timelines")
    suspend fun getTimelines(
        @Query("location") location: String,
        @Query("fields") fields: String,
        @Query("timesteps") timesteps: String,
        @Query("units") units: String = "metric",
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String,
        @Query("apikey") apiKey: String
    ): TomorrowTimelinesResponse
}
