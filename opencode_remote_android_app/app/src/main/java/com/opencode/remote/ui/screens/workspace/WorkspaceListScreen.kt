package com.opencode.remote.ui.screens.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.WorkspaceDto
import com.opencode.remote.data.network.RemoteSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceListScreen(
    sessionManager: RemoteSessionManager,
    onWorkspaceSelected: () -> Unit,
    onDisconnect: () -> Unit
) {
    val workspaces by sessionManager.workspaces.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        sessionManager.fetchWorkspaces()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add workspace") },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    placeholder = { Text("/path/to/project") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPath.isNotBlank()) sessionManager.addWorkspace(newPath.trim())
                    newPath = ""
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add workspace")
                    }
                    Button(onClick = {
                        sessionManager.disconnect()
                        onDisconnect()
                    }) {
                        Text("Disconnect")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (workspaces.isEmpty()) {
                Text(
                    text = "No workspaces available",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workspaces) { workspace ->
                        WorkspaceItem(
                            workspace = workspace,
                            onClick = {
                                sessionManager.openWorkspace(workspace.path)
                                onWorkspaceSelected()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceItem(workspace: WorkspaceDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = workspace.name, style = MaterialTheme.typography.titleMedium)
                Text(text = workspace.path, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
