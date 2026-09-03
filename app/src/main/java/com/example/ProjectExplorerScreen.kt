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
import androidx.compose.ui.unit.dp
import com.example.architecture.*
import com.example.ui.theme.*

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CustomUnstyledTextField

data class FlattenedFileNode(
    val node: com.example.network.FileNode,
    val depth: Int
)

private fun flattenTree(
    node: com.example.network.FileNode,
    depth: Int,
    expandedFolders: Map<String, Boolean>,
    result: MutableList<FlattenedFileNode>
) {
    result.add(FlattenedFileNode(node, depth))
    val isExpanded = expandedFolders[node.path.ifEmpty { node.name }] ?: true
    if (node.isFolder && isExpanded) {
        node.children?.forEach { child ->
            flattenTree(child, depth + 1, expandedFolders, result)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectExplorerScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit, onFileSelected: (com.example.network.FileNode) -> Unit) {
    val fileTree by viewModel.fileTree.collectAsStateWithLifecycle()
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    val flattenedTree = remember(fileTree, expandedFolders.toMap()) {
        val list = mutableListOf<FlattenedFileNode>()
        fileTree?.let { root ->
            flattenTree(root, 0, expandedFolders, list)
        }
        list
    }
    
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
        if (flattenedTree.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading...", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .background(Surface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                items(
                    count = flattenedTree.size,
                    key = { index -> 
                        val fNode = flattenedTree[index].node
                        val uniqueId = fNode.id.ifEmpty { fNode.path }.ifEmpty { fNode.name }
                        uniqueId + "_$index"
                    }
                ) { index ->
                    val item = flattenedTree[index]
                    val node = item.node
                    val depth = item.depth
                    val isExpanded = expandedFolders[node.path.ifEmpty { node.name }] ?: true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)
                            .clickable {
                                if (node.isFolder) {
                                    val key = node.path.ifEmpty { node.name }
                                    expandedFolders[key] = !isExpanded
                                    onAction(FileAction.ToggleFolder(node.name))
                                } else {
                                    onFileSelected(node)
                                    onAction(FileAction.OpenFile(node.path))
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
                }
            }
        }
    }
}
