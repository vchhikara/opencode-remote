package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.architecture.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    val fileTree by viewModel.fileTree.collectAsState()
    val selectedFileContent by viewModel.selectedFileContent.collectAsState()
    var selectedFile by remember { mutableStateOf<com.example.network.FileNode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Explorer", color = TextPrimary) },
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
        Row(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Surface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                item {
                    fileTree?.let { root -> 
                        FileTreeItem(root, 0, onAction) { selectedFile = it }
                    } ?: Text("Loading...", color = TextSecondary)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .background(TerminalBackground, RoundedCornerShape(8.dp))
            ) {
                if (selectedFile != null && !selectedFile!!.isFolder) {
                    CodeViewer(
                        fileName = selectedFile!!.name,
                        content = selectedFileContent ?: "Loading...",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Select a file to view code",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(node: com.example.network.FileNode, depth: Int, onAction: (AppAction) -> Unit, onFileClick: (com.example.network.FileNode) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)
                .clickable {
                    if (node.isFolder) {
                        expanded = !expanded
                        onAction(FileAction.ToggleFolder(node.name))
                    } else {
                        onFileClick(node)
                        onAction(FileAction.OpenFile(node.name))
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (node.isFolder) Primary else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = node.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        if (node.isFolder && expanded) {
            node.children?.forEach { child ->
                FileTreeItem(child, depth + 1, onAction, onFileClick)
            }
        }
    }
}
