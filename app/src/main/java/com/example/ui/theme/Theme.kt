package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.WeatherCondition
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.WeatherPalettes

@Composable
fun AuraWeatherTheme(
    paletteMode: String = "auto",
    condition: WeatherCondition = WeatherCondition.ClearDay,
    isDaytime: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    materialYouMode: String = "auto",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // "monet" (Material You) takes its colours from the phone; "expressive"
    // resolves through the condition schemes, so it is a motion/theme
    // treatment, not a second palette. The automatic "auto" palette was
    // removed 2026-11-08, so these are the only two modes.
    val colorScheme = when {
        paletteMode == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Material You: the Settings dropdown (Auto/Light/Dark)
            // overrides the global theme mode for the dynamic scheme only.
            // "auto" falls through to the theme's darkTheme.
            val materialYouDark = when (materialYouMode) {
                "dark" -> true
                "light" -> false
                else -> darkTheme
            }
            if (materialYouDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            WeatherPalettes.getSchemeForCondition(condition, isDaytime, darkTheme)
        }
    }

    // "expressive" now does something real (audit finding 3): it selects
    // the bouncier AuraMotion spring set at the animated call sites in
    // 2.3+, instead of silently falling back to the default dark scheme.
    // There is no theme-level expressive entry point to call here —
    // material3 1.4.0 keeps MotionScheme and MaterialExpressiveTheme
    // internal — so the theme itself is the one public MaterialTheme.
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AuraShapes.materialShapes(),
        typography = Typography,
        content = content
    )
}
