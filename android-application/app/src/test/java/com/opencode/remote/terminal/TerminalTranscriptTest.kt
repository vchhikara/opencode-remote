package com.opencode.remote.terminal

import com.opencode.remote.data.terminal.TerminalLineKind
import com.opencode.remote.data.terminal.TerminalTranscript
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTranscriptTest {

    private fun TerminalTranscript.texts() = lines.value.map { it.text }

    @Test
    fun partialLinesContinueAcrossChunks() {
        val t = TerminalTranscript()
        t.appendOutput("hel")
        t.appendOutput("lo\nwor")
        t.appendOutput("ld\n")
        assertEquals(listOf("hello", "world"), t.texts())
    }

    @Test
    fun carriageReturnRewritesTheOpenLine() {
        val t = TerminalTranscript()
        t.appendOutput("progress 10%")
        t.appendOutput("\rprogress 90%")
        assertEquals(listOf("progress 90%"), t.texts())
    }

    @Test
    fun ansiAndControlSequencesAreStripped() {
        val t = TerminalTranscript()
        t.appendOutput("\u001B[32mok\u001B[0m done\r\n\u001B]0;title\u0007next\n")
        assertEquals(listOf("ok done", "next"), t.texts())
    }

    @Test
    fun commandsAreEchoedAndDeduplicatedInHistory() {
        val t = TerminalTranscript()
        t.appendOutput("partial")
        t.appendCommand("ls")
        t.appendOutput("a\n")
        t.appendCommand("pwd")
        t.appendCommand("ls")
        assertEquals(TerminalLineKind.Prompt, t.lines.value[1].kind)
        assertEquals("$ ls", t.lines.value[1].text)
        assertEquals("a", t.lines.value[2].text) // output after a command starts a new line
        assertEquals(listOf("pwd", "ls"), t.history.value)
    }

    @Test
    fun transcriptIsBounded() {
        val t = TerminalTranscript(maxLines = 3)
        t.appendOutput("1\n2\n3\n4\n5\n")
        assertEquals(listOf("3", "4", "5"), t.texts())
        t.clear()
        assertEquals(emptyList<String>(), t.texts())
    }
}
