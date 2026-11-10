package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class OpenMeteoWeatherResponse(
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int?,
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
    @Json(name = "surface_pressure") val surfacePressure: Double?,
    @Json(name = "cloud_cover") val cloudCover: Double?
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "temperature_2m") val temperature2m: List<Double>?,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>?,
    @Json(name = "precipitation") val precipitation: List<Double>?,
    @Json(name = "snowfall") val snowfall: List<Double>?,
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
    @Json(name = "precipitation_sum") val precipitationSum: List<Double>?,
    @Json(name = "snowfall_sum") val snowfallSum: List<Double>?,
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
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure,cloud_cover",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation,snowfall,weather_code,wind_speed_10m",
        @Query("minutely_15") minutely15: String = "precipitation",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,snowfall_sum,uv_index_max",
        // Open-Meteo defaults to 7 days; request 16 (its max) so the
        // outlook can show 15. NWS only covers ~7 days, so days 8-15
        // come from Open-Meteo (see fillDailyPrecipAndExtend).
        @Query("forecast_days") forecastDays: Int = 16,
        // Request UTC explicitly. The old "auto" value returned local wall
        // times, which the repository then parsed as UTC ("${t}:00Z") and
        // shifted every hour 4-5 hours. All parsing goes through
        // parseOpenMeteoUtcInstant (see WeatherMappers).
        @Query("timezone") timezone: String = "UTC"
    ): OpenMeteoWeatherResponse

    /**
     * Daily max/min in the PLACE's local timezone, for the date-matched
     * daily fills (fillDailyHighs, fillDailyPrecipAndExtend, the UV merge).
     *
     * The main getForecast call requests timezone=UTC (so its hourly
     * parses as UTC), which means its daily array starts at the UTC date.
     * In the evening, once the UTC date has rolled past the local date
     * (after 7 PM EST / 8 PM EDT), the local today is missing from that
     * array, so fillDailyHighs cannot fill TODAY's high and the outlook
     * shows "HI --". This lean daily-only call uses the place timezone
     * (or "auto"), so its daily array starts at the local today. Only
     * the daily section is read from this response; the hourly wall-time
     * question does not apply to daily dates.
     */
    @GET("v1/forecast")
    suspend fun getDailyLocal(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,snowfall_sum,uv_index_max",
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("timezone") timezone: String
    ): OpenMeteoWeatherResponse

    /**
     * Multi-location current conditions (R5 regional map): one call for
     * several points. latitude/longitude are comma-separated lists and the
     * response is a JSON array in the same order. `encoded = true` so the
     * commas go through verbatim.
     */
    @GET("v1/forecast")
    suspend fun getCurrentMulti(
        @Query("latitude", encoded = true) latitudes: String,
        @Query("longitude", encoded = true) longitudes: String,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("timezone") timezone: String = "UTC"
    ): List<OpenMeteoWeatherResponse>
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "us_aqi,pm2_5,pm10,ozone"
    ): OpenMeteoAirQualityResponse
}
