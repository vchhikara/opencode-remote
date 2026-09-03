package com.example

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.architecture.*
import com.example.network.RemoteSessionManager
import com.example.network.ConnectionState
import com.example.network.Workspace
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class Navigate(val route: String) : UiEvent
    object NavigateBack : UiEvent
    object OpenDrawer : UiEvent
    object CloseDrawer : UiEvent
}

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state: StateFlow<GenerationState> = _state

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    val connectionStatus: StateFlow<ConnectionState> = RemoteSessionManager.status
    val workspaces: StateFlow<List<Workspace>> = RemoteSessionManager.workspaces
    val activeWorkspace: StateFlow<Workspace?> = RemoteSessionManager.activeWorkspace
    
    val fileTree = RemoteSessionManager.fileTree
    val selectedFileContent = RemoteSessionManager.selectedFileContent
    val chatMessages = RemoteSessionManager.chatMessages
    val agentState = RemoteSessionManager.agentState
    val fileDiffs = RemoteSessionManager.fileDiffs
    val terminalLines = RemoteSessionManager.terminalLines
    val runningTasks = RemoteSessionManager.runningTasks
    val gitStatus = RemoteSessionManager.gitStatus

    private val dateFormat = SimpleDateFormat("[HH:mm:ss]", Locale.getDefault())

    fun dispatch(action: AppAction) {
        val startTime = System.currentTimeMillis()
        var result = "Success"
        
        viewModelScope.launch {
            try {
                processAction(action)
            } catch (e: Exception) {
                result = "Error: ${e.message}"
                _uiEvents.emit(UiEvent.ShowSnackbar(result))
            } finally {
                val duration = System.currentTimeMillis() - startTime
                val timeString = dateFormat.format(Date())
                val actionName = action.javaClass.simpleName
                Log.d("ActionDispatcher", "\n$timeString\nAction: $actionName\nResult: $result\nDuration: ${duration}ms\n")
            }
        }
    }

    private suspend fun processAction(action: AppAction) {
        when (action) {
            is NavigationAction.Navigate -> _uiEvents.emit(UiEvent.Navigate(action.route))
            is NavigationAction.NavigateBack -> _uiEvents.emit(UiEvent.NavigateBack)
            is NavigationAction.OpenDrawer -> _uiEvents.emit(UiEvent.OpenDrawer)
            is NavigationAction.CloseDrawer -> _uiEvents.emit(UiEvent.CloseDrawer)
            
            is PairingAction.SimulatePairing -> {
                RemoteSessionManager.connect("10.0.2.2")
                _uiEvents.emit(UiEvent.Navigate("workspaces"))
            }
            
            is PairingAction.ConnectManually -> {
                RemoteSessionManager.connect(action.ip)
                _uiEvents.emit(UiEvent.Navigate("workspaces"))
            }
            
            is PairingAction.Disconnect -> {
                RemoteSessionManager.disconnect()
            }
            
            // E6: device management (remember/rename/remove a paired device) is not
            // implemented - there is no persisted device list beyond the last-used IP.
            is PairingAction.RemoveDevice -> _uiEvents.emit(UiEvent.ShowSnackbar("Device management not implemented"))
            
            is WorkspaceAction.OpenWorkspace -> {
                RemoteSessionManager.openWorkspace(action.workspace)
                RemoteSessionManager.fetchFileTree()
            }
            
            is WorkspaceAction.FetchWorkspaces -> {
                RemoteSessionManager.fetchWorkspaces()
            }
            
            is ChatAction.SetChatMode -> {
                _uiEvents.emit(UiEvent.ShowSnackbar("Mode: ${action.mode.name}"))
            }
            is ChatAction.AttachFile -> _uiEvents.emit(UiEvent.ShowSnackbar("Attachment not implemented"))
            is ChatAction.StartVoiceInput -> _uiEvents.emit(UiEvent.ShowSnackbar("Voice input not implemented"))
            is ChatAction.SendPrompt -> {
                RemoteSessionManager.sendCommand("PROMPT", kotlinx.serialization.json.JsonPrimitive(action.prompt))
            }
            is ChatAction.UndoLastOperation -> _uiEvents.emit(UiEvent.ShowSnackbar("Undo coming soon"))
            is ChatAction.AcceptDiff -> _uiEvents.emit(UiEvent.ShowSnackbar("All changes accepted"))
            is ChatAction.ShowDetails -> _uiEvents.emit(UiEvent.ShowSnackbar("Opening details: ${action.details}"))
            is ChatAction.OpenPreview -> _uiEvents.emit(UiEvent.Navigate("preview"))
            is ChatAction.OpenShareMenu -> _uiEvents.emit(UiEvent.ShowSnackbar("Sharing not implemented"))
            is ChatAction.PublishConversation -> _uiEvents.emit(UiEvent.ShowSnackbar("Publishing to network..."))
            is ChatAction.OpenCode -> _uiEvents.emit(UiEvent.Navigate("repository"))
            is ChatAction.OpenFiles -> _uiEvents.emit(UiEvent.Navigate("repository"))
            is ChatAction.OpenHistory -> _uiEvents.emit(UiEvent.ShowSnackbar("Conversation history not synced — chat resets each session"))
            is ChatAction.OpenMoreMenu -> { /* Local UI State */ }
            
            is DiffAction.AcceptFileDiff -> {
                RemoteSessionManager.acceptDiff(action.file)
                _uiEvents.emit(UiEvent.ShowSnackbar("Accepted diff for ${action.file}"))
            }
            is DiffAction.RejectFileDiff -> {
                RemoteSessionManager.rejectDiff(action.file)
                _uiEvents.emit(UiEvent.ShowSnackbar("Rejected diff for ${action.file}"))
            }
            is DiffAction.AcceptHunk -> {
                RemoteSessionManager.acceptHunk(action.file, action.hunkIndex)
                _uiEvents.emit(UiEvent.ShowSnackbar("Accepted hunk ${action.hunkIndex} in ${action.file}"))
            }
            is DiffAction.RejectHunk -> {
                RemoteSessionManager.rejectHunk(action.file, action.hunkIndex)
                _uiEvents.emit(UiEvent.ShowSnackbar("Rejected hunk ${action.hunkIndex} in ${action.file}"))
            }
            
            is FileAction.OpenFile -> {
                RemoteSessionManager.fetchFileContent(action.path)
            }
            is FileAction.ToggleFolder -> { /* Usually local state but action logged */ }
            
            is TaskAction.StopTask -> RemoteSessionManager.killTask(action.taskId)
            
            is TerminalAction.SendCommand -> RemoteSessionManager.sendTerminalCommand(action.command)
            is TerminalAction.Resize -> RemoteSessionManager.resizeTerminal(action.cols, action.rows)
            
            is GitAction.OpenGitProvider -> _uiEvents.emit(UiEvent.ShowSnackbar("Connecting to ${action.providerName}..."))
            is GitAction.Pull -> RemoteSessionManager.sendGitCommand("pull")
            is GitAction.Push -> RemoteSessionManager.sendGitCommand("push")
            is GitAction.Fetch -> RemoteSessionManager.sendGitCommand("fetch")
            is GitAction.Commit -> RemoteSessionManager.sendGitCommand("commit -m \"${action.message}\"")
            is GitAction.Stash -> RemoteSessionManager.sendGitCommand("stash")
            is GitAction.StashPop -> RemoteSessionManager.sendGitCommand("stash pop")
            is GitAction.SwitchBranch -> RemoteSessionManager.sendGitCommand("checkout ${action.branch}")
            is GitAction.CreateBranch -> RemoteSessionManager.sendGitCommand("checkout -b ${action.branch}")
            is GitAction.CherryPick -> RemoteSessionManager.sendGitCommand("cherry-pick ${action.commitHash}")
            
            is SettingsAction.OpenSettings -> _uiEvents.emit(UiEvent.Navigate("settings"))
        }
    }

    fun reset() {
        _state.value = GenerationState.Idle
    }
}
