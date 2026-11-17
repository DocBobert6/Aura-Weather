package com.example.data.mapper

/**
 * Era segmented AQI bar (Item 9, 0.3.3): the pure half of the Air
 * Quality screen's segmented bar. The six EPA categories and their
 * breakpoints are the standard scale (Good 0-50, Moderate 51-100,
 * Unhealthy for Sensitive Groups 101-150, Unhealthy 151-200, Very
 * Unhealthy 201-300, Hazardous 301-500); the bar spans 0..500, so
 * each segment's width is its span over 500. [aqiSegments] returns
 * one [AqiSegment] per category with the fraction of that segment
 * the value fills (0 = empty, 1 = full); Compose only draws what
 * this returns. No Compose/Android imports — pure Kotlin.
 */

/** One segment of the AQI bar. */
data class AqiSegment(
    /** 0 = Good, 1 = Moderate, 2 = USG, 3 = Unhealthy, 4 = Very Unhealthy, 5 = Hazardous. */
    val categoryIndex: Int,
    /** The EPA category word, e.g. "Good". */
    val label: String,
    /** A representative AQI inside the band, for the band colour. */
    val representativeAqi: Int,
    /** Fraction of this segment the value fills, 0..1. */
    val fillFraction: Float,
    /** Fraction of the whole 0..500 bar this segment spans. */
    val widthFraction: Float
)

/** One EPA band: [start] is inclusive, [end] is the breakpoint. */
private data class AqiBand(
    val start: Int,
    val end: Int,
    val label: String,
    val representativeAqi: Int
)

/** The six EPA bands. The representatives are the same values the old
 * semicircular gauge used for its band colours (25/75/125/175/250/400). */
private val AQI_BANDS = listOf(
    AqiBand(0, 50, "Good", 25),
    AqiBand(50, 100, "Moderate", 75),
    AqiBand(100, 150, "Unhealthy for Sensitive Groups", 125),
    AqiBand(150, 200, "Unhealthy", 175),
    AqiBand(200, 300, "Very Unhealthy", 250),
    AqiBand(300, 500, "Hazardous", 400)
)

/**
 * The six bar segments for [aqi]. Null means unavailable — every
 * segment is empty, never a made-up fill. Values outside 0..500 are
 * clamped to the bar, so 0 leaves every segment empty and 500 fills
 * them all.
 */
fun aqiSegments(aqi: Int?): List<AqiSegment> {
    val value = aqi?.coerceIn(0, 500) ?: 0
    return AQI_BANDS.mapIndexed { index, band ->
        val span = (band.end - band.start).toFloat()
        val fill = ((value - band.start) / span).coerceIn(0f, 1f)
        AqiSegment(
            categoryIndex = index,
            label = band.label,
            representativeAqi = band.representativeAqi,
            fillFraction = fill,
            widthFraction = span / 500f
        )
    }
}
