package com.opencode.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.ui.components.ConnectionBanner
import com.opencode.remote.ui.navigation.NavRoutes
import com.opencode.remote.ui.screens.pairing.PairingScreen
import com.opencode.remote.ui.screens.workspace.WorkspaceListScreen
import com.opencode.remote.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private lateinit var tokenStorage: TokenStorage
    private lateinit var sessionManager: RemoteSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tokenStorage = TokenStorage(this)
        sessionManager = RemoteSessionManager()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val connectionState by sessionManager.connectionState.collectAsState()

                    Column(modifier = Modifier.fillMaxSize()) {
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
                                        navController.navigate(NavRoutes.Main.route)
                                    },
                                    onDisconnect = {
                                        navController.navigate(NavRoutes.Pairing.route) {
                                            popUpTo(NavRoutes.Workspaces.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.Main.route) {
                                com.opencode.remote.ui.screens.main.MainDashboardScreen(
                                    sessionManager = sessionManager,
                                    tokenStorage = tokenStorage,
                                    onNavigateToFileViewer = {
                                        navController.navigate(NavRoutes.FileViewer.route)
                                    },
                                    onDisconnect = {
                                        navController.navigate(NavRoutes.Pairing.route) {
                                            popUpTo(NavRoutes.Main.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.FileViewer.route) {
                                com.opencode.remote.ui.screens.files.FileViewerScreen(
                                    sessionManager = sessionManager,
                                    onBack = {
                                        navController.popBackStack()
                                    }
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
