package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.WeatherCondition
import com.example.ui.designsystem.AuraMotion
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

    // "expressive" now does something real (audit finding 3): the expressive
    // theme + motion scheme, instead of silently falling back to the default
    // dark scheme. Other modes keep the standard theme and motion.
    val motionScheme: MotionScheme =
        if (paletteMode == "expressive") AuraMotion.expressive else AuraMotion.standard

    if (paletteMode == "expressive") {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = AuraShapes.materialShapes(),
            typography = Typography,
            content = content
        )
    } else {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = AuraShapes.materialShapes(),
            typography = Typography,
            content = content
        )
    }
}
