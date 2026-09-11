package com.opencode.remote.session

import com.opencode.remote.data.dto.*
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.run.RunEventKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * State the redesigned UI derives inside RemoteSessionManager (tool steps, run log,
 * terminal transcript, surfaced errors). Frames are shaped exactly like the existing
 * SessionManagerTest's — no new wire events are introduced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RedesignSessionStateTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var testScope: TestScope
    private lateinit var manager: RemoteSessionManager

    @Before
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
        manager = RemoteSessionManager("localhost", 8080, "testDevice", "testToken", testScope)
    }

    private suspend fun incoming(frame: WebSocketFrame) = manager.handleIncomingMessage(json.encodeToString(frame))

    @Test
    fun toolCallsCarryTargetsAndMoveOntoTheFinalMessage() = testScope.runTest {
        val input = buildJsonObject { put("filePath", JsonPrimitive("src/a.kt")) }
        incoming(createFrame("STREAM_TOOL_CALL", StreamToolCallDto(tool = "read", input = input), json))
        assertEquals(listOf(ToolStep("read", "src/a.kt")), manager.streamingMessage.value?.toolSteps)

        incoming(createFrame("STREAM_TOOL_RESULT", StreamToolResultDto(tool = "read", output = JsonPrimitive("ok")), json))
        assertEquals(true, manager.streamingMessage.value?.toolSteps?.single()?.done)

        incoming(createFrame("CHAT_MESSAGE", BridgeChatMessageDto(id = "m1", text = "done"), json))
        assertEquals("src/a.kt", manager.chatMessages.value.single().tools.single().target)
        assertNull(manager.streamingMessage.value)
    }

    @Test
    fun errorToolResultIsMarkedFailed() = testScope.runTest {
        incoming(createFrame("STREAM_TOOL_CALL", StreamToolCallDto(tool = "bash"), json))
        incoming(createFrame("STREAM_TOOL_RESULT", StreamToolResultDto(tool = "bash", output = buildJsonObject { put("error", JsonPrimitive("boom")) }), json))
        assertEquals(true, manager.streamingMessage.value?.toolSteps?.single()?.failed)
    }

    @Test
    fun permissionDecisionsUseTheExistingWireStrings() {
        assertEquals("allow", PermissionDecision.AllowOnce.wire)
        assertEquals("always", PermissionDecision.AlwaysAllow.wire)
        assertEquals("deny", PermissionDecision.Deny.wire)
    }

    @Test
    fun replyingToAPermissionIsRecordedInTheRunLog() = testScope.runTest {
        incoming(createFrame("PERMISSION_REQUEST", PermissionRequestDto("per1", tool = "bash"), json))
        manager.replyPermission("per1", PermissionDecision.Deny.wire)
        assertNull(manager.pendingPermission.value)
        val decision = manager.runEvents.value.last()
        assertEquals(RunEventKind.Decision, decision.kind)
        assertEquals("Denied", decision.text)
        assertEquals("bash", decision.detail)
    }

    @Test
    fun agentStateDrivesTheRunClock() = testScope.runTest {
        assertNull(manager.runStartedAt.value)
        incoming(createFrame("AGENT_STATE", "Thinking...", json))
        assertTrue(manager.runStartedAt.value != null)
        incoming(createFrame("AGENT_STATE", "Idle", json))
        assertNull(manager.runStartedAt.value)
    }

    @Test
    fun terminalOutputIsKeptInTheTranscript() = testScope.runTest {
        manager.runTerminal("ls")
        incoming(createFrame("TERMINAL_OUTPUT", "a.txt\nb.txt\n", json))
        assertEquals(listOf("$ ls", "a.txt", "b.txt"), manager.terminalLines.value.map { it.text })
        assertEquals(listOf("ls"), manager.terminalHistory.value)
    }

    @Test
    fun diffDecisionsAreLogged() = testScope.runTest {
        incoming(createFrame("FILE_DIFF", BridgeFileDiffDto("a.txt", "patch"), json))
        manager.rejectDiff("a.txt")
        val kinds = manager.runEvents.value.map { it.kind }
        assertEquals(listOf(RunEventKind.Diff, RunEventKind.DiffReverted), kinds)
    }
}
