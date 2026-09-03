package com.opencode.remote.data.network

import android.util.Log
import com.opencode.remote.data.dto.*
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The app's single view onto the bridge session: domain state (workspaces, chat,
 * files, diffs, tasks, git) plus the commands that mutate it. The socket lifecycle
 * itself lives in [WsClient] — this class only drives it and routes/emits frames.
 *
 * Wire format quirks (bridge/main.js is the source of truth, not this file): many
 * outbound payloads are bare strings, not JSON objects (see [sendString] call sites),
 * and a few inbound ones are too (AGENT_STATE, FILE_CONTENT, TERMINAL_OUTPUT,
 * TASK_REMOVED). Where the wire shape differs from the UI's shape, a `Bridge*` DTO
 * models it exactly and gets mapped below — see [com.opencode.remote.data.dto].
 */
class RemoteSessionManager(
    private var host: String = "",
    private var port: Int = 8080,
    private var deviceName: String = "",
    private var token: String = "",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val ws = WsClient(scope)
    val connectionState: StateFlow<ConnectionState> = ws.connectionState

    private val json = Json { ignoreUnknownKeys = true }

    private val _workspaces = MutableStateFlow<List<WorkspaceDto>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceDto>> = _workspaces.asStateFlow()

    private val _activeWorkspace = MutableStateFlow<String?>(null)
    val activeWorkspace: StateFlow<String?> = _activeWorkspace.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageDto>> = _chatMessages.asStateFlow()

    // Raw text from the bridge ("Idle", "Thinking...", whatever the wrapped CLI
    // emits) — no fixed vocabulary, so no DTO to decode into. AgentStateBadge maps
    // it to a display style case-insensitively.
    private val _agentState = MutableStateFlow("Idle")
    val agentState: StateFlow<String> = _agentState.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNodeDto>>(emptyList())
    val fileTree: StateFlow<List<FileNodeDto>> = _fileTree.asStateFlow()

    private val _fileContent = MutableStateFlow<FileContentDto?>(null)
    val fileContent: StateFlow<FileContentDto?> = _fileContent.asStateFlow()

    private val _pendingDiff = MutableStateFlow<FileDiffDto?>(null)
    val pendingDiff: StateFlow<FileDiffDto?> = _pendingDiff.asStateFlow()

    // In-progress assistant turn, built up from STREAM_TEXT_DELTA/STREAM_TOOL_CALL/
    // STREAM_TOOL_RESULT frames. Null when no generation is in flight. Cleared when
    // the final CHAT_MESSAGE for the turn arrives (see "CHAT_MESSAGE" branch below).
    private val _streamingMessage = MutableStateFlow<StreamingMessageDto?>(null)
    val streamingMessage: StateFlow<StreamingMessageDto?> = _streamingMessage.asStateFlow()

    private val _pendingPermission = MutableStateFlow<PermissionRequestDto?>(null)
    val pendingPermission: StateFlow<PermissionRequestDto?> = _pendingPermission.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<QuestionRequestDto?>(null)
    val pendingQuestion: StateFlow<QuestionRequestDto?> = _pendingQuestion.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions: StateFlow<List<SessionDto>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _terminalOutput = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    private val _tasks = MutableStateFlow<Map<String, TaskDto>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskDto>> = _tasks.asStateFlow()

    private val _gitStatus = MutableStateFlow<GitStatusDto?>(null)
    val gitStatus: StateFlow<GitStatusDto?> = _gitStatus.asStateFlow()

    // The bridge's FILE_CONTENT reply carries no path, only the raw text — remember
    // what we last asked for so the UI-facing FileContentDto can be paired up.
    private var pendingFileRequestPath: String? = null

    fun connect(host: String, port: Int, token: String, deviceName: String) {
        this.host = host
        this.port = port
        this.token = token
        this.deviceName = deviceName
        connect()
    }

    fun connect() {
        ws.connect(host, port) session@{ markConnected ->
            send(Frame.Text(json.encodeToString(createFrame("CONNECT", ConnectPayload(deviceName, token), json))))

            var handshakeOk = false
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                try {
                    val wsFrame = json.decodeFromString<WebSocketFrame>(text)
                    when (wsFrame.eventType) {
                        "CONNECTED" -> {
                            handshakeOk = true
                            markConnected()
                        }
                        "ERROR" -> {
                            val err = wsFrame.decodePayload<ErrorPayload>(json)
                            Log.e("RemoteSessionManager", "Handshake rejected: ${err?.message}")
                            close()
                            return@session
                        }
                        else -> handleIncomingMessage(text) // CONNECTED is followed by WORKSPACE_LIST/GIT_STATUS pushes
                    }
                } catch (e: Exception) {
                    Log.e("RemoteSessionManager", "Error parsing handshake frame", e)
                }
                if (handshakeOk) break
            }
            if (!handshakeOk) throw Exception("Handshake failed")

            for (frame in incoming) {
                if (frame is Frame.Text) handleIncomingMessage(frame.readText())
            }
        }
    }

    internal suspend fun handleIncomingMessage(text: String) {
        try {
            val frame = json.decodeFromString<WebSocketFrame>(text)
            when (frame.eventType) {
                "WORKSPACE_LIST" -> frame.decodePayload<List<WorkspaceDto>>(json)?.let { _workspaces.value = it }
                "WORKSPACE_OPENED" -> frame.decodePayload<OpenWorkspacePayload>(json)?.let { _activeWorkspace.value = it.path }
                "CHAT_MESSAGE" -> frame.decodePayload<BridgeChatMessageDto>(json)?.let { msg ->
                    _chatMessages.update { it + msg.toUi() }
                    _streamingMessage.value = null // final message landed; turn is over
                }
                "STREAM_TEXT_DELTA" -> frame.decodePayload<StreamTextDeltaDto>(json)?.let { delta ->
                    _streamingMessage.update { (it ?: StreamingMessageDto()).copy(text = it?.text.orEmpty() + delta.text) }
                }
                "STREAM_TOOL_CALL" -> frame.decodePayload<StreamToolCallDto>(json)?.let { call ->
                    _streamingMessage.update { (it ?: StreamingMessageDto()).copy(runningTool = call.tool) }
                }
                "STREAM_TOOL_RESULT" -> frame.decodePayload<StreamToolResultDto>(json)?.let {
                    _streamingMessage.update { (it ?: StreamingMessageDto()).copy(runningTool = null) }
                }
                "SESSION_LIST" -> frame.decodePayload<List<SessionDto>>(json)?.let { _sessions.value = it }
                "SESSION_SWITCHED" -> frame.decodePayload<SessionSwitchedDto>(json)?.let { _activeSessionId.value = it.id }
                "PERMISSION_REQUEST" -> frame.decodePayload<PermissionRequestDto>(json)?.let { _pendingPermission.value = it }
                "QUESTION_REQUEST" -> frame.decodePayload<QuestionRequestDto>(json)?.let { _pendingQuestion.value = it }
                "AGENT_STATE" -> frame.decodePayload<String>(json)?.let {
                    _agentState.value = it
                    if (it.equals("Idle", ignoreCase = true)) _streamingMessage.value = null
                }
                "FILE_TREE" -> frame.decodePayload<List<FileNodeDto>>(json)?.let { _fileTree.value = it }
                "FILE_CONTENT" -> frame.decodePayload<String>(json)?.let { content ->
                    _fileContent.value = FileContentDto(path = pendingFileRequestPath ?: "", content = content)
                }
                "FILE_DIFF" -> frame.decodePayload<BridgeFileDiffDto>(json)?.let { diff ->
                    _pendingDiff.value = FileDiffDto(filePath = diff.fileName, patch = diff.diffText)
                }
                "TERMINAL_OUTPUT" -> frame.decodePayload<String>(json)?.let { _terminalOutput.emit(it) }
                "TASK_UPDATED" -> frame.decodePayload<TaskDto>(json)?.let { task ->
                    _tasks.update { it + (task.id to task) }
                }
                "TASK_REMOVED" -> frame.decodePayload<String>(json)?.let { id ->
                    _tasks.update { it - id }
                }
                "GIT_STATUS" -> frame.decodePayload<GitStatusDto>(json)?.let { _gitStatus.value = it }
                "ERROR" -> {
                    val msg = frame.decodePayload<ErrorPayload>(json)?.message ?: "Unknown error"
                    Log.e("RemoteSessionManager", "Server sent error: $msg")
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteSessionManager", "Error handling message", e)
        }
    }

    private fun sendRaw(eventType: String, payload: JsonElement? = null) {
        scope.launch {
            try {
                ws.send(json.encodeToString(WebSocketFrame(eventType, payload)))
            } catch (e: Exception) {
                Log.e("RemoteSessionManager", "Failed to send $eventType", e)
            }
        }
    }

    private fun sendString(eventType: String, value: String) {
        sendRaw(eventType, JsonPrimitive(value))
    }

    fun sendPrompt(text: String) {
        // The bridge never echoes the user's own prompt back, so append it locally.
        _chatMessages.update { it + ChatMessageDto(role = ChatRole.User, content = text) }
        sendString("PROMPT", text)
    }

    fun openWorkspace(path: String) = sendString("OPEN_WORKSPACE", path)

    fun fetchWorkspaces() = sendRaw("FETCH_WORKSPACES")

    fun fetchFileTree() = sendRaw("FETCH_FILE_TREE")

    fun fetchFile(path: String) {
        pendingFileRequestPath = path
        sendString("FETCH_FILE", path)
    }

    fun acceptDiff(filePath: String) {
        sendString("ACCEPT_DIFF", filePath)
        if (_pendingDiff.value?.filePath == filePath) _pendingDiff.value = null
    }

    fun rejectDiff(filePath: String) {
        sendString("REJECT_DIFF", filePath)
        if (_pendingDiff.value?.filePath == filePath) _pendingDiff.value = null
    }

    /** decision: bridge forwards this string verbatim as {decision} to
     *  POST /permission/{id}/reply — e.g. "allow" or "deny". */
    fun replyPermission(permissionId: String, decision: String) {
        sendRaw("PERMISSION_REPLY", json.encodeToJsonElement(PermissionReplyPayload(permissionId, decision)))
        if (_pendingPermission.value?.permissionId == permissionId) _pendingPermission.value = null
    }

    fun replyQuestion(questionId: String, answer: String) {
        sendRaw("QUESTION_REPLY", json.encodeToJsonElement(QuestionReplyPayload(questionId, answer)))
        if (_pendingQuestion.value?.questionId == questionId) _pendingQuestion.value = null
    }

    fun listSessions() = sendRaw("LIST_SESSIONS")

    fun switchSession(sessionId: String) = sendString("SWITCH_SESSION", sessionId)

    fun newSession(title: String? = null) {
        sendRaw("NEW_SESSION", json.encodeToJsonElement(NewSessionPayload(title)))
    }

    fun forkSession(sessionId: String) = sendString("FORK_SESSION", sessionId)

    fun runTerminal(command: String) = sendString("TERMINAL", command)

    /** Resizes the active PTY-backed terminal (Task 4.2.2) — call this from a
     *  real layout/size-change callback (see TerminalScreen's onSizeChanged),
     *  not on a timer or as dead code. */
    fun resizeTerminal(cols: Int, rows: Int) {
        sendRaw("TERMINAL_RESIZE", json.encodeToJsonElement(TerminalResizePayload(cols, rows)))
    }

    fun killTask(taskId: String) = sendString("KILL_TASK", taskId)

    fun runGit(command: String) = sendString("GIT", command)

    fun disconnect() {
        ws.disconnect()
    }
}
