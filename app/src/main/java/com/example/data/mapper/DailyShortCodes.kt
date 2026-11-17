package com.example.data.mapper

import com.example.domain.model.WeatherCondition

/**
 * Daily-row short condition codes (0.3.3 UI/font audit, item 5).
 *
 * The daily row's condition column only fits ~5 characters of the full
 * display name at 14sp Star4000 Small, so "PARTLY CLOUDY" clipped to
 * "PARTL" and "DRIZZLE" to "DRIZZ" on Robert's screenshots. The row
 * renders these WeatherStar-4000-style abbreviations instead. The full
 * display name is still what the row's icon announces (its
 * contentDescription) and what the hero condition line shows — the code
 * is a space-saving label, not a new condition.
 *
 * Pure (no Compose imports): every code is uppercase already, and the
 * widest code is 8 characters. At 14sp every Star4000 Small capital
 * advances 973/2048 em (6.65dp) plus 0.25sp letter spacing, so the
 * widest code renders ~55dp — inside the 83dp condition column on a
 * 411dp screen (16dp list padding + 18dp card padding + 252dp fixed
 * columns: day 48, icon/pop 40, amount 56, LO 54, HI 54). See
 * DailyShortCodesTest for the width assertion.
 */
fun shortConditionCode(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.ClearDay, WeatherCondition.ClearNight -> "CLEAR"
    WeatherCondition.PartlyCloudyDay, WeatherCondition.PartlyCloudyNight -> "PT CLDY"
    WeatherCondition.Cloudy -> "CLOUDY"
    WeatherCondition.Fog -> "FOG"
    WeatherCondition.Haze -> "HAZY"
    WeatherCondition.Drizzle -> "DRIZZLE"
    WeatherCondition.Rain -> "RAIN"
    WeatherCondition.HeavyRain -> "HVY RAIN"
    WeatherCondition.Thunderstorm -> "T-STORMS"
    WeatherCondition.Snow -> "SNOW"
    WeatherCondition.Sleet -> "SLEET"
    WeatherCondition.FreezingRain -> "FRZ RAIN"
    WeatherCondition.Windy -> "WINDY"
    WeatherCondition.Unknown -> "FAIR"
}
