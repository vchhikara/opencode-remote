package com.opencode.remote.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.terminal.TerminalLineKind
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.OcChip
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType
import kotlin.math.max

/**
 * Keys the bridge's command-string protocol can support honestly. TERMINAL carries a
 * whole command string and TERMINAL_RESIZE the grid size — there is no raw-keystroke
 * frame — so Esc, Ctrl, Tab-completion and ^C from the prototype are omitted rather
 * than faked. These keys only edit or recall the command line on the phone.
 */
private enum class TermKey(val label: String, val description: String) {
    HistoryUp("↑", "Previous command"),
    HistoryDown("↓", "Next command"),
    Left("←", "Move cursor left"),
    Right("→", "Move cursor right"),
    Pipe("|", "Insert pipe"),
    Slash("/", "Insert slash"),
    Tilde("~", "Insert tilde"),
    Dash("-", "Insert dash")
}

@Composable
fun TerminalScreen(sessionManager: RemoteSessionManager) {
    val c = Oc.colors
    val lines by sessionManager.terminalLines.collectAsStateWithLifecycle()
    val history by sessionManager.terminalHistory.collectAsStateWithLifecycle()
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var grid by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val connected = connection is ConnectionState.Connected

    // Real glyph metrics for the terminal face, so cols/rows match what's rendered.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cell = remember(measurer, density) {
        val sample = measurer.measure("MMMMMMMMMM", OcType.terminal)
        val w = sample.size.width / 10f
        val h = with(density) { OcType.terminal.lineHeight.toPx() }
        w.coerceAtLeast(1f) to h.coerceAtLeast(1f)
    }
    // A reconnect may bring up a fresh PTY: re-announce the size we're drawing.
    LaunchedEffect(connected) {
        if (connected) grid?.let { (cols, rows) -> sessionManager.resizeTerminal(cols, rows) }
    }

    fun run(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || !connected) return
        sessionManager.runTerminal(cmd)
        input = TextFieldValue("")
        historyCursor = -1
    }

    fun press(key: TermKey) {
        val text = input.text
        val sel = input.selection
        when (key) {
            TermKey.HistoryUp -> if (history.isNotEmpty()) {
                historyCursor = (historyCursor + 1).coerceAtMost(history.lastIndex)
                val cmd = history[history.lastIndex - historyCursor]
                input = TextFieldValue(cmd, TextRange(cmd.length))
            }
            TermKey.HistoryDown -> if (historyCursor >= 0) {
                historyCursor -= 1
                val cmd = if (historyCursor < 0) "" else history[history.lastIndex - historyCursor]
                input = TextFieldValue(cmd, TextRange(cmd.length))
            }
            TermKey.Left -> input = input.copy(selection = TextRange((sel.min - 1).coerceAtLeast(0)))
            TermKey.Right -> input = input.copy(selection = TextRange((sel.max + 1).coerceAtMost(text.length)))
            else -> {
                val insert = key.label
                val next = text.substring(0, sel.min) + insert + text.substring(sel.max)
                input = TextFieldValue(next, TextRange(sel.min + insert.length))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .bottomRule(c.rule)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Terminal", style = OcType.screenTitle, color = c.ink, modifier = Modifier.weight(1f))
            Eyebrow(grid?.let { (cols, rows) -> "pty · ${cols}×${rows}" } ?: "pty · measuring", style = OcType.tag)
            HeaderAction("Clear", onClick = { sessionManager.clearTerminalTranscript() }, enabled = lines.isNotEmpty())
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(c.codeBg)
                .onSizeChanged { size ->
                    val padH = with(density) { 28.dp.toPx() }
                    val padV = with(density) { 24.dp.toPx() }
                    val cols = max(1, ((size.width - padH) / cell.first).toInt())
                    val rows = max(1, ((size.height - padV) / cell.second).toInt())
                    if (grid != (cols to rows)) {
                        grid = cols to rows
                        sessionManager.resizeTerminal(cols, rows)
                    }
                }
        ) {
            if (lines.isEmpty()) {
                Text(
                    "No output yet. Commands run on the bridge host; their output streams here.",
                    style = OcType.terminal, color = c.ink3,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            } else {
                // reverseLayout keeps the newest output pinned to the bottom as it streams.
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    // Fill from the top like a terminal; once output overflows, the
                    // reverse layout keeps the newest line in view.
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                ) {
                    items(count = lines.size, key = { i -> lines[lines.lastIndex - i].id }) { i ->
                        val line = lines[lines.lastIndex - i]
                        Text(
                            text = line.text.ifEmpty { " " },
                            style = OcType.terminal,
                            color = if (line.kind == TerminalLineKind.Prompt) c.goldInk else c.ink2,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val recent = history.asReversed().take(8)
        if (recent.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recent.forEach { cmd -> OcChip(cmd, onClick = { run(cmd) }, mono = true, enabled = connected) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("$", style = OcType.terminalInput, color = c.goldInk)
            OcTextField(
                value = input,
                onValueChange = { input = it; historyCursor = -1 },
                placeholder = if (connected) "command" else "offline",
                modifier = Modifier.weight(1f),
                textStyle = OcType.terminalInput,
                minHeight = 44.dp,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { run(input.text) })
            )
            OcButton(
                "Run",
                onClick = { run(input.text) },
                kind = OcButtonKind.Gold,
                enabled = connected && input.text.isNotBlank(),
                height = 44.dp,
                contentPadding = PaddingValues(horizontal = 16.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(c.ruleStrong)
                .topRule(c.ruleStrong)
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            TermKey.entries.forEach { key ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(if (pressed) c.gold else c.panel)
                        .clickable(interactionSource = source, indication = null, role = Role.Button) { press(key) }
                        .semantics { contentDescription = key.description },
                    contentAlignment = Alignment.Center
                ) {
                    Text(key.label, style = OcType.key, color = if (pressed) c.onGold else c.ink2)
                }
            }
        }
    }
}
