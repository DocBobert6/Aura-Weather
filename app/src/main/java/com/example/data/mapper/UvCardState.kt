package com.example.data.mapper

/**
 * UV card selection (0.3.3 UI/font audit, item 6). Pure (no Compose).
 *
 * The card shows today's UV MAX — but only in daylight. At night it
 * must say NIGHT however high today's max was: the old call-site order
 * checked the max first, so the NIGHT branch was unreachable whenever
 * a max was present and 11:53 PM showed "3 MODERATE". Night therefore
 * wins here, before the max is even looked at. A null isDaytime (no
 * sun data) is unknown, not night, so a missing max in daylight stays
 * Unknown ("--") rather than inventing a night.
 *
 * The daylight category label ("MODERATE" etc.) still comes from
 * AuraColors.uvCategory at the call site — this function only decides
 * WHICH state the card is in, not how the category reads.
 */
sealed interface UvCardState {
    /** After dark: the card says NIGHT, however high today's max was. */
    data object Night : UvCardState

    /** Daylight with a known daily max: the card shows that max. */
    data class Daytime(val uvIndexMax: Double) : UvCardState

    /** Daylight with no max: unknown, rendered "--", not invented. */
    data object Unknown : UvCardState
}

fun uvCardState(uvIndexMax: Double?, isNight: Boolean): UvCardState = when {
    isNight -> UvCardState.Night
    uvIndexMax != null -> UvCardState.Daytime(uvIndexMax)
    else -> UvCardState.Unknown
}
