package com.example.data.geocoding

import com.example.data.api.NominatimApi
import com.example.data.api.OpenMeteoGeocodingApi
import com.example.data.api.ZippopotamApi
import com.example.data.api.toSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Place search (0.2.0): one query in, a picker list out.
 *
 * Routing (probe-verified 2026-11-16, address path 2026-11-17):
 * - 5-digit zip -> Zippopotam.us first; when it 404s, errors, or
 *   returns nothing usable, Open-Meteo `name={zip}` is the fallback
 *   (it genuinely accepts a US zip as the query).
 * - ZIP+4 -> stripped to its first 5 digits, same zip route.
 * - street address (starts with a house number) -> Nominatim, at
 *   most 1 request/second, with the app User-Agent. When Nominatim
 *   itself fails (network, 403/429, bad body), the existing city
 *   search runs on the same text as the fallback; an empty Nominatim
 *   answer is an honest no-results, not a failure, so it does not
 *   fall back. The typed address is stored as the place name, with
 *   the matched coordinates — the picker shows Nominatim's display
 *   name so the user confirms the match.
 * - anything else -> Open-Meteo only, `count=10`, `language=en`.
 *
 * Never auto-selects: the caller always shows the full list and the
 * user picks (Open-Meteo fuzzy-matches unrelated names and non-place
 * features, and count=10 truncates big name lists). Throws on network
 * failure — the caller reports "Search failed"; an empty list means
 * no places found. No timezone comes from here: the first weather
 * load resolves it (WeatherRepository.resolvePlaceZone).
 */
class GeocodingRepository(
    private val openMeteoGeocodingApi: OpenMeteoGeocodingApi,
    private val zippopotamApi: ZippopotamApi,
    private val nominatimApi: NominatimApi,
    private val nominatimRateLimiter: NominatimRateLimiter = NominatimRateLimiter()
) {
    suspend fun search(rawQuery: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        when (val query = classifyPlaceQuery(rawQuery) ?: return@withContext emptyList()) {
            is PlaceQuery.Zip -> searchZip(query.zip5)
            is PlaceQuery.Address -> searchAddress(query.text)
            is PlaceQuery.Name -> searchByName(query.text)
        }
    }

    private suspend fun searchZip(zip5: String): List<PlaceSearchResult> {
        val fromZip = runCatching { zippopotamApi.lookupZip(zip5).toSearchResult() }.getOrNull()
        if (fromZip != null) return listOf(fromZip)
        return searchByName(zip5)
    }

    private suspend fun searchAddress(query: String): List<PlaceSearchResult> {
        val waitMs = nominatimRateLimiter.delayBeforeRequest()
        if (waitMs > 0) delay(waitMs)
        nominatimRateLimiter.markRequested()
        return runCatching {
            nominatimApi.search(query = query).mapNotNull { it.toSearchResult(query) }
        }.getOrElse {
            // Nominatim failed — fall back to the existing city search
            // on the same text (throws onward if that fails too, so the
            // caller still shows "Search failed").
            searchByName(query)
        }
    }

    private suspend fun searchByName(name: String): List<PlaceSearchResult> {
        return openMeteoGeocodingApi.search(name = name).results
            ?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }
}
