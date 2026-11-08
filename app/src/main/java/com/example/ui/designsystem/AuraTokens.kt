package com.example.ui.designsystem

import android.content.Context
import android.os.PowerManager
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherCondition

/**
 * Aura design tokens (Phase 2.2). One home for every colour, shape, glass
 * level, spring, and sky value the screens use — no screen pastes a literal
 * after this phase. Values come from the 2.1 audit's token table; the
 * per-screen colour maps there are the source of truth for what each token
 * replaces.
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
    val RadarLegend: List<Pair<String, Color>> = listOf(
        "Light" to Color(0xFF00E676),
        "Moderate" to Color(0xFFFFEA00),
        "Heavy" to Color(0xFFFF9100),
        "Severe" to Color(0xFFFF1744),
        "Hail" to Color(0xFFD500F9),
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

/** Glass levels for [GlassSurface]: content cards vs floating bars. */
enum class GlassLevel { Card, Bar }

object AuraGlass {
    /** Backdrop blur radius (applied by the glass implementation in 2.4). */
    fun blurRadius(level: GlassLevel): Dp =
        when (level) {
            GlassLevel.Card -> 16.dp
            GlassLevel.Bar -> 24.dp
        }

    /** How opaque the scheme-tinted surface is: bars read over bright sky. */
    fun scrimAlpha(level: GlassLevel): Float =
        when (level) {
            GlassLevel.Card -> 0.72f
            // 0.88: scrolling text must not read through the floating bars.
            GlassLevel.Bar -> 0.88f
        }

    /** Hairline border alpha (white, slightly brighter on the top edge). */
    const val borderAlpha: Float = 0.12f

    /** Scheme-tinted surface for a level. */
    fun tint(surface: Color, level: GlassLevel): Color =
        surface.copy(alpha = scrimAlpha(level))
}

/**
 * Motion tokens: the spring set every press / selection / bar / sheet
 * animation references, in a standard set and a bouncier expressive set.
 * Duration/easing curves are kept only for page enter/exit transitions.
 * Springs collapse to instant changes when reduced motion is on.
 *
 * Note: material3 1.4.0's MotionScheme / MaterialExpressiveTheme are
 * `internal` (verified in the 1.4.0 sources), so there is no public
 * theme-level expressive entry point to call. Expressive motion is
 * delivered here, as our own spring sets applied at the animated call
 * sites in 2.3+, selected with [AuraMotion.springs].
 */
data class AuraSprings(
    /** Press squash on pills and buttons. */
    val press: SpringSpec<Float>,
    /** Selection morph on nav / tab / palette pills. */
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

    /** Bouncier set for the expressive palette mode. */
    val expressiveSprings: AuraSprings = AuraSprings(
        press = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        selection = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        bar = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        sheet = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    /** The spring set for the palette mode in effect. */
    fun springs(expressive: Boolean): AuraSprings =
        if (expressive) expressiveSprings else standardSprings

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
    fun <T> spec(expressive: Boolean, pick: AuraSprings.() -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (rememberReducedMotion()) tween(durationMillis = 0) else springs(expressive).pick()
}

/**
 * True while the device is in battery-saver mode (2.4a guardrail: the sky
 * keeps only drift + glow, and the backdrop blur is off).
 */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    return remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isPowerSaveMode
    }
}

/**
 * Sky colour inputs (Phase 2.2 token; the mesh sky in 2.4 consumes them).
 * The same condition key as [WeatherPalettes.getSchemeForCondition], so sky
 * and chrome can never disagree. Values are the gradients WeatherSky already
 * shipped, moved here as the single source.
 */
object AuraSky {
    fun gradientColors(condition: WeatherCondition, isDaytime: Boolean): List<Color> =
        when {
            condition == WeatherCondition.Thunderstorm -> listOf(
                Color(0xFF0F0B1E),
                Color(0xFF1B1438),
                Color(0xFF2E1A47)
            )
            condition == WeatherCondition.Rain ||
                condition == WeatherCondition.HeavyRain ||
                condition == WeatherCondition.Drizzle -> listOf(
                Color(0xFF121B24),
                Color(0xFF1E2D3D),
                Color(0xFF2C3E50)
            )
            condition == WeatherCondition.Snow ||
                condition == WeatherCondition.Sleet ||
                condition == WeatherCondition.FreezingRain -> listOf(
                Color(0xFF162534),
                Color(0xFF233950),
                Color(0xFF355270)
            )
            isDaytime && (condition == WeatherCondition.ClearDay ||
                condition == WeatherCondition.PartlyCloudyDay) -> listOf(
                Color(0xFF1565C0),
                Color(0xFF1E88E5),
                Color(0xFF42A5F5)
            )
            isDaytime -> listOf( // Cloudy / foggy day
                Color(0xFF263238),
                Color(0xFF37474F),
                Color(0xFF546E7A)
            )
            else -> listOf( // Night
                Color(0xFF070B19),
                Color(0xFF0D172E),
                Color(0xFF132247)
            )
        }

    fun celestialColor(isDaytime: Boolean): Color =
        if (isDaytime) Color(0xFFFFD54F) else Color(0xFFE0E0E0)

    /**
     * Text colour for a heading that sits directly on the sky (not on a
     * card). The sky's brightness follows the condition, not the theme, so
     * onSurface can be invisible on it — dark text on a dark slate sky.
     * Picked from the sky's own average luminance: light text on a dark
     * sky, dark text on a light one.
     */
    fun headerTextColor(gradientColors: List<Color>): Color {
        if (gradientColors.isEmpty()) return Color.White
        // The headers sit at the top of the sky, so the top stop is what
        // they read against — not the average (the bottom stops are
        // lighter and would vote dark text onto a dark top). Light text
        // wins below ~0.25 (the contrast crossover); dark text above it.
        val top = gradientColors.first()
        val luminance = 0.2126f * top.red + 0.7152f * top.green + 0.0722f * top.blue
        return if (luminance < 0.25f) Color(0xFFF8FAFC) else Color(0xFF1A1C1E)
    }
}

/**
 * Header colour for headings that sit directly on the sky. Provided by
 * [WeatherNavDisplay] from the sky's own luminance; headings inside cards
 * keep using onSurface.
 */
val LocalSkyHeaderColor = androidx.compose.runtime.staticCompositionLocalOf { Color(0xFFF8FAFC) }
