package com.example.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.data.work.WidgetUpdateScheduler

/**
 * The Build E hourly-strip widget receiver. Glance routes updates to
 * [HourlyStripWidget]. The first widget placed (of either widget type)
 * starts the shared periodic update work; the last one removed stops
 * it — [WidgetUpdateScheduler.cancelIfNoWidgetsPlaced] checks both
 * receivers, so removing one widget type never strands the other
 * without updates.
 */
class HourlyStripWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HourlyStripWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancelIfNoWidgetsPlaced(context)
    }
}
