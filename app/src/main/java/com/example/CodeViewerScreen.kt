package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.architecture.*
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit, selectedFile: com.example.network.FileNode?) {
    val selectedFileContent by viewModel.selectedFileContent.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedFile?.name ?: "Code Viewer", color = TextPrimary) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(TerminalBackground, RoundedCornerShape(8.dp))
        ) {
            if (selectedFile != null && !selectedFile.isFolder) {
                CodeViewer(
                    fileName = selectedFile.name,
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
