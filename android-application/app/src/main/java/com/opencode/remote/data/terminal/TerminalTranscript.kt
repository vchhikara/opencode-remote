package com.opencode.remote.data.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TerminalLineKind { Output, Prompt }

data class TerminalLine(val id: Long, val text: String, val kind: TerminalLineKind)

/**
 * Accumulates the bridge's TERMINAL_OUTPUT chunks into display lines. The wire only
 * carries raw string chunks (possibly partial lines, possibly with ANSI control codes
 * from the PTY), so this splits on newlines, keeps an unterminated trailing line open
 * for the next chunk, honours bare carriage returns (progress bars), and strips escape
 * sequences for display. Commands the user sends are echoed as [TerminalLineKind.Prompt]
 * lines and remembered in [history] for one-tap reuse — both are purely local records of
 * what this phone sent; nothing is synthesised on the bridge's behalf.
 */
class TerminalTranscript(private val maxLines: Int = 2000, private val maxHistory: Int = 30) {
    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    /** Distinct commands, oldest first. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var nextId = 1L
    private var lineOpen = false

    @Synchronized
    fun appendOutput(chunk: String) {
        if (chunk.isEmpty()) return
        val clean = stripAnsi(chunk).replace("\r\n", "\n")
        if (clean.isEmpty()) return
        val current = _lines.value.toMutableList()
        val pieces = clean.split('\n')
        pieces.forEachIndexed { index, piece ->
            val isLast = index == pieces.lastIndex
            if (isLast && piece.isEmpty()) return@forEachIndexed // chunk ended with a newline
            val hasCr = piece.contains('\r')
            val text = if (hasCr) piece.substringAfterLast('\r') else piece
            val last = current.lastOrNull()
            if (index == 0 && lineOpen && last != null && last.kind == TerminalLineKind.Output) {
                current[current.lastIndex] = last.copy(text = if (hasCr) text else last.text + text)
            } else {
                current.add(TerminalLine(nextId++, text, TerminalLineKind.Output))
            }
        }
        lineOpen = !clean.endsWith("\n")
        _lines.value = if (current.size > maxLines) current.takeLast(maxLines) else current
    }

    @Synchronized
    fun appendCommand(command: String) {
        lineOpen = false
        val current = _lines.value + TerminalLine(nextId++, "$ $command", TerminalLineKind.Prompt)
        _lines.value = if (current.size > maxLines) current.takeLast(maxLines) else current
        _history.value = (_history.value.filterNot { it == command } + command).takeLast(maxHistory)
    }

    @Synchronized
    fun clear() {
        lineOpen = false
        _lines.value = emptyList()
    }

    companion object {
        private val CSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
        private val OSC = Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")
        private val ESC2 = Regex("\u001B[@-Z\\\\-_]")
        private val CONTROL = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")

        fun stripAnsi(text: String): String =
            text.replace(OSC, "").replace(CSI, "").replace(ESC2, "").replace(CONTROL, "")
    }
}
