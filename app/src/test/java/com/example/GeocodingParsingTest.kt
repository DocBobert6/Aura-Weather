package com.example

import com.example.data.api.OpenMeteoGeocodingResponse
import com.example.data.api.ZippopotamResponse
import com.example.data.api.toSearchResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Place-search response parsing (0.2.0): replays the exact payloads
 * probed live 2026-11-16 (~/workspace/aura-weather/
 * geocoding-probe-20261116.json) through the same Moshi setup the app
 * uses, and asserts the mapping to picker results. Runs in CI
 * (testDebugUnitTest); the pure tests cover routing and display.
 */
class GeocodingParsingTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Open-Meteo "Crittenden": Crittenden KY first, then the fuzzy
    // Mulberry AR leak the picker must show (never auto-select past).
    private val openMeteoCrittendenJson = """
        {
          "results": [
            {
              "id": 4289072,
              "name": "Crittenden",
              "latitude": 38.78284,
              "longitude": -84.60522,
              "elevation": 284.0,
              "feature_code": "PPL",
              "country_code": "US",
              "admin1": "Kentucky",
              "admin2": "Grant",
              "timezone": "America/New_York",
              "population": 3848,
              "postcodes": ["41030"],
              "country": "United States"
            },
            {
              "id": 4123120,
              "name": "Mulberry",
              "latitude": 35.50064,
              "longitude": -94.05159,
              "country_code": "US",
              "admin1": "Arkansas",
              "timezone": "America/Chicago",
              "population": 1634,
              "postcodes": ["72947"],
              "country": "United States"
            }
          ],
          "generationtime_ms": 0.1667738
        }
    """.trimIndent()

    private val zippopotam41030Json =
        """{"post code": "41030", "country": "United States", "country abbreviation": "US", "places": [{"place name": "Crittenden", "longitude": "-84.5982", "latitude": "38.7741", "state": "Kentucky", "state abbreviation": "KY"}]}"""

    @Test
    fun testOpenMeteoCrittendenParsesBothResults() {
        val response = moshi.adapter(OpenMeteoGeocodingResponse::class.java)
            .fromJson(openMeteoCrittendenJson)!!
        val results = response.results!!.mapNotNull { it.toSearchResult() }
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals("Crittenden", first.name)
        assertEquals("Kentucky", first.stateName)
        assertEquals("United States", first.country)
        assertEquals(38.78284, first.latitude, 0.00001)
        assertEquals(-84.60522, first.longitude, 0.00001)
        assertEquals(3848, first.population)
        assertEquals("Crittenden, Kentucky", first.placeLine())
        assertEquals("United States - Pop. 3,848", first.detailLine())
        // The fuzzy second result is a different town — the picker
        // shows it, it is never silently skipped or auto-picked past.
        assertEquals("Mulberry", results[1].name)
        assertEquals("Arkansas", results[1].stateName)
    }

    @Test
    fun testOpenMeteoResultWithoutCoordinatesIsDropped() {
        val response = moshi.adapter(OpenMeteoGeocodingResponse::class.java)
            .fromJson("""{"results": [{"name": "Nowhere", "country": "United States", "admin1": "Kentucky"}]}""")!!
        val results = response.results!!.mapNotNull { it.toSearchResult() }
        assertTrue("a result with no coordinates cannot be saved", results.isEmpty())
    }

    @Test
    fun testOpenMeteoNoResultsIsEmpty() {
        val response = moshi.adapter(OpenMeteoGeocodingResponse::class.java)
            .fromJson("""{"generationtime_ms": 0.1}""")!!
        assertNull(response.results)
    }

    @Test
    fun testZippopotam41030Parses() {
        val response = moshi.adapter(ZippopotamResponse::class.java)
            .fromJson(zippopotam41030Json)!!
        val result = response.toSearchResult()!!
        assertEquals("Crittenden", result.name)
        assertEquals("Kentucky", result.stateName)
        assertEquals("KY", result.stateCode)
        assertEquals("KY", result.storedStateCode())
        assertEquals(38.7741, result.latitude, 0.0001)
        assertEquals(-84.5982, result.longitude, 0.0001)
        assertNull("Zippopotam gives no population — none is invented", result.population)
    }

    @Test
    fun testZippopotamEmptyBodyHasNoResult() {
        val response = moshi.adapter(ZippopotamResponse::class.java)
            .fromJson("""{}""")!!
        assertNull("an empty body (the ZIP+4 404 body) falls back to Open-Meteo", response.toSearchResult())
    }
}
