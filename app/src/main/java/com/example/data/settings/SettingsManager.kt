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
        val KEY_PALETTE_MODE = stringPreferencesKey("palette_mode") // "monet", "expressive" ("auto" removed 2026-11-08)
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // "auto", "dark", "light"
        val KEY_SELECTED_PLACE_ID = stringPreferencesKey("selected_place_id")
        val KEY_TOMORROW_API_KEY = stringPreferencesKey("tomorrow_api_key")
        val KEY_AIRNOW_API_KEY = stringPreferencesKey("airnow_api_key")
    }

    val useMetricFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_METRIC] ?: false
    }

    val paletteModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        // The automatic "auto" palette was removed (2026-11-08); a stored
        // "auto" now means Material You, and the default is Material You.
        when (val mode = preferences[KEY_PALETTE_MODE] ?: "monet") {
            "auto" -> "monet"
            else -> mode
        }
    }

    // "auto" follows the system dark/light setting; "dark"/"light" force it.
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "auto"
    }

    // No default place: empty until the user selects one. (Selection lives
    // in Room; this flow is currently unused.)
    val selectedPlaceIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_PLACE_ID] ?: ""
    }

    val tomorrowApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOMORROW_API_KEY] ?: ""
    }

    val airNowApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AIRNOW_API_KEY] ?: ""
    }

    suspend fun setUseMetric(useMetric: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_METRIC] = useMetric
        }
    }

    suspend fun setPaletteMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PALETTE_MODE] = mode
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
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
        }
    }

    suspend fun setAirNowApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AIRNOW_API_KEY] = key
        }
    }
}
