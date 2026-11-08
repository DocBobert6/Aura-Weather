package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.WeatherCondition
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

    val colorScheme = when {
        paletteMode == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        paletteMode == "auto" -> {
            WeatherPalettes.getSchemeForCondition(condition, isDaytime)
        }
        else -> {
            WeatherPalettes.DefaultDarkScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
