package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AuraWeatherApp
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

/**
 * The 3.1 periodic worker: one alert-check pass every 15 minutes for the
 * selected place only (v1 scope). Thin by design — the real work is in
 * [AlertChecker], which is unit-tested; this class only resolves the
 * collaborators and maps failures.
 *
 * - Alerts disabled (toggle off) → success immediately, no notification.
 *   Disabling also cancels the work, but a run already in flight must not
 *   notify after the user opted out.
 * - Network failure (IOException / HttpException) → retry with backoff.
 * - Any other failure → failure for this run (the next periodic run still
 *   happens); a programming error must not spin in a retry loop.
 */
class AlertCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AuraWeatherApp
        val container = app.container
        if (!container.settingsManager.alertsEnabledFlow.first()) {
            return Result.success()
        }
        val checker = AlertChecker(
            repository = container.weatherRepository,
            seenAlertDao = container.seenAlertDao,
            onNotify = { alert, placeName ->
                AlertNotifier.notify(applicationContext, alert, placeName)
            }
        )
        return try {
            checker.check()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
