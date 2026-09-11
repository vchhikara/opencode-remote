package com.opencode.remote.run

import com.opencode.remote.data.dto.TaskDto
import com.opencode.remote.data.run.RunEventKind
import com.opencode.remote.data.run.RunLogRecorder
import com.opencode.remote.data.run.extractToolTarget
import com.opencode.remote.ui.screens.runlog.currentRunTrail
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLogRecorderTest {
    private var now = 1_000L
    private val log = RunLogRecorder(clock = { now })

    @Test
    fun runStartsOnFirstActivityAndEndsOnIdle() {
        assertNull(log.runStartedAt.value)
        log.agentState("Idle") // idle while already idle: nothing recorded
        assertTrue(log.events.value.isEmpty())

        now = 2_000L
        log.agentState("Thinking...")
        assertEquals(2_000L, log.runStartedAt.value)

        now = 3_000L
        log.agentState("Thinking...") // repeated state is not re-logged
        assertEquals(1, log.events.value.size)

        log.agentState("Idle")
        assertNull(log.runStartedAt.value)
        assertEquals(RunEventKind.Finished, log.events.value.last().kind)
    }

    @Test
    fun toolCallsOpenAndCloseInOrder() {
        log.toolCall("read", "a.kt")
        log.toolCall("grep", "sleep")
        assertEquals(2, log.events.value.count { it.kind == RunEventKind.Tool && !it.done })
        log.toolResult("read", failed = false)
        val read = log.events.value.first { it.text == "read" }
        assertTrue(read.done)
        log.toolResult("grep", failed = true)
        assertTrue(log.events.value.first { it.text == "grep" }.failed)
    }

    @Test
    fun permissionDecisionClosesTheRequest() {
        log.permissionRequested("bash")
        log.permissionAnswered("Allowed once", "bash")
        val events = log.events.value
        assertTrue(events.first { it.kind == RunEventKind.Permission }.done)
        assertEquals("Allowed once", events.last { it.kind == RunEventKind.Decision }.text)
        assertEquals("bash", events.last().detail)
    }

    @Test
    fun taskEventsOnlyOnStatusChange() {
        log.task(TaskDto("t1", "opencode run", null, "running"))
        log.task(TaskDto("t1", "opencode run", null, "running"))
        log.task(TaskDto("t1", "opencode run", null, "completed"))
        assertEquals(2, log.events.value.count { it.kind == RunEventKind.Task })
    }

    @Test
    fun logIsBounded() {
        val small = RunLogRecorder(clock = { now }, capacity = 5)
        repeat(20) { small.gitCommand("status $it") }
        assertEquals(5, small.events.value.size)
        assertEquals("You ran git status 19", small.events.value.last().text)
    }

    @Test
    fun trailIncludesThePromptThatStartedTheRun() {
        now = 10L; log.promptSent("fix it")
        now = 20L; log.agentState("Thinking...")
        now = 30L; log.toolCall("read", "a.kt")
        val trail = currentRunTrail(log.events.value, log.runStartedAt.value)
        assertEquals(listOf(RunEventKind.Prompt, RunEventKind.State, RunEventKind.Tool), trail.map { it.kind })
    }

    @Test
    fun toolTargetsComeOnlyFromKnownStringKeys() {
        assertEquals("src/a.kt", extractToolTarget(buildJsonObject { put("filePath", JsonPrimitive("src/a.kt")) }))
        assertEquals("npm test", extractToolTarget(buildJsonObject { put("command", JsonPrimitive("npm   test")) }))
        assertNull(extractToolTarget(buildJsonObject { put("limit", JsonPrimitive(10)) }))
        assertNull(extractToolTarget(null))
    }
}
