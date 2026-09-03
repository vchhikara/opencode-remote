package com.opencode.remote.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.ChatRole
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.AgentStateBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionManager: RemoteSessionManager
) {
    val chatMessages by sessionManager.chatMessages.collectAsState()
    val agentState by sessionManager.agentState.collectAsState()
    val streamingMessage by sessionManager.streamingMessage.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }

    // +1 for the streaming bubble, which isn't in chatMessages until the turn finishes.
    val itemCount = chatMessages.size + if (streamingMessage != null) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                actions = {
                    AgentStateBadge(agentState = agentState, modifier = Modifier.padding(end = 16.dp))
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the agent...") },
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            sessionManager.sendPrompt(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                val isUser = message.role == ChatRole.User
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (streamingMessage != null) {
                item(key = "streaming") {
                    StreamingBubble(streamingMessage!!)
                }
            }
        }
    }
}

/** Renders the in-progress assistant turn: accumulated streamed text, and which tool
 *  (if any) is currently running — replaces the old static "Thinking..." placeholder
 *  once any streamed content has arrived. */
@Composable
private fun StreamingBubble(streaming: com.opencode.remote.data.dto.StreamingMessageDto) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Column {
                if (streaming.text.isNotEmpty()) {
                    Text(
                        text = streaming.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val tool = streaming.runningTool
                if (tool != null) {
                    Text(
                        text = "Running `$tool`...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else if (streaming.text.isEmpty()) {
                    Text(
                        text = "Thinking...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
