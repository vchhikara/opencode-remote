package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.architecture.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.composeunstyled.UnstyledTextField
import com.composeunstyled.UnstyledButton
import com.composeunstyled.TextInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*
import com.example.utils.AnsiParser
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    val lines by viewModel.terminalLines.collectAsStateWithLifecycle(emptyList())
    val inputTextState = rememberTextFieldState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Terminal", color = TextPrimary) },
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
        var lastTerminalSize by remember { mutableStateOf(0 to 0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(TerminalBackground, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .onSizeChanged { size ->
                    // Approximate character size based on 14sp monospace font
                    val charWidth = 24 // approximate px
                    val charHeight = 40 // approximate px
                    val cols = size.width / charWidth
                    val rows = size.height / charHeight
                    if (lastTerminalSize != (cols to rows)) {
                        lastTerminalSize = cols to rows
                        onAction(TerminalAction.Resize(cols, rows))
                    }
                }
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(lines) { line ->
                    val color = when {
                        line.startsWith(">") -> TerminalTextSuccess
                        line.contains("error", ignoreCase = true) -> TerminalTextError
                        else -> TerminalTextDefault
                    }
                    val annotatedLine = AnsiParser.parse(line, color)
                    Text(
                        text = annotatedLine,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalKey("Ctrl+C") { onAction(TerminalAction.SendCommand("\u0003")) }
                TerminalKey("Ctrl+Z") { onAction(TerminalAction.SendCommand("\u001A")) }
                TerminalKey("Ctrl+D") { onAction(TerminalAction.SendCommand("\u0004")) }
                TerminalKey("Tab") { onAction(TerminalAction.SendCommand("\t")) }
            }

            var isInputFocused by remember { mutableStateOf(false) }
            UnstyledTextField(
                state = inputTextState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .border(
                        width = 1.5.dp,
                        color = if (isInputFocused) TerminalTextSuccess else Border,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onFocusChanged { isInputFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && 
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                            val cmd = inputTextState.text.toString()
                            if (cmd.isNotBlank()) {
                                onAction(TerminalAction.SendCommand(cmd))
                                inputTextState.setTextAndPlaceCursorAtEnd("")
                            }
                            true
                        } else {
                            false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("$", color = TerminalTextSuccess, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    TextInput(
                        placeholder = {
                            Text(
                                text = "Enter command...",
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalKey(text: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
    }
}
