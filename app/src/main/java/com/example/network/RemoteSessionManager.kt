package com.example.network

import android.util.Log
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.network.protocol.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

// Using ConnectionState from com.example.network.ConnectionState

object RemoteSessionManager {
    private val _status = MutableStateFlow(ConnectionState.DISCONNECTED)
    val status: StateFlow<ConnectionState> = _status.asStateFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    
    private var baseUrl = "http://10.0.2.2:8080"
    private var wsUrl = "ws://10.0.2.2:8080/ws"
    private var currentIp = "10.0.2.2"

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _activeWorkspace = MutableStateFlow<Workspace?>(null)
    val activeWorkspace: StateFlow<Workspace?> = _activeWorkspace.asStateFlow()

    private val _fileTree = MutableStateFlow<FileNode?>(null)
    val fileTree: StateFlow<FileNode?> = _fileTree.asStateFlow()
    
    private val _selectedFileContent = MutableStateFlow<String?>(null)
    val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    
    private val _agentState = MutableStateFlow<String>("Idle")
    val agentState: StateFlow<String> = _agentState.asStateFlow()

    private val _fileDiffs = MutableStateFlow<List<FileDiff>>(emptyList())
    val fileDiffs: StateFlow<List<FileDiff>> = _fileDiffs.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<String>>(emptyList())
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    private val _runningTasks = MutableStateFlow<List<RunningTask>>(emptyList())
    val runningTasks: StateFlow<List<RunningTask>> = _runningTasks.asStateFlow()

    private val _gitStatus = MutableStateFlow<GitStatus?>(null)
    val gitStatus: StateFlow<GitStatus?> = _gitStatus.asStateFlow()

    fun connect(ip: String) {
        currentIp = ip
        baseUrl = "http://$ip:8080"
        wsUrl = "ws://$ip:8080/ws"
        startConnectionLoop()
    }

    private fun startConnectionLoop() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var retryDelay = 1000L
            val maxDelay = 30000L
            
            while (isActive) {
                _status.value = ConnectionState.CONNECTING
                try {
                    KtorClient.client.webSocket(urlString = wsUrl) {
                        webSocketSession = this
                        retryDelay = 1000L // Reset on successful connect
                        Log.d("RemoteSession", "Socket open, sending CONNECT handshake")
                        val connectPayload = buildJsonObject {
                            put("deviceName", android.os.Build.MODEL ?: "Android")
                            put("token", KtorClient.getApiKey())
                        }
                        sendCommandOnSession(this, "CONNECT", connectPayload)

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    Log.d("RemoteSession", "Received: $text")
                                    try {
                                        val jsonFrame = Json.decodeFromString<WebSocketFrame>(text)
                                        handleIncomingFrame(jsonFrame)
                                    } catch (e: Exception) {
                                        Log.e("RemoteSession", "Failed to parse frame: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RemoteSession", "WebSocket error: ${e.message}")
                        } finally {
                            webSocketSession = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RemoteSession", "Connection failed: ${e.message}")
                    _status.value = ConnectionState.ERROR
                }
                
                // If we get here, connection was lost or failed to connect
                _status.value = ConnectionState.RECONNECTING
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
            }
        }
    }
    
    private suspend fun sendCommandOnSession(session: DefaultClientWebSocketSession, eventType: String, payload: JsonElement? = null) {
        try {
            val frame = WebSocketFrame(eventType, payload)
            val json = Json.encodeToString(frame)
            Log.d("RemoteSession", "Sending: $json")
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            Log.e("RemoteSession", "Failed to send command: ${e.message}")
        }
    }

    private fun handleIncomingFrame(frame: WebSocketFrame) {
        when (frame.eventType) {
            "CONNECTED" -> {
                _status.value = ConnectionState.CONNECTED
                Log.d("RemoteSession", "Handshake accepted by bridge")
                fetchWorkspaces()
            }
            "ERROR" -> {
                val message = frame.payload?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                Log.e("RemoteSession", "Bridge error: ${message ?: frame.payload}")
            }
            "AGENT_STATE" -> {
                frame.payload?.jsonPrimitive?.contentOrNull?.let { _agentState.value = it }
            }
            "CHAT_MESSAGE" -> {
                frame.payload?.let {
                    val msg = Json.decodeFromJsonElement<ChatMessageDto>(it)
                    _chatMessages.value = (_chatMessages.value + ChatMessage(msg.id, msg.text, msg.isUser, msg.actionDescription, null, msg.hasDetails)).takeLast(500)
                }
            }
            "FILE_DIFF" -> {
                frame.payload?.let {
                    val diff = Json.decodeFromJsonElement<DiffPatchDto>(it)
                    _fileDiffs.value = _fileDiffs.value.filter { d -> d.fileName != diff.fileName } + FileDiff(diff.fileName, diff.diffText)
                }
            }
            "TERMINAL_OUTPUT" -> {
                frame.payload?.jsonPrimitive?.contentOrNull?.let { _terminalLines.value = (_terminalLines.value + it).takeLast(1000) }
            }
            "TASK_UPDATED" -> {
                frame.payload?.let {
                    val task = Json.decodeFromJsonElement<TaskProcessDto>(it)
                    val current = _runningTasks.value.toMutableList()
                    val index = current.indexOfFirst { t -> t.id == task.id }
                    val runningTask = RunningTask(task.id, task.name, task.port, task.status)
                    if (index >= 0) {
                        current[index] = runningTask
                    } else {
                        current.add(runningTask)
                    }
                    _runningTasks.value = current
                }
            }
            "TASK_REMOVED" -> {
                frame.payload?.jsonPrimitive?.contentOrNull?.let { id ->
                    _runningTasks.value = _runningTasks.value.filter { it.id != id }
                }
            }
            "GIT_STATUS" -> {
                frame.payload?.let {
                    val status = Json.decodeFromJsonElement<GitStatusDto>(it)
                    _gitStatus.value = GitStatus(status.branch, status.modifiedFiles, status.addedFiles, status.deletedFiles, status.canPush, status.canPull)
                }
            }
            "WORKSPACE_LIST" -> {
                frame.payload?.let {
                    val list = Json.decodeFromJsonElement<List<WorkspaceDto>>(it)
                    _workspaces.value = list.map { w -> Workspace(w.id, w.name, w.path) }
                }
            }
            "FILE_TREE" -> {
                frame.payload?.let {
                    val root = Json.decodeFromJsonElement<FileNodeDto>(it)
                    _fileTree.value = mapFileNode(root, "root")
                }
            }
            "FILE_CONTENT" -> {
                frame.payload?.jsonPrimitive?.contentOrNull?.let { _selectedFileContent.value = it }
            }
        }
    }
    
    private fun mapFileNode(dto: FileNodeDto, id: String): FileNode {
        return FileNode(
            id = id,
            name = dto.name,
            isFolder = dto.isDirectory,
            size = "",
            path = dto.path,
            children = dto.children?.mapIndexed { idx, child -> mapFileNode(child, "${id}_$idx") }
        )
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.launch {
            try {
                webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            } catch (e: Exception) {
                Log.e("RemoteSession", "Error during disconnect: ${e.message}")
            } finally {
                webSocketSession = null
                _status.value = ConnectionState.DISCONNECTED
            }
        }
    }

    suspend fun sendCommand(eventType: String, payload: JsonElement? = null) {
        if (_status.value == ConnectionState.CONNECTED) {
            try {
                val frame = WebSocketFrame(eventType, payload)
                val json = Json.encodeToString(frame)
                Log.d("RemoteSession", "Sending: $json")
                webSocketSession?.send(Frame.Text(json))
            } catch (e: Exception) {
                Log.e("RemoteSession", "Failed to send command: ${e.message}")
            }
        } else {
            Log.e("RemoteSession", "Cannot send command, disconnected")
        }
        
    }

    fun openWorkspace(workspace: Workspace) {
        _activeWorkspace.value = workspace
        scope.launch {
            sendCommand("OPEN_WORKSPACE", JsonPrimitive(workspace.path))
        }
    }
    
    fun acceptDiff(fileName: String) {
        _fileDiffs.value = _fileDiffs.value.filter { it.fileName != fileName }
        scope.launch { sendCommand("ACCEPT_DIFF", JsonPrimitive(fileName)) }
    }
    

    fun acceptHunk(fileName: String, hunkIndex: Int) {
        scope.launch { 
            try {
                val payload = buildJsonObject {
                    put("fileName", fileName)
                    put("hunkIndex", hunkIndex)
                }
                sendCommand("ACCEPT_HUNK", payload)
            } catch (e: Exception) {
                Log.e("RemoteSession", "Failed to accept hunk: ${e.message}")
            }
        }
    }
    
    fun rejectHunk(fileName: String, hunkIndex: Int) {
        scope.launch { 
            try {
                val payload = buildJsonObject {
                    put("fileName", fileName)
                    put("hunkIndex", hunkIndex)
                }
                sendCommand("REJECT_HUNK", payload)
            } catch (e: Exception) {
                Log.e("RemoteSession", "Failed to reject hunk: ${e.message}")
            }
        }
    }

    fun rejectDiff(fileName: String) {
        _fileDiffs.value = _fileDiffs.value.filter { it.fileName != fileName }
        scope.launch { sendCommand("REJECT_DIFF", JsonPrimitive(fileName)) }
    }
    

    fun resizeTerminal(cols: Int, rows: Int) {
        scope.launch { 
            try {
                val payload = buildJsonObject {
                    put("cols", cols)
                    put("rows", rows)
                }
                sendCommand("TERMINAL_RESIZE", payload)
            } catch (e: Exception) {
                Log.e("RemoteSession", "Failed to resize terminal: ${e.message}")
            }
        }
    }

    fun sendTerminalCommand(command: String) {
        _terminalLines.value = (_terminalLines.value + "> $command").takeLast(1000)
        scope.launch { 
            sendCommand("TERMINAL", JsonPrimitive(command))
        }
    }
    
    fun killTask(taskId: String) {
        _runningTasks.value = _runningTasks.value.filter { it.id != taskId }
        scope.launch { sendCommand("KILL_TASK", JsonPrimitive(taskId)) }
    }
    
    fun sendGitCommand(command: String) {
        scope.launch { 
            sendCommand("GIT", JsonPrimitive(command))
        }
    }

    fun fetchWorkspaces() {
        scope.launch {
            sendCommand("FETCH_WORKSPACES")
        }
    }

    fun fetchFileTree() {
        scope.launch {
            sendCommand("FETCH_FILE_TREE")
        }
    }

    fun fetchFileContent(path: String) {
        _selectedFileContent.value = null
        scope.launch {
            sendCommand("FETCH_FILE", JsonPrimitive(path))
        }
    }
}
