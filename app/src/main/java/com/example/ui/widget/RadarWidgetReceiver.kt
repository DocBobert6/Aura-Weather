package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.example.data.work.RadarWidgetScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The radar widget receiver (Item 4: classic RemoteViews, 4x2). The
 * platform routes updates here; onUpdate bakes a fresh image via
 * [RadarWidgetUpdater] (a network fetch — the radar image cannot
 * come from the weather cache). The first widget placed starts the
 * 15-minute periodic update work and the last one removed stops it,
 * so the device is never woken for a widget nobody placed. The
 * 15-min cadence is Robert's scoped approval for this widget only
 * (2026-11-16); the main/strip widgets keep their 60-min cadence.
 */
class RadarWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // onUpdate is synchronous: the bake runs off-thread and
        // goAsync keeps the broadcast alive until it finishes.
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RadarWidgetUpdater.updateRadar(context)
            } finally {
                result.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        RadarWidgetScheduler.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        // onDisabled fires when the last radar widget is removed, so
        // this work belongs to the radar widget alone — no cross-type
        // check (unlike the shared 60-min work of the other two).
        RadarWidgetScheduler.cancel(context)
    }
}
