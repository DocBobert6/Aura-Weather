package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AuraWeatherApp
import com.example.data.work.RainStartDecision
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
        // Build C: the worker runs when EITHER toggle is on — the rain
        // check reuses this same 15-minute pass, not a second schedule.
        val alertsOn = container.settingsManager.alertsEnabledFlow.first()
        val rainOn = container.settingsManager.rainAlertsEnabledFlow.first()
        if (!alertsOn && !rainOn) {
            return Result.success()
        }
        return try {
            if (alertsOn) {
                val checker = AlertChecker(
                    repository = container.weatherRepository,
                    seenAlertDao = container.seenAlertDao,
                    onNotify = { alert, placeName ->
                        AlertNotifier.notify(applicationContext, alert, placeName)
                    }
                )
                checker.check()
            }
            if (rainOn) {
                // A rain-check failure must not fail the alert check that
                // already ran (or the reverse order on the next run) —
                // the periodic pass runs again in 15 minutes either way.
                runCatching { checkRain(container) }
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /**
     * Build C rain check, in the same worker pass as the NWS alerts.
     * Loads the selected place's weather (cached if < 15 min old, else
     * fresh) and runs the pure rain-start decision on its minute-cast.
     * Stale data never notifies — a stale minute-cast could falsely
     * predict rain — and never clears the flag either (the event may
     * still be in progress; the next run decides).
     */
    private suspend fun checkRain(container: com.example.AppContainer) {
        val settings = container.settingsManager
        val repository = container.weatherRepository
        val place = repository.getSelectedPlace() ?: return
        repository.loadWeather(place)
        val state = repository.weatherState.value
            ?.takeIf { it.place.id == place.id && !it.isStale }
            ?: return
        val activeSince = settings.rainAlertActiveSinceFlow.first()
        when (val decision = decideRainStart(state.minutely60m, java.time.Instant.now(), activeSince)) {
            RainStartDecision.NoRain -> {
                if (activeSince != 0L) settings.setRainAlertActiveSince(0L)
            }
            RainStartDecision.AlreadyRaining,
            RainStartDecision.AlreadyNotified -> {
                // Rain is here, or this event was already notified.
            }
            is RainStartDecision.Notify -> {
                AlertNotifier.notifyRain(
                    applicationContext,
                    decision.minutesUntil,
                    decision.intensity,
                    place.name
                )
                settings.setRainAlertActiveSince(java.time.Instant.now().toEpochMilli())
            }
        }
    }
}
