package com.example.data.api

import com.example.data.geocoding.PlaceSearchResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Place-search APIs (0.2.0). Both ride the shared plain client (never
 * the NWS client — no NWS headers to a non-NWS host). Response shapes
 * are probe-verified live 2026-11-16 (~/workspace/aura-weather/
 * geocoding-probe-20261116.json); the parsing test replays those
 * exact payloads (GeocodingParsingTest).
 */
@JsonClass(generateAdapter = true)
data class OpenMeteoGeocodingResponse(
    @Json(name = "results") val results: List<OpenMeteoGeocodingPlace>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoGeocodingPlace(
    @Json(name = "name") val name: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "admin1") val admin1: String? = null,
    @Json(name = "admin2") val admin2: String? = null,
    @Json(name = "population") val population: Int? = null,
    @Json(name = "postcodes") val postcodes: List<String>? = null
)

/**
 * A result without a name or coordinates cannot be saved — drop it,
 * never invent the missing half. Open-Meteo gives the full region name
 * (admin1, "Kentucky") but no state abbreviation, so stateCode stays
 * blank and the stored value falls back to the region name.
 */
fun OpenMeteoGeocodingPlace.toSearchResult(): PlaceSearchResult? {
    val placeName = name?.takeIf { it.isNotBlank() } ?: return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return PlaceSearchResult(
        name = placeName,
        stateName = admin1 ?: "",
        stateCode = "",
        country = country ?: "",
        latitude = lat,
        longitude = lon,
        population = population
    )
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocodingResponse
}

@JsonClass(generateAdapter = true)
data class ZippopotamResponse(
    @Json(name = "post code") val postCode: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "country abbreviation") val countryAbbreviation: String? = null,
    @Json(name = "places") val places: List<ZippopotamPlace>? = null
)

@JsonClass(generateAdapter = true)
data class ZippopotamPlace(
    @Json(name = "place name") val placeName: String? = null,
    @Json(name = "longitude") val longitude: String? = null,
    @Json(name = "latitude") val latitude: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "state abbreviation") val stateAbbreviation: String? = null
)

/**
 * Zippopotam returns one place per zip, with coordinates as strings.
 * No place, no name, or unparsable coordinates -> null, so the caller
 * falls back to Open-Meteo instead of saving a broken place.
 */
fun ZippopotamResponse.toSearchResult(): PlaceSearchResult? {
    val place = places?.firstOrNull() ?: return null
    val placeName = place.placeName?.takeIf { it.isNotBlank() } ?: return null
    val lat = place.latitude?.toDoubleOrNull() ?: return null
    val lon = place.longitude?.toDoubleOrNull() ?: return null
    return PlaceSearchResult(
        name = placeName,
        stateName = place.state ?: "",
        stateCode = place.stateAbbreviation ?: "",
        country = country ?: "",
        latitude = lat,
        longitude = lon,
        population = null
    )
}

interface ZippopotamApi {
    @GET("us/{zip}")
    suspend fun lookupZip(@Path("zip") zip: String): ZippopotamResponse
}
