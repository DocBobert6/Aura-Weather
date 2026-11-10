package com.example.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.ui.designsystem.RetroTokens

/**
 * Shared Glance pieces for the two widgets (Build E). The 3.2 main
 * widget and the hourly-strip widget render from the same palette,
 * the same condition drawables, and the same empty prompt, so they
 * live here instead of being duplicated.
 *
 * Retro palette as day/night providers, from RetroTokens (the §2.8
 * gate: no hardcoded Color(0xFF...) outside the token files). Night is
 * the app's own navy/yellow; day is the light variant (white background,
 * navy text, orange temp — yellow is unreadable on white).
 */
internal object WidgetColors {
    val background = ColorProvider(
        day = RetroTokens.TextPrimary,
        night = RetroTokens.Background
    )
    val primary = ColorProvider(
        day = RetroTokens.Background,
        night = RetroTokens.TextPrimary
    )
    val temp = ColorProvider(
        day = RetroTokens.HeaderOrange,
        night = RetroTokens.Yellow
    )
    val icon = ColorProvider(
        day = RetroTokens.Background,
        night = RetroTokens.TextPrimary
    )
}

internal fun conditionIconRes(icon: WidgetConditionIcon): Int = when (icon) {
    WidgetConditionIcon.Sunny -> R.drawable.ic_widget_sunny
    WidgetConditionIcon.Moon -> R.drawable.ic_widget_moon
    WidgetConditionIcon.PartlyCloudy -> R.drawable.ic_widget_partly
    WidgetConditionIcon.Cloudy -> R.drawable.ic_widget_cloud
    WidgetConditionIcon.Rain -> R.drawable.ic_widget_rain
    WidgetConditionIcon.Snow -> R.drawable.ic_widget_snow
    WidgetConditionIcon.Storm -> R.drawable.ic_widget_storm
}

internal fun iconTint(icon: WidgetConditionIcon) =
    if (icon == WidgetConditionIcon.Sunny) WidgetColors.temp else WidgetColors.icon

@Composable
internal fun WidgetEmptyContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "AURA WEATHER",
            style = TextStyle(
                fontSize = 13.sp,
                color = WidgetColors.temp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "OPEN AURA WEATHER TO LOAD",
            style = TextStyle(
                fontSize = 13.sp,
                color = WidgetColors.primary
            )
        )
    }
}

/** One hour of the strip: label, condition icon, temp. */
@Composable
internal fun WidgetHourColumn(
    point: WidgetHourlyPoint,
    useMetric: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val icon = conditionToWidgetIcon(point.condition, point.isDaytime)
    val formatter = com.example.core.UnitFormatter(useMetric)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = point.hourLabel,
            style = TextStyle(
                fontSize = 11.sp,
                color = WidgetColors.primary
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(conditionIconRes(icon)),
            contentDescription = point.condition.displayName,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(iconTint(icon))
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = formatter.formatTemperature(point.tempC),
            style = TextStyle(
                fontSize = 14.sp,
                color = WidgetColors.temp
            ),
            maxLines = 1
        )
    }
}
