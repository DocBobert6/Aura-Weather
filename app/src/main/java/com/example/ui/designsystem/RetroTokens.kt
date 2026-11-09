package com.example.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Retro tokens (R1 of the late-90s / early-2000s Weather Channel reskin).
 * The ONLY colour source for the retro UI: every retro colour lives here,
 * and the §2.8 grep gate (no `Color(0xFF` outside the token files) applies.
 * The app is always this navy look — there is no theme name, no palette,
 * and no dark/light branch.
 *
 * Data helpers that are colour scales in their own right (severity, AQI,
 * temperature, precipitation, UV, cloud cover) keep living in [AuraColors];
 * the values there are the classic scales and are unchanged by the reskin.
 */
object RetroTokens {
    /** App background navy. */
    val Background: Color = Color(0xFF0A1E5C)

    /** Panel navy — slightly lighter than the background. */
    val Panel: Color = Color(0xFF14307E)

    /** A step lighter again, for pressed/selected fills and containers. */
    val PanelLight: Color = Color(0xFF1E3F9E)

    /** Lighter-blue hairline / border. */
    val Border: Color = Color(0xFF4A6AC9)

    /** Classic header red (R3 header bar). */
    val HeaderRed: Color = Color(0xFFD93A2B)

    /** Classic header orange (severity-adjacent accents, R3/R4). */
    val HeaderOrange: Color = Color(0xFFF26522)

    /** Yellow — temperatures, headings, the active nav indicator. */
    val Yellow: Color = Color(0xFFFFD600)

    /** Body text. */
    val TextPrimary: Color = Color(0xFFFFFFFF)

    /** Secondary text. */
    val TextSecondary: Color = Color.White.copy(alpha = 0.7f)

    /** Bottom ticker blue (R3). */
    val TickerBlue: Color = Color(0xFF0D2B7A)
}

/** Classic radius set: square-ish corners (4–8dp), not the 20–28dp modern radii. */
object RetroShapes {
    val small: RoundedCornerShape = RoundedCornerShape(4.dp)
    val medium: RoundedCornerShape = RoundedCornerShape(6.dp)
    val large: RoundedCornerShape = RoundedCornerShape(8.dp)

    /** The [Shapes] handed to MaterialTheme. */
    fun materialShapes(): Shapes = Shapes(
        extraSmall = small,
        small = medium,
        medium = large,
        large = large,
        extraLarge = medium
    )
}

/**
 * The solid navy panel that replaces the old glass surface everywhere.
 * Solid navy background (or the optional [color] override), a 1dp
 * lighter-blue border, a classic radius — NO blur, NO scrim, NO lit edge,
 * NO glass shadow.
 */
@Composable
fun RetroPanel(
    shape: Shape = RetroShapes.medium,
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color ?: RetroTokens.Panel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, RetroTokens.Border)
    ) {
        // Centred like the old glass surface was, so short content (a freshness
        // pill's "Updated just now") does not pin to the top of its panel.
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
