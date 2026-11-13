package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Star4000 type (R1 of the retro reskin). The WeatherStar 4000 faces,
 * originally created by Nick Smith (twcclassics.com), redistributed in the
 * MIT-licensed ws4kp / FUN-WeatherStar-4000 fan projects — credited in
 * Settings > Data Sources & Attribution. Large is the display face
 * (headings, big temperatures); Small is the label/body face. All retro
 * text is uppercase (the screens uppercase in R2–R4).
 */
val Star4000Large = FontFamily(
    Font(R.font.star4000_large, FontWeight.Normal),
    Font(R.font.star4000_large, FontWeight.Bold)
)

val Star4000Small = FontFamily(
    Font(R.font.star4000_small, FontWeight.Normal),
    Font(R.font.star4000_small, FontWeight.Bold)
)

// Set of Material typography styles (Phase 2.2 type tokens, re-faced
// Star4000 in R1). L-8: every style the app actually uses is defined
// here at its Material 3 default size, so the retro face applies
// app-wide instead of falling back to default sans for most text.
// Star4000 has NO en dash, em dash, or ellipsis glyphs — strings
// rendered in these styles must not contain them (see star4000Safe).
// The hero temperature is a display token with tabular
// numbers ("tnum"), so the temperature cannot jitter as its digits change.
val Typography =
  Typography(
    // Hero temperature (Home): large display style, tabular figures.
    displayLarge =
      TextStyle(
        fontFamily = Star4000Large,
        fontWeight = FontWeight.Bold,
        fontSize = 72.sp,
        lineHeight = 76.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
      ),
    // Big AQI number (Air Quality screen): the display face at the
    // Material 3 default size — the size is unchanged, only the face.
    displayMedium =
      TextStyle(
        fontFamily = Star4000Large,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
      ),
    // Detail-card main values (Home): display face, M3 default size.
    headlineSmall =
      TextStyle(
        fontFamily = Star4000Large,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
      ),
    // Place name in the floating top bar.
    titleLarge =
      TextStyle(
        fontFamily = Star4000Large,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
      ),
    // Condition line under the hero temperature.
    titleMedium =
      TextStyle(
        fontFamily = Star4000Large,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
      ),
    // Settings headings: label/body face, M3 default size.
    titleSmall =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ),
    // Daily rows, hero "FEELS LIKE" line, guidance text: M3 default size.
    bodyMedium =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
      ),
    // Settings body, allergy-index notes: M3 default size.
    bodySmall =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
      ),
    // Section headings, category pills: M3 default size.
    labelMedium =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
    // Freshness pill / caption text.
    labelSmall =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
  )
