package com.example

import com.example.core.ProviderEndpoints
import com.example.data.geocoding.ADDRESS_SEARCH_DEBOUNCE_MS
import com.example.data.geocoding.NOMINATIM_MIN_INTERVAL_MS
import com.example.data.geocoding.NominatimRateLimiter
import com.example.data.geocoding.PLACE_SEARCH_DEBOUNCE_MS
import com.example.data.geocoding.PlaceQuery
import com.example.data.geocoding.PlaceSearchResult
import com.example.data.geocoding.PlaceSearchSource
import com.example.data.geocoding.classifyPlaceQuery
import com.example.data.geocoding.debounceMsFor
import com.example.data.geocoding.formatPopulation
import com.example.data.geocoding.nominatimDelayMs
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

    // Nominatim address routing (0.4.0): a query starting with a house
    // number (digits, then a space or comma, then street text) is an
    // exact street address. The zip checks run first, so a bare zip
    // never lands here.

    @Test
    fun testFergusonAddressIsAddressQuery() {
        assertEquals(
            PlaceQuery.Address("20 Ferguson Blvd, Dry Ridge, KY"),
            classifyPlaceQuery("20 Ferguson Blvd, Dry Ridge, KY")
        )
    }

    @Test
    fun testWhiteHouseAddressIsAddressQuery() {
        assertEquals(
            PlaceQuery.Address("1600 Pennsylvania Ave NW, Washington, DC"),
            classifyPlaceQuery("1600 Pennsylvania Ave NW, Washington, DC")
        )
    }

    @Test
    fun testBareStreetAddressIsAddressQuery() {
        assertEquals(
            PlaceQuery.Address("20 Ferguson Blvd"),
            classifyPlaceQuery("20 Ferguson Blvd")
        )
        assertEquals(
            PlaceQuery.Address("1 Infinite Loop, Cupertino, CA"),
            classifyPlaceQuery("1 Infinite Loop, Cupertino, CA")
        )
    }

    @Test
    fun testAddressWithCommaAfterNumberIsAddressQuery() {
        assertEquals(
            PlaceQuery.Address("20, Ferguson Blvd, Dry Ridge, KY"),
            classifyPlaceQuery("20, Ferguson Blvd, Dry Ridge, KY")
        )
    }

    @Test
    fun testAddressQueryIsTrimmed() {
        assertEquals(
            PlaceQuery.Address("20 Ferguson Blvd, Dry Ridge, KY"),
            classifyPlaceQuery("  20 Ferguson Blvd, Dry Ridge, KY  ")
        )
    }

    @Test
    fun testZipWithStreetTextIsAddressNotZip() {
        assertEquals(
            "a zip followed by street text is an address, not a bare zip",
            PlaceQuery.Address("41030 Main St"),
            classifyPlaceQuery("41030 Main St")
        )
    }

    @Test
    fun testDigitLedNonAddressStaysName() {
        assertEquals(
            "\"1st Street\" has no space after the digits — it is a street name, not a house number",
            PlaceQuery.Name("1st Street"),
            classifyPlaceQuery("1st Street")
        )
        assertEquals(
            "a bare house number with no street text is not an address",
            PlaceQuery.Name("20"),
            classifyPlaceQuery("20")
        )
    }

    @Test
    fun testZipStillWinsOverAddress() {
        assertEquals(PlaceQuery.Zip("41030"), classifyPlaceQuery("41030"))
        assertEquals(PlaceQuery.Zip("41030"), classifyPlaceQuery("41030-1234"))
    }

    // Address debounce (0.4.0): the address path waits 1000 ms per
    // keystroke pause (Nominatim allows 1 req/s); city/zip stay 300 ms.

    @Test
    fun testDebounceIsLongerForAddresses() {
        assertEquals(ADDRESS_SEARCH_DEBOUNCE_MS, debounceMsFor("20 Ferguson Blvd, Dry Ridge, KY"))
        assertEquals(1000L, ADDRESS_SEARCH_DEBOUNCE_MS)
        assertEquals(PLACE_SEARCH_DEBOUNCE_MS, debounceMsFor("Crittenden"))
        assertEquals(300L, PLACE_SEARCH_DEBOUNCE_MS)
        assertEquals(PLACE_SEARCH_DEBOUNCE_MS, debounceMsFor("41030"))
    }

    // Nominatim throttle (0.4.0): at most 1 request per second.

    @Test
    fun testNominatimDelayMs() {
        assertEquals("first request ever sends at once", 0L, nominatimDelayMs(null, 10_000L))
        assertEquals(
            "a request 200 ms after the last waits 800 ms",
            800L,
            nominatimDelayMs(10_000L, 10_200L)
        )
        assertEquals(
            "a request exactly 1 s after the last sends at once",
            0L,
            nominatimDelayMs(10_000L, 11_000L)
        )
        assertEquals(
            "a request long after the last sends at once",
            0L,
            nominatimDelayMs(10_000L, 60_000L)
        )
        assertEquals(1000L, NOMINATIM_MIN_INTERVAL_MS)
    }

    @Test
    fun testNominatimRateLimiterWithFakeClock() {
        var now = 50_000L
        val limiter = NominatimRateLimiter(nowMs = { now })
        assertEquals("first request sends at once", 0L, limiter.delayBeforeRequest())
        limiter.markRequested()
        assertEquals("immediate second request waits the full second", 1000L, limiter.delayBeforeRequest())
        now += 400
        assertEquals("400 ms later, 600 ms remain", 600L, limiter.delayBeforeRequest())
        now += 600
        assertEquals("a second after the send, the next is free", 0L, limiter.delayBeforeRequest())
    }

    // Nominatim result display (0.4.0): the picker shows the full
    // display name; the stored name is the typed address.

    @Test
    fun testPlaceLinePrefersNominatimDisplayName() {
        val result = PlaceSearchResult(
            name = "20 Ferguson Blvd, Dry Ridge, KY",
            stateName = "Kentucky",
            stateCode = "KY",
            country = "United States",
            latitude = 38.6789290,
            longitude = -84.5973400,
            source = PlaceSearchSource.NOMINATIM,
            displayName = "Walmart Supercenter, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States"
        )
        assertEquals(
            "Walmart Supercenter, 20, Ferguson Boulevard, Dry Ridge, Grant County, Kentucky, 41035, United States",
            result.placeLine()
        )
        assertEquals("United States", result.detailLine())
        assertEquals("the typed address is the stored name", "20 Ferguson Blvd, Dry Ridge, KY", result.name)
        assertEquals("KY", result.storedStateCode())
    }

    @Test
    fun testPlaceLineWithoutDisplayNameIsUnchanged() {
        val result = PlaceSearchResult(
            name = "Crittenden",
            stateName = "Kentucky",
            country = "United States",
            latitude = 38.78284,
            longitude = -84.60522,
            population = 3848
        )
        assertEquals("Crittenden, Kentucky", result.placeLine())
        assertEquals(
            "city results default to the Open-Meteo source",
            PlaceSearchSource.OPEN_METEO,
            result.source
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
            ProviderEndpoints.OPEN_METEO_GEOCODING_BASE_URL.startsWith("https://geocoding-api.open-meteo.com/")
        )
        assertTrue(
            "the zip host must not be the NWS host",
            ProviderEndpoints.ZIPPOPOTAM_BASE_URL.startsWith("https://api.zippopotam.us/")
        )
        assertTrue(
            "the Nominatim host must not be the NWS host",
            ProviderEndpoints.NOMINATIM_BASE_URL.startsWith("https://nominatim.openstreetmap.org/")
        )
        assertEquals(
            "the Nominatim User-Agent is the app User-Agent with the repo URL",
            "AuraWeather/1.0 (https://github.com/DocBobert6/Aura-Weather)",
            ProviderEndpoints.NOMINATIM_USER_AGENT
        )
    }
}
