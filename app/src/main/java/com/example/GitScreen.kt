package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Merge

import com.example.architecture.*
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git Dashboard", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { onAction(NavigationAction.OpenDrawer) }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { padding ->
        val status by viewModel.gitStatus.collectAsStateWithLifecycle(null)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (status == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading git status...", color = TextSecondary)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Branch: ${status!!.branch}", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onAction(GitAction.Fetch) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Fetch", tint = TextSecondary)
                        }
                        if (status!!.canPull) {
                            IconButton(onClick = { onAction(GitAction.Pull) }) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Pull", tint = Primary)
                            }
                        }
                        if (status!!.canPush) {
                            IconButton(onClick = { onAction(GitAction.Push) }) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Push", tint = TerminalTextSuccess)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Changes", color = TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    
                    items(status!!.modifiedFiles) { file ->
                        FileStatusItem(file, "M", TerminalTextDefault)
                    }
                    
                    items(status!!.addedFiles) { file ->
                        FileStatusItem(file, "A", TerminalTextSuccess)
                    }
                    
                    items(status!!.deletedFiles) { file ->
                        FileStatusItem(file, "D", TerminalTextError)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        var commitMsg by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = commitMsg,
                            onValueChange = { commitMsg = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Commit message", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                if (commitMsg.isNotBlank()) {
                                    onAction(GitAction.Commit(commitMsg))
                                    commitMsg = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text("Commit")
                        }
                    }
                    
                    item {
                        // E7: stash/cherry-pick have no real UX (message entry, commit
                        // picking) built - label not-implemented rather than build it.
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Stash and cherry-pick not implemented", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Git Providers", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        GitProviderItem(
                            name = "GitHub",
                            description = "Sync your projects with GitHub",
                            iconColor = TextPrimary,
                            onClick = { onAction(GitAction.OpenGitProvider("GitHub")) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitProviderItem(name: String, description: String, iconColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = AppBackground, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun FileStatusItem(file: String, status: String, statusColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(file, color = TextPrimary)
    }
}
