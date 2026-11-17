package com.example.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ui.widget.RADAR_WIDGET_RETRY_DELAY_MINUTES
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

    /** Unique name for the one-off retry after a bake that showed nothing. */
    const val UNIQUE_RETRY_WORK_NAME = "aura-radar-widget-retry"

    /**
     * One-off retry after a failed placement bake (Item 4
     * refinement): the periodic 15-min tick is too long to leave a
     * fresh widget blank, so a bake that showed nothing schedules
     * one retry in [RADAR_WIDGET_RETRY_DELAY_MINUTES]. The worker
     * maps UNAVAILABLE to Result.retry(), so a retry that also fails
     * backs off and tries again. REPLACE: repeated failures re-arm
     * one retry, never a pile. The periodic worker's own
     * Result.retry() already covers its ticks, so only the
     * receiver's placement path calls this.
     */
    fun enqueueRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<RadarWidgetWorker>()
            .setInitialDelay(RADAR_WIDGET_RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

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
