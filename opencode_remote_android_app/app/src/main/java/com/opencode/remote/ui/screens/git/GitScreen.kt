package com.opencode.remote.ui.screens.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.RemoteSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(sessionManager: RemoteSessionManager) {
    val gitStatus by sessionManager.gitStatus.collectAsState()
    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Git") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCommitDialog = true }) {
                Text("Commit", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val status = gitStatus
            if (status == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Git Status Available")
                }
            } else {
                // Top section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Branch: ${status.branch}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Badge(
                                containerColor = if (status.isClean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ) {
                                Text(if (status.isClean) "Clean" else "Dirty", modifier = Modifier.padding(4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = { sessionManager.runGit("pull") }) { Text("Pull") }
                            Button(onClick = { sessionManager.runGit("push") }) { Text("Push") }
                            Button(onClick = { sessionManager.runGit("fetch") }) { Text("Fetch") }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (status.modifiedFiles.isNotEmpty()) {
                        item {
                            Text("Modified", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(status.modifiedFiles) { file ->
                            Text(file, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        }
                    }

                    if (status.addedFiles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Added", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(status.addedFiles) { file ->
                            Text(file, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        }
                    }

                    if (status.deletedFiles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Deleted", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(status.deletedFiles) { file ->
                            Text(file, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit Changes") },
            text = {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (commitMessage.isNotBlank()) {
                            sessionManager.runGit("commit -m \"$commitMessage\"")
                            commitMessage = ""
                            showCommitDialog = false
                        }
                    }
                ) {
                    Text("Commit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
