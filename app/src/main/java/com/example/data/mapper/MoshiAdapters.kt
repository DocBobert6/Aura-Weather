package com.example.data.mapper

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.LocalDate

/**
 * Moshi has no built-in adapter for java.time types. The cache stores real
 * JSON (see WeatherRepository), so these two adapters are what make the
 * round-trip work: instants as epoch millis, dates as ISO strings.
 * Register them before KotlinJsonAdapterFactory — first match wins.
 */
object InstantJsonAdapter {
    @ToJson
    fun toJson(value: Instant): Long = value.toEpochMilli()

    @FromJson
    fun fromJson(value: Long): Instant = Instant.ofEpochMilli(value)
}

object LocalDateJsonAdapter {
    @ToJson
    fun toJson(value: LocalDate): String = value.toString()

    @FromJson
    fun fromJson(value: String): LocalDate = LocalDate.parse(value)
}
