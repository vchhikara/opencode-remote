package com.opencode.remote.data.dto

import kotlinx.serialization.Serializable
import java.util.UUID

// Wire contract note: bridge/main.js is the single source of truth for event names
// and payload shapes (it is frozen/trusted infrastructure — not edited from the
// Android side). Several payloads it sends/expects are bare strings, not JSON
// objects; where the wire shape differs from what the UI wants, a `Bridge*` type
// models the wire shape exactly and a `toUi()`/mapping in RemoteSessionManager
// converts it. Keeping the two separate is what stops a bridge field rename from
// silently breaking Compose code (see CONTEXT.md).

@Serializable
data class ConnectPayload(val deviceName: String, val token: String)

@Serializable
data class ConnectedPayload(val sessionId: String, val deviceName: String)

@Serializable
data class ErrorPayload(val message: String)

@Serializable
data class WorkspaceDto(val id: String, val name: String, val path: String)

@Serializable
data class OpenWorkspacePayload(val path: String)

/** Who sent a [ChatMessageDto]. The bridge only ever pushes assistant messages; the
 *  user's own text is appended locally the moment it's sent (RemoteSessionManager.sendPrompt),
 *  since the bridge never echoes it back. */
enum class ChatRole { User, Assistant }

/** UI-facing chat message. Not [Serializable] — never decoded directly off the wire,
 *  see [BridgeChatMessageDto]. */
data class ChatMessageDto(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Exact wire shape of the bridge's CHAT_MESSAGE payload (main.js pushChatMessage). */
@Serializable
data class BridgeChatMessageDto(
    val id: String,
    val text: String,
    val isUser: Boolean = false,
    val actionDescription: String = "",
    val hasDetails: Boolean = false
) {
    fun toUi() = ChatMessageDto(
        id = id,
        role = if (isUser) ChatRole.User else ChatRole.Assistant,
        content = text
    )
}

@Serializable
data class FileNodeDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNodeDto>? = null
)

/** UI-facing file content. The bridge's FILE_CONTENT reply is a bare string with no
 *  path in it, so RemoteSessionManager pairs it with the path it just requested. */
data class FileContentDto(
    val path: String,
    val content: String
)

/** UI-facing pending diff — a filePath and one unified patch. The bridge never sends
 *  before/after file content, only the patch text; see [BridgeFileDiffDto]. */
data class FileDiffDto(
    val filePath: String,
    val patch: String
)

/** Exact wire shape of the bridge's FILE_DIFF payload (main.js pushFileDiff). */
@Serializable
data class BridgeFileDiffDto(val fileName: String, val diffText: String)

/** Typed view of [TaskDto.status] for exhaustive `when`s in UI code, instead of raw
 *  string comparisons that a bridge-side rename would silently break. */
enum class TaskStatus {
    RUNNING, COMPLETED, FAILED, UNKNOWN;

    companion object {
        fun of(raw: String) = when (raw.lowercase()) {
            "running" -> RUNNING
            "completed" -> COMPLETED
            "failed" -> FAILED
            else -> UNKNOWN
        }
    }
}

/** Matches the bridge's TASK_UPDATED payload exactly (main.js pushTaskUpdated) —
 *  note the field is `name`, not `command`, and there is no `startTime`. */
@Serializable
data class TaskDto(val id: String, val name: String, val port: Int? = null, val status: String)

/** Matches the bridge's GIT_STATUS payload exactly (main.js pushGitStatus). */
@Serializable
data class GitStatusDto(
    val branch: String = "main",
    val modifiedFiles: List<String> = emptyList(),
    val addedFiles: List<String> = emptyList(),
    val deletedFiles: List<String> = emptyList(),
    val canPush: Boolean = false,
    val canPull: Boolean = false
) {
    val isClean: Boolean get() = modifiedFiles.isEmpty() && addedFiles.isEmpty() && deletedFiles.isEmpty()
}
