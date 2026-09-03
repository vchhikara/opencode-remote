package com.opencode.remote.session

import com.opencode.remote.data.dto.*
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Exercises RemoteSessionManager.handleIncomingMessage directly with frames shaped
 * exactly as bridge/main.js actually emits them (event names and field names copied
 * from the source, not invented) — see the class doc on RemoteSessionManager for the
 * wire-format quirks this asserts against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SessionManagerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var testScope: TestScope
    private lateinit var manager: RemoteSessionManager

    @Before
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
        manager = RemoteSessionManager("localhost", 8080, "testDevice", "testToken", testScope)
    }

    @Test
    fun testInitialStates() = testScope.runTest {
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
        assertTrue(manager.workspaces.value.isEmpty())
        assertEquals(null, manager.activeWorkspace.value)
        assertTrue(manager.chatMessages.value.isEmpty())
        assertEquals("Idle", manager.agentState.value)
        assertTrue(manager.fileTree.value.isEmpty())
        assertEquals(null, manager.fileContent.value)
        assertEquals(null, manager.pendingDiff.value)
        assertEquals(null, manager.streamingMessage.value)
        assertEquals(null, manager.pendingPermission.value)
        assertEquals(null, manager.pendingQuestion.value)
        assertTrue(manager.sessions.value.isEmpty())
        assertEquals(null, manager.activeSessionId.value)
        assertTrue(manager.tasks.value.isEmpty())
        assertEquals(null, manager.gitStatus.value)
    }

    private suspend fun simulateIncomingMessage(frame: WebSocketFrame) {
        val text = json.encodeToString(frame)
        manager.handleIncomingMessage(text)
    }

    @Test
    fun testWorkspaceEvents() = testScope.runTest {
        val workspaces = listOf(WorkspaceDto("w1", "W1", "/w1"))
        simulateIncomingMessage(createFrame("WORKSPACE_LIST", workspaces, json))
        assertEquals(workspaces, manager.workspaces.value)

        simulateIncomingMessage(createFrame("WORKSPACE_OPENED", OpenWorkspacePayload("/w1"), json))
        assertEquals("/w1", manager.activeWorkspace.value)
    }

    @Test
    fun testChatMessageIsMappedFromBridgeShape() = testScope.runTest {
        // Bridge's field names (text/isUser) differ from the UI's (content/role) on
        // purpose — this is exactly the mapping that used to fail silently.
        val bridgeMsg = BridgeChatMessageDto(id = "msg1", text = "hi", isUser = false)
        simulateIncomingMessage(createFrame("CHAT_MESSAGE", bridgeMsg, json))

        val ui = manager.chatMessages.value.single()
        assertEquals("msg1", ui.id)
        assertEquals("hi", ui.content)
        assertEquals(ChatRole.Assistant, ui.role)
    }

    @Test
    fun testSendPromptAppendsLocalUserMessageImmediately() = testScope.runTest {
        // The bridge never echoes the user's own prompt back (only assistant output),
        // so sendPrompt must append it locally or the user never sees what they sent.
        manager.sendPrompt("do the thing")
        val ui = manager.chatMessages.value.single()
        assertEquals("do the thing", ui.content)
        assertEquals(ChatRole.User, ui.role)
    }

    @Test
    fun testAgentStateIsStoredRaw() = testScope.runTest {
        // No fixed vocabulary/casing on the bridge side — stored as-is.
        simulateIncomingMessage(createFrame("AGENT_STATE", "Thinking...", json))
        assertEquals("Thinking...", manager.agentState.value)
    }

    @Test
    fun testFileTreeEvent() = testScope.runTest {
        val fileTree = listOf(FileNodeDto("file.txt", "/file.txt", false))
        simulateIncomingMessage(createFrame("FILE_TREE", fileTree, json))
        assertEquals(fileTree, manager.fileTree.value)
    }

    @Test
    fun testFileContentIsPairedWithRequestedPath() = testScope.runTest {
        manager.fetchFile("/file.txt")
        simulateIncomingMessage(createFrame("FILE_CONTENT", "hello", json))
        assertEquals(FileContentDto("/file.txt", "hello"), manager.fileContent.value)
    }

    @Test
    fun testFileDiffIsMappedFromBridgeShape() = testScope.runTest {
        // Bridge's fileName/diffText map to filePath/patch.
        simulateIncomingMessage(createFrame("FILE_DIFF", BridgeFileDiffDto("pending_changes.diff", "patch text"), json))
        assertEquals(FileDiffDto("pending_changes.diff", "patch text"), manager.pendingDiff.value)
    }

    @Test
    fun testTaskLifecycle() = testScope.runTest {
        val task = TaskDto("t1", "opencode run", 4096, "running")
        simulateIncomingMessage(createFrame("TASK_UPDATED", task, json))
        assertEquals(mapOf("t1" to task), manager.tasks.value)

        // TASK_REMOVED is a bare id string, not an object.
        simulateIncomingMessage(createFrame("TASK_REMOVED", "t1", json))
        assertTrue(manager.tasks.value.isEmpty())
    }

    @Test
    fun testStreamTextDeltaAccumulatesIntoStreamingMessage() = testScope.runTest {
        assertEquals(null, manager.streamingMessage.value)
        simulateIncomingMessage(createFrame("STREAM_TEXT_DELTA", StreamTextDeltaDto(text = "Hel"), json))
        simulateIncomingMessage(createFrame("STREAM_TEXT_DELTA", StreamTextDeltaDto(text = "lo"), json))
        assertEquals("Hello", manager.streamingMessage.value?.text)
    }

    @Test
    fun testStreamToolCallAndResultTrackRunningTool() = testScope.runTest {
        simulateIncomingMessage(createFrame("STREAM_TOOL_CALL", StreamToolCallDto(tool = "bash"), json))
        assertEquals("bash", manager.streamingMessage.value?.runningTool)

        simulateIncomingMessage(createFrame("STREAM_TOOL_RESULT", StreamToolResultDto(tool = "bash"), json))
        assertEquals(null, manager.streamingMessage.value?.runningTool)
    }

    @Test
    fun testStreamingMessageClearedWhenFinalChatMessageArrives() = testScope.runTest {
        simulateIncomingMessage(createFrame("STREAM_TEXT_DELTA", StreamTextDeltaDto(text = "partial"), json))
        assertEquals("partial", manager.streamingMessage.value?.text)

        simulateIncomingMessage(createFrame("CHAT_MESSAGE", BridgeChatMessageDto(id = "m1", text = "final"), json))
        assertEquals(null, manager.streamingMessage.value)
    }

    @Test
    fun testPermissionRequestFlowAndReply() = testScope.runTest {
        assertEquals(null, manager.pendingPermission.value)
        simulateIncomingMessage(createFrame("PERMISSION_REQUEST", PermissionRequestDto("per1", tool = "bash"), json))
        assertEquals("per1", manager.pendingPermission.value?.permissionId)
        assertEquals("bash", manager.pendingPermission.value?.tool)

        manager.replyPermission("per1", "allow")
        assertEquals(null, manager.pendingPermission.value)
    }

    @Test
    fun testQuestionRequestFlowAndReply() = testScope.runTest {
        assertEquals(null, manager.pendingQuestion.value)
        simulateIncomingMessage(createFrame("QUESTION_REQUEST", QuestionRequestDto("q1", text = "Which?"), json))
        assertEquals("q1", manager.pendingQuestion.value?.questionId)
        assertEquals("Which?", manager.pendingQuestion.value?.text)

        manager.replyQuestion("q1", "a")
        assertEquals(null, manager.pendingQuestion.value)
    }

    @Test
    fun testSessionListAndSwitchedEvents() = testScope.runTest {
        assertTrue(manager.sessions.value.isEmpty())
        assertEquals(null, manager.activeSessionId.value)

        val sessions = listOf(SessionDto("s1", "First"), SessionDto("s2", "Second"))
        simulateIncomingMessage(createFrame("SESSION_LIST", sessions, json))
        assertEquals(sessions, manager.sessions.value)

        simulateIncomingMessage(createFrame("SESSION_SWITCHED", SessionSwitchedDto("s2"), json))
        assertEquals("s2", manager.activeSessionId.value)
    }

    @Test
    fun testGitStatusEvent() = testScope.runTest {
        val gitStatus = GitStatusDto(branch = "main", modifiedFiles = listOf("a.kt"))
        simulateIncomingMessage(createFrame("GIT_STATUS", gitStatus, json))
        assertEquals(gitStatus, manager.gitStatus.value)
        assertEquals(false, manager.gitStatus.value?.isClean)
    }
}
