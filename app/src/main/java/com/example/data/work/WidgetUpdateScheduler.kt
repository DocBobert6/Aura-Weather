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
 * Schedules the 3.2 widget update. 60 minutes is deliberately the
 * slowest useful cadence: the widget re-renders from cache on every
 * in-app refresh (the Application collects the weather state), so this
 * worker only exists to keep a placed widget fresh while the app is
 * closed. It never wakes the device every 15 minutes for a widget.
 *
 * Network is required: with no network there is nothing new to show
 * (the cache only changes on a refresh, which needs network), so the
 * run would only re-render identical data. Enqueue is idempotent
 * (KEEP); cancel is what the receiver calls when the last widget is
 * removed.
 */
object WidgetUpdateScheduler {
    const val UNIQUE_WORK_NAME = "aura-widget-update"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            60, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES
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
