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
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Colour source is the condition (decision D1), in the requested
    // dark/light mode. "monet" keeps its dynamic-colour branch; every other
    // mode resolves through the condition schemes — "expressive" included,
    // so it is a motion/theme treatment, not a second palette.
    val colorScheme = when {
        paletteMode == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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
