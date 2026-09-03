package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.architecture.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.composeunstyled.UnstyledTextField
import com.composeunstyled.TextInput
import androidx.compose.ui.focus.onFocusChanged
import com.example.ui.theme.*
import androidx.compose.ui.graphics.Color

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectChatScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    val promptTextState = rememberTextFieldState()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            if (spokenText.isNotBlank()) {
                val currentText = promptTextState.text.toString()
                val newText = if (currentText.isEmpty()) spokenText else "$currentText $spokenText"
                promptTextState.setTextAndPlaceCursorAtEnd(newText)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: "attachment"
            val fileName = path.substringAfterLast("/")
            val currentText = promptTextState.text.toString()
            val newText = if (currentText.isEmpty()) "[Attached: $fileName]" else "$currentText [Attached: $fileName]"
            promptTextState.setTextAndPlaceCursorAtEnd(newText)
        }
    }

    val listState = rememberLazyListState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(ChatMode.Build) }

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle(emptyList())
    val agentState by viewModel.agentState.collectAsStateWithLifecycle("Idle")
    val activeWorkspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("V", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(activeWorkspace?.name ?: "No Workspace Connected", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clip(CircleShape)
                            .clickable { onAction(NavigationAction.OpenDrawer) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = com.example.ui.theme.AppBackground)
            )
        },
        containerColor = com.example.ui.theme.AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message, onAction)
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (agentState != "Idle") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(agentState, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground)
                    .padding(16.dp)
            ) {
                var isFocused by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(24.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (isFocused) Primary else Border,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(8.dp)
                ) {
                    UnstyledTextField(
                        state = promptTextState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        TextInput(
                            placeholder = {
                                Text(
                                    text = "What do you want to build?",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SurfaceVariant, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { 
                                        onAction(ChatAction.OpenMoreMenu)
                                        showMoreMenu = true 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = TextSecondary)
                                
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    containerColor = SurfaceVariant
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(ChatAction.OpenShareMenu)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Publish", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Publish, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(ChatAction.PublishConversation)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(SettingsAction.OpenSettings)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Code", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(ChatAction.OpenCode)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Files", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(ChatAction.OpenFiles)
                                        }
                                    )
                                    HorizontalDivider(color = Border)
                                    DropdownMenuItem(
                                        text = { Text("History", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary) },
                                        onClick = { 
                                            showMoreMenu = false 
                                            onAction(ChatAction.OpenHistory)
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .background(SurfaceVariant, RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { showBuildMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (chatMode == ChatMode.Build) "Build" else "Plan", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                
                                DropdownMenu(
                                    expanded = showBuildMenu,
                                    onDismissRequest = { showBuildMenu = false },
                                    containerColor = SurfaceVariant
                                ) {
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text("Build", color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text("Make changes directly", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = { 
                                            showBuildMenu = false
                                            chatMode = ChatMode.Build
                                            onAction(ChatAction.SetChatMode(ChatMode.Build))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text("Plan", color = TextPrimary)
                                                Text("Discuss before building", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        leadingIcon = { Spacer(modifier = Modifier.size(16.dp)) },
                                        onClick = { 
                                            showBuildMenu = false 
                                            chatMode = ChatMode.Plan
                                            onAction(ChatAction.SetChatMode(ChatMode.Plan))
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SurfaceVariant, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = TextSecondary)
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SurfaceVariant, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { 
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        }
                                        speechRecognizerLauncher.launch(intent)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Mic", tint = TextSecondary)
                            }

                            val promptStr = promptTextState.text.toString()
                            if (promptStr.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Primary, CircleShape)
                                        .clip(CircleShape)
                                        .clickable { 
                                            onAction(ChatAction.SendPrompt(promptStr))
                                            promptTextState.setTextAndPlaceCursorAtEnd("")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: com.example.network.ChatMessage, onAction: (AppAction) -> Unit) {
    if (message.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .padding(16.dp)
            ) {
                MarkdownText(message.text)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                if (message.actionDescription != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.actionIcon != null) {
                                    Icon(message.actionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(message.actionDescription, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            
                            if (message.hasDetails) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onAction(ChatAction.ShowDetails(message.actionDescription)) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text("Details")
                                    }
                                    
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                MarkdownText(message.text, modifier = Modifier.padding(start = 4.dp))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAction(ChatAction.UndoLastOperation) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    OutlinedButton(
                        onClick = { onAction(ChatAction.AcceptDiff) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
