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
 * Schedules the 3.1 alert check. 15 minutes is the platform floor for
 * periodic work, with a 5-minute flex window; network is required
 * (there is nothing to check offline — the cached alerts are already
 * shown in the app). No requiresBatteryNotLow: an alert check is a short
 * network call, and the plan leaves that constraint out unless the user
 * asks for it.
 *
 * Enqueue is idempotent (KEEP): calling it on app start, on place
 * change, and on toggle-on never creates a duplicate schedule. Cancel
 * is what the Settings toggle calls when alerts are turned off.
 * NWS polling cannot do push — nothing here promises "instant" alerts.
 */
object AlertScheduler {
    const val UNIQUE_WORK_NAME = "aura-alert-check"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
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
