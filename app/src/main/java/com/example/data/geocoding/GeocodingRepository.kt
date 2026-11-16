package com.example.data.geocoding

import com.example.data.api.OpenMeteoGeocodingApi
import com.example.data.api.ZippopotamApi
import com.example.data.api.toSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Place search (0.2.0): one query in, a picker list out.
 *
 * Routing (probe-verified 2026-11-16):
 * - 5-digit zip -> Zippopotam.us first; when it 404s, errors, or
 *   returns nothing usable, Open-Meteo `name={zip}` is the fallback
 *   (it genuinely accepts a US zip as the query).
 * - ZIP+4 -> stripped to its first 5 digits, same zip route.
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
    private val zippopotamApi: ZippopotamApi
) {
    suspend fun search(rawQuery: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        when (val query = classifyPlaceQuery(rawQuery) ?: return@withContext emptyList()) {
            is PlaceQuery.Zip -> searchZip(query.zip5)
            is PlaceQuery.Name -> searchByName(query.text)
        }
    }

    private suspend fun searchZip(zip5: String): List<PlaceSearchResult> {
        val fromZip = runCatching { zippopotamApi.lookupZip(zip5).toSearchResult() }.getOrNull()
        if (fromZip != null) return listOf(fromZip)
        return searchByName(zip5)
    }

    private suspend fun searchByName(name: String): List<PlaceSearchResult> {
        return openMeteoGeocodingApi.search(name = name).results
            ?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }
}
