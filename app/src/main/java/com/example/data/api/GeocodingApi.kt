package com.example.data.api

import com.example.data.geocoding.PlaceSearchResult
import com.example.data.geocoding.PlaceSearchSource
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

/**
 * Nominatim (OpenStreetMap, no key) — exact street addresses (0.4.0).
 * Response shape probe-verified live 2026-11-17
 * (~/workspace/aura-weather/nominatim-ferguson.json,
 * nominatim-whitehouse.json, nominatim-ferguson-details.json):
 * "20 Ferguson Blvd, Dry Ridge, KY" -> Walmart Supercenter,
 * 38.6789290, -84.5973400; "1600 Pennsylvania Ave NW, Washington, DC"
 * -> White House, 38.8976387, -77.0365525. `addressdetails=1` returns
 * the structured address (state, ISO3166-2-lvl4, country) so the
 * stored state/country come from fields, never parsed out of
 * display_name. Usage policy: max 1 req/s (enforced by
 * NominatimRateLimiter in the repository), the app User-Agent, and a
 * "© OpenStreetMap contributors" line in the picker.
 */
@JsonClass(generateAdapter = true)
data class NominatimPlace(
    @Json(name = "lat") val lat: String? = null,
    @Json(name = "lon") val lon: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "address") val address: Map<String, String>? = null
)

/**
 * A Nominatim hit without coordinates or a display name cannot be
 * shown or saved — drop it, never invent the missing half. The
 * stored place name is the typed address ([typedQuery]), with the
 * matched coordinates; the picker shows [displayName] so the user
 * confirms the match is the right spot. The state abbreviation
 * comes from ISO3166-2-lvl4 only when it is a US code ("US-KY" ->
 * "KY"); anything else falls back to the full state name via
 * [PlaceSearchResult.storedStateCode].
 */
fun NominatimPlace.toSearchResult(typedQuery: String): PlaceSearchResult? {
    val lat = lat?.toDoubleOrNull() ?: return null
    val lon = lon?.toDoubleOrNull() ?: return null
    val display = displayName?.takeIf { it.isNotBlank() } ?: return null
    val address = address ?: emptyMap()
    val iso = address["ISO3166-2-lvl4"] ?: ""
    val stateCode = if (iso.startsWith("US-") && iso.length == 5) iso.substring(3) else ""
    return PlaceSearchResult(
        name = typedQuery,
        stateName = address["state"] ?: "",
        stateCode = stateCode,
        country = address["country"] ?: "",
        latitude = lat,
        longitude = lon,
        population = null,
        source = PlaceSearchSource.NOMINATIM,
        displayName = display
    )
}

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 5
    ): List<NominatimPlace>
}
