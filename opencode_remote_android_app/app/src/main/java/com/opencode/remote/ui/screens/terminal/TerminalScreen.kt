package com.opencode.remote.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.RemoteSessionManager
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(sessionManager: RemoteSessionManager) {
    val terminalOutput by sessionManager.terminalOutput.collectAsState(initial = "")
    var outputLines by remember { mutableStateOf(listOf<String>()) }
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Approximate monospace char cell size at the bodySmall font size used
    // below, to derive a cols/rows grid from the actual rendered surface
    // size (Task 4.2.2 — TERMINAL_RESIZE must be tied to a real layout/size
    // callback, not a dead helper). A 0.6x width / 1.3x line-height ratio is
    // the standard monospace approximation; exact glyph metrics aren't worth
    // the complexity here since resize only needs to be roughly right.
    var lastSentCols by remember { mutableStateOf(-1) }
    var lastSentRows by remember { mutableStateOf(-1) }

    LaunchedEffect(terminalOutput) {
        if (terminalOutput.isNotEmpty()) {
            outputLines = outputLines + terminalOutput
            if (outputLines.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(outputLines.lastIndex)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                actions = {
                    IconButton(onClick = { outputLines = emptyList() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Console")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { sizePx ->
                        val widthDp = with(density) { sizePx.width.toDp().value }
                        val heightDp = with(density) { sizePx.height.toDp().value }
                        val fontSizeSp = 12f // matches MaterialTheme.typography.bodySmall used below
                        val cols = max(1, (widthDp / (fontSizeSp * 0.6f)).toInt())
                        val rows = max(1, (heightDp / (fontSizeSp * 1.3f)).toInt())
                        if (cols != lastSentCols || rows != lastSentRows) {
                            lastSentCols = cols
                            lastSentRows = rows
                            sessionManager.resizeTerminal(cols, rows)
                        }
                    },
                color = Color.Black
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(outputLines) { line ->
                        Text(
                            text = line,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter command") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (command.isNotBlank()) {
                        sessionManager.runTerminal(command)
                        command = ""
                    }
                }) {
                    Text("Run")
                }
            }
        }
    }
}
