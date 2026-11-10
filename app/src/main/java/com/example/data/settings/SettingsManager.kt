package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        val KEY_TOMORROW_API_KEY = stringPreferencesKey("tomorrow_api_key")
        val KEY_AIRNOW_API_KEY = stringPreferencesKey("airnow_api_key")
        val KEY_RAINBOW_API_KEY = stringPreferencesKey("rainbow_api_key")
        val KEY_MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val KEY_TOMORROW_WORKING = booleanPreferencesKey("tomorrow_working")
        val KEY_RAINBOW_WORKING = booleanPreferencesKey("rainbow_working")
        val KEY_AIRNOW_WORKING = booleanPreferencesKey("airnow_working")
        val KEY_TOMORROW_ATTEMPTED = booleanPreferencesKey("tomorrow_attempted")
        val KEY_AIRNOW_ATTEMPTED = booleanPreferencesKey("airnow_attempted")
        val KEY_RAINBOW_ATTEMPTED = booleanPreferencesKey("rainbow_attempted")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
    }

    val useMetricFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_METRIC] ?: false
    }

    // No default place: empty until the user selects one. (Selection lives
    // in Room; this flow is currently unused.)
    val selectedPlaceIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_PLACE_ID] ?: ""
    }

    val tomorrowApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOMORROW_API_KEY] ?: ""
    }

    // Tomorrow.io "KEY ACTIVE" pill: true only after the client has
    // actually fetched usable Tomorrow.io data with the stored key.
    // A stored key that has never successfully fetched does not show
    // as active. Reset whenever the key is saved/changed/cleared, so
    // a new key must re-verify before the pill appears.
    val tomorrowWorkingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOMORROW_WORKING] ?: false
    }

    val airNowApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AIRNOW_API_KEY] ?: ""
    }

    val rainbowApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_RAINBOW_API_KEY] ?: ""
    }

    // Rainbow "KEY ACTIVE" pill: true only after the client has
    // actually fetched usable nowcast data with the stored key.
    // A stored key that has never successfully fetched does not show
    // as active. Reset whenever the key is saved/changed/cleared, so
    // a new key must re-verify before the pill appears.
    val rainbowWorkingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RAINBOW_WORKING] ?: false
    }

    // AirNow "KEY ACTIVE" pill: same honest pattern — true only after
    // a real successful AirNow fetch with the stored key.
    val airNowWorkingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AIRNOW_WORKING] ?: false
    }

    // "KEY FAILED" pills: true once a fetch has been attempted with
    // the stored key (whether or not it worked). A stored key that
    // was never tried shows no pill; a tried key that never delivered
    // shows KEY FAILED instead of a misleading blank.
    val tomorrowAttemptedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOMORROW_ATTEMPTED] ?: false
    }

    val airNowAttemptedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AIRNOW_ATTEMPTED] ?: false
    }

    val rainbowAttemptedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RAINBOW_ATTEMPTED] ?: false
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

    suspend fun setTomorrowApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOMORROW_API_KEY] = key
            // A changed key must re-verify before it can show active.
            preferences[KEY_TOMORROW_WORKING] = false
            preferences[KEY_TOMORROW_ATTEMPTED] = false
        }
    }

    suspend fun setTomorrowAttempted(attempted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOMORROW_ATTEMPTED] = attempted
        }
    }

    suspend fun setTomorrowWorking(working: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOMORROW_WORKING] = working
        }
    }

    suspend fun setAirNowApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AIRNOW_API_KEY] = key
            // A changed key must re-verify before it can show active.
            preferences[KEY_AIRNOW_WORKING] = false
            preferences[KEY_AIRNOW_ATTEMPTED] = false
        }
    }

    suspend fun setAirNowWorking(working: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AIRNOW_WORKING] = working
        }
    }

    suspend fun setAirNowAttempted(attempted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AIRNOW_ATTEMPTED] = attempted
        }
    }

    suspend fun setRainbowApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAINBOW_API_KEY] = key
            // A changed key must re-verify before it can show active.
            preferences[KEY_RAINBOW_WORKING] = false
            preferences[KEY_RAINBOW_ATTEMPTED] = false
        }
    }

    suspend fun setRainbowAttempted(attempted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAINBOW_ATTEMPTED] = attempted
        }
    }

    suspend fun setRainbowWorking(working: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAINBOW_WORKING] = working
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
}
