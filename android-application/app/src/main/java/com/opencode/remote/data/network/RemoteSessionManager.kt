package com.opencode.remote.data.network

import android.util.Log
import com.opencode.remote.data.dto.*
import com.opencode.remote.data.run.RunEvent
import com.opencode.remote.data.run.RunLogRecorder
import com.opencode.remote.data.run.extractToolTarget
import com.opencode.remote.data.run.isErrorToolOutput
import com.opencode.remote.data.terminal.TerminalLine
import com.opencode.remote.data.terminal.TerminalTranscript
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val clock: () -> Long = System::currentTimeMillis
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
    // emits) — no fixed vocabulary, so no DTO to decode into. The UI derives a
    // presentation category from it (ui/state/AgentPresentation) without ever
    // rejecting an unknown string.
    private val _agentState = MutableStateFlow("Idle")
    val agentState: StateFlow<String> = _agentState.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNodeDto>>(emptyList())
    val fileTree: StateFlow<List<FileNodeDto>> = _fileTree.asStateFlow()

    private val _fileContent = MutableStateFlow<FileContentDto?>(null)
    val fileContent: StateFlow<FileContentDto?> = _fileContent.asStateFlow()

    // Every file with a pending (unreviewed) diff, keyed by filePath — a
    // single FILE_DIFF frame per changed file, so more than one can be
    // outstanding at once (Task 5.1; previously a single value that silently
    // dropped all but the last-received diff).
    private val _pendingDiffs = MutableStateFlow<List<FileDiffDto>>(emptyList())
    val pendingDiffs: StateFlow<List<FileDiffDto>> = _pendingDiffs.asStateFlow()

    /** Convenience accessor for screens that only ever show one diff at a
     *  time (e.g. a "first outstanding diff" summary badge) — intentionally
     *  kept alongside [pendingDiffs] rather than migrating every call site.
     *  Updated in lockstep with [_pendingDiffs] rather than derived via a
     *  live collector, so it needs no coroutine of its own. */
    private val _pendingDiff = MutableStateFlow<FileDiffDto?>(null)
    val pendingDiff: StateFlow<FileDiffDto?> = _pendingDiff.asStateFlow()

    private fun setPendingDiffs(update: (List<FileDiffDto>) -> List<FileDiffDto>) {
        _pendingDiffs.update(update)
        _pendingDiff.value = _pendingDiffs.value.firstOrNull()
    }

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

    // Global, cross-workspace session list (ADR-0002) — distinct from
    // [_sessions], which the bridge scopes to activeWorkspace only. Replaced
    // wholesale on each ALL_SESSIONS_LIST reply; fetchAllSessions(cursor)
    // callers combine pages themselves if they want to accumulate one.
    private val _allSessions = MutableStateFlow<List<GlobalSessionDto>>(emptyList())
    val allSessions: StateFlow<List<GlobalSessionDto>> = _allSessions.asStateFlow()

    private val _allSessionsNextCursor = MutableStateFlow<String?>(null)
    val allSessionsNextCursor: StateFlow<String?> = _allSessionsNextCursor.asStateFlow()

    private val _allSessionsError = MutableStateFlow<String?>(null)
    val allSessionsError: StateFlow<String?> = _allSessionsError.asStateFlow()

    private val _allSessionsLoading = MutableStateFlow(false)
    val allSessionsLoading: StateFlow<Boolean> = _allSessionsLoading.asStateFlow()

    // Tracks an in-flight OPEN_SESSION_GLOBAL separately from the fetch-list
    // loading/error state above — opening a session (which restarts opencode
    // serve bound to a different workspace on the bridge) is not instant, and
    // the UI needs its own loading/error signal so a row tap can show a
    // spinner and reject a second tap while it's in flight (Phase 4.4/4.6).
    private val _openGlobalSessionLoading = MutableStateFlow(false)
    val openGlobalSessionLoading: StateFlow<Boolean> = _openGlobalSessionLoading.asStateFlow()

    private val _openGlobalSessionError = MutableStateFlow<String?>(null)
    val openGlobalSessionError: StateFlow<String?> = _openGlobalSessionError.asStateFlow()

    private val _terminalOutput = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    // NOTIFY frames (bridge/main.js notifyExternal) — delivered only while this
    // connection is live, not a real background push (see the bridge-side comment
    // at its call site). A SharedFlow, not StateFlow: each notification is a
    // one-shot event, not persistent state a late subscriber should replay.
    private val _notifyEvents = MutableSharedFlow<NotifyPayload>(extraBufferCapacity = 20)
    val notifyEvents: SharedFlow<NotifyPayload> = _notifyEvents.asSharedFlow()

    private val _tasks = MutableStateFlow<Map<String, TaskDto>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskDto>> = _tasks.asStateFlow()

    private val _gitStatus = MutableStateFlow<GitStatusDto?>(null)
    val gitStatus: StateFlow<GitStatusDto?> = _gitStatus.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceDto>>(emptyList())
    val devices: StateFlow<List<DeviceDto>> = _devices.asStateFlow()

    private val _auditLog = MutableStateFlow<List<AuditLogEntryDto>>(emptyList())
    val auditLog: StateFlow<List<AuditLogEntryDto>> = _auditLog.asStateFlow()

    // Set when the bridge's CONNECTED payload carries a freshly issued
    // per-device token (Task 8.1) in place of the pairing secret just used to
    // connect — the caller (MainShell, which owns tokenStorage)
    // observes this to persist the new token so future connects use it
    // instead of the (possibly now-invalid) pairing secret.
    private val _issuedToken = MutableStateFlow<String?>(null)
    val issuedToken: StateFlow<String?> = _issuedToken.asStateFlow()

    // deviceId from the CONNECTED handshake, when the bridge includes it — the only
    // genuine way to tell which DEVICE_LIST entry is this phone (never guessed from
    // the display name).
    private val _currentDeviceId = MutableStateFlow<String?>(null)
    val currentDeviceId: StateFlow<String?> = _currentDeviceId.asStateFlow()

    // Most recent post-handshake ERROR frame (e.g. a rejected git subcommand or a
    // path-escape attempt) so the UI can surface it instead of only logging it.
    private val _lastError = MutableStateFlow<BridgeError?>(null)
    val lastError: StateFlow<BridgeError?> = _lastError.asStateFlow()
    private var errorSeq = 0L

    // Run log: genuine activity observed on this connection (see RunLogRecorder).
    private val runLog = RunLogRecorder(clock)
    val runEvents: StateFlow<List<RunEvent>> = runLog.events
    val runStartedAt: StateFlow<Long?> = runLog.runStartedAt

    // TERMINAL_OUTPUT is a no-replay SharedFlow; the transcript keeps the accumulated
    // lines here so the Terminal screen survives navigation without losing output.
    private val terminal = TerminalTranscript()
    val terminalLines: StateFlow<List<TerminalLine>> = terminal.lines
    val terminalHistory: StateFlow<List<String>> = terminal.history

    // The bridge's FILE_CONTENT reply carries no path, only the raw text — remember
    // what we last asked for so the UI-facing FileContentDto can be paired up.
    private var pendingFileRequestPath: String? = null

    // Settings-gated raw frame trace, set by the caller (MainActivity, from
    // SettingsRepository) rather than read from DataStore in here — this class has
    // no Android Context and stays free of storage concerns.
    var traceEnabled: Boolean = false

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
                            val connected = wsFrame.decodePayload<ConnectedPayload>(json)
                            connected?.deviceId?.let { _currentDeviceId.value = it }
                            connected?.issuedToken?.let { fresh ->
                                if (fresh != token) {
                                    token = fresh
                                    _issuedToken.value = fresh
                                }
                            }
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
            if (traceEnabled) Log.d(TRACE_TAG, "<< $text")
            val frame = json.decodeFromString<WebSocketFrame>(text)
            when (frame.eventType) {
                "WORKSPACE_LIST" -> frame.decodePayload<List<WorkspaceDto>>(json)?.let { _workspaces.value = it }
                "WORKSPACE_OPENED" -> frame.decodePayload<OpenWorkspacePayload>(json)?.let {
                    _activeWorkspace.value = it.path
                    _fileTree.value = emptyList() // stale tree belongs to the old workspace; re-fetch on next Files visit
                }
                "CHAT_MESSAGE" -> frame.decodePayload<BridgeChatMessageDto>(json)?.let { msg ->
                    val turnTools = _streamingMessage.value?.toolSteps.orEmpty()
                    _chatMessages.update { it + msg.toUi().copy(tools = turnTools) }
                    _streamingMessage.value = null // final message landed; turn is over
                }
                "STREAM_TEXT_DELTA" -> frame.decodePayload<StreamTextDeltaDto>(json)?.let { delta ->
                    _streamingMessage.update { (it ?: StreamingMessageDto()).copy(text = it?.text.orEmpty() + delta.text) }
                    runLog.streamActivity()
                }
                "STREAM_TOOL_CALL" -> frame.decodePayload<StreamToolCallDto>(json)?.let { call ->
                    val target = extractToolTarget(call.input)
                    _streamingMessage.update { current ->
                        val base = current ?: StreamingMessageDto()
                        base.copy(
                            runningTool = call.tool,
                            toolSteps = (base.toolSteps + ToolStep(call.tool, target)).takeLast(MAX_TOOL_STEPS)
                        )
                    }
                    runLog.toolCall(call.tool, target)
                }
                "STREAM_TOOL_RESULT" -> frame.decodePayload<StreamToolResultDto>(json)?.let { result ->
                    val failed = isErrorToolOutput(result.output)
                    _streamingMessage.update { current ->
                        val base = current ?: StreamingMessageDto()
                        base.copy(runningTool = null, toolSteps = closeToolStep(base.toolSteps, result.tool, failed))
                    }
                    runLog.toolResult(result.tool, failed)
                }
                "SESSION_LIST" -> frame.decodePayload<List<SessionDto>>(json)?.let { _sessions.value = it }
                "SESSION_SWITCHED" -> frame.decodePayload<SessionSwitchedDto>(json)?.let { _activeSessionId.value = it.id }
                "ALL_SESSIONS_LIST" -> {
                    _allSessionsLoading.value = false
                    frame.decodePayload<AllSessionsListDto>(json)?.let {
                        _allSessions.value = it.sessions
                        _allSessionsNextCursor.value = it.nextCursor
                        _allSessionsError.value = null
                    }
                }
                // Reuses OPEN_WORKSPACE's WORKSPACE_OPENED stale-tree-clear (this
                // frame changes activeWorkspace too, via OPEN_SESSION_GLOBAL on the
                // bridge) plus SESSION_SWITCHED's activeSessionId update — see
                // adr/0002-global-cross-workspace-session-search.md. Applying only
                // what each of those two frames already does individually, not new
                // scope, per the Files-bug lesson about not leaving a stale-cache
                // path unhandled on a workspace-changing frame.
                "SESSION_OPENED" -> frame.decodePayload<SessionOpenedDto>(json)?.let {
                    _openGlobalSessionLoading.value = false
                    _activeWorkspace.value = it.worktree
                    _fileTree.value = emptyList()
                    _activeSessionId.value = it.id
                }
                "PERMISSION_REQUEST" -> frame.decodePayload<PermissionRequestDto>(json)?.let {
                    _pendingPermission.value = it
                    runLog.permissionRequested(it.tool)
                }
                "QUESTION_REQUEST" -> frame.decodePayload<QuestionRequestDto>(json)?.let {
                    _pendingQuestion.value = it
                    runLog.questionAsked(it.text)
                }
                "AGENT_STATE" -> frame.decodePayload<String>(json)?.let {
                    _agentState.value = it
                    if (it.equals("Idle", ignoreCase = true)) _streamingMessage.value = null
                    runLog.agentState(it)
                }
                // Bridge sends one root FileNodeDto (see bridge/main.js FETCH_FILE_TREE), not
                // a list — the explorer renders the workspace's children flat, without a
                // wrapping root row (see flattenTree in FileTreeRows.kt).
                "FILE_TREE" -> frame.decodePayload<FileNodeDto>(json)?.let { _fileTree.value = it.children ?: emptyList() }
                "FILE_CONTENT" -> frame.decodePayload<String>(json)?.let { content ->
                    _fileContent.value = FileContentDto(path = pendingFileRequestPath ?: "", content = content)
                }
                "FILE_DIFF" -> frame.decodePayload<BridgeFileDiffDto>(json)?.let { diff ->
                    val entry = FileDiffDto(filePath = diff.fileName, patch = diff.diffText)
                    setPendingDiffs { list -> list.filterNot { it.filePath == entry.filePath } + entry }
                    runLog.diffProposed(entry.filePath)
                }
                "TERMINAL_OUTPUT" -> frame.decodePayload<String>(json)?.let {
                    terminal.appendOutput(it)
                    _terminalOutput.emit(it)
                }
                "TASK_UPDATED" -> frame.decodePayload<TaskDto>(json)?.let { task ->
                    _tasks.update { it + (task.id to task) }
                    runLog.task(task)
                }
                "TASK_REMOVED" -> frame.decodePayload<String>(json)?.let { id ->
                    _tasks.update { it - id }
                    runLog.taskRemoved(id)
                }
                "GIT_STATUS" -> frame.decodePayload<GitStatusDto>(json)?.let { _gitStatus.value = it }
                "DEVICE_LIST" -> frame.decodePayload<List<DeviceDto>>(json)?.let { _devices.value = it }
                "AUDIT_LOG" -> frame.decodePayload<List<AuditLogEntryDto>>(json)?.let { _auditLog.value = it }
                "NOTIFY" -> frame.decodePayload<NotifyPayload>(json)?.let { _notifyEvents.emit(it) }
                "ERROR" -> {
                    val msg = frame.decodePayload<ErrorPayload>(json)?.message ?: "Unknown error"
                    _lastError.value = BridgeError(++errorSeq, msg, clock())
                    Log.e("RemoteSessionManager", "Server sent error: $msg")
                    // FETCH_ALL_SESSIONS/OPEN_SESSION_GLOBAL failures arrive as a
                    // generic ERROR frame (no eventType correlation on the wire),
                    // so any ERROR while a global-sessions request was in flight is
                    // attributed to it — surfaced via allSessionsError instead of
                    // only the generic lastError, since the dedicated screen needs
                    // an inline message, not a toast-style one-shot.
                    if (_allSessionsLoading.value) {
                        _allSessionsLoading.value = false
                        _allSessionsError.value = msg
                    }
                    if (_openGlobalSessionLoading.value) {
                        _openGlobalSessionLoading.value = false
                        _openGlobalSessionError.value = msg
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteSessionManager", "Error handling message", e)
        }
    }

    private fun sendRaw(eventType: String, payload: JsonElement? = null) {
        scope.launch {
            try {
                val encoded = json.encodeToString(WebSocketFrame(eventType, payload))
                if (traceEnabled) Log.d(TRACE_TAG, ">> $encoded")
                ws.send(encoded)
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
        runLog.promptSent(text)
        sendString("PROMPT", text)
    }

    fun openWorkspace(path: String) = sendString("OPEN_WORKSPACE", path)

    fun fetchWorkspaces() = sendRaw("FETCH_WORKSPACES")

    /** Registers a directory in the bridge's workspace list without
     *  switching to it (Task 6.2.1) — the bridge replies with an updated
     *  WORKSPACE_LIST, which [workspaces] already reflects. */
    fun addWorkspace(path: String) = sendString("ADD_WORKSPACE", path)

    fun fetchFileTree() = sendRaw("FETCH_FILE_TREE")

    fun fetchFile(path: String) {
        pendingFileRequestPath = path
        sendString("FETCH_FILE", path)
    }

    fun acceptDiff(filePath: String) {
        sendString("ACCEPT_DIFF", filePath)
        setPendingDiffs { list -> list.filterNot { it.filePath == filePath } }
        runLog.diffKept(filePath)
    }

    fun rejectDiff(filePath: String) {
        sendString("REJECT_DIFF", filePath)
        setPendingDiffs { list -> list.filterNot { it.filePath == filePath } }
        runLog.diffReverted(filePath)
    }

    /** decision: bridge forwards this string verbatim as {decision} to
     *  POST /permission/{id}/reply — e.g. "allow" or "deny". */
    fun replyPermission(permissionId: String, decision: String) {
        sendRaw("PERMISSION_REPLY", json.encodeToJsonElement(PermissionReplyPayload(permissionId, decision)))
        val pending = _pendingPermission.value
        if (pending?.permissionId == permissionId) _pendingPermission.value = null
        runLog.permissionAnswered(
            PermissionDecision.fromWire(decision)?.label ?: "Answered: $decision",
            pending?.takeIf { it.permissionId == permissionId }?.tool
        )
    }

    fun replyQuestion(questionId: String, answer: String) {
        sendRaw("QUESTION_REPLY", json.encodeToJsonElement(QuestionReplyPayload(questionId, answer)))
        if (_pendingQuestion.value?.questionId == questionId) _pendingQuestion.value = null
        runLog.questionAnswered(answer)
    }

    fun listSessions() = sendRaw("LIST_SESSIONS")

    fun switchSession(sessionId: String) = sendString("SWITCH_SESSION", sessionId)

    fun newSession(title: String? = null) {
        sendRaw("NEW_SESSION", json.encodeToJsonElement(NewSessionPayload(title)))
    }

    fun forkSession(sessionId: String) = sendString("FORK_SESSION", sessionId)

    /** ADR-0002: fetches the global, cross-workspace session list, read
     *  directly from OpenCode's own on-disk session database — independent
     *  of [activeWorkspace]. Pass `cursor` (from [allSessionsNextCursor]) to
     *  fetch the next page; response replaces [allSessions] wholesale, it is
     *  not appended automatically. */
    fun fetchAllSessions(cursor: String? = null) {
        _allSessionsLoading.value = true
        _allSessionsError.value = null
        // A fresh list fetch is the user starting over — an earlier
        // OPEN_SESSION_GLOBAL failure must not keep GlobalSessionsScreen
        // stuck on its error state forever (found via on-device testing:
        // openGlobalSessionError otherwise never clears, so a screen that
        // hit an open error stays on it even after a successful re-fetch).
        _openGlobalSessionError.value = null
        sendRaw("FETCH_ALL_SESSIONS", json.encodeToJsonElement(FetchAllSessionsPayload(cursor = cursor)))
    }

    /** ADR-0002: opens a session found via [fetchAllSessions], switching
     *  [activeWorkspace] to `worktree` (which may differ from the current
     *  one) and restarting the bridge's opencode-serve process bound there —
     *  not instant, hence [openGlobalSessionLoading]. */
    fun openGlobalSession(id: String, worktree: String) {
        _openGlobalSessionLoading.value = true
        _openGlobalSessionError.value = null
        sendRaw("OPEN_SESSION_GLOBAL", json.encodeToJsonElement(OpenSessionGlobalPayload(id, worktree)))
    }

    fun runTerminal(command: String) {
        terminal.appendCommand(command)
        sendString("TERMINAL", command)
    }

    /** Clears the local transcript only; nothing is sent to the bridge. */
    fun clearTerminalTranscript() = terminal.clear()

    /** Resizes the active PTY-backed terminal (Task 4.2.2) — call this from a
     *  real layout/size-change callback (see TerminalScreen's onSizeChanged),
     *  not on a timer or as dead code. */
    fun resizeTerminal(cols: Int, rows: Int) {
        sendRaw("TERMINAL_RESIZE", json.encodeToJsonElement(TerminalResizePayload(cols, rows)))
    }

    fun killTask(taskId: String) {
        runLog.stopRequested(_tasks.value[taskId]?.name ?: taskId)
        sendString("KILL_TASK", taskId)
    }

    fun runGit(command: String) {
        runLog.gitCommand(command)
        sendString("GIT", command)
    }

    fun clearLastError() {
        _lastError.value = null
    }

    /** Task 8.2.2: lists paired devices (never exposes raw tokens — see
     *  [DeviceDto]); response lands in [devices]. */
    fun listDevices() = sendRaw("LIST_DEVICES")

    /** Task 8.2.1/8.2.2: revokes a paired device's token, closing its live
     *  connection (if any) and rejecting any future reconnect with it. */
    fun revokeToken(deviceId: String) = sendString("REVOKE_TOKEN", deviceId)

    /** Task 8.4.2: fetches recent audit log entries; response lands in [auditLog]. */
    fun fetchAuditLog() = sendRaw("FETCH_AUDIT_LOG")

    fun disconnect() {
        ws.disconnect()
    }

    private fun closeToolStep(steps: List<ToolStep>, tool: String, failed: Boolean): List<ToolStep> {
        var idx = steps.indexOfLast { !it.done && it.tool == tool }
        if (idx < 0) idx = steps.indexOfLast { !it.done }
        if (idx < 0) return steps
        return steps.toMutableList().also { it[idx] = it[idx].copy(done = true, failed = failed) }
    }

    private companion object {
        const val MAX_TOOL_STEPS = 50
        const val TRACE_TAG = "RemoteSessionManager.Trace"
    }
}

/** A post-handshake ERROR frame surfaced to the UI. [id] increases per error so the
 *  same message arriving twice is still shown twice. */
data class BridgeError(val id: Long, val message: String, val receivedAt: Long)
