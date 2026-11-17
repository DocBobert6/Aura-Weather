package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.example.data.work.WidgetUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Build E hourly-strip widget receiver (Item 3: classic
 * RemoteViews, replacing Glance). The platform routes updates here;
 * onUpdate re-renders from the repository cache via [WidgetUpdater].
 * The first widget placed (of either widget type) starts the shared
 * periodic update work; the last one removed stops it —
 * [WidgetUpdateScheduler.cancelIfNoWidgetsPlaced] checks both
 * receivers, so removing one widget type never strands the other
 * without updates.
 */
class HourlyStripWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // onUpdate is synchronous: the render runs off-thread and
        // goAsync keeps the broadcast alive until it finishes.
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateStrip(context, appWidgetManager)
            } finally {
                result.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.cancelIfNoWidgetsPlaced(context)
    }
}
