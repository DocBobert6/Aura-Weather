package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * AirNow current-observation response (docs.airnowapi.org,
 * aq/observation/latLon/current): one object per pollutant (PM2.5,
 * PM10, O3) for the reporting area nearest the coordinates, within
 * the requested distance (miles). AirNow reports AQI + category per
 * pollutant but NO concentrations, so the pollutant concentrations on
 * the Air Quality screen stay Open-Meteo's; AirNow supplies only the
 * headline AQI, its category, and the dominant pollutant. AirNow is
 * US-only: outside the US it returns an empty array, and the app
 * falls back to the Open-Meteo AQI.
 */
@JsonClass(generateAdapter = true)
data class AirNowCategory(
    @Json(name = "Number") val number: Int?,
    @Json(name = "Name") val name: String?
)

@JsonClass(generateAdapter = true)
data class AirNowObservation(
    @Json(name = "DateObserved") val dateObserved: String?,
    @Json(name = "HourObserved") val hourObserved: Int?,
    @Json(name = "LocalTimeZone") val localTimeZone: String?,
    @Json(name = "ReportingArea") val reportingArea: String?,
    @Json(name = "StateCode") val stateCode: String?,
    @Json(name = "ParameterName") val parameterName: String?,
    @Json(name = "AQI") val aqi: Int?,
    @Json(name = "Category") val category: AirNowCategory?
)

interface AirNowApi {
    @GET("aq/observation/latLon/current/")
    suspend fun getCurrentObservations(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("distance") distance: Int = 25,
        @Query("format") format: String = "application/json",
        @Query("API_KEY") apiKey: String
    ): List<AirNowObservation>
}
