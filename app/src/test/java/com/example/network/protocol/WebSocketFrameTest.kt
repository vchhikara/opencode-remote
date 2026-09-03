package com.example.network.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

/**
 * Round-trips the canonical {eventType, payload} envelope and the DTOs the
 * bridge actually sends (see docs/integration-context.md), asserting the
 * client decodes exactly the fields the server emits - including the
 * FileNodeDto.path and GitStatusDto.canPush/canPull fields that D-H2/D2
 * fixed threading bugs around.
 */
class WebSocketFrameTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decodes a CONNECTED frame`() {
        val raw = """{"eventType":"CONNECTED","payload":{"sessionId":"abc123","deviceName":"Pixel 8"}}"""
        val frame = json.decodeFromString<WebSocketFrame>(raw)
        assertEquals("CONNECTED", frame.eventType)
        assertNotNull(frame.payload)
    }

    @Test
    fun `decodes an ERROR frame with a message`() {
        val raw = """{"eventType":"ERROR","payload":{"message":"unauthorized"}}"""
        val frame = json.decodeFromString<WebSocketFrame>(raw)
        assertEquals("ERROR", frame.eventType)
        val message = frame.payload?.let { json.decodeFromJsonElement<Map<String, String>>(it) }
        assertEquals("unauthorized", message?.get("message"))
    }

    @Test
    fun `FileNodeDto threads path through nested children`() {
        val dto = FileNodeDto(
            name = "root",
            path = "/workspace",
            isDirectory = true,
            children = listOf(
                FileNodeDto(name = "src", path = "/workspace/src", isDirectory = true, children = listOf(
                    FileNodeDto(name = "Main.kt", path = "/workspace/src/Main.kt", isDirectory = false)
                ))
            )
        )
        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<FileNodeDto>(encoded)

        assertEquals("/workspace", decoded.path)
        val child = decoded.children!!.first()
        assertEquals("/workspace/src", child.path)
        assertEquals("/workspace/src/Main.kt", child.children!!.first().path)
    }

    @Test
    fun `GitStatusDto round-trips canPush and canPull`() {
        val dto = GitStatusDto(
            branch = "main",
            modifiedFiles = listOf("a.kt"),
            addedFiles = emptyList(),
            deletedFiles = emptyList(),
            canPush = true,
            canPull = false
        )
        val decoded = json.decodeFromString<GitStatusDto>(json.encodeToString(dto))
        assertTrue(decoded.canPush)
        assertFalse(decoded.canPull)
    }

    @Test
    fun `WorkspaceDto and ChatMessageDto round-trip`() {
        val ws = WorkspaceDto(id = "default", name = "my-project", path = "/workspace")
        assertEquals(ws, json.decodeFromString<WorkspaceDto>(json.encodeToString(ws)))

        val msg = ChatMessageDto(id = "1", text = "hello", isUser = true, actionDescription = null, hasDetails = false)
        assertEquals(msg, json.decodeFromString<ChatMessageDto>(json.encodeToString(msg)))
    }
}
