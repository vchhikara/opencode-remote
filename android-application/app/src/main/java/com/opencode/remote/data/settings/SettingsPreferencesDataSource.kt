package com.opencode.remote.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Wraps a `DataStore<Preferences>` instance and maps raw preference keys to
 * the [UserSettings] domain model. The only place in the app that knows
 * about [Preferences] keys.
 */
class SettingsPreferencesDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val APPEARANCE = stringPreferencesKey("appearance")
        val TRUE_BLACK = booleanPreferencesKey("true_black_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val DIAGNOSTIC_TRACE = booleanPreferencesKey("diagnostic_trace_enabled")
    }

    // A corrupt preferences file throws IOException from dataStore.data; fall back to
    // defaults rather than crashing every collector of this flow.
    val userSettingsStream: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> mapToUserSettings(preferences) }

    suspend fun updateAppearance(appearance: Appearance) {
        dataStore.edit { preferences -> preferences[Keys.APPEARANCE] = appearance.name }
    }

    suspend fun updateTrueBlackEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.TRUE_BLACK] = enabled }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateDiagnosticTraceEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.DIAGNOSTIC_TRACE] = enabled }
    }

    private fun mapToUserSettings(preferences: Preferences): UserSettings {
        val appearanceName = preferences[Keys.APPEARANCE]
        val appearance = appearanceName
            ?.let { name -> Appearance.entries.firstOrNull { it.name == name } }
            ?: Appearance.Dark
        return UserSettings(
            appearance = appearance,
            trueBlackEnabled = preferences[Keys.TRUE_BLACK] ?: false,
            notificationsEnabled = preferences[Keys.NOTIFICATIONS] ?: false,
            diagnosticTraceEnabled = preferences[Keys.DIAGNOSTIC_TRACE] ?: false
        )
    }
}
