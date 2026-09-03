package com.opencode.remote.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.ui.navigation.NavRoutes
import com.opencode.remote.ui.screens.chat.ChatScreen
import com.opencode.remote.ui.screens.files.FileExplorerScreen
import com.opencode.remote.ui.screens.diff.DiffReviewScreen
import com.opencode.remote.ui.screens.terminal.TerminalScreen
import com.opencode.remote.ui.screens.activity.ActivityScreen
import com.opencode.remote.ui.screens.git.GitScreen
import com.opencode.remote.ui.screens.settings.SettingsScreen
import com.opencode.remote.ui.screens.sessions.SessionsScreen
import androidx.compose.material.icons.filled.History

/** One tab = one route + its icon + its label. Replaces three hand-aligned parallel
 *  lists (labels/icons/routes) that had to stay index-synced on every change. */
private data class DashboardTab(val route: NavRoutes.Tab, val label: String, val icon: ImageVector)

private val DASHBOARD_TABS = listOf(
    DashboardTab(NavRoutes.Tab.Chat, "Chat", Icons.Default.Chat),
    DashboardTab(NavRoutes.Tab.Files, "Files", Icons.Default.Folder),
    DashboardTab(NavRoutes.Tab.Diff, "Diff", Icons.Default.Code),
    DashboardTab(NavRoutes.Tab.Terminal, "Terminal", Icons.Default.Terminal),
    DashboardTab(NavRoutes.Tab.Tasks, "Tasks", Icons.Default.List),
    DashboardTab(NavRoutes.Tab.Sessions, "Sessions", Icons.Default.History),
    DashboardTab(NavRoutes.Tab.Git, "Git", Icons.Default.Code), // placeholder icon, no dedicated Git glyph in Material icons
    DashboardTab(NavRoutes.Tab.Settings, "Settings", Icons.Default.Settings),
)

@Composable
fun MainDashboardScreen(
    sessionManager: RemoteSessionManager,
    tokenStorage: TokenStorage,
    onNavigateToFileViewer: () -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    var selectedIndex by remember { mutableStateOf(0) }
    val pendingDiff by sessionManager.pendingDiff.collectAsState()

    Scaffold(
        bottomBar = {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp
            ) {
                DASHBOARD_TABS.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            navController.navigate(tab.route.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        text = { Text(tab.label) },
                        icon = {
                            if (tab.route == NavRoutes.Tab.Diff && pendingDiff != null) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.Tab.Chat.route
            ) {
                composable(NavRoutes.Tab.Chat.route) {
                    ChatScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Files.route) {
                    FileExplorerScreen(
                        sessionManager = sessionManager,
                        onFileSelected = onNavigateToFileViewer
                    )
                }
                composable(NavRoutes.Tab.Diff.route) {
                    DiffReviewScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Terminal.route) {
                    TerminalScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Tasks.route) {
                    ActivityScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Sessions.route) {
                    SessionsScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Git.route) {
                    GitScreen(sessionManager = sessionManager)
                }
                composable(NavRoutes.Tab.Settings.route) {
                    SettingsScreen(
                        sessionManager = sessionManager,
                        tokenStorage = tokenStorage,
                        onDisconnect = onDisconnect
                    )
                }
            }
        }
    }
}
