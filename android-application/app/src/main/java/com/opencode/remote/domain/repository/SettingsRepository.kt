package com.opencode.remote.domain.repository

import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing persisted app settings. Implementations
 * hide the storage mechanism (DataStore, SharedPreferences, whatever comes
 * next) entirely behind this interface — UI and view-model code never touch
 * a `PreferencesKey` or a `SharedPreferences.Editor` directly.
 */
interface SettingsRepository {
    val settingsFlow: Flow<UserSettings>
    suspend fun setAppearance(appearance: Appearance)
    suspend fun setTrueBlackEnabled(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setDiagnosticTraceEnabled(enabled: Boolean)
}
