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
// Star4000 in R1). The hero temperature is a display token with tabular
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
    bodyLarge =
      TextStyle(
        fontFamily = Star4000Small,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
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
