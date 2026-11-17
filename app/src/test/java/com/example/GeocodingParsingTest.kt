package com.example

import com.example.data.api.NominatimPlace
import com.example.data.api.OpenMeteoGeocodingResponse
import com.example.data.api.ZippopotamResponse
import com.example.data.api.toSearchResult
import com.example.data.geocoding.PlaceSearchSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

    // Nominatim (0.4.0): replays the exact payloads probed live
    // 2026-11-17 (~/workspace/aura-weather/nominatim-ferguson.json,
    // nominatim-whitehouse.json, nominatim-ferguson-details.json).
    // The typed address is the stored name; the picker shows the
    // display name; state/country come from addressdetails, never
    // parsed out of display_name.

    private val nominatimFergusonJson = """
        [
          {
            "place_id": 340516733,
            "licence": "Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright",
            "osm_type": "way",
            "osm_id": 119970231,
            "lat": "38.6789290",
            "lon": "-84.5973400",
            "class": "shop",
            "type": "supermarket",
            "place_rank": 30,
            "importance": 4.947814145108747e-05,
            "addresstype": "shop",
            "name": "Walmart Supercenter",
            "display_name": "Walmart Supercenter, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States",
            "address": {
              "shop": "Walmart Supercenter",
              "house_number": "20",
              "road": "Ferguson Boulevard",
              "town": "Dry Ridge",
              "county": "Grant County",
              "state": "Kentucky",
              "ISO3166-2-lvl4": "US-KY",
              "postcode": "41035",
              "country": "United States",
              "country_code": "us"
            },
            "boundingbox": ["38.6782077", "38.6794628", "-84.5980901", "-84.5962538"]
          },
          {
            "place_id": 338941519,
            "licence": "Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright",
            "osm_type": "node",
            "osm_id": 9178758804,
            "lat": "38.6792637",
            "lon": "-84.5965327",
            "class": "shop",
            "type": "garden_centre",
            "place_rank": 30,
            "importance": 4.947814145108747e-05,
            "addresstype": "shop",
            "name": "Walmart Garden Center",
            "display_name": "Walmart Garden Center, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States",
            "address": {
              "shop": "Walmart Garden Center",
              "house_number": "20",
              "road": "Ferguson Boulevard",
              "town": "Dry Ridge",
              "county": "Grant County",
              "state": "Kentucky",
              "ISO3166-2-lvl4": "US-KY",
              "postcode": "41035",
              "country": "United States",
              "country_code": "us"
            },
            "boundingbox": ["38.6792137", "38.6793137", "-84.5965827", "-84.5964827"]
          }
        ]
    """.trimIndent()

    private val nominatimListType = Types.newParameterizedType(List::class.java, NominatimPlace::class.java)

    private fun parseNominatim(json: String, typedQuery: String) =
        moshi.adapter<List<NominatimPlace>>(nominatimListType).fromJson(json)!!
            .mapNotNull { it.toSearchResult(typedQuery) }

    @Test
    fun testNominatimFergusonParses() {
        val results = parseNominatim(nominatimFergusonJson, "20 Ferguson Blvd, Dry Ridge, KY")
        assertEquals("limit=5 returns the nearby matches at the same address", 2, results.size)
        val first = results[0]
        assertEquals("the typed address is the stored name", "20 Ferguson Blvd, Dry Ridge, KY", first.name)
        assertEquals("Kentucky", first.stateName)
        assertEquals("KY", first.stateCode)
        assertEquals("KY", first.storedStateCode())
        assertEquals("United States", first.country)
        assertEquals(38.6789290, first.latitude, 0.0000001)
        assertEquals(-84.5973400, first.longitude, 0.0000001)
        assertEquals(PlaceSearchSource.NOMINATIM, first.source)
        assertEquals(
            "Walmart Supercenter, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States",
            first.placeLine()
        )
        assertEquals("United States", first.detailLine())
        assertNull("Nominatim gives no population — none is invented", first.population)
        // The second match at the same address is a different place —
        // the picker shows it, it is never silently skipped or
        // auto-picked past.
        assertEquals(
            "Walmart Garden Center, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States",
            results[1].placeLine()
        )
        assertEquals(38.6792637, results[1].latitude, 0.0000001)
    }

    @Test
    fun testNominatimWhiteHouseParsesWithoutAddressDetails() {
        // Probed without addressdetails=1: no structured address, so
        // state/country stay blank rather than being parsed out of
        // display_name — the coordinates and display name still map.
        val whiteHouseJson = """
            [
              {
                "place_id": 345445948,
                "licence": "Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright",
                "osm_type": "relation",
                "osm_id": 19761182,
                "lat": "38.8976387",
                "lon": "-77.0365525",
                "class": "office",
                "type": "government",
                "place_rank": 30,
                "importance": 0.6958339719610435,
                "addresstype": "office",
                "name": "White House",
                "display_name": "White House, 1600, Pennsylvania Avenue Northwest, Ward 2, Washington, District of Columbia, 20500, United States",
                "boundingbox": ["38.8974904", "38.8977959", "-77.0368541", "-77.0362509"]
              }
            ]
        """.trimIndent()
        val results = parseNominatim(whiteHouseJson, "1600 Pennsylvania Ave NW, Washington, DC")
        assertEquals(1, results.size)
        val first = results[0]
        assertEquals("1600 Pennsylvania Ave NW, Washington, DC", first.name)
        assertEquals(38.8976387, first.latitude, 0.0000001)
        assertEquals(-77.0365525, first.longitude, 0.0000001)
        assertEquals(
            "White House, 1600, Pennsylvania Avenue Northwest, Ward 2, Washington, District of Columbia, 20500, United States",
            first.placeLine()
        )
        assertEquals("", first.stateName)
        assertEquals("", first.storedStateCode())
    }

    @Test
    fun testNominatimResultWithoutCoordinatesOrDisplayNameIsDropped() {
        val noCoords = parseNominatim(
            """[{"display_name": "Nowhere, United States"}]""",
            "1 Nowhere St"
        )
        assertTrue("a result with no coordinates cannot be saved", noCoords.isEmpty())
        val noDisplayName = parseNominatim(
            """[{"lat": "38.0", "lon": "-84.0"}]""",
            "1 Nowhere St"
        )
        assertTrue("a result with no display name cannot be shown", noDisplayName.isEmpty())
    }

    @Test
    fun testNominatimNonUsIsoCodeDoesNotBecomeStateCode() {
        val result = parseNominatim(
            """[{"lat": "51.5074", "lon": "-0.1278", "display_name": "10 Downing Street, London, England, United Kingdom", "address": {"state": "England", "ISO3166-2-lvl4": "GB-ENG", "country": "United Kingdom"}}]""",
            "10 Downing St, London"
        )[0]
        assertEquals("England", result.stateName)
        assertEquals(
            "a non-US ISO code is not a state abbreviation — the full state name is stored",
            "England",
            result.storedStateCode()
        )
        assertEquals("United Kingdom", result.country)
    }
}
