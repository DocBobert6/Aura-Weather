package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_USE_METRIC = booleanPreferencesKey("use_metric_units")
        val KEY_SELECTED_PLACE_ID = stringPreferencesKey("selected_place_id")
        val KEY_MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_RAIN_ALERTS_ENABLED = booleanPreferencesKey("rain_alerts_enabled")
        val KEY_RAIN_ALERT_ACTIVE_SINCE = longPreferencesKey("rain_alert_active_since")
    }

    val useMetricFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_METRIC] ?: false
    }

    // No default place: empty until the user selects one. (Selection lives
    // in Room; this flow is currently unused.)
    val selectedPlaceIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_PLACE_ID] ?: ""
    }

    // Background music (Home toggle): off by default, remembered across launches.
    val musicEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MUSIC_ENABLED] ?: false
    }

    // 3.1 alert notifications: off by default — the user opts in via the
    // Settings toggle, which is also the moment the notification
    // permission is requested (never on first launch).
    val alertsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALERTS_ENABLED] ?: false
    }

    // Build C rain-starting alerts: off by default (opt-in, like NWS
    // alerts). The worker only runs the rain check when this is on.
    val rainAlertsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RAIN_ALERTS_ENABLED] ?: false
    }

    // Build C dedupe: epoch millis of the last rain notification, or 0
    // when no rain event is in progress. One notification per rain
    // event — the flag, not the (drifting) forecast start time, is the
    // dedupe key. Cleared when a check finds no rain in 60 minutes.
    val rainAlertActiveSinceFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_RAIN_ALERT_ACTIVE_SINCE] ?: 0L
    }

    suspend fun setUseMetric(useMetric: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_METRIC] = useMetric
        }
    }

    suspend fun setSelectedPlaceId(placeId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_PLACE_ID] = placeId
        }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MUSIC_ENABLED] = enabled
        }
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setRainAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAIN_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setRainAlertActiveSince(epochMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAIN_ALERT_ACTIVE_SINCE] = epochMs
        }
    }
}
