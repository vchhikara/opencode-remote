package com.opencode.remote.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.storage.TokenStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: RemoteSessionManager,
    tokenStorage: TokenStorage,
    onDisconnect: () -> Unit
) {
    val activeWorkspace by sessionManager.activeWorkspace.collectAsState()
    val credentials = tokenStorage.getLastCredentials()
    var showForgetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connection Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Host: ${credentials?.host ?: "Unknown"}")
                    Text("Port: ${credentials?.port ?: "Unknown"}")
                    Text("Device Name: ${credentials?.deviceName ?: "Unknown"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Active Workspace:", fontWeight = FontWeight.Bold)
                    Text(activeWorkspace ?: "None selected")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    sessionManager.disconnect()
                    onDisconnect()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Disconnect")
            }

            Button(
                onClick = { showForgetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Forget Credentials")
            }
        }
    }

    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("Forget Credentials?") },
            text = { Text("Are you sure you want to clear your saved credentials? You will need to enter them again to connect.") },
            confirmButton = {
                Button(
                    onClick = {
                        tokenStorage.clear()
                        showForgetDialog = false
                        sessionManager.disconnect()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
