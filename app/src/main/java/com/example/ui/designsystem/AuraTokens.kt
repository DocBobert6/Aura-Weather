package com.example.ui.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity

/**
 * Aura design tokens (Phase 2.2, reskinned R1). One home for the data
 * colour scales (severity, AQI, temperature, precipitation), the shapes,
 * and the motion springs the screens use — no screen pastes a literal
 * after this phase. The retro look's own colours live in [RetroTokens];
 * the glass levels, the sky gradients, and the condition palettes were
 * removed with the glass/sky/palette system in R1.
 */
object AuraColors {

    /** The de-facto brand accent, pasted 13 times before tokenisation. */
    val Accent: Color = Color(0xFF00E676)
    val OnAccent: Color = Color(0xFF003314)
    val AccentContainer: Color = Color(0xFF004D26)

    /** Warning amber — stale-data text and error icons. */
    val Warning: Color = Color(0xFFFFB74D)

    /** Water blue — precipitation icons and rain-chance text. */
    val Water: Color = Color(0xFF64B5F6)
    /** Rain % text: darker than Water so it reads on a light card. */
    val PopBlue: Color = Color(0xFF1565C0)

    /** Sunset coral — the sunset icon. */
    val Sunset: Color = Color(0xFFFF8A65)

    /** Rain — the animated rain streaks in the sky. */
    val Rain: Color = Color(0xFFA5D6A7)

    /**
     * Moon shadow — fixed dark in both modes. A phase shadow must read as
     * dark; the scheme surface would go light in light mode and break the
     * phase rendering.
     */
    val MoonShadow: Color = Color(0xFF0F172A)

    /** Moon blue — the "Next Full Moon" text. */
    val MoonBlue: Color = Color(0xFF81D4FA)

    /** Pollen gauge "has data" green — the active gauge icon tint. */
    val PollenActive: Color = Color(0xFF81C784)

    /**
     * Minute-cast bar colour by precipitation rate (mm/h): light rain is
     * blue, moderate amber, heavy red; the baseline is a faint neutral.
     */
    fun precipColor(mmPerHour: Double): Color =
        when {
            mmPerHour > 5.0 -> Color(0xFFE53935) // Heavy
            mmPerHour > 2.0 -> Color(0xFFFFB300) // Moderate
            mmPerHour > 0.1 -> Color(0xFF42A5F5) // Light
            else -> Color.White.copy(alpha = 0.15f) // Baseline
        }

    /**
     * Radar echo-intensity legend (dBZ bands), used by the Radar screen's
     * legend bar. These are the NWS reflectivity colours, not app accents.
     */
    // dBZ bands with their ranges. 'Hail' is gone: it is a dual-
    // polarisation product, not a dBZ threshold, so it does not belong on
    // a dBZ legend.
    val RadarLegend: List<Pair<String, Color>> = listOf(
        "Light 5–20" to Color(0xFF00E676),
        "Moderate 20–35" to Color(0xFFFFEA00),
        "Heavy 35–50" to Color(0xFFFF9100),
        "Severe 50–65" to Color(0xFFFF1744),
    )

    /**
     * The one severity scale, shared by Home and Alerts (the audit found two
     * disagreeing scales). Colours are the Alerts screen's complete scale;
     * labels are its words — Minor is "ADVISORY", Unknown is "STATEMENT".
     */
    fun severityStyle(severity: AlertSeverity): Pair<Color, String> =
        when (severity) {
            AlertSeverity.Extreme -> Color(0xFFD32F2F) to "EXTREME"
            AlertSeverity.Severe -> Color(0xFFF57C00) to "SEVERE"
            AlertSeverity.Moderate -> Color(0xFFFFA000) to "MODERATE"
            AlertSeverity.Minor -> Color(0xFF1E88E5) to "ADVISORY"
            AlertSeverity.Unknown -> Color(0xFF5C6BC0) to "STATEMENT"
        }

    fun severityColor(severity: AlertSeverity): Color = severityStyle(severity).first
    fun severityLabel(severity: AlertSeverity): String = severityStyle(severity).second

    /** Rank for picking the most severe alert (the banner shows that one). */
    fun severityRank(severity: AlertSeverity): Int = when (severity) {
        AlertSeverity.Extreme -> 4
        AlertSeverity.Severe -> 3
        AlertSeverity.Moderate -> 2
        AlertSeverity.Minor -> 1
        AlertSeverity.Unknown -> 0
    }

    /** Temperature → colour for the 7-day range bars (cold blue → warm orange/red). */
    fun tempColor(tempC: Double): Color = when {
        tempC < 0.0 -> Color(0xFF1565C0)
        tempC < 10.0 -> Color(0xFF42A5F5)
        tempC < 20.0 -> Color(0xFF26A69A)
        tempC < 27.0 -> Color(0xFFFFB300)
        tempC < 32.0 -> Color(0xFFFF8A65)
        else -> Color(0xFFE53935)
    }

    /** UV index → the EPA category word. */
    fun uvCategory(uv: Double): String = when {
        uv < 3.0 -> "Low"
        uv < 6.0 -> "Moderate"
        uv < 8.0 -> "High"
        uv < 11.0 -> "Very High"
        else -> "Extreme"
    }

    /** Cloud cover % → a plain-language description. */
    fun cloudCoverDesc(pct: Double): String = when {
        pct < 10.0 -> "Clear sky"
        pct < 30.0 -> "Mostly clear"
        pct < 60.0 -> "Partly cloudy"
        pct < 90.0 -> "Mostly cloudy"
        else -> "Overcast"
    }

    /**
     * The one AQI band scale (the Air screen's complete 6-band version; Home
     * pasted a truncated copy of the same scale). Null means unavailable —
     * never a made-up value — and renders as dimmed white.
     */
    fun aqiColor(aqi: Int?): Color =
        when {
            aqi == null -> Color.White.copy(alpha = 0.5f)
            aqi <= 50 -> Color(0xFF00E676)
            aqi <= 100 -> Color(0xFFFFEB3B)
            aqi <= 150 -> Color(0xFFFF9800)
            aqi <= 200 -> Color(0xFFF44336)
            aqi <= 300 -> Color(0xFF9C27B0)
            else -> Color(0xFF795548)
        }
}

/** Shape tokens: the 10 radii in use collapse to these four. */
object AuraShapes {
    /** Stadium shape for bars, chips, and controls. */
    val pill: RoundedCornerShape = RoundedCornerShape(50)

    /** Hero cards. */
    val large: RoundedCornerShape = RoundedCornerShape(28.dp)

    /** Content cards. */
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp)

    /** Inner elements. */
    val small: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** The [Shapes] handed to MaterialTheme. */
    fun materialShapes(): Shapes = Shapes(
        extraSmall = small,
        small = medium,
        medium = large,
        large = large,
        extraLarge = pill
    )
}


/**
 * Motion tokens: the one spring set every press / selection / bar / sheet
 * animation references. Duration/easing curves are kept only for page
 * enter/exit transitions. Springs collapse to instant changes when reduced
 * motion is on. (The bouncier expressive set was removed with the palette
 * system in R1; there is a single motion treatment now.)
 */
data class AuraSprings(
    /** Press squash on pills and buttons. */
    val press: SpringSpec<Float>,
    /** Selection morph on nav / tab pills. */
    val selection: SpringSpec<Float>,
    /** Floating bar hide/show. */
    val bar: SpringSpec<Float>,
    /** Sheets and expanding cards. */
    val sheet: SpringSpec<Float>
)

object AuraMotion {
    val standardSprings: AuraSprings = AuraSprings(
        press = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        selection = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        bar = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        sheet = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )



    /**
     * True when the user turned animations off system-wide
     * (animator duration scale = 0). Callers switch to instant state changes.
     */
    fun reducedMotion(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f

    /** Composable wrapper for [reducedMotion]: the animator scale is read once. */
    @Composable
    fun rememberReducedMotion(): Boolean {
        val context = LocalContext.current
        return remember { reducedMotion(context) }
    }

    /**
     * The animation spec for one interactive spring (2.4d): the chosen
     * spring set, or an instant (0ms) tween when reduced motion is on.
     * Returns a [FiniteAnimationSpec] so it also fits `scaleIn` /
     * `scaleOut`, which do not take a general animation spec. The caller
     * picks which spring of the set with [pick] — `{ press }`, `{ bar }` …
     */
    @Composable
    fun <T> spec(pick: AuraSprings.() -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (rememberReducedMotion()) tween(durationMillis = 0) else standardSprings.pick()
}
