package com.opencode.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.settings.SettingsPreferencesDataSource
import com.opencode.remote.data.settings.SettingsRepositoryImpl
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.domain.repository.SettingsRepository
import com.opencode.remote.notification.BridgeNotifier
import com.opencode.remote.ui.components.ConnectionBanner
import com.opencode.remote.ui.navigation.NavRoutes
import com.opencode.remote.ui.screens.files.FileViewerScreen
import com.opencode.remote.ui.screens.main.MainShell
import com.opencode.remote.ui.screens.pairing.PairingScreen
import com.opencode.remote.ui.screens.workspace.WorkspaceListScreen
import com.opencode.remote.ui.theme.AppTheme
import com.opencode.remote.ui.theme.animatedBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private lateinit var tokenStorage: TokenStorage
    private lateinit var sessionManager: RemoteSessionManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; every screen below respects WindowInsets
        // (status bar, cutout, navigation bar and IME) via the safeDrawing padding.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        tokenStorage = TokenStorage(this)
        // One session manager for the whole app, shared by every destination.
        sessionManager = RemoteSessionManager()

        // One DataStore-backed settings repository for the whole app, built once here
        // exactly like sessionManager/tokenStorage above (no DI framework in this app).
        val settingsDataStore = PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.preferencesDataStoreFile("user_settings.preferences_pb") }
        )
        settingsRepository = SettingsRepositoryImpl(SettingsPreferencesDataSource(settingsDataStore))
        // One-time synchronous read of the small preferences file at cold start, so the
        // first frame renders the saved theme instead of flashing the default — the same
        // trade-off the old synchronous SharedPreferences-backed AppearanceStore made.
        val initialSettings = runBlocking { settingsRepository.settingsFlow.first() }
        val bridgeNotifier = BridgeNotifier(applicationContext)

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = initialSettings)
            val appearance = settings.appearance
            sessionManager.traceEnabled = settings.diagnosticTraceEnabled

            // NOTIFY frames only turn into a system notification while the toggle is on —
            // re-checked per event (not captured once) so a mid-session toggle takes effect
            // immediately without needing a reconnect.
            LaunchedEffect(Unit) {
                sessionManager.notifyEvents.collect { payload ->
                    if (settingsRepository.settingsFlow.first().notificationsEnabled) {
                        bridgeNotifier.notify(payload)
                    }
                }
            }

            AppTheme(appearance = appearance, trueBlack = settings.trueBlackEnabled) {
                val background by animatedBackground()
                val navController = rememberNavController()
                val connectionState by sessionManager.connectionState.collectAsStateWithLifecycle()

                Box(Modifier.fillMaxSize().background(background)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        ConnectionBanner(connectionState = connectionState)

                        NavHost(
                            navController = navController,
                            startDestination = NavRoutes.Pairing.route,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(NavRoutes.Pairing.route) {
                                PairingScreen(
                                    sessionManager = sessionManager,
                                    tokenStorage = tokenStorage,
                                    onNavigateToWorkspaces = {
                                        navController.navigate(NavRoutes.Workspaces.route) {
                                            popUpTo(NavRoutes.Pairing.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.Workspaces.route) {
                                WorkspaceListScreen(
                                    sessionManager = sessionManager,
                                    onWorkspaceSelected = {
                                        navController.navigate(NavRoutes.Main.route) { launchSingleTop = true }
                                    },
                                    onDisconnect = {
                                        navController.navigate(NavRoutes.Pairing.route) {
                                            popUpTo(NavRoutes.Workspaces.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.Main.route) {
                                MainShell(
                                    sessionManager = sessionManager,
                                    tokenStorage = tokenStorage,
                                    settingsRepository = settingsRepository,
                                    appearance = appearance,
                                    onAppearanceChange = { next ->
                                        lifecycleScope.launch { settingsRepository.setAppearance(next) }
                                    },
                                    onOpenFileViewer = {
                                        navController.navigate(NavRoutes.FileViewer.route)
                                    },
                                    onDisconnect = {
                                        // Clear the whole stack: after a disconnect, back must
                                        // not return to a stale workspace list or shell.
                                        navController.navigate(NavRoutes.Pairing.route) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.FileViewer.route) {
                                FileViewerScreen(
                                    sessionManager = sessionManager,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.disconnect()
    }
}
