package com.example

import com.example.data.geocoding.PlaceQuery
import com.example.data.geocoding.PlaceSearchResult
import com.example.data.geocoding.classifyPlaceQuery
import com.example.data.geocoding.formatPopulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the place-search core (0.2.0): query routing, result
 * display lines, and population formatting. No Android, Retrofit, or
 * Moshi — these run in run-pure-tests-placesearch.sh. The JSON ->
 * model mapping is covered by GeocodingParsingTest in CI.
 */
class PlaceSearchTest {

    @Test
    fun testFiveDigitQueryIsZip() {
        assertEquals(PlaceQuery.Zip("41030"), classifyPlaceQuery("41030"))
    }

    @Test
    fun testZipPlus4StripsToFiveDigits() {
        assertEquals(PlaceQuery.Zip("41030"), classifyPlaceQuery("41030-1234"))
    }

    @Test
    fun testCityNameIsNameQuery() {
        assertEquals(PlaceQuery.Name("Crittenden"), classifyPlaceQuery("Crittenden"))
    }

    @Test
    fun testCityStateIsNameQueryUnchanged() {
        assertEquals(PlaceQuery.Name("Crittenden, KY"), classifyPlaceQuery("Crittenden, KY"))
    }

    @Test
    fun testQueryIsTrimmed() {
        assertEquals(PlaceQuery.Name("Dry Ridge"), classifyPlaceQuery("  Dry Ridge  "))
        assertEquals(PlaceQuery.Zip("90210"), classifyPlaceQuery(" 90210 "))
    }

    @Test
    fun testShortQueriesAreNotSearchable() {
        assertNull("blank is not searchable", classifyPlaceQuery(""))
        assertNull("whitespace is not searchable", classifyPlaceQuery("   "))
        assertNull("one character returns nothing from Open-Meteo", classifyPlaceQuery("C"))
    }

    @Test
    fun testTwoCharacterQueryIsSearchable() {
        assertEquals(PlaceQuery.Name("Lo"), classifyPlaceQuery("Lo"))
    }

    @Test
    fun testSixDigitsIsNotAZip() {
        assertEquals(
            "a 6-digit string is not a US zip — it goes to Open-Meteo as a name",
            PlaceQuery.Name("410300"),
            classifyPlaceQuery("410300")
        )
    }

    @Test
    fun testPlaceLineWithStateName() {
        val result = PlaceSearchResult(
            name = "Crittenden",
            stateName = "Kentucky",
            country = "United States",
            latitude = 38.78284,
            longitude = -84.60522,
            population = 3848
        )
        assertEquals("Crittenden, Kentucky", result.placeLine())
    }

    @Test
    fun testPlaceLineFallsBackToStateCode() {
        val result = PlaceSearchResult(
            name = "Crittenden",
            stateName = "",
            stateCode = "KY",
            country = "United States",
            latitude = 38.7741,
            longitude = -84.5982
        )
        assertEquals("Crittenden, KY", result.placeLine())
    }

    @Test
    fun testDetailLineIncludesPopulationOnlyWhenGiven() {
        val withPop = PlaceSearchResult(
            name = "Crittenden",
            stateName = "Kentucky",
            country = "United States",
            latitude = 38.78284,
            longitude = -84.60522,
            population = 3848
        )
        assertEquals("United States - Pop. 3,848", withPop.detailLine())
        val withoutPop = withPop.copy(population = null)
        assertEquals("United States", withoutPop.detailLine())
    }

    @Test
    fun testStoredStateCodePrefersAbbreviation() {
        val zippopotam = PlaceSearchResult(
            name = "Crittenden",
            stateName = "Kentucky",
            stateCode = "KY",
            country = "United States",
            latitude = 38.7741,
            longitude = -84.5982
        )
        assertEquals("KY", zippopotam.storedStateCode())
        val openMeteo = zippopotam.copy(stateCode = "")
        assertEquals("Open-Meteo gives no abbreviation — the region name is stored", "Kentucky", openMeteo.storedStateCode())
    }

    @Test
    fun testFormatPopulation() {
        assertEquals("Pop. 3,848", formatPopulation(3848))
        assertEquals("Pop. 8,865,000", formatPopulation(8865000))
        assertNull("no population is never invented", formatPopulation(null))
    }

    @Test
    fun testGeocodingEndpointsAreNotNws() {
        assertTrue(
            "the geocoding host must not be the NWS host",
            com.example.core.ProviderEndpoints.OPEN_METEO_GEOCODING_BASE_URL.startsWith("https://geocoding-api.open-meteo.com/")
        )
        assertTrue(
            "the zip host must not be the NWS host",
            com.example.core.ProviderEndpoints.ZIPPOPOTAM_BASE_URL.startsWith("https://api.zippopotam.us/")
        )
    }
}
