package com.opencode.remote.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.domain.model.UserSettings
import com.opencode.remote.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * Runs against a real, temp-file-backed DataStore rather than a mock — exercises the
 * actual serialization/IO path, including the corrupted-file fallback in
 * [SettingsPreferencesDataSource.userSettingsStream].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun createTestSettingsRepository(
        testScope: TestScope,
        testFile: File
    ): SettingsRepository {
        val testDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            // backgroundScope, not testScope itself — DataStore's internal disk-sync
            // coroutines are long-lived, and runTest would otherwise wait for them
            // forever and fail with UncompletedCoroutinesError.
            scope = testScope.backgroundScope,
            produceFile = { testFile }
        )
        val dataSource = SettingsPreferencesDataSource(testDataStore)
        return SettingsRepositoryImpl(dataSource)
    }

    @Test
    fun `settingsFlow emits default appearance when storage file is freshly created`() = runTest {
        val testFile = tempFolder.newFile("test_settings_defaults.preferences_pb")
        val repository = createTestSettingsRepository(this, testFile)

        repository.settingsFlow.test {
            assertEquals(UserSettings(appearance = Appearance.Dark), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAppearance persists and updates the flow reactively`() = runTest {
        val testFile = tempFolder.newFile("test_settings_appearance.preferences_pb")
        val repository = createTestSettingsRepository(this, testFile)

        repository.settingsFlow.test {
            assertEquals(Appearance.Dark, awaitItem().appearance)

            repository.setAppearance(Appearance.Light)
            assertEquals(Appearance.Light, awaitItem().appearance)

            repository.setAppearance(Appearance.System)
            assertEquals(Appearance.System, awaitItem().appearance)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTrueBlackEnabled, setNotificationsEnabled and setDiagnosticTraceEnabled persist independently`() = runTest {
        val testFile = tempFolder.newFile("test_settings_flags.preferences_pb")
        val repository = createTestSettingsRepository(this, testFile)

        repository.settingsFlow.test {
            val initial = awaitItem()
            assertEquals(false, initial.trueBlackEnabled)
            assertEquals(false, initial.notificationsEnabled)
            assertEquals(false, initial.diagnosticTraceEnabled)

            repository.setTrueBlackEnabled(true)
            assertEquals(true, awaitItem().trueBlackEnabled)

            repository.setNotificationsEnabled(true)
            val afterNotifications = awaitItem()
            assertEquals(true, afterNotifications.notificationsEnabled)
            assertEquals(true, afterNotifications.trueBlackEnabled) // unrelated flag untouched

            repository.setDiagnosticTraceEnabled(true)
            val afterTrace = awaitItem()
            assertEquals(true, afterTrace.diagnosticTraceEnabled)
            assertEquals(true, afterTrace.trueBlackEnabled)
            assertEquals(true, afterTrace.notificationsEnabled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `corrupted storage file falls back to default settings without crashing the stream`() = runTest {
        val testFile = tempFolder.newFile("corrupted_settings.preferences_pb")

        // Intentionally malformed bytes — not a valid serialized Preferences proto.
        FileOutputStream(testFile).use { output ->
            output.write(byteArrayOf(0x01, 0x02, 0x7F, 0x00, 0xFF.toByte()))
        }

        val repository = createTestSettingsRepository(this, testFile)

        repository.settingsFlow.test {
            // Verifies the .catch block in SettingsPreferencesDataSource intercepts the
            // resulting IOException and emits defaults instead of propagating the crash.
            assertEquals(UserSettings(appearance = Appearance.Dark), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
