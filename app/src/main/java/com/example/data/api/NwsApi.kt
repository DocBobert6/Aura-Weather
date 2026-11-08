package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class NwsPointsResponse(
    @Json(name = "properties") val properties: NwsPointsProperties?
)

@JsonClass(generateAdapter = true)
data class NwsPointsProperties(
    @Json(name = "gridId") val gridId: String?,
    @Json(name = "gridX") val gridX: Int?,
    @Json(name = "gridY") val gridY: Int?,
    @Json(name = "forecast") val forecast: String?,
    @Json(name = "forecastHourly") val forecastHourly: String?,
    @Json(name = "forecastZone") val forecastZone: String?,
    @Json(name = "radarStation") val radarStation: String?,
    // IANA timezone for the point, e.g. "America/Denver". This is how a
    // place's zone is resolved — never a hardcoded default.
    @Json(name = "timeZone") val timeZone: String?
)

@JsonClass(generateAdapter = true)
data class NwsStationsResponse(
    @Json(name = "features") val features: List<NwsStationFeature>?
)

@JsonClass(generateAdapter = true)
data class NwsStationFeature(
    @Json(name = "properties") val properties: NwsStationProperties?
)

@JsonClass(generateAdapter = true)
data class NwsStationProperties(
    @Json(name = "stationIdentifier") val stationIdentifier: String?,
    @Json(name = "name") val name: String?
)

@JsonClass(generateAdapter = true)
data class NwsObservationResponse(
    @Json(name = "properties") val properties: NwsObservationProperties?
)

@JsonClass(generateAdapter = true)
data class NwsObservationProperties(
    @Json(name = "timestamp") val timestamp: String?,
    @Json(name = "textDescription") val textDescription: String?,
    @Json(name = "temperature") val temperature: NwsUnitValue?,
    @Json(name = "dewpoint") val dewpoint: NwsUnitValue?,
    @Json(name = "windDirection") val windDirection: NwsUnitValue?,
    @Json(name = "windSpeed") val windSpeed: NwsUnitValue?,
    @Json(name = "windGust") val windGust: NwsUnitValue?,
    @Json(name = "barometricPressure") val barometricPressure: NwsUnitValue?,
    @Json(name = "relativeHumidity") val relativeHumidity: NwsUnitValue?,
    @Json(name = "visibility") val visibility: NwsUnitValue?,
    @Json(name = "heatIndex") val heatIndex: NwsUnitValue?,
    @Json(name = "windChill") val windChill: NwsUnitValue?
)

@JsonClass(generateAdapter = true)
data class NwsUnitValue(
    @Json(name = "value") val value: Double?,
    @Json(name = "unitCode") val unitCode: String?
)

@JsonClass(generateAdapter = true)
data class NwsForecastResponse(
    @Json(name = "properties") val properties: NwsForecastProperties?
)

@JsonClass(generateAdapter = true)
data class NwsForecastProperties(
    @Json(name = "updated") val updated: String?,
    @Json(name = "periods") val periods: List<NwsForecastPeriod>?
)

@JsonClass(generateAdapter = true)
data class NwsForecastPeriod(
    @Json(name = "number") val number: Int,
    @Json(name = "name") val name: String?,
    @Json(name = "startTime") val startTime: String?,
    @Json(name = "endTime") val endTime: String?,
    @Json(name = "isDaytime") val isDaytime: Boolean = true,
    @Json(name = "temperature") val temperature: Double?,
    @Json(name = "temperatureUnit") val temperatureUnit: String?,
    @Json(name = "windSpeed") val windSpeed: String?,
    @Json(name = "windDirection") val windDirection: String?,
    @Json(name = "shortForecast") val shortForecast: String?,
    @Json(name = "detailedForecast") val detailedForecast: String?,
    @Json(name = "probabilityOfPrecipitation") val probabilityOfPrecipitation: NwsUnitValue?
)

@JsonClass(generateAdapter = true)
data class NwsAlertsResponse(
    @Json(name = "features") val features: List<NwsAlertFeature>?
)

@JsonClass(generateAdapter = true)
data class NwsAlertFeature(
    @Json(name = "properties") val properties: NwsAlertProperties?
)

@JsonClass(generateAdapter = true)
data class NwsAlertProperties(
    @Json(name = "id") val id: String?,
    @Json(name = "areaDesc") val areaDesc: String?,
    @Json(name = "event") val event: String?,
    @Json(name = "headline") val headline: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "instruction") val instruction: String?,
    @Json(name = "severity") val severity: String?,
    @Json(name = "urgency") val urgency: String?,
    @Json(name = "certainty") val certainty: String?,
    @Json(name = "effective") val effective: String?,
    @Json(name = "expires") val expires: String?,
    @Json(name = "ends") val ends: String?
)

interface NwsApi {
    @GET("points/{latitude},{longitude}")
    suspend fun getPoints(
        @Path("latitude") lat: Double,
        @Path("longitude") lon: Double
    ): NwsPointsResponse

    @GET("points/{latitude},{longitude}/stations")
    suspend fun getStations(
        @Path("latitude") lat: Double,
        @Path("longitude") lon: Double
    ): NwsStationsResponse

    @GET("stations/{stationId}/observations/latest")
    suspend fun getLatestObservation(
        @Path("stationId") stationId: String
    ): NwsObservationResponse

    @GET("gridpoints/{wfo}/{x},{y}/forecast")
    suspend fun getForecast(
        @Path("wfo") wfo: String,
        @Path("x") x: Int,
        @Path("y") y: Int
    ): NwsForecastResponse

    @GET("gridpoints/{wfo}/{x},{y}/forecast/hourly")
    suspend fun getHourlyForecast(
        @Path("wfo") wfo: String,
        @Path("x") x: Int,
        @Path("y") y: Int
    ): NwsForecastResponse

    @GET("alerts/active")
    suspend fun getActiveAlerts(
        @Query("point") point: String
    ): NwsAlertsResponse
}
