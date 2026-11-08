package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class OpenMeteoWeatherResponse(
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "current") val current: OpenMeteoCurrent?,
    @Json(name = "hourly") val hourly: OpenMeteoHourly?,
    @Json(name = "minutely_15") val minutely15: OpenMeteoMinutely15?,
    @Json(name = "daily") val daily: OpenMeteoDaily?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    @Json(name = "time") val time: String?,
    @Json(name = "temperature_2m") val temperature2m: Double?,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Double?,
    @Json(name = "apparent_temperature") val apparentTemperature: Double?,
    @Json(name = "precipitation") val precipitation: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double?,
    @Json(name = "wind_direction_10m") val windDirection10m: Double?,
    @Json(name = "surface_pressure") val surfacePressure: Double?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "temperature_2m") val temperature2m: List<Double>?,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>?,
    @Json(name = "precipitation") val precipitation: List<Double>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?,
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double>?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoMinutely15(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "precipitation") val precipitation: List<Double>?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>?,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>?,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double>?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoAirQualityResponse(
    @Json(name = "current") val current: OpenMeteoAqCurrent?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoAqCurrent(
    @Json(name = "us_aqi") val usAqi: Int?,
    @Json(name = "pm2_5") val pm25: Double?,
    @Json(name = "pm10") val pm10: Double?,
    @Json(name = "ozone") val ozone: Double?
)

interface OpenMeteoWeatherApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m",
        @Query("minutely_15") minutely15: String = "precipitation",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoWeatherResponse
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "us_aqi,pm2_5,pm10,ozone"
    ): OpenMeteoAirQualityResponse
}
