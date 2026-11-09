package com.example.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AuraWeatherApp
import com.example.ui.widget.AuraWeatherWidget
import retrofit2.HttpException
import java.io.IOException

/**
 * The 3.2 periodic widget update (60 minutes). Cheap by design: it
 * asks the repository to load weather for the selected place, which is
 * cache-first and only fetches when the cache is stale, then re-renders
 * the widget from the result. No widget placed → the receiver never
 * enqueues this work, so it never runs.
 *
 * loadWeather swallows network failures itself (it keeps the cached
 * state and marks it stale), so a network failure here still ends in
 * success with the best available data — the widget shows the cache,
 * never invented weather.
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AuraWeatherApp
        val container = app.container
        val place = container.weatherRepository.getSelectedPlace() ?: return Result.success()
        return try {
            container.weatherRepository.loadWeather(place, forceRefresh = false)
            AuraWeatherWidget().updateAll(applicationContext)
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
