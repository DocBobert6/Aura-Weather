package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import java.util.Locale

/**
 * The classic RemoteViews widget layer (Item 3, replacing the Glance
 * UI). Two layouts, both rendered from [WidgetState] (cache-only, never
 * a network fetch from the widget, never invented weather):
 *
 * - [R.layout.widget_main]: the 2x2 square default (stretches to 4x2) —
 *   logo box, condition icon + big Star4000 temp, place, condition,
 *   high/low, updated line, and the ticker-blue strip with one fact.
 * - [R.layout.widget_strip]: the 4x1 hourly strip — place name plus
 *   4–6 hour columns ([stripHourCount] by placement width).
 *
 * The retro chrome is static (no live map/animation/shader — a widget
 * host cannot run those); the Star4000 faces come from
 * `android:fontFamily="@font/star4000_large|small"` in the layouts,
 * which the framework resolves on API 26+ and falls back to system
 * sans on API 24–25. Colours come from the `retro_*` resources in
 * colors.xml, whose values are copied from RetroTokens.
 */
object WidgetRemoteViews {

    /** Drawable for one condition icon (the white vector icons). */
    internal fun conditionIconRes(icon: WidgetConditionIcon): Int = when (icon) {
        WidgetConditionIcon.Sunny -> R.drawable.ic_widget_sunny
        WidgetConditionIcon.Moon -> R.drawable.ic_widget_moon
        WidgetConditionIcon.PartlyCloudy -> R.drawable.ic_widget_partly
        WidgetConditionIcon.Cloudy -> R.drawable.ic_widget_cloud
        WidgetConditionIcon.Rain -> R.drawable.ic_widget_rain
        WidgetConditionIcon.Snow -> R.drawable.ic_widget_snow
        WidgetConditionIcon.Storm -> R.drawable.ic_widget_storm
    }

    /** Tint for one condition icon: yellow sun, white for everything else. */
    internal fun conditionIconTintRes(icon: WidgetConditionIcon): Int =
        if (icon == WidgetConditionIcon.Sunny) R.color.retro_yellow
        else R.color.retro_text_primary

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The main widget. A null [state] renders the empty prompt. */
    fun buildMain(context: Context, state: WidgetState?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_main)
        views.setOnClickPendingIntent(R.id.widget_main_root, openAppPendingIntent(context))
        if (state == null) {
            views.setViewVisibility(R.id.widget_main_content, View.GONE)
            views.setViewVisibility(R.id.widget_main_ticker, View.GONE)
            views.setViewVisibility(R.id.widget_main_empty, View.VISIBLE)
            return views
        }
        val display = state.toDisplay()
        val icon = conditionToWidgetIcon(state.condition, state.isDaytime)
        views.setImageViewResource(R.id.widget_main_icon, conditionIconRes(icon))
        // RemoteViews has no tint setter: setColorFilter(int) via setInt.
        views.setInt(
            R.id.widget_main_icon,
            "setColorFilter",
            context.getColor(conditionIconTintRes(icon))
        )
        views.setContentDescription(R.id.widget_main_icon, display.conditionText)
        views.setTextViewText(R.id.widget_main_temp, display.tempText)
        views.setTextViewText(
            R.id.widget_main_place,
            state.placeName.uppercase(Locale.US)
        )
        views.setTextViewText(R.id.widget_main_condition, display.conditionText)
        views.setTextViewText(R.id.widget_main_highlow, display.highLowText)
        views.setTextViewText(R.id.widget_main_updated, display.updatedText)
        // Ticker strip: one fact per update, swapped on refresh.
        views.setTextViewText(R.id.widget_main_ticker, state.tickerFact())
        return views
    }

    /**
     * The hourly strip. A null [state] or an empty hour list renders
     * the empty prompt; otherwise the first [stripHourCount] hours for
     * this placement's width are shown and the rest hidden.
     */
    fun buildStrip(context: Context, state: WidgetState?, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_strip)
        views.setOnClickPendingIntent(R.id.widget_strip_root, openAppPendingIntent(context))
        if (state == null || state.hourly.isEmpty()) {
            views.setViewVisibility(R.id.widget_strip_content, View.GONE)
            views.setViewVisibility(R.id.widget_strip_empty, View.VISIBLE)
            return views
        }
        val minWidth = AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val count = stripHourCount(minWidth)
        val hours = state.hourly.take(count)
        views.setTextViewText(
            R.id.widget_strip_place,
            state.placeName.uppercase(Locale.US)
        )
        hourColumns.forEachIndexed { index, column ->
            if (index < hours.size) {
                val point = hours[index]
                val icon = conditionToWidgetIcon(point.condition, point.isDaytime)
                views.setViewVisibility(column.root, View.VISIBLE)
                views.setTextViewText(column.label, point.hourLabel)
                views.setImageViewResource(column.icon, conditionIconRes(icon))
                views.setInt(
                    column.icon,
                    "setColorFilter",
                    context.getColor(conditionIconTintRes(icon))
                )
                views.setContentDescription(column.icon, point.condition.displayName)
                views.setTextViewText(
                    column.temp,
                    com.example.core.UnitFormatter(state.useMetric)
                        .formatTemperature(point.tempC)
                )
            } else {
                views.setViewVisibility(column.root, View.GONE)
            }
        }
        return views
    }

    /** One hour column of the strip layout, by view id. */
    private data class HourColumn(val root: Int, val label: Int, val icon: Int, val temp: Int)

    private val hourColumns = listOf(
        HourColumn(
            R.id.widget_strip_hour_1,
            R.id.widget_strip_hour_label_1,
            R.id.widget_strip_hour_icon_1,
            R.id.widget_strip_hour_temp_1
        ),
        HourColumn(
            R.id.widget_strip_hour_2,
            R.id.widget_strip_hour_label_2,
            R.id.widget_strip_hour_icon_2,
            R.id.widget_strip_hour_temp_2
        ),
        HourColumn(
            R.id.widget_strip_hour_3,
            R.id.widget_strip_hour_label_3,
            R.id.widget_strip_hour_icon_3,
            R.id.widget_strip_hour_temp_3
        ),
        HourColumn(
            R.id.widget_strip_hour_4,
            R.id.widget_strip_hour_label_4,
            R.id.widget_strip_hour_icon_4,
            R.id.widget_strip_hour_temp_4
        ),
        HourColumn(
            R.id.widget_strip_hour_5,
            R.id.widget_strip_hour_label_5,
            R.id.widget_strip_hour_icon_5,
            R.id.widget_strip_hour_temp_5
        ),
        HourColumn(
            R.id.widget_strip_hour_6,
            R.id.widget_strip_hour_label_6,
            R.id.widget_strip_hour_icon_6,
            R.id.widget_strip_hour_temp_6
        )
    )
}
