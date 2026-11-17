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
 * The 3.2 widget receiver (Item 3: classic RemoteViews, replacing
 * Glance). The platform routes updates here; onUpdate re-renders from
 * the repository cache via [WidgetUpdater]. The first widget placed
 * starts the periodic update work and the last one removed stops it,
 * so the device is never woken for a widget nobody placed.
 */
class AuraWeatherWidgetReceiver : AppWidgetProvider() {

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
                WidgetUpdater.updateMain(context, appWidgetManager)
            } finally {
                result.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        // Build E: only stop the shared work when no widget of either
        // type is placed — removing the last main widget must not strand
        // a placed hourly strip (and vice versa).
        WidgetUpdateScheduler.cancelIfNoWidgetsPlaced(context)
    }
}
