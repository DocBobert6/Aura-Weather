package com.example.data.geocoding

import java.util.Locale

/**
 * Place search (0.2.0): the pure-Kotlin core — query routing and result
 * shaping, with no Android, Retrofit, or Moshi dependency, so it runs in
 * the pure tests (run-pure-tests-placesearch.sh) as well as in CI.
 *
 * Routing (probe-verified 2026-11-16, see GeocodingRepository):
 * - a 5-digit query is a US zip: Zippopotam.us first, Open-Meteo fallback.
 * - ZIP+4 is stripped to its first 5 digits and routed as a zip.
 * - anything else is a name for Open-Meteo only (worldwide).
 *
 * The picker ALWAYS shows the full list and the user picks — never an
 * auto-selected first result (Open-Meteo fuzzy-matches unrelated names
 * and non-place features, and count=10 truncates big name lists).
 * No timezone ever comes from the geocoder: the first weather load
 * resolves it (WeatherRepository.resolvePlaceZone).
 */
data class PlaceSearchResult(
    val name: String,
    /** Full region name from the provider ("Kentucky"). */
    val stateName: String = "",
    /** Abbreviation when the provider gives one ("KY" — Zippopotam only). */
    val stateCode: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    /** Only when the API returned one — never invented. */
    val population: Int? = null
) {
    /** Picker line 1: "Crittenden, Kentucky". Blanks are skipped. */
    fun placeLine(): String =
        listOf(name, stateName.ifBlank { stateCode })
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** Picker line 2: "United States - Pop. 3,848". Population only when given. */
    fun detailLine(): String {
        val parts = mutableListOf<String>()
        if (country.isNotBlank()) parts.add(country)
        formatPopulation(population)?.let { parts.add(it) }
        return parts.joinToString(" - ")
    }

    /** The state value stored on the saved place: the abbreviation when
     * the provider gave one, else the full region name. */
    fun storedStateCode(): String = stateCode.ifBlank { stateName }
}

/** "Pop. 3,848", or null when the API gave no population. */
fun formatPopulation(population: Int?): String? {
    if (population == null) return null
    return "Pop. ${String.format(Locale.US, "%,d", population)}"
}

sealed interface PlaceQuery {
    /** A 5-digit US zip (ZIP+4 already stripped to its first 5 digits). */
    data class Zip(val zip5: String) : PlaceQuery

    /** A place name, optionally with ", ST" (e.g. "Crittenden, KY"). */
    data class Name(val text: String) : PlaceQuery
}

private val ZIP_REGEX = Regex("^\\d{5}$")
private val ZIP_PLUS_4_REGEX = Regex("^(\\d{5})-\\d{4}$")

/**
 * Routes a raw search query. Null when there is nothing searchable:
 * blank, or under 2 characters (Open-Meteo returns nothing for an
 * empty/1-character name, and 2 characters is exact-match only).
 * A ZIP+4 is silently routed as its 5-digit prefix — the prefix
 * identifies the same town.
 */
fun classifyPlaceQuery(raw: String): PlaceQuery? {
    val query = raw.trim()
    if (query.length < 2) return null
    if (ZIP_REGEX.matches(query)) return PlaceQuery.Zip(query)
    val zipPlus4 = ZIP_PLUS_4_REGEX.matchEntire(query)
    if (zipPlus4 != null) return PlaceQuery.Zip(zipPlus4.groupValues[1])
    return PlaceQuery.Name(query)
}
