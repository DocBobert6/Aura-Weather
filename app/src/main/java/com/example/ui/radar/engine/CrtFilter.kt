package com.example.ui.radar.engine

import kotlin.math.sqrt

/**
 * 90s CRT filter parameters — the mask part of a period CRT, no glow,
 * no curvature.
 *
 * The filter is a static overlay drawn between the MapView and the
 * chrome in [RadarEngineScreen]: horizontal scanlines, a vertical
 * aperture-grille mask, a warm phosphor tint and a vignette. It never
 * reads the map's pixels (there is no bloom / halation / glow pass —
 * that is a separate future run), so it cannot blur or soften the
 * radar itself; the "tonal treatment" is in the mask alone — the
 * scanlines are faint enough to read as phosphor gaps rather than a
 * digital overlay, and the warm tint + vignette sell the glass.
 *
 * Pure Kotlin, no Compose / Android imports, so every number here is
 * unit-testable ([CrtFilterTest]) and adjustable from one place. All
 * spacings are in dp and converted with the display density at draw
 * time; all alphas are 0..1 fractions.
 *
 * Numbers (chosen for a ~400 dpi phone screen — at density 2.625 a
 * 3dp pitch is ~8px, a 1dp line ~2.6px):
 * - Scanlines: 3dp pitch, 1dp line, 0.10 alpha — visible on the dark
 *   radar basemap without banding the dBZ colours.
 * - Aperture grille: 3dp pitch, 1dp line, 0.03 alpha — about a
 *   third of the scanline strength, so the vertical stripes read as
 *   phosphor mask, not a grid. (A Trinitron-style grille is vertical
 *   stripes only; a shadow-mask dot grid would need both axes at full
 *   strength and would moire at this pitch.)
 * - Phosphor tint: warm white 0xFFFFF3D6 at 0.03 alpha — a whisper of
 *   warm glass over the whole map.
 * - Vignette: transparent until 55% of the way from centre to corner,
 *   ramping to 0.28 black at the corner; gradient radius 0.75x the
 *   centre-to-corner distance, so the corners sit at full edge alpha.
 */
object CrtFilter {

    /** Default: on — Robert asked for the filter; the Settings toggle turns it off. */
    const val DEFAULT_ENABLED = true

    const val SCANLINE_SPACING_DP = 3f
    const val SCANLINE_THICKNESS_DP = 1f
    const val SCANLINE_ALPHA = 0.10f

    const val GRILLE_SPACING_DP = 3f
    const val GRILLE_THICKNESS_DP = 1f
    const val GRILLE_ALPHA = 0.03f

    /** Warm phosphor glass, ARGB. */
    const val PHOSPHOR_TINT_ARGB = 0xFFFFF3D6L
    const val PHOSPHOR_TINT_ALPHA = 0.03f

    const val VIGNETTE_EDGE_ALPHA = 0.28f

    /** Vignette stays transparent inside this fraction of the centre-to-corner distance. */
    const val VIGNETTE_INNER_FRACTION = 0.55f

    /** Radial-gradient radius, as a fraction of the centre-to-corner distance. */
    const val VIGNETTE_RADIUS_FRACTION = 0.75f

    /** Scanline pitch in px for a display density. */
    fun scanlineSpacingPx(density: Float): Float = SCANLINE_SPACING_DP * density

    /** Scanline thickness in px for a display density. */
    fun scanlineThicknessPx(density: Float): Float = SCANLINE_THICKNESS_DP * density

    /** Aperture-grille pitch in px for a display density. */
    fun grilleSpacingPx(density: Float): Float = GRILLE_SPACING_DP * density

    /** Aperture-grille thickness in px for a display density. */
    fun grilleThicknessPx(density: Float): Float = GRILLE_THICKNESS_DP * density

    /** How many horizontal scanlines cover a layer [heightPx] tall. */
    fun scanlineCount(heightPx: Float, density: Float): Int =
        (heightPx / scanlineSpacingPx(density)).toInt().coerceAtLeast(0)

    /** How many vertical grille stripes cover a layer [widthPx] wide. */
    fun grilleLineCount(widthPx: Float, density: Float): Int =
        (widthPx / grilleSpacingPx(density)).toInt().coerceAtLeast(0)

    /** Centre-to-corner distance of a layer, in px. */
    fun cornerDistancePx(widthPx: Float, heightPx: Float): Float =
        sqrt((widthPx / 2f) * (widthPx / 2f) + (heightPx / 2f) * (heightPx / 2f))

    /** Radial-gradient radius for the vignette, in px. */
    fun vignetteRadiusPx(widthPx: Float, heightPx: Float): Float =
        VIGNETTE_RADIUS_FRACTION * cornerDistancePx(widthPx, heightPx)

    /**
     * Vignette black alpha at a point [distanceFraction] of the way
     * from the centre (0) to the corner (1): transparent inside
     * [VIGNETTE_INNER_FRACTION], then a straight ramp to
     * [VIGNETTE_EDGE_ALPHA] at the corner.
     */
    fun vignetteAlphaAt(distanceFraction: Float): Float {
        if (distanceFraction <= VIGNETTE_INNER_FRACTION) return 0f
        val t = (distanceFraction - VIGNETTE_INNER_FRACTION) / (1f - VIGNETTE_INNER_FRACTION)
        return (VIGNETTE_EDGE_ALPHA * t.coerceIn(0f, 1f))
    }

    /** Sanity: every spacing exceeds its line thickness, every alpha is 0..1. */
    fun isValid(): Boolean =
        SCANLINE_SPACING_DP > SCANLINE_THICKNESS_DP &&
            SCANLINE_THICKNESS_DP > 0f &&
            GRILLE_SPACING_DP > GRILLE_THICKNESS_DP &&
            GRILLE_THICKNESS_DP > 0f &&
            SCANLINE_ALPHA in 0f..1f &&
            GRILLE_ALPHA in 0f..1f &&
            PHOSPHOR_TINT_ALPHA in 0f..1f &&
            VIGNETTE_EDGE_ALPHA in 0f..1f &&
            VIGNETTE_INNER_FRACTION in 0f..1f &&
            VIGNETTE_RADIUS_FRACTION > 0f
}
