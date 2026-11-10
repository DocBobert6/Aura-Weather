package com.example

import com.example.data.mapper.AllergyLevel
import com.example.data.mapper.allergyIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure tests for the Build D allergy index. */
class AllergyIndexTest {

    @Test
    fun noData_returnsNull() {
        assertNull(allergyIndex(null, null, null, null))
    }

    @Test
    fun lowPollenAndCleanAir_isLow() {
        val index = allergyIndex(treeIndex = 1, grassIndex = 0, weedIndex = 1, aqi = 30)
        assertEquals(AllergyLevel.LOW, index?.level)
        assertEquals("LOW POLLEN AND CLEAN AIR", index?.driver)
    }

    @Test
    fun pollen2_isModerate_drivenByPollen() {
        val index = allergyIndex(treeIndex = 2, grassIndex = 0, weedIndex = 0, aqi = 30)
        assertEquals(AllergyLevel.MODERATE, index?.level)
        assertEquals("DRIVEN BY TREE POLLEN (2 OF 5)", index?.driver)
    }

    @Test
    fun maxPollenWins_andNamesTheHighestType() {
        // Grass is the highest count, so it drives the index.
        val index = allergyIndex(treeIndex = 1, grassIndex = 4, weedIndex = 2, aqi = 30)
        assertEquals(AllergyLevel.HIGH, index?.level)
        assertEquals("DRIVEN BY GRASS POLLEN (4 OF 5)", index?.driver)
    }

    @Test
    fun pollen5_isVeryHigh() {
        val index = allergyIndex(treeIndex = 0, grassIndex = 0, weedIndex = 5, aqi = 30)
        assertEquals(AllergyLevel.VERY_HIGH, index?.level)
        assertEquals("DRIVEN BY WEED POLLEN (5 OF 5)", index?.driver)
    }

    @Test
    fun badAqiAlone_raisesIndex_drivenByAqi() {
        // No pollen data at all: the AQI alone sets the index.
        val index = allergyIndex(null, null, null, 120)
        assertEquals(AllergyLevel.HIGH, index?.level)
        assertEquals("DRIVEN BY AIR QUALITY (AQI 120)", index?.driver)
    }

    @Test
    fun aqiWorseThanPollen_drivenByAqi() {
        val index = allergyIndex(treeIndex = 2, grassIndex = 1, weedIndex = 0, aqi = 180)
        assertEquals(AllergyLevel.VERY_HIGH, index?.level)
        assertEquals("DRIVEN BY AIR QUALITY (AQI 180)", index?.driver)
    }

    @Test
    fun tie_prefersPollen() {
        // Pollen 3 (HIGH) and AQI 120 (HIGH): pollen wins the tie.
        val index = allergyIndex(treeIndex = 3, grassIndex = 0, weedIndex = 0, aqi = 120)
        assertEquals(AllergyLevel.HIGH, index?.level)
        assertEquals("DRIVEN BY TREE POLLEN (3 OF 5)", index?.driver)
    }

    @Test
    fun missingPollenCount_isIgnored_notZero() {
        // Only grass reported (2 = Moderate); the missing tree/weed
        // counts must not drag the index down or invent a value.
        val index = allergyIndex(null, 2, null, 40)
        assertEquals(AllergyLevel.MODERATE, index?.level)
        assertEquals("DRIVEN BY GRASS POLLEN (2 OF 5)", index?.driver)
    }

    @Test
    fun aqiBands_matchEpaBands() {
        assertEquals(AllergyLevel.LOW, allergyIndex(null, null, null, 50)?.level)
        assertEquals(AllergyLevel.MODERATE, allergyIndex(null, null, null, 51)?.level)
        assertEquals(AllergyLevel.MODERATE, allergyIndex(null, null, null, 100)?.level)
        assertEquals(AllergyLevel.HIGH, allergyIndex(null, null, null, 101)?.level)
        assertEquals(AllergyLevel.HIGH, allergyIndex(null, null, null, 150)?.level)
        assertEquals(AllergyLevel.VERY_HIGH, allergyIndex(null, null, null, 151)?.level)
    }

    @Test
    fun cleanAirAlone_isLow() {
        val index = allergyIndex(null, null, null, 25)
        assertEquals(AllergyLevel.LOW, index?.level)
        assertEquals("CLEAN AIR", index?.driver)
    }

    @Test
    fun lowPollenAlone_isLow() {
        val index = allergyIndex(1, null, null, null)
        assertEquals(AllergyLevel.LOW, index?.level)
        assertEquals("LOW POLLEN", index?.driver)
    }
}
