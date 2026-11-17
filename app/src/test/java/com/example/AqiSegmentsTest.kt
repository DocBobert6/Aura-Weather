package com.example

import com.example.data.mapper.aqiSegments
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure tests for the era segmented AQI bar (Item 9, 0.3.3): the
 * segment-fill computation in AqiSegments. The six EPA bands are
 * Good 0-50, Moderate 51-100, USG 101-150, Unhealthy 151-200, Very
 * Unhealthy 201-300, Hazardous 301-500, over a 0..500 bar.
 */
class AqiSegmentsTest {

    private fun fills(aqi: Int?): List<Float> =
        aqiSegments(aqi).map { it.fillFraction }

    private fun assertFills(aqi: Int?, vararg expected: Float) {
        val actual = fills(aqi)
        assertEquals("segment count for AQI $aqi", expected.size, actual.size)
        expected.forEachIndexed { i, e ->
            assertEquals("segment $i fill for AQI $aqi", e, actual[i], 0.001f)
        }
    }

    @Test
    fun testAqi0_allEmpty() {
        assertFills(0, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqiNull_allEmpty() {
        assertFills(null, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi44_goodPartFilled() {
        // The live case from the audit: 44 / 50 = 0.88 of Good.
        assertFills(44, 0.88f, 0f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi50_goodFull() {
        assertFills(50, 1f, 0f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi51_moderateJustStarted() {
        assertFills(51, 1f, 0.02f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi100_moderateFull() {
        assertFills(100, 1f, 1f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi101_usgJustStarted() {
        assertFills(101, 1f, 1f, 0.02f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi150_usgFull() {
        assertFills(150, 1f, 1f, 1f, 0f, 0f, 0f)
    }

    @Test
    fun testAqi151_unhealthyJustStarted() {
        assertFills(151, 1f, 1f, 1f, 0.02f, 0f, 0f)
    }

    @Test
    fun testAqi200_unhealthyFull() {
        assertFills(200, 1f, 1f, 1f, 1f, 0f, 0f)
    }

    @Test
    fun testAqi201_veryUnhealthyJustStarted() {
        // The 201-300 band is 100 wide, so 201 fills 0.01 of it.
        assertFills(201, 1f, 1f, 1f, 1f, 0.01f, 0f)
    }

    @Test
    fun testAqi300_veryUnhealthyFull() {
        assertFills(300, 1f, 1f, 1f, 1f, 1f, 0f)
    }

    @Test
    fun testAqi301_hazardousJustStarted() {
        // The 301-500 band is 200 wide, so 301 fills 0.005 of it.
        assertFills(301, 1f, 1f, 1f, 1f, 1f, 0.005f)
    }

    @Test
    fun testAqi500_allFull() {
        assertFills(500, 1f, 1f, 1f, 1f, 1f, 1f)
    }

    @Test
    fun testAqiOutOfRange_clamped() {
        assertFills(-5, 0f, 0f, 0f, 0f, 0f, 0f)
        assertFills(600, 1f, 1f, 1f, 1f, 1f, 1f)
    }

    @Test
    fun testSegments_widthsMatchBreakpoints() {
        // 50/50/50/50/100/200 over a 500-wide bar.
        val widths = aqiSegments(0).map { it.widthFraction }
        val expected = listOf(0.1f, 0.1f, 0.1f, 0.1f, 0.2f, 0.4f)
        expected.forEachIndexed { i, e ->
            assertEquals("segment $i width", e, widths[i], 0.001f)
        }
        assertEquals("widths sum to the whole bar", 1.0f, widths.sum(), 0.001f)
    }

    @Test
    fun testSegments_labelsAndRepresentatives() {
        val segments = aqiSegments(0)
        assertEquals(
            listOf(
                "Good", "Moderate", "Unhealthy for Sensitive Groups",
                "Unhealthy", "Very Unhealthy", "Hazardous"
            ),
            segments.map { it.label }
        )
        assertEquals(listOf(25, 75, 125, 175, 250, 400), segments.map { it.representativeAqi })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), segments.map { it.categoryIndex })
    }
}
