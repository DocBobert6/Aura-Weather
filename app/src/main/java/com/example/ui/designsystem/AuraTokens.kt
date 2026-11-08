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
            AlertSeverity.Minor -> Color(0xFF388E3C) to "ADVISORY"
            AlertSeverity.Unknown -> Color(0xFF5C6BC0) to "STATEMENT"
        }

    fun severityColor(severity: AlertSeverity): Color = severityStyle(severity).first
    fun severityLabel(severity: AlertSeverity): String = severityStyle(severity).second

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
            GlassLevel.Bar -> 0.80f
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
}
