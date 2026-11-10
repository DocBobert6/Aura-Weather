package com.example.data.mapper

/**
 * The pure allergy index for Build D (audit item 16). No Android, no
 * Room — plain JUnit tests cover it (AllergyIndexTest).
 *
 * A SIMPLE index from the pollen counts (tree/grass/weed, each 0-5) and
 * the AQI (EPA 0-500) already fetched for this screen. Pollen counts
 * are currently always missing — there is no free US pollen source —
 * so in practice the index runs on the AQI alone; the pollen inputs
 * stay so the function (and its tests) still cover the combined case.
 * It is NOT a medical-grade allergy forecast and is never presented as
 * one — the screen labels it as a simple index from these numbers.
 *
 * Bands (honest, coarse):
 * - Pollen (max of the three): 0-1 Low, 2 Moderate, 3-4 High, 5 Very High.
 * - AQI: 0-50 Low, 51-100 Moderate, 101-150 High, 151+ Very High
 *   (the same EPA bands the AQI gauge uses; poor air aggravates
 *   allergy symptoms, so it can raise the index).
 * - The index is the WORST of the two. The driver names what set it:
 *   the pollen type with the highest count, or the AQI when air quality
 *   is the worse of the two (pollen wins a tie — it is the
 *   allergy-specific input).
 * - No pollen counts AND no AQI → null (no data, never an invented
 *   index). A missing pollen count is ignored, not treated as zero:
 *   only the counts actually present can raise the index.
 */
enum class AllergyLevel(val label: String) {
    LOW("LOW"),
    MODERATE("MODERATE"),
    HIGH("HIGH"),
    VERY_HIGH("VERY HIGH")
}

data class AllergyIndex(
    val level: AllergyLevel,
    /** What drives the level, e.g. "DRIVEN BY TREE POLLEN (4 OF 5)". */
    val driver: String
)

private fun pollenLevel(index: Int): AllergyLevel = when {
    index <= 1 -> AllergyLevel.LOW
    index == 2 -> AllergyLevel.MODERATE
    index <= 4 -> AllergyLevel.HIGH
    else -> AllergyLevel.VERY_HIGH
}

private fun aqiLevel(aqi: Int): AllergyLevel = when {
    aqi <= 50 -> AllergyLevel.LOW
    aqi <= 100 -> AllergyLevel.MODERATE
    aqi <= 150 -> AllergyLevel.HIGH
    else -> AllergyLevel.VERY_HIGH
}

fun allergyIndex(
    treeIndex: Int?,
    grassIndex: Int?,
    weedIndex: Int?,
    aqi: Int?
): AllergyIndex? {
    val pollen = listOfNotNull(
        treeIndex?.let { "TREE" to it },
        grassIndex?.let { "GRASS" to it },
        weedIndex?.let { "WEED" to it }
    )
    val maxPollen = pollen.maxByOrNull { it.second }
    val pollenLvl = maxPollen?.let { pollenLevel(it.second) }
    val aqiLvl = aqi?.let { aqiLevel(it) }
    if (pollenLvl == null && aqiLvl == null) return null

    val level = maxOf(pollenLvl ?: AllergyLevel.LOW, aqiLvl ?: AllergyLevel.LOW)
    val driver = when {
        level == AllergyLevel.LOW -> when {
            pollenLvl != null && aqiLvl != null -> "LOW POLLEN AND CLEAN AIR"
            pollenLvl != null -> "LOW POLLEN"
            else -> "CLEAN AIR"
        }
        pollenLvl != null && (aqiLvl == null || pollenLvl >= aqiLvl) ->
            "DRIVEN BY ${maxPollen!!.first} POLLEN (${maxPollen.second} OF 5)"
        else -> "DRIVEN BY AIR QUALITY (AQI $aqi)"
    }
    return AllergyIndex(level = level, driver = driver)
}
