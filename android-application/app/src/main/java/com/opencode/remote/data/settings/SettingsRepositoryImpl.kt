package com.opencode.remote.data.settings

import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.domain.model.UserSettings
import com.opencode.remote.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Manually constructed (no DI framework in this app) — one instance built
 *  once in MainActivity and shared, same pattern as RemoteSessionManager. */
class SettingsRepositoryImpl(
    private val localDataSource: SettingsPreferencesDataSource
) : SettingsRepository {

    override val settingsFlow: Flow<UserSettings> = localDataSource.userSettingsStream

    override suspend fun setAppearance(appearance: Appearance) =
        localDataSource.updateAppearance(appearance)

    override suspend fun setTrueBlackEnabled(enabled: Boolean) =
        localDataSource.updateTrueBlackEnabled(enabled)

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        localDataSource.updateNotificationsEnabled(enabled)

    override suspend fun setDiagnosticTraceEnabled(enabled: Boolean) =
        localDataSource.updateDiagnosticTraceEnabled(enabled)
}
