package com.opencode.remote.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.FileNodeDto
import com.opencode.remote.data.network.RemoteSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    sessionManager: RemoteSessionManager,
    onFileSelected: () -> Unit
) {
    val fileTree by sessionManager.fileTree.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                actions = {
                    IconButton(onClick = { sessionManager.fetchFileTree() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (fileTree.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No files found in workspace")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(fileTree, key = { it.path }) { node ->
                    FileNodeItem(
                        node = node,
                        level = 0,
                        sessionManager = sessionManager,
                        onFileSelected = onFileSelected
                    )
                }
            }
        }
    }
}

@Composable
fun FileNodeItem(
    node: FileNodeDto,
    level: Int,
    sessionManager: RemoteSessionManager,
    onFileSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        expanded = !expanded
                    } else {
                        sessionManager.fetchFile(node.path)
                        onFileSelected()
                    }
                }
                .padding(start = (level * 16 + 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (expanded && node.isDirectory && node.children != null) {
            node.children.forEach { child ->
                FileNodeItem(
                    node = child,
                    level = level + 1,
                    sessionManager = sessionManager,
                    onFileSelected = onFileSelected
                )
            }
        }
    }
}
