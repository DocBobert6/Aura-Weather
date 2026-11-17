package com.example.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the radar widget's 15-minute update (Item 4).
 *
 * 15 minutes is a SCOPED exception to Robert's 2026-11-01
 * nothing-runs-automatically order, approved by him for this widget
 * only (2026-11-16, when the cadence was presented as needing his
 * say-so): the radar image is only useful fresh — a 60-min radar is
 * a stale radar — and the widget re-bakes one small image per run
 * (~8 basemap tiles + one MRMS image or one HRRR frame). It is NOT
 * a change to that order, and the main/strip widgets keep their
 * 60-min cadence ([WidgetUpdateScheduler]).
 *
 * Network is required: with no network there is nothing new to bake
 * (the updater shows the last baked image labelled stale instead).
 * Enqueue is idempotent (KEEP); cancel is what the receiver calls
 * when the last radar widget is removed.
 */
object RadarWidgetScheduler {
    const val UNIQUE_WORK_NAME = "aura-radar-widget-update"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<RadarWidgetWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
