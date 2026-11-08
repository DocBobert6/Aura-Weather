package com.example.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.domain.model.WeatherCondition

/**
 * Condition-driven colour tokens (Phase 2.2).
 *
 * One condition key drives both the chrome ([getSchemeForCondition]) and the
 * sky ([AuraSky.gradientColors]), so sky and chrome can never disagree.
 * Every scheme is a full [ColorScheme] per condition × day/night, in a dark
 * and a light variant (decision D2: Auto / Dark / Light — the light variants
 * are defined here; the Settings choice that selects them lands in 2.5).
 *
 * Dark schemes are hand-picked per condition. Light schemes are derived
 * from the same keys by a documented transform (background/surface washed
 * toward white, hues deepened slightly) so the two modes can never drift
 * apart. Slot values the keys don't cover are derived from the keys with
 * [lerp] — no second set of hand-picked values to keep in sync.
 */
object WeatherPalettes {

    /** The six hues a scheme is built from. */
    private data class SchemeKeys(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val background: Color,
        val surface: Color,
        val onSurface: Color
    )

    // Dark key sets, per condition × day/night. Values follow the palettes
    // the app already shipped (Clear gold, Storm violet, Snow frost) and the
    // sky gradients in WeatherSky, so the redesign keeps the app's identity.
    private val clearDayKeys = SchemeKeys(
        primary = Color(0xFFFFCA28),
        secondary = Color(0xFF81D4FA),
        tertiary = Color(0xFFFF8A65),
        background = Color(0xFF0E1E33),
        surface = Color(0xFF1B2F4A),
        onSurface = Color(0xFFF8FAFC)
    )
    private val clearNightKeys = SchemeKeys(
        primary = Color(0xFFFFD54F),
        secondary = Color(0xFF90CAF9),
        tertiary = Color(0xFFFFAB91),
        background = Color(0xFF070B19),
        surface = Color(0xFF0D172E),
        onSurface = Color(0xFFE2E8F0)
    )
    private val partlyDayKeys = SchemeKeys(
        primary = Color(0xFFFFD54F),
        secondary = Color(0xFF90CAF9),
        tertiary = Color(0xFFFFB74D),
        background = Color(0xFF101F2E),
        surface = Color(0xFF1A2E42),
        onSurface = Color(0xFFF1F5F9)
    )
    private val partlyNightKeys = SchemeKeys(
        primary = Color(0xFFFFE082),
        secondary = Color(0xFFA5D6FA),
        tertiary = Color(0xFFFFCC80),
        background = Color(0xFF0A1220),
        surface = Color(0xFF101C2B),
        onSurface = Color(0xFFE2E8F0)
    )
    private val cloudyDayKeys = SchemeKeys(
        primary = Color(0xFF90A4AE),
        secondary = Color(0xFFB0BEC5),
        tertiary = Color(0xFFFFB74D),
        background = Color(0xFF141C22),
        surface = Color(0xFF1E293B),
        onSurface = Color(0xFFECEFF1)
    )
    private val cloudyNightKeys = SchemeKeys(
        primary = Color(0xFF78909C),
        secondary = Color(0xFF90A4AE),
        tertiary = Color(0xFFFFD54F),
        background = Color(0xFF0D1216),
        surface = Color(0xFF141D24),
        onSurface = Color(0xFFE2E8F0)
    )
    private val rainDayKeys = SchemeKeys(
        primary = Color(0xFF64B5F6),
        secondary = Color(0xFF81D4FA),
        tertiary = Color(0xFF4DB6AC),
        background = Color(0xFF121B24),
        surface = Color(0xFF1E2D3D),
        onSurface = Color(0xFFE3F2FD)
    )
    private val rainNightKeys = SchemeKeys(
        primary = Color(0xFF42A5F5),
        secondary = Color(0xFF64B5F6),
        tertiary = Color(0xFF26A69A),
        background = Color(0xFF0C141C),
        surface = Color(0xFF16222E),
        onSurface = Color(0xFFE2E8F0)
    )
    private val stormDayKeys = SchemeKeys(
        primary = Color(0xFFB388FF),
        secondary = Color(0xFF80D8FF),
        tertiary = Color(0xFFFF5252),
        background = Color(0xFF0F0B1E),
        surface = Color(0xFF1B1438),
        onSurface = Color(0xFFEDE7F6)
    )
    private val stormNightKeys = SchemeKeys(
        primary = Color(0xFFB388FF),
        secondary = Color(0xFF80D8FF),
        tertiary = Color(0xFFFF5252),
        background = Color(0xFF0A071A),
        surface = Color(0xFF13102B),
        onSurface = Color(0xFFEDE7F6)
    )
    private val snowDayKeys = SchemeKeys(
        primary = Color(0xFF80DEEA),
        secondary = Color(0xFFCFD8DC),
        tertiary = Color(0xFF90CAF9),
        background = Color(0xFF162534),
        surface = Color(0xFF233950),
        onSurface = Color(0xFFF0F4F8)
    )
    private val snowNightKeys = SchemeKeys(
        primary = Color(0xFF80DEEA),
        secondary = Color(0xFFCFD8DC),
        tertiary = Color(0xFF90CAF9),
        background = Color(0xFF0D1B2A),
        surface = Color(0xFF15263D),
        onSurface = Color(0xFFF0F4F8)
    )
    private val fogDayKeys = SchemeKeys(
        primary = Color(0xFFB0BEC5),
        secondary = Color(0xFF90A4AE),
        tertiary = Color(0xFFFFD54F),
        background = Color(0xFF1A2027),
        surface = Color(0xFF263238),
        onSurface = Color(0xFFECEFF1)
    )
    private val fogNightKeys = SchemeKeys(
        primary = Color(0xFF90A4AE),
        secondary = Color(0xFF78909C),
        tertiary = Color(0xFFFFE082),
        background = Color(0xFF10161B),
        surface = Color(0xFF1B262C),
        onSurface = Color(0xFFE2E8F0)
    )

    /** Light variant of a dark key set: wash toward white, deepen the hues. */
    private fun SchemeKeys.toLight(): SchemeKeys = SchemeKeys(
        primary = lerp(primary, Color.Black, 0.12f),
        secondary = lerp(secondary, Color.Black, 0.12f),
        tertiary = lerp(tertiary, Color.Black, 0.12f),
        background = lerp(background, Color.White, 0.93f),
        surface = lerp(surface, Color.White, 0.90f),
        onSurface = Color(0xFF1B2430)
    )

    private fun keysFor(condition: WeatherCondition, isDaytime: Boolean): SchemeKeys =
        when (condition) {
            WeatherCondition.ClearDay -> if (isDaytime) clearDayKeys else clearNightKeys
            WeatherCondition.ClearNight -> clearNightKeys
            WeatherCondition.PartlyCloudyDay -> if (isDaytime) partlyDayKeys else partlyNightKeys
            WeatherCondition.PartlyCloudyNight -> partlyNightKeys
            WeatherCondition.Cloudy, WeatherCondition.Haze, WeatherCondition.Windy ->
                if (isDaytime) cloudyDayKeys else cloudyNightKeys
            WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.HeavyRain ->
                if (isDaytime) rainDayKeys else rainNightKeys
            WeatherCondition.Thunderstorm ->
                if (isDaytime) stormDayKeys else stormNightKeys
            WeatherCondition.Snow, WeatherCondition.Sleet, WeatherCondition.FreezingRain ->
                if (isDaytime) snowDayKeys else snowNightKeys
            WeatherCondition.Fog -> if (isDaytime) fogDayKeys else fogNightKeys
        }

    private fun buildScheme(keys: SchemeKeys, dark: Boolean): ColorScheme {
        // Text on the primary: dark ink on a bright hue, white on a deep one.
        val onPrimary =
            if (keys.primary.luminance() > 0.5f) Color(0xFF141414) else Color.White
        val onSecondary =
            if (keys.secondary.luminance() > 0.5f) Color(0xFF141414) else Color.White
        val onTertiary =
            if (keys.tertiary.luminance() > 0.5f) Color(0xFF141414) else Color.White
        // Containers: a step from the hue toward the background (dark) or
        // toward white (light); the "on" colour is the matching step back.
        fun container(hue: Color) =
            if (dark) lerp(hue, keys.background, 0.55f) else lerp(hue, Color.White, 0.78f)
        fun onContainer(hue: Color) =
            if (dark) lerp(hue, Color.White, 0.75f) else lerp(hue, Color.Black, 0.45f)
        val surfaceVariant =
            if (dark) lerp(keys.surface, Color.White, 0.09f)
            else lerp(keys.surface, Color.Black, 0.05f)
        val onSurfaceVariant = lerp(keys.onSurface, keys.surface, 0.30f)
        val outline = lerp(keys.onSurface, keys.surface, 0.55f)
        val outlineVariant = lerp(keys.surface, keys.onSurface, 0.10f)
        val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
        val errorContainer =
            if (dark) Color(0xFF8C1D18) else Color(0xFFF9DEDC)
        val onErrorContainer =
            if (dark) Color(0xFFF9DEDC) else Color(0xFF410E0B)

        return if (dark) {
            darkColorScheme(
                primary = keys.primary,
                onPrimary = onPrimary,
                primaryContainer = container(keys.primary),
                onPrimaryContainer = onContainer(keys.primary),
                secondary = keys.secondary,
                onSecondary = onSecondary,
                secondaryContainer = container(keys.secondary),
                onSecondaryContainer = onContainer(keys.secondary),
                tertiary = keys.tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = container(keys.tertiary),
                onTertiaryContainer = onContainer(keys.tertiary),
                background = keys.background,
                onBackground = keys.onSurface,
                surface = keys.surface,
                onSurface = keys.onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = if (dark) Color(0xFF601410) else Color.White,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer
            )
        } else {
            lightColorScheme(
                primary = keys.primary,
                onPrimary = onPrimary,
                primaryContainer = container(keys.primary),
                onPrimaryContainer = onContainer(keys.primary),
                secondary = keys.secondary,
                onSecondary = onSecondary,
                secondaryContainer = container(keys.secondary),
                onSecondaryContainer = onContainer(keys.secondary),
                tertiary = keys.tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = container(keys.tertiary),
                onTertiaryContainer = onContainer(keys.tertiary),
                background = keys.background,
                onBackground = keys.onSurface,
                surface = keys.surface,
                onSurface = keys.onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = Color.White,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer
            )
        }
    }

    /**
     * The full scheme for a condition, time of day, and dark/light mode.
     * Every [WeatherCondition] maps here — no condition falls through to a
     * generic default, and `isDaytime` is always honoured (the old version
     * ignored it).
     */
    fun getSchemeForCondition(
        condition: WeatherCondition,
        isDaytime: Boolean,
        darkTheme: Boolean = true
    ): ColorScheme {
        val keys = keysFor(condition, isDaytime)
        return buildScheme(if (darkTheme) keys else keys.toLight(), darkTheme)
    }

    /** Fallback scheme: cloudy night, dark. Used only when no condition is known. */
    val DefaultDarkScheme: ColorScheme = buildScheme(cloudyNightKeys, dark = true)

    /** Named aliases kept from the pre-token palettes (same keys as above). */
    val ClearDayDarkScheme: ColorScheme = buildScheme(clearDayKeys, dark = true)
    val StormDarkScheme: ColorScheme = buildScheme(stormNightKeys, dark = true)
    val SnowDarkScheme: ColorScheme = buildScheme(snowNightKeys, dark = true)
}
