package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.architecture.AppAction
import com.example.architecture.NavigationAction
import com.example.architecture.SettingsAction
import com.example.data.DataStoreManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.ConnectionState
import com.example.network.KtorClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val dataStoreManager = DataStoreManager(this)
        
        setContent {
            com.example.ui.theme.MyApplicationTheme {
                val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
                
                LaunchedEffect(Unit) {
                    val lastIp = dataStoreManager.lastIpFlow.first()
                    val apiKey = dataStoreManager.apiKeyFlow.first()
                    if (apiKey != null) {
                        KtorClient.updateApiKey(apiKey)
                    }
                    if (lastIp != null) {
                        viewModel.dispatch(com.example.architecture.PairingAction.ConnectManually(lastIp))
                    }
                }
                
                LaunchedEffect(connectionStatus) {
                    if (connectionStatus == ConnectionState.CONNECTED) {
                        viewModel.dispatch(NavigationAction.Navigate("workspaces"))
                    } else if (connectionStatus == ConnectionState.DISCONNECTED || connectionStatus == ConnectionState.ERROR) {
                        viewModel.dispatch(NavigationAction.Navigate("auth"))
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.AppBackground
                ) {
                    OpenCodeApp(viewModel, dataStoreManager)
                }
            }
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun OpenCodeDrawerContent(currentRoute: String?, onAction: (AppAction) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = com.example.ui.theme.AppBackground,
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 32.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Vipul's OpenCode", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Connected to Remote", color = com.example.ui.theme.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("PROJECT DASHBOARD", color = com.example.ui.theme.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            DrawerItem(icon = Icons.Default.Chat, text = "AI Chat", selected = currentRoute == "project_chat", onClick = { onAction(NavigationAction.Navigate("project_chat")) })
            DrawerItem(icon = Icons.Default.Folder, text = "Project Explorer", selected = currentRoute == "repository", onClick = { onAction(NavigationAction.Navigate("repository")) })
            DrawerItem(icon = Icons.Default.CompareArrows, text = "Diff Review", selected = currentRoute == "diff_review", onClick = { onAction(NavigationAction.Navigate("diff_review")) })
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("EXECUTION", color = com.example.ui.theme.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            DrawerItem(icon = Icons.Default.Terminal, text = "Remote Terminal", selected = currentRoute == "terminal", onClick = { onAction(NavigationAction.Navigate("terminal")) })
            DrawerItem(icon = Icons.Default.FormatListBulleted, text = "Running Tasks", selected = currentRoute == "tasks", onClick = { onAction(NavigationAction.Navigate("tasks")) })
            DrawerItem(icon = Icons.Default.AccountTree, text = "Git Dashboard", selected = currentRoute == "git", onClick = { onAction(NavigationAction.Navigate("git")) })
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("OBSERVABILITY", color = com.example.ui.theme.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            DrawerItem(icon = Icons.Default.Insights, text = "Activity Monitor", selected = currentRoute == "activity", onClick = { onAction(NavigationAction.Navigate("activity")) })
            DrawerItem(icon = Icons.Default.ReceiptLong, text = "Logs", selected = currentRoute == "logs", onClick = { onAction(NavigationAction.Navigate("logs")) })
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onAction(SettingsAction.OpenSettings) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.SurfaceVariant)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
fun OpenCodeApp(viewModel: MainViewModel, dataStoreManager: DataStoreManager, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val onAction: (AppAction) -> Unit = { action -> 
        if (action is com.example.architecture.PairingAction.ConnectManually) {
            scope.launch {
                dataStoreManager.saveLastIp(action.ip)
                val currentKey = KtorClient.getApiKey()
                if (currentKey.isNotBlank()) {
                    dataStoreManager.saveApiKey(currentKey)
                }
            }
        }
        viewModel.dispatch(action) 
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.Navigate -> {
                    navController.navigate(event.route) {
                        if (event.route == "workspaces") {
                            popUpTo("auth") { inclusive = true }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                is UiEvent.NavigateBack -> {
                    navController.popBackStack()
                }
                is UiEvent.OpenDrawer -> {
                    drawerState.open()
                }
                is UiEvent.CloseDrawer -> {
                    drawerState.close()
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { 
            OpenCodeDrawerContent(
                currentRoute = currentRoute,
                onAction = { action ->
                    if (action is NavigationAction.Navigate) {
                        scope.launch { drawerState.close() }
                    }
                    onAction(action)
                }
            ) 
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "auth",
                modifier = modifier.padding(innerPadding),
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)) }
            ) {
                composable("auth") { AuthScreen(onAction = onAction) }
                composable("devices") { DevicesScreen(onAction = onAction) }
                composable("workspaces") { WorkspacesScreen(viewModel = viewModel, onAction = onAction) }
                composable("project_chat") { WorkspaceHostScreen(viewModel = viewModel, onAction = onAction) }
                composable("repository") { WorkspaceHostScreen(viewModel = viewModel, onAction = onAction) }
                composable("diff_review") { DiffReviewScreen(viewModel = viewModel, onAction = onAction) }
                composable("terminal") { TerminalScreen(viewModel = viewModel, onAction = onAction) }
                composable("tasks") { TasksScreen(viewModel = viewModel, onAction = onAction) }
                composable("git") { GitScreen(viewModel = viewModel, onAction = onAction) }
                composable("activity") { ActivityScreen(viewModel = viewModel, onAction = onAction) }
                composable("logs") { LogsScreen(onAction = onAction) }
                composable("settings") { SettingsScreen(onAction = onAction) }
                composable("preview") { PreviewScreen(onAction = onAction) }
            }
        }
    }
}
