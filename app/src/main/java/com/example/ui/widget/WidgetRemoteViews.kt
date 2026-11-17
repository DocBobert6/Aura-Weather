package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.work.AlertNotifier
import java.util.Locale

/**
 * The classic RemoteViews widget layer (Item 3, replacing the Glance
 * UI). Three layouts, all rendered from [WidgetState] (cache-only, never
 * a network fetch from the widget, never invented weather):
 *
 * - [R.layout.widget_main]: the 2x2 square default (stretches to 4x2) —
 *   logo box, condition icon + big Star4000 temp, place, condition,
 *   high/low, updated line, and the ticker-blue strip with one fact.
 * - [R.layout.widget_strip]: the 4x1 hourly strip — place name plus
 *   4–6 hour columns ([stripHourCount] by placement width).
 * - [R.layout.widget_radar]: the static composited radar bitmap plus
 *   its label line (Item 4).
 *
 * Text is BITMAP-rendered Star4000 (see [WidgetTextBitmapRenderer]):
 * the @font/ XML route failed on Robert's Pixel launcher — all three
 * widgets rendered system sans despite
 * `android:fontFamily="@font/star4000_*"` in the layouts (proved
 * 2026-11-16 by rendering the actual TTFs and comparing letterforms
 * against his screenshot) — so no layout here relies on @font/ any
 * more. Every text run is drawn with the Star4000 Typeface on a Canvas
 * into a small bitmap and set via `setImageViewBitmap`, with the run's
 * string as the ImageView's content description. Bitmap text is not
 * selectable or readable as text by accessibility services; the app
 * itself remains the accessible surface. Colours come from the
 * `retro_*` resources in colors.xml, whose values are copied from
 * RetroTokens. The retro chrome is static (no live map/animation/
 * shader — a widget host cannot run those).
 */
object WidgetRemoteViews {

    /** Max run width for the main widget's short text lines, in dp. */
    private const val MAX_WIDTH_MAIN_TEXT_DP = 180f

    /** Max run width for the main widget's big temp, in dp. */
    private const val MAX_WIDTH_MAIN_TEMP_DP = 160f

    /** Max run width for the ticker strip and the empty prompts, in dp. */
    private const val MAX_WIDTH_WIDE_DP = 320f

    /** Max run width for one strip hour column's label/temp, in dp. */
    private const val MAX_WIDTH_STRIP_COLUMN_DP = 56f

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

    /** Tap target for the radar widget: opens the app on the Radar tab. */
    private fun openRadarPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(AlertNotifier.EXTRA_DESTINATION, AlertNotifier.DESTINATION_RADAR)
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Set one bitmap text run on [viewId]: render [text] in [face] at
     * [textSizeSp] in the [colorRes] colour, ellipsised to [maxWidthDp].
     * The run's string becomes the view's content description, so the
     * text is still announced even though it is a bitmap. If rendering
     * fails the run is hidden rather than left blank — the renderer
     * never throws, so this only happens on a catastrophic failure.
     */
    private fun setBitmapText(
        views: RemoteViews,
        context: Context,
        viewId: Int,
        text: String,
        face: WidgetTextFace,
        textSizeSp: Float,
        colorRes: Int,
        maxWidthDp: Float
    ) {
        val bitmap = WidgetTextBitmapRenderer.renderText(
            context,
            text,
            face,
            textSizeSp,
            context.getColor(colorRes),
            maxWidthDp
        )
        if (bitmap != null) {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setImageViewBitmap(viewId, bitmap)
            views.setContentDescription(viewId, text)
        } else {
            views.setViewVisibility(viewId, View.GONE)
        }
    }

    /**
     * The radar widget (Item 4): a static composited bitmap plus its
     * label line. [bitmap] null with [showEmptyPrompt] false is the
     * unavailable state (label only, no image, never a blank);
     * [showEmptyPrompt] true is the no-selected-place prompt.
     */
    fun buildRadar(
        context: Context,
        bitmap: Bitmap?,
        label: String,
        showEmptyPrompt: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_radar)
        views.setOnClickPendingIntent(R.id.widget_radar_root, openRadarPendingIntent(context))
        if (showEmptyPrompt) {
            views.setViewVisibility(R.id.widget_radar_image, View.GONE)
            views.setViewVisibility(R.id.widget_radar_label_bar, View.GONE)
            views.setViewVisibility(R.id.widget_radar_empty, View.VISIBLE)
            setBitmapText(
                views, context, R.id.widget_radar_empty,
                "OPEN AURA WEATHER TO ADD A PLACE",
                WidgetTextFace.Small, 11f, R.color.retro_text_primary,
                MAX_WIDTH_WIDE_DP
            )
            return views
        }
        views.setViewVisibility(R.id.widget_radar_empty, View.GONE)
        views.setViewVisibility(R.id.widget_radar_label_bar, View.VISIBLE)
        setBitmapText(
            views, context, R.id.widget_radar_label,
            label, WidgetTextFace.Small, 11f, R.color.retro_text_primary,
            MAX_WIDTH_WIDE_DP
        )
        if (bitmap != null) {
            views.setViewVisibility(R.id.widget_radar_image, View.VISIBLE)
            views.setImageViewBitmap(R.id.widget_radar_image, bitmap)
        } else {
            views.setViewVisibility(R.id.widget_radar_image, View.GONE)
        }
        return views
    }

    /** The main widget. A null [state] renders the empty prompt. */
    fun buildMain(context: Context, state: WidgetState?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_main)
        views.setOnClickPendingIntent(R.id.widget_main_root, openAppPendingIntent(context))
        setBitmapText(
            views, context, R.id.widget_main_logo,
            "AURA WEATHER", WidgetTextFace.Small, 12f, R.color.retro_yellow,
            MAX_WIDTH_WIDE_DP
        )
        if (state == null) {
            views.setViewVisibility(R.id.widget_main_content, View.GONE)
            views.setViewVisibility(R.id.widget_main_ticker, View.GONE)
            views.setViewVisibility(R.id.widget_main_empty, View.VISIBLE)
            setBitmapText(
                views, context, R.id.widget_main_empty_text,
                "OPEN AURA WEATHER TO LOAD",
                WidgetTextFace.Small, 12f, R.color.retro_text_primary,
                MAX_WIDTH_WIDE_DP
            )
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
        setBitmapText(
            views, context, R.id.widget_main_temp,
            display.tempText, WidgetTextFace.Large, 34f, R.color.retro_yellow,
            MAX_WIDTH_MAIN_TEMP_DP
        )
        setBitmapText(
            views, context, R.id.widget_main_place,
            state.placeName.uppercase(Locale.US),
            WidgetTextFace.Small, 13f, R.color.retro_text_primary,
            MAX_WIDTH_MAIN_TEXT_DP
        )
        setBitmapText(
            views, context, R.id.widget_main_condition,
            display.conditionText, WidgetTextFace.Small, 12f,
            R.color.retro_text_primary, MAX_WIDTH_MAIN_TEXT_DP
        )
        setBitmapText(
            views, context, R.id.widget_main_highlow,
            display.highLowText, WidgetTextFace.Small, 12f,
            R.color.retro_text_primary, MAX_WIDTH_MAIN_TEXT_DP
        )
        setBitmapText(
            views, context, R.id.widget_main_updated,
            display.updatedText, WidgetTextFace.Small, 10f,
            R.color.retro_text_primary, MAX_WIDTH_MAIN_TEXT_DP
        )
        // Ticker strip: one fact per update, swapped on refresh.
        setBitmapText(
            views, context, R.id.widget_main_ticker_text,
            state.tickerFact(), WidgetTextFace.Small, 11f,
            R.color.retro_text_primary, MAX_WIDTH_WIDE_DP
        )
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
            setBitmapText(
                views, context, R.id.widget_strip_empty_text,
                "OPEN AURA WEATHER TO LOAD",
                WidgetTextFace.Small, 12f, R.color.retro_text_primary,
                MAX_WIDTH_WIDE_DP
            )
            return views
        }
        val minWidth = AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val count = stripHourCount(minWidth)
        val hours = state.hourly.take(count)
        setBitmapText(
            views, context, R.id.widget_strip_place,
            state.placeName.uppercase(Locale.US),
            WidgetTextFace.Small, 12f, R.color.retro_text_primary,
            MAX_WIDTH_WIDE_DP
        )
        hourColumns.forEachIndexed { index, column ->
            if (index < hours.size) {
                val point = hours[index]
                val icon = conditionToWidgetIcon(point.condition, point.isDaytime)
                views.setViewVisibility(column.root, View.VISIBLE)
                setBitmapText(
                    views, context, column.label,
                    point.hourLabel, WidgetTextFace.Small, 11f,
                    R.color.retro_text_primary, MAX_WIDTH_STRIP_COLUMN_DP
                )
                views.setImageViewResource(column.icon, conditionIconRes(icon))
                views.setInt(
                    column.icon,
                    "setColorFilter",
                    context.getColor(conditionIconTintRes(icon))
                )
                views.setContentDescription(column.icon, point.condition.displayName)
                setBitmapText(
                    views, context, column.temp,
                    com.example.core.UnitFormatter(state.useMetric)
                        .formatTemperature(point.tempC),
                    WidgetTextFace.Large, 15f, R.color.retro_yellow,
                    MAX_WIDTH_STRIP_COLUMN_DP
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
