package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.AuraWeatherApp
import kotlinx.coroutines.flow.first

/**
 * Re-renders the classic RemoteViews widgets from the repository cache
 * (Item 3). One entry point for every trigger — the receivers'
 * onUpdate, the Application's weather-state collector, and the
 * 60-minute [com.example.data.work.WidgetUpdateWorker] — so all three
 * stay in step. Each type is a no-op when none of it is placed.
 *
 * Data comes from [com.example.data.repo.WeatherRepository.getWidgetState]
 * (cache-first, never a network fetch from the widget itself, never
 * invented weather): no selected place or no cache renders the empty
 * prompt. A widget failure must never crash the caller, so call sites
 * guard this with runCatching.
 */
object WidgetUpdater {

    /** Re-render every placed widget of both types. */
    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        updateMain(context, manager)
        updateStrip(context, manager)
    }

    /** Re-render every placed main widget. */
    suspend fun updateMain(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        val ids = manager.getAppWidgetIds(
            ComponentName(context, AuraWeatherWidgetReceiver::class.java)
        )
        if (ids.isEmpty()) return
        val state = loadState(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, WidgetRemoteViews.buildMain(context, state))
        }
    }

    /** Re-render every placed hourly-strip widget. */
    suspend fun updateStrip(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        val ids = manager.getAppWidgetIds(
            ComponentName(context, HourlyStripWidgetReceiver::class.java)
        )
        if (ids.isEmpty()) return
        val state = loadState(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, WidgetRemoteViews.buildStrip(context, state, id))
        }
    }

    private suspend fun loadState(context: Context): WidgetState? {
        val container = (context.applicationContext as AuraWeatherApp).container
        val useMetric = container.settingsManager.useMetricFlow.first()
        return container.weatherRepository.getWidgetState(useMetric)
    }
}
