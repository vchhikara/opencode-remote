package com.opencode.remote.ui.screens.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.TaskStatus
import com.opencode.remote.data.network.RemoteSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(sessionManager: RemoteSessionManager) {
    val agentState by sessionManager.agentState.collectAsState()
    val tasksMap by sessionManager.tasks.collectAsState()
    val tasks = tasksMap.values.toList()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Activity & Tasks") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Agent State Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agent State: $agentState",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No running or recent tasks")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Task: ${task.id}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Badge(
                                        containerColor = when (TaskStatus.of(task.status)) {
                                            TaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                                            TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                                            TaskStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(task.status, modifier = Modifier.padding(4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = task.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (TaskStatus.of(task.status) == TaskStatus.RUNNING) {
                                    Button(
                                        onClick = { sessionManager.killTask(task.id) },
                                        modifier = Modifier.align(Alignment.End),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Kill")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
