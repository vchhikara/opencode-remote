package com.example.architecture

sealed interface AppAction

sealed interface NavigationAction : AppAction {
    data class Navigate(val route: String) : NavigationAction
    object NavigateBack : NavigationAction
    object OpenDrawer : NavigationAction
    object CloseDrawer : NavigationAction
}

sealed interface PairingAction : AppAction {
    object SimulatePairing : PairingAction
    data class ConnectManually(val ip: String) : PairingAction
    data class RemoveDevice(val deviceId: String) : PairingAction
    object Disconnect : PairingAction
}

sealed interface WorkspaceAction : AppAction {
    data class OpenWorkspace(val workspace: com.example.network.Workspace) : WorkspaceAction
    object FetchWorkspaces : WorkspaceAction
}

enum class ChatMode { Build, Plan }

sealed interface ChatAction : AppAction {
    data class SetChatMode(val mode: ChatMode) : ChatAction
    object AttachFile : ChatAction
    object StartVoiceInput : ChatAction
    data class SendPrompt(val prompt: String) : ChatAction
    object UndoLastOperation : ChatAction
    object AcceptDiff : ChatAction
    data class ShowDetails(val details: String) : ChatAction
    object OpenPreview : ChatAction
    object OpenShareMenu : ChatAction
    object PublishConversation : ChatAction
    object OpenCode : ChatAction
    object OpenFiles : ChatAction
    object OpenHistory : ChatAction
    object OpenMoreMenu : ChatAction
}

sealed interface DiffAction : AppAction {
    data class AcceptFileDiff(val file: String) : DiffAction
    data class AcceptHunk(val file: String, val hunkIndex: Int) : DiffAction
    data class RejectHunk(val file: String, val hunkIndex: Int) : DiffAction
    data class RejectFileDiff(val file: String) : DiffAction
}

sealed interface FileAction : AppAction {
    data class OpenFile(val path: String) : FileAction
    data class ToggleFolder(val path: String) : FileAction
}

sealed interface TaskAction : AppAction {
    data class StopTask(val taskId: String) : TaskAction
}

sealed interface TerminalAction : AppAction {
    data class SendCommand(val command: String) : TerminalAction
    data class Resize(val cols: Int, val rows: Int) : TerminalAction
}

sealed interface GitAction : AppAction {
    data class OpenGitProvider(val providerName: String) : GitAction
    object Pull : GitAction
    object Push : GitAction
    object Fetch : GitAction
    data class Commit(val message: String) : GitAction
    object Stash : GitAction
    object StashPop : GitAction
    data class SwitchBranch(val branch: String) : GitAction
    data class CreateBranch(val branch: String) : GitAction
    data class CherryPick(val commitHash: String) : GitAction
}

sealed interface SettingsAction : AppAction {
    object OpenSettings : SettingsAction
}
