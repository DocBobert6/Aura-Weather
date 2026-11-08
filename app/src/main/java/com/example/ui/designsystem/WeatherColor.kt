package com.example.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.domain.model.WeatherCondition

object WeatherPalettes {

    // Default Aura Slate/Navy (Clean, Modern Pixel Weather)
    val DefaultDarkScheme = darkColorScheme(
        primary = Color(0xFF90CAF9),
        onPrimary = Color(0xFF0D253F),
        primaryContainer = Color(0xFF1E3A5F),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFB0BEC5),
        onSecondary = Color(0xFF1C2731),
        secondaryContainer = Color(0xFF37474F),
        onSecondaryContainer = Color(0xFFCFD8DC),
        tertiary = Color(0xFFFFB74D),
        onTertiary = Color(0xFF452B00),
        tertiaryContainer = Color(0xFF654000),
        onTertiaryContainer = Color(0xFFFFDDB3),
        background = Color(0xFF0B131E),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF111C2B),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF334155),
        outlineVariant = Color(0xFF1E293B)
    )

    // Sunny/Clear Gold Scheme
    val ClearDayDarkScheme = darkColorScheme(
        primary = Color(0xFFFFCA28),
        onPrimary = Color(0xFF3E2800),
        primaryContainer = Color(0xFF5A3C00),
        onPrimaryContainer = Color(0xFFFFE082),
        secondary = Color(0xFF81D4FA),
        onSecondary = Color(0xFF00344F),
        secondaryContainer = Color(0xFF004D73),
        onSecondaryContainer = Color(0xFFB3E5FC),
        tertiary = Color(0xFFFF8A65),
        onTertiary = Color(0xFF4A1808),
        background = Color(0xFF0F172A),
        surface = Color(0xFF172554),
        onSurface = Color(0xFFF8FAFC)
    )

    // Storm/Thunderstorm Midnight Violet Scheme
    val StormDarkScheme = darkColorScheme(
        primary = Color(0xFFB388FF),
        onPrimary = Color(0xFF280064),
        primaryContainer = Color(0xFF3F0099),
        onPrimaryContainer = Color(0xFFD1C4E9),
        secondary = Color(0xFF80D8FF),
        onSecondary = Color(0xFF003652),
        tertiary = Color(0xFFFF5252),
        onTertiary = Color(0xFF5C0007),
        background = Color(0xFF0A071A),
        surface = Color(0xFF13102B),
        onSurface = Color(0xFFEDE7F6)
    )

    // Snow/Ice Frost Scheme
    val SnowDarkScheme = darkColorScheme(
        primary = Color(0xFF80DEEA),
        onPrimary = Color(0xFF00363A),
        primaryContainer = Color(0xFF004D54),
        onPrimaryContainer = Color(0xFFB2EBF2),
        secondary = Color(0xFFCFD8DC),
        onSecondary = Color(0xFF263238),
        tertiary = Color(0xFF90CAF9),
        onTertiary = Color(0xFF0D253F),
        background = Color(0xFF0D1B2A),
        surface = Color(0xFF15263D),
        onSurface = Color(0xFFF0F4F8)
    )

    fun getSchemeForCondition(condition: WeatherCondition, isDaytime: Boolean): ColorScheme {
        return when (condition) {
            WeatherCondition.Thunderstorm -> StormDarkScheme
            WeatherCondition.Snow, WeatherCondition.Sleet, WeatherCondition.FreezingRain -> SnowDarkScheme
            WeatherCondition.ClearDay, WeatherCondition.PartlyCloudyDay -> ClearDayDarkScheme
            else -> DefaultDarkScheme
        }
    }
}
