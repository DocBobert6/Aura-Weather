package com.example.ui.widget

import android.content.Context
import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.example.AuraWeatherApp
import com.example.MainActivity
import com.example.R
import com.example.ui.designsystem.RetroTokens
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The 3.2 home-screen widget (Glance, v1). Current temp + condition
 * icon + place name + high/low + an "updated HH:MM" line; tap opens the
 * app. One responsive layout: a wide 4x1 row and a square 2x2 column.
 *
 * Data comes from the repository cache (never a network fetch from the
 * widget itself, never invented weather): no selected place or no cache
 * shows the empty prompt. The retro look is kept where Glance allows —
 * solid navy at night, Star4000 type, yellow temp — with a light
 * variant for day mode (white background, navy text, orange temp).
 */
class AuraWeatherWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(180.dp, 180.dp),
                DpSize(320.dp, 110.dp)
            )
        )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as AuraWeatherApp).container
        val useMetric = container.settingsManager.useMetricFlow.first()
        val state = container.weatherRepository.getWidgetState(useMetric)
        val openApp = actionStartActivity(
            ComponentName(context, MainActivity::class.java)
        )
        provideContent {
            val size = LocalSize.current
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetColors.background)
                    .padding(12.dp)
                    .clickable(openApp)
            ) {
                if (state == null) {
                    WidgetEmptyContent()
                } else if (size.width > size.height) {
                    WidgetWideContent(state)
                } else {
                    WidgetSquareContent(state)
                }
            }
        }
    }
}

/** Retro palette as day/night providers, from RetroTokens (the §2.8
 * gate: no hardcoded Color(0xFF...) outside the token files). Night is
 * the app's own navy/yellow; day is the light variant (white background,
 * navy text, orange temp — yellow is unreadable on white). */
private object WidgetColors {
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

private fun conditionIconRes(icon: WidgetConditionIcon): Int = when (icon) {
    WidgetConditionIcon.Sunny -> R.drawable.ic_widget_sunny
    WidgetConditionIcon.Moon -> R.drawable.ic_widget_moon
    WidgetConditionIcon.PartlyCloudy -> R.drawable.ic_widget_partly
    WidgetConditionIcon.Cloudy -> R.drawable.ic_widget_cloud
    WidgetConditionIcon.Rain -> R.drawable.ic_widget_rain
    WidgetConditionIcon.Snow -> R.drawable.ic_widget_snow
    WidgetConditionIcon.Storm -> R.drawable.ic_widget_storm
}

private fun iconTint(icon: WidgetConditionIcon) =
    if (icon == WidgetConditionIcon.Sunny) WidgetColors.temp else WidgetColors.icon

@Composable
private fun WidgetEmptyContent() {
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

@Composable
private fun WidgetWideContent(state: WidgetState) {
    val display = state.toDisplay()
    val icon = conditionToWidgetIcon(state.condition, state.isDaytime)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(conditionIconRes(icon)),
            contentDescription = display.conditionText,
            modifier = GlanceModifier.size(56.dp),
            colorFilter = ColorFilter.tint(iconTint(icon))
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.placeName.uppercase(Locale.US),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = WidgetColors.primary
                ),
                maxLines = 1
            )
            Text(
                text = display.tempText,
                style = TextStyle(
                    fontSize = 36.sp,
                    color = WidgetColors.temp
                )
            )
            Text(
                text = display.conditionText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = WidgetColors.primary
                ),
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(
                text = display.highLowText,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = WidgetColors.primary
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = display.updatedText,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = WidgetColors.primary
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WidgetSquareContent(state: WidgetState) {
    val display = state.toDisplay()
    val icon = conditionToWidgetIcon(state.condition, state.isDaytime)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(conditionIconRes(icon)),
            contentDescription = display.conditionText,
            modifier = GlanceModifier.size(40.dp),
            colorFilter = ColorFilter.tint(iconTint(icon))
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = display.tempText,
            style = TextStyle(
                fontSize = 32.sp,
                color = WidgetColors.temp
            )
        )
        Text(
            text = state.placeName.uppercase(Locale.US),
            style = TextStyle(
                fontSize = 12.sp,
                color = WidgetColors.primary
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = display.highLowText,
            style = TextStyle(
                fontSize = 12.sp,
                color = WidgetColors.primary
            ),
            maxLines = 1
        )
        Text(
            text = display.updatedText,
            style = TextStyle(
                fontSize = 10.sp,
                color = WidgetColors.primary
            ),
            maxLines = 1
        )
    }
}
