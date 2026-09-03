package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.example.ui.components.CustomUnstyledTextField
import com.example.architecture.AppAction
import com.example.architecture.NavigationAction
import com.example.architecture.WorkspaceAction
import com.example.network.Workspace
import com.example.ui.theme.AppBackground
import com.example.ui.theme.Primary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    val searchQueryState = rememberTextFieldState()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    
    val filteredWorkspaces = workspaces.filter { 
         val query = searchQueryState.text.toString()
         it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
                actions = {
                    IconButton(onClick = { onAction(WorkspaceAction.FetchWorkspaces) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Primary)
                    }
                }
            )
        },
        containerColor = AppBackground
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CustomUnstyledTextField(
                state = searchQueryState,
                placeholder = "Search workspaces...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            when (connectionStatus) {
                com.example.network.ConnectionState.CONNECTING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting to OpenCode Server...", color = TextSecondary)
                        }
                    }
                }
                com.example.network.ConnectionState.RECONNECTING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connection lost. Reconnecting...", color = TextSecondary)
                        }
                    }
                }
                com.example.network.ConnectionState.ERROR, com.example.network.ConnectionState.DISCONNECTED -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Refresh, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connection Failed", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text("Host unreachable or authentication failed.", color = TextSecondary)
                        }
                    }
                }
                com.example.network.ConnectionState.CONNECTED -> {
                    if (filteredWorkspaces.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Requesting workspaces...", color = TextSecondary)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredWorkspaces) { workspace ->
                                WorkspaceItem(workspace = workspace, onClick = {
                                    onAction(com.example.architecture.WorkspaceAction.OpenWorkspace(workspace))
                                    onAction(NavigationAction.Navigate("project_chat"))
                                })
                            }
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Initializing...", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceItem(workspace: Workspace, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(workspace.name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(workspace.path, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
