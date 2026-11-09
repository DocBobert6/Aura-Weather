package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens

/**
 * The app theme (R1 of the retro reskin). ONE fixed retro navy colour
 * scheme — no dark/light branch, no palette branch, no dynamic-colour /
 * Material You code, no Expressive code. The app is always navy. All
 * colours come from [RetroTokens]; there are no hardcoded colours here.
 */
@Composable
fun AuraWeatherTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = RetroTokens.Yellow,
        onPrimary = RetroTokens.Background,
        primaryContainer = RetroTokens.PanelLight,
        onPrimaryContainer = RetroTokens.Yellow,
        secondary = RetroTokens.HeaderOrange,
        onSecondary = RetroTokens.TextPrimary,
        secondaryContainer = RetroTokens.PanelLight,
        onSecondaryContainer = RetroTokens.TextPrimary,
        tertiary = RetroTokens.HeaderRed,
        onTertiary = RetroTokens.TextPrimary,
        background = RetroTokens.Background,
        onBackground = RetroTokens.TextPrimary,
        surface = RetroTokens.Panel,
        onSurface = RetroTokens.TextPrimary,
        surfaceVariant = RetroTokens.PanelLight,
        onSurfaceVariant = RetroTokens.TextSecondary,
        surfaceContainer = RetroTokens.Panel,
        surfaceContainerHigh = RetroTokens.PanelLight,
        outline = RetroTokens.Border,
        outlineVariant = RetroTokens.Border,
        error = RetroTokens.HeaderRed,
        onError = RetroTokens.TextPrimary,
        errorContainer = RetroTokens.HeaderRed,
        onErrorContainer = RetroTokens.TextPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = RetroShapes.materialShapes(),
        typography = Typography,
        content = content
    )
}
