package com.example.ui.widget

import android.content.Context
import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.AuraWeatherApp
import com.example.MainActivity
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The Build E hourly-strip widget (Glance, audit item 19): the next
 * hours' temp + condition icon in a horizontal strip, with the place
 * name above. Tap opens the app.
 *
 * Follows the 3.2 widget pattern exactly: cache-only via
 * [com.example.data.repo.WeatherRepository.getWidgetState] (never a
 * network fetch from the widget, never invented weather), solid
 * navy/yellow colours from RetroTokens at all times (no light/day
 * variant), system sans font (Star4000 cannot be used
 * in Glance), and the same update triggers — the Application re-renders
 * it on every in-app refresh and the shared 60-minute
 * WidgetUpdateWorker keeps it fresh while the app is closed.
 *
 * Layout: a wide 4x1 strip shows 6 hours, the middle size shows 5,
 * and a narrower placement shows 4. The hours come from
 * WidgetState.hourly (the repository fills the first 6 from the
 * 48-hour forecast).
 */
class HourlyStripWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(320.dp, 110.dp),
                DpSize(250.dp, 110.dp),
                DpSize(180.dp, 110.dp)
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
                if (state == null || state.hourly.isEmpty()) {
                    WidgetEmptyContent()
                } else {
                    HourlyStripContent(
                        state,
                        when {
                            size.width >= 300.dp -> 6
                            size.width >= 250.dp -> 5
                            else -> 4
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyStripContent(state: WidgetState, hourCount: Int) {
    val hours = state.hourly.take(hourCount)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = state.placeName.uppercase(Locale.US),
            style = TextStyle(
                fontSize = 12.sp,
                color = WidgetColors.primary
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            hours.forEachIndexed { index, point ->
                WidgetHourColumn(
                    point = point,
                    useMetric = state.useMetric,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (index < hours.lastIndex) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
            }
        }
    }
}
