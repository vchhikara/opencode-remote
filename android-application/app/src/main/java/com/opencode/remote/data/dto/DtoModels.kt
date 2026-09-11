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
data class ConnectedPayload(
    val sessionId: String,
    val deviceName: String,
    val deviceId: String? = null,
    /** Present when the bridge just issued a fresh per-device token in
     *  place of the pairing token that was used to connect (Task 8.1) — the
     *  client must persist this and use it on every future connect, since
     *  the pairing token may not authenticate again on its own. */
    val issuedToken: String? = null
)

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
    val timestamp: Long = System.currentTimeMillis(),
    /** Tool calls streamed during the turn that produced this message (from
     *  STREAM_TOOL_CALL/STREAM_TOOL_RESULT), carried over when the final CHAT_MESSAGE
     *  lands so the conversation can keep showing what the agent actually touched. */
    val tools: List<ToolStep> = emptyList()
)

/** One streamed tool invocation: the tool name and, when its input carried one, the
 *  file/command/pattern it targeted (see data/run/ToolTargets.kt). Not [Serializable] —
 *  assembled locally from STREAM_TOOL_* frames. */
data class ToolStep(
    val tool: String,
    val target: String? = null,
    val done: Boolean = false,
    val failed: Boolean = false
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

/** Wire shape of the bridge's STREAM_TEXT_DELTA payload (main.js relaySseEvent). */
@Serializable
data class StreamTextDeltaDto(val sessionId: String? = null, val text: String)

/** Wire shape of the bridge's STREAM_TOOL_CALL payload (main.js relaySseEvent). Input
 *  is opencode's free-form tool-input object — not typed further, just shown. */
@Serializable
data class StreamToolCallDto(val sessionId: String? = null, val tool: String, val input: kotlinx.serialization.json.JsonElement? = null)

/** Wire shape of the bridge's STREAM_TOOL_RESULT payload. `output` is a string on
 *  success but may be an error object per opencode's ToolStateError — decoded loosely
 *  as JsonElement and rendered as text either way. */
@Serializable
data class StreamToolResultDto(val sessionId: String? = null, val tool: String, val output: kotlinx.serialization.json.JsonElement? = null)

/** UI-facing view of an in-progress assistant turn: accumulated streamed text plus
 *  which tool (if any) is currently running. Cleared once the final CHAT_MESSAGE for
 *  this turn lands (see RemoteSessionManager). Not [Serializable] — built up locally
 *  from StreamTextDeltaDto/StreamToolCallDto/StreamToolResultDto frames, never decoded
 *  directly off the wire. */
data class StreamingMessageDto(
    val text: String = "",
    val runningTool: String? = null,
    /** Every tool call seen so far in this turn, oldest first (bounded). */
    val toolSteps: List<ToolStep> = emptyList()
)

/** Wire shape of the bridge's PERMISSION_REQUEST payload (main.js relaySseEvent). */
@Serializable
data class PermissionRequestDto(
    val permissionId: String,
    val sessionId: String? = null,
    val tool: String? = null
)

/** Wire shape of the bridge's QUESTION_REQUEST payload (main.js relaySseEvent). */
@Serializable
data class QuestionRequestDto(
    val questionId: String,
    val sessionId: String? = null,
    val text: String? = null,
    val options: List<String>? = null
)

/**
 * The permission decisions this client offers, mapped to the exact decision strings the
 * pre-redesign client already sent in PERMISSION_REPLY ("allow" / "always" / "deny").
 * The bridge forwards the string verbatim; whether "always" persists is decided entirely
 * server-side — the app keeps no permission memory of its own.
 */
enum class PermissionDecision(val wire: String, val label: String) {
    AllowOnce("allow", "Allowed once"),
    AlwaysAllow("always", "Always allowed"),
    Deny("deny", "Denied");

    companion object {
        fun fromWire(value: String): PermissionDecision? = entries.firstOrNull { it.wire == value }
    }
}

/** Outbound wire shape for RemoteSessionManager.replyPermission's PERMISSION_REPLY
 *  frame (main.js reads payload.permissionId/payload.decision). */
@Serializable
data class PermissionReplyPayload(val permissionId: String, val decision: String)

/** Outbound wire shape for RemoteSessionManager.replyQuestion's QUESTION_REPLY frame
 *  (main.js reads payload.questionId/payload.answer). */
@Serializable
data class QuestionReplyPayload(val questionId: String, val answer: String)

/** Wire shape of the bridge's SESSION_LIST payload entries (main.js LIST_SESSIONS). */
@Serializable
data class SessionDto(val id: String, val title: String? = null, val updatedAt: Long? = null)

/** Wire shape of the bridge's SESSION_SWITCHED payload (main.js NEW_SESSION /
 *  SWITCH_SESSION). */
@Serializable
data class SessionSwitchedDto(val id: String)

/** Outbound wire shape for RemoteSessionManager.newSession's NEW_SESSION frame
 *  (main.js reads payload.title, optional). */
@Serializable
data class NewSessionPayload(val title: String? = null)

/** Outbound wire shape for RemoteSessionManager.resizeTerminal's TERMINAL_RESIZE
 *  frame (main.js reads payload.cols/payload.rows, resizes the active PTY —
 *  Task 4.2.2). */
@Serializable
data class TerminalResizePayload(val cols: Int, val rows: Int)

/** Wire shape of the bridge's DEVICE_LIST payload entries (main.js LIST_DEVICES,
 *  Task 8.2.2) — never carries a raw token, only what's needed to identify and
 *  revoke a paired device. */
@Serializable
data class DeviceDto(val deviceId: String, val deviceName: String, val issuedAt: Long)

/** Wire shape of one bridge AUDIT_LOG entry (main.js FETCH_AUDIT_LOG, Task 8.4.2).
 *  `detail` is free-form per `kind` (e.g. git_command has a `command` field,
 *  permission_decision has `permissionId`/`decision`) — decoded loosely and
 *  rendered as text rather than typed further. */
@Serializable
data class AuditLogEntryDto(
    val timestamp: String,
    val kind: String,
    val detail: kotlinx.serialization.json.JsonElement? = null,
    val deviceId: String? = null,
    val deviceName: String? = null
)

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
