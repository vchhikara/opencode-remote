package com.opencode.remote.ui.screens.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.RemoteSessionManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    sessionManager: RemoteSessionManager,
    onBack: () -> Unit
) {
    val fileContent by sessionManager.fileContent.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileContent?.path?.let { File(it).name } ?: "Loading...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        fileContent?.path?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (fileContent == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        } else {
            val lines = fileContent!!.content.split("\n")
            val maxLineLength = lines.size.toString().length
            val scrollState = rememberScrollState()
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .horizontalScroll(scrollState)
            ) {
                itemsIndexed(lines) { index, line ->
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        Text(
                            text = (index + 1).toString().padStart(maxLineLength, ' '),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
