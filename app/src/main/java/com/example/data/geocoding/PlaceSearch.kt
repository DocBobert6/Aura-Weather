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
 * - a query starting with a house number (digits + street) is an exact
 *   street address: Nominatim (0.4.0, probe-verified 2026-11-17).
 * - anything else is a name for Open-Meteo only (worldwide).
 *
 * The picker ALWAYS shows the full list and the user picks — never an
 * auto-selected first result (Open-Meteo fuzzy-matches unrelated names
 * and non-place features, and count=10 truncates big name lists).
 * No timezone ever comes from the geocoder: the first weather load
 * resolves it (WeatherRepository.resolvePlaceZone).
 */
/** Which provider produced a search result (0.4.0). */
enum class PlaceSearchSource {
    OPEN_METEO,
    ZIPPOPOTAM,
    NOMINATIM
}

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
    val population: Int? = null,
    val source: PlaceSearchSource = PlaceSearchSource.OPEN_METEO,
    /**
     * The provider's full display name (Nominatim only, e.g. "Walmart
     * Supercenter, 20, Ferguson Boulevard, Dry Ridge, Grant County,
     * Kentucky, 41035, United States"). The picker shows this so the
     * user can confirm the match is the right spot; the stored place
     * name is the typed address ([name]), not this string.
     */
    val displayName: String = ""
) {
    /**
     * Picker line 1. A Nominatim result shows its full display name
     * (the confirmation the user needs); anything else shows
     * "Crittenden, Kentucky". Blanks are skipped.
     */
    fun placeLine(): String =
        if (displayName.isNotBlank()) displayName
        else listOf(name, stateName.ifBlank { stateCode })
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

/** Place-search UI states (0.2.0): the picker always shows the full
 * list for the user to pick from — never an auto-selected first result. */
sealed interface PlaceSearchUiState {
    object Idle : PlaceSearchUiState
    object Searching : PlaceSearchUiState
    data class Results(val results: List<PlaceSearchResult>) : PlaceSearchUiState
    data class NoResults(val query: String) : PlaceSearchUiState
    data class Failed(val query: String) : PlaceSearchUiState
}

sealed interface PlaceQuery {
    /** A 5-digit US zip (ZIP+4 already stripped to its first 5 digits). */
    data class Zip(val zip5: String) : PlaceQuery

    /**
     * An exact street address: starts with a house number (digits,
     * then a space or comma, then street text). Routed to Nominatim.
     */
    data class Address(val text: String) : PlaceQuery

    /** A place name, optionally with ", ST" (e.g. "Crittenden, KY"). */
    data class Name(val text: String) : PlaceQuery
}

private val ZIP_REGEX = Regex("^\\d{5}$")
private val ZIP_PLUS_4_REGEX = Regex("^(\\d{5})-\\d{4}$")
private val ADDRESS_REGEX = Regex("^\\d+[\\s,]+\\S.*$")

/** Typing debounce for city/zip search (0.2.0, unchanged). */
const val PLACE_SEARCH_DEBOUNCE_MS = 300L

/**
 * Typing debounce for the Nominatim address path (0.4.0): longer than
 * city typing, because an address query is longer and each keystroke
 * pause must not fire a request of its own — Nominatim's usage policy
 * allows at most 1 request/second.
 */
const val ADDRESS_SEARCH_DEBOUNCE_MS = 1000L

/** Debounce for a raw query: address queries wait longer (0.4.0). */
fun debounceMsFor(rawQuery: String): Long =
    if (classifyPlaceQuery(rawQuery) is PlaceQuery.Address) ADDRESS_SEARCH_DEBOUNCE_MS
    else PLACE_SEARCH_DEBOUNCE_MS

/** Nominatim usage policy: at most 1 request per second (0.4.0). */
const val NOMINATIM_MIN_INTERVAL_MS = 1000L

/**
 * Pure throttle for the Nominatim address path (0.4.0). The repository
 * asks [delayBeforeRequest] before every Nominatim call and records
 * the send with [markRequested]; the clock is injectable so the
 * pure tests can drive it without sleeping.
 */
fun nominatimDelayMs(lastRequestMs: Long?, nowMs: Long): Long {
    if (lastRequestMs == null) return 0L
    return (lastRequestMs + NOMINATIM_MIN_INTERVAL_MS - nowMs).coerceAtLeast(0L)
}

class NominatimRateLimiter(private val nowMs: () -> Long = { System.currentTimeMillis() }) {
    private var lastRequestMs: Long? = null

    /** Milliseconds to wait before the next Nominatim request (0 = send now). */
    fun delayBeforeRequest(): Long = nominatimDelayMs(lastRequestMs, nowMs())

    /** Record that a Nominatim request is being sent now. */
    fun markRequested() {
        lastRequestMs = nowMs()
    }
}

/**
 * Routes a raw search query. Null when there is nothing searchable:
 * blank, or under 2 characters (Open-Meteo returns nothing for an
 * empty/1-character name, and 2 characters is exact-match only).
 * A ZIP+4 is silently routed as its 5-digit prefix — the prefix
 * identifies the same town. A query starting with a house number
 * (digits, then a space or comma, then street text) is an exact
 * street address for Nominatim — the zip checks run first, so a
 * bare 5-digit zip never lands here.
 */
fun classifyPlaceQuery(raw: String): PlaceQuery? {
    val query = raw.trim()
    if (query.length < 2) return null
    if (ZIP_REGEX.matches(query)) return PlaceQuery.Zip(query)
    val zipPlus4 = ZIP_PLUS_4_REGEX.matchEntire(query)
    if (zipPlus4 != null) return PlaceQuery.Zip(zipPlus4.groupValues[1])
    if (ADDRESS_REGEX.matches(query)) return PlaceQuery.Address(query)
    return PlaceQuery.Name(query)
}
