package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.core.HttpClients
import com.example.data.work.AlertNotifier
import com.example.data.work.AlertScheduler
import com.example.ui.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuraWeatherApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    // AUDIT6 section 9 Fix 1: dedicated tile client. The app had no
    // custom ImageLoader, so Coil used a plain OkHttpClient with the
    // default dispatcher (5 requests per host) and a 77-tile frame took
    // ~16 s to arrive. This singleton is the display lane (16/host);
    // every AsyncImage on screen uses it. Coil picks this up because
    // the Application implements ImageLoaderFactory (Coil 2.7.0).
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(HttpClients.tileDisplayClient())
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // No seeded places: on first launch the user picks a place (or uses
        // their current location) and the app loads weather for that place.

        // 3.1: the alert channel exists from the first launch (creating it
        // is not asking for permission — the runtime prompt only appears
        // when the user turns alerts on in Settings).
        AlertNotifier.createChannel(this)

        // 3.1: re-establish the periodic alert check on app start, when
        // alerts are enabled. KEEP makes this idempotent with the
        // ViewModel's enqueue on place change / toggle-on.
        applicationScope.launch {
            val alertsOn = container.settingsManager.alertsEnabledFlow.first()
            val rainOn = container.settingsManager.rainAlertsEnabledFlow.first()
            if (alertsOn || rainOn) {
                AlertScheduler.enqueue(this@AuraWeatherApp)
            }
        }

        // 3.2: keep a placed home-screen widget in step with the app.
        // Every time the weather state changes (initial load, refresh,
        // place change) re-render the widget from the fresh state/cache.
        // updateAll is a no-op when no widget is placed, and a widget
        // failure must never crash the app, so it is guarded.
        applicationScope.launch {
            container.weatherRepository.weatherState.collect { state ->
                if (state != null) {
                    runCatching { WidgetUpdater.updateAll(this@AuraWeatherApp) }
                }
            }
        }
    }
}
