package com.example

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.example.data.work.AlertNotifier
import com.example.data.work.AlertScheduler
import com.example.ui.widget.AuraWeatherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuraWeatherApp : Application() {

    lateinit var container: AppContainer
        private set

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
            if (container.settingsManager.alertsEnabledFlow.first()) {
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
                    runCatching { AuraWeatherWidget().updateAll(this@AuraWeatherApp) }
                }
            }
        }
    }
}
