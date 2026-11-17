package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ui.widget.RadarWidgetUpdater
import retrofit2.HttpException
import java.io.IOException

/**
 * The radar widget's 15-minute update (Item 4). It bakes a fresh
 * radar image and re-renders the widget from it. The updater
 * contains the failure paths (a failed bake shows the last baked
 * image labelled stale), so this worker only maps outcomes to
 * WorkManager results: UNAVAILABLE (no bake and no cache — nothing
 * was ever shown) retries with backoff, everything else is success.
 * No radar widget placed → the receiver never enqueues this work,
 * so it never runs.
 */
class RadarWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            when (RadarWidgetUpdater.updateRadar(applicationContext)) {
                RadarWidgetUpdater.Outcome.UNAVAILABLE -> Result.retry()
                else -> Result.success()
            }
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
