package com.example.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.data.work.WidgetUpdateScheduler

/**
 * The 3.2 widget receiver. Glance routes updates to [AuraWeatherWidget];
 * the first widget placed starts the periodic update work and the last
 * one removed stops it, so the device is never woken for a widget
 * nobody placed.
 */
class AuraWeatherWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = AuraWeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Build E: only stop the shared work when no widget of either
        // type is placed — removing the last main widget must not strand
        // a placed hourly strip (and vice versa).
        WidgetUpdateScheduler.cancelIfNoWidgetsPlaced(context)
    }
}
