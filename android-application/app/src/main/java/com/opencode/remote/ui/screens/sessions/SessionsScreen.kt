package com.opencode.remote.ui.screens.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.SessionDto
import com.opencode.remote.data.network.RemoteSessionManager

/** Lists opencode sessions known to the bridge's workspace, lets the user resume any
 *  of them or start a fresh one — replaces the previous implicit single-session model
 *  (one session, created lazily, never listed) with real visibility and management
 *  (roadmap §3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(sessionManager: RemoteSessionManager) {
    val sessions by sessionManager.sessions.collectAsState()
    val activeSessionId by sessionManager.activeSessionId.collectAsState()

    LaunchedEffect(Unit) {
        sessionManager.listSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = { sessionManager.newSession() }) {
                        Icon(Icons.Default.Add, contentDescription = "New session")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions yet — start one with the + button",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            isActive = session.id == activeSessionId,
                            onClick = { sessionManager.switchSession(session.id) },
                            onFork = { sessionManager.forkSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(session: SessionDto, isActive: Boolean, onClick: () -> Unit, onFork: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.title ?: session.id, style = MaterialTheme.typography.titleMedium)
                Text(text = session.id, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onFork) {
                Icon(imageVector = Icons.Default.CallSplit, contentDescription = "Fork session")
            }
            if (isActive) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Active session")
            }
        }
    }
}
