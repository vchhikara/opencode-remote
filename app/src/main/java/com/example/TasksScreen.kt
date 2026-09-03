package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.architecture.*
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class RunningTask(val id: String, val name: String, val port: String?, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    val tasks by viewModel.runningTasks.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Running Tasks", color = TextPrimary) },
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
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No running tasks", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                            if (task.port != null) {
                                Text("Port: ${task.port}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { onAction(TaskAction.StopTask(task.id)) }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TerminalTextError)
                        }
                    }
                }
            }
        }
    }
}
