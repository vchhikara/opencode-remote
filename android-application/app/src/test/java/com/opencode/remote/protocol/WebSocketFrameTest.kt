package com.opencode.remote.protocol

import com.opencode.remote.data.dto.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Envelope + DTO round-trip tests. Every eventType and payload shape here is copied
 * verbatim from bridge/main.js (not invented) — see RemoteSessionManager's class doc
 * for the wire-contract notes these tests are meant to keep honest.
 */
@RunWith(JUnit4::class)
class WebSocketFrameTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testConnectPayloadRoundTrip() {
        val payload = ConnectPayload("Device1", "token123")
        val frame = createFrame("CONNECT", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("CONNECT", deserializedFrame.eventType)
        assertEquals(payload, deserializedFrame.decodePayload<ConnectPayload>(json))
    }

    @Test
    fun testConnectedPayloadRoundTrip() {
        val payload = ConnectedPayload("session-123", "Device1")
        val frame = createFrame("CONNECTED", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("CONNECTED", deserializedFrame.eventType)
        assertEquals(payload, deserializedFrame.decodePayload<ConnectedPayload>(json))
    }

    @Test
    fun testErrorPayloadRoundTrip() {
        val payload = ErrorPayload("Some error occurred")
        val frame = createFrame("ERROR", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<ErrorPayload>(json))
    }

    @Test
    fun testWorkspaceListRoundTrip() {
        val payload = listOf(WorkspaceDto("w1", "My Workspace", "/path/to/workspace"))
        val frame = createFrame("WORKSPACE_LIST", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<List<WorkspaceDto>>(json))
    }

    @Test
    fun testWorkspaceOpenedRoundTrip() {
        val payload = OpenWorkspacePayload("/w1")
        val frame = createFrame("WORKSPACE_OPENED", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<OpenWorkspacePayload>(json))
    }

    @Test
    fun testBridgeChatMessageRoundTrip() {
        // Matches bridge's pushChatMessage shape exactly (main.js:109).
        val payload = BridgeChatMessageDto(id = "msg1", text = "Hello", isUser = false, actionDescription = "Generated code", hasDetails = true)
        val frame = createFrame("CHAT_MESSAGE", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        val decoded = deserializedFrame.decodePayload<BridgeChatMessageDto>(json)
        assertEquals(payload, decoded)
        assertEquals(ChatRole.Assistant, decoded?.toUi()?.role)
    }

    @Test
    fun testAgentStateIsABareString() {
        // Bridge sends AGENT_STATE as a raw string payload (main.js pushAgentState),
        // not an object.
        val frame = createFrame("AGENT_STATE", "Thinking...", json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("Thinking...", deserializedFrame.decodePayload<String>(json))
    }

    @Test
    fun testFileNodeDtoRoundTrip() {
        val child = FileNodeDto("child.txt", "/path/child.txt", false, null)
        val payload = FileNodeDto("root", "/path", true, listOf(child))
        val frame = createFrame("FILE_TREE", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<FileNodeDto>(json))
    }

    @Test
    fun testFileContentIsABareString() {
        // Bridge sends FILE_CONTENT as a raw string (main.js FETCH_FILE handler),
        // not an object with a path.
        val frame = createFrame("FILE_CONTENT", "content here", json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("content here", deserializedFrame.decodePayload<String>(json))
    }

    @Test
    fun testBridgeFileDiffRoundTrip() {
        // Matches bridge's pushFileDiff shape exactly (main.js:114).
        val payload = BridgeFileDiffDto(fileName = "pending_changes.diff", diffText = "patch")
        val frame = createFrame("FILE_DIFF", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<BridgeFileDiffDto>(json))
    }

    @Test
    fun testTerminalOutputIsABareString() {
        // Bridge sends TERMINAL_OUTPUT as a raw string line (main.js pushTerminalLine).
        val frame = createFrame("TERMINAL_OUTPUT", "hello from shell", json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("hello from shell", deserializedFrame.decodePayload<String>(json))
    }

    @Test
    fun testTaskDtoRoundTrip() {
        // Matches bridge's pushTaskUpdated shape exactly (main.js:122): id/name/port/status.
        val payload = TaskDto("t1", "opencode run", 4096, "running")
        val frame = createFrame("TASK_UPDATED", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<TaskDto>(json))
    }

    @Test
    fun testTaskRemovedIsABareString() {
        // Bridge sends TASK_REMOVED as a raw task id string (main.js pushTaskRemoved).
        val frame = createFrame("TASK_REMOVED", "t1", json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("t1", deserializedFrame.decodePayload<String>(json))
    }

    @Test
    fun testGitStatusDtoRoundTrip() {
        // Matches bridge's pushGitStatus shape exactly (main.js:98).
        val payload = GitStatusDto("main", listOf("file1"), listOf("file2"), listOf("file3"), canPush = true, canPull = false)
        val frame = createFrame("GIT_STATUS", payload, json)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(payload, deserializedFrame.decodePayload<GitStatusDto>(json))
    }

    @Test
    fun testNullOrMissingPayload() {
        val frame = WebSocketFrame("PING", null)
        val serialized = json.encodeToString(frame)
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals("PING", deserializedFrame.eventType)
        assertNull(deserializedFrame.payload)
        assertNull(deserializedFrame.decodePayload<ConnectPayload>(json))
    }

    @Test
    fun testInvalidJsonResilience() {
        val invalidJson = """{"eventType":"TEST", "payload": "this should be an object but is string"}"""
        val deserializedFrame = json.decodeFromString<WebSocketFrame>(invalidJson)
        assertEquals("TEST", deserializedFrame.eventType)

        // decodePayload should handle the mismatch or throw, but our extension catches exceptions
        val decoded = deserializedFrame.decodePayload<ConnectPayload>(json)
        assertNull(decoded)
    }
}
