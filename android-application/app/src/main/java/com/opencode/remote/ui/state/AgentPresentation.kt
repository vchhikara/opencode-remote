package com.opencode.remote.ui.state

import com.opencode.remote.data.dto.GitStatusDto
import com.opencode.remote.data.dto.PermissionRequestDto
import com.opencode.remote.data.dto.QuestionRequestDto
import com.opencode.remote.data.dto.StreamingMessageDto
import com.opencode.remote.data.run.AgentStateText
import com.opencode.remote.data.run.oneLine

/**
 * Presentation categories for the status card, derived — never decoded — from the
 * bridge's free-text AGENT_STATE plus the live streaming/permission/question flows.
 * These are *not* wire values: arbitrary future state strings still land in a
 * category (usually [Working]) and are shown verbatim as the card title.
 */
enum class AgentPhase { Idle, Thinking, Working, Waiting, Error }

data class StatusCopy(val eyebrow: String, val title: String, val detail: String)

/** Git facts for the idle status line; all optional because the bridge may not have
 *  pushed GIT_STATUS yet. */
data class RepoFacts(
    val branch: String?,
    val uncommittedFiles: Int?,
    val pendingDiffFiles: Int,
    val lastActivityAt: Long?
)

object AgentPresentation {

    fun phaseOf(
        rawState: String,
        streaming: StreamingMessageDto?,
        permission: PermissionRequestDto?,
        question: QuestionRequestDto?
    ): AgentPhase {
        if (permission != null || question != null) return AgentPhase.Waiting
        if (streaming != null) {
            return if (streaming.runningTool != null || streaming.text.isNotEmpty() || streaming.toolSteps.isNotEmpty()) {
                AgentPhase.Working
            } else AgentPhase.Thinking
        }
        if (AgentStateText.isError(rawState)) return AgentPhase.Error
        if (AgentStateText.isIdle(rawState)) return AgentPhase.Idle
        if (AgentStateText.isThinking(rawState)) return AgentPhase.Thinking
        return AgentPhase.Working
    }

    fun isActive(phase: AgentPhase) = phase == AgentPhase.Thinking || phase == AgentPhase.Working || phase == AgentPhase.Waiting

    fun eyebrow(phase: AgentPhase): String = when (phase) {
        AgentPhase.Idle -> "Idle"
        AgentPhase.Thinking -> "Thinking"
        AgentPhase.Working -> "Working"
        AgentPhase.Waiting -> "Waiting on you"
        AgentPhase.Error -> "Error"
    }

    /** Raw bridge text tidied for display ("thinking..." → "Thinking..."). */
    fun displayState(raw: String, fallback: String): String {
        val s = raw.oneLine(60)
        if (s.isEmpty()) return fallback
        return s.replaceFirstChar { it.uppercaseChar() }
    }

    fun statusCopy(
        phase: AgentPhase,
        rawState: String,
        streaming: StreamingMessageDto?,
        permission: PermissionRequestDto?,
        question: QuestionRequestDto?,
        repo: RepoFacts,
        now: Long
    ): StatusCopy {
        val eyebrow = eyebrow(phase)
        return when (phase) {
            AgentPhase.Waiting -> if (permission != null) {
                StatusCopy(
                    eyebrow,
                    "Paused for your decision",
                    "The agent wants to use ${permission.tool ?: "a tool"}. Nothing runs until you answer."
                )
            } else {
                StatusCopy(
                    eyebrow,
                    "The agent has a question",
                    question?.text?.oneLine(180) ?: "Answer it below and the agent picks up where it left off."
                )
            }
            AgentPhase.Thinking -> StatusCopy(
                eyebrow,
                displayState(rawState, "Thinking"),
                latestLine(streaming?.text) ?: "Working through the request."
            )
            AgentPhase.Working -> {
                val running = streaming?.runningTool
                val lastStep = streaming?.toolSteps?.lastOrNull()
                val title = if (running != null) "Running $running" else displayState(rawState, "Working")
                val detail = (if (running != null) lastStep?.takeIf { it.tool == running }?.target else null)
                    ?: latestLine(streaming?.text)
                    ?: lastStep?.target
                    ?: "Working through the request."
                StatusCopy(eyebrow, title, detail)
            }
            AgentPhase.Error -> StatusCopy(
                eyebrow,
                displayState(rawState, "Error"),
                "The bridge reported this state. The run log and terminal show what happened last."
            )
            AgentPhase.Idle -> StatusCopy(eyebrow, "Nothing running", idleDetail(repo, now))
        }
    }

    fun idleDetail(repo: RepoFacts, now: Long): String {
        val parts = mutableListOf<String>()
        repo.lastActivityAt?.let { parts += "Last activity ${Formatting.agoPhrase(it, now)}." }
        val branch = repo.branch
        val uncommitted = repo.uncommittedFiles
        if (branch != null && uncommitted != null) {
            parts += if (uncommitted > 0) "${Formatting.plural(uncommitted, "file")} uncommitted on $branch."
            else "Working tree is clean on $branch."
        }
        if (repo.pendingDiffFiles > 0) parts += "${Formatting.plural(repo.pendingDiffFiles, "diff")} waiting for review."
        return if (parts.isEmpty()) "Send a prompt to start a run." else parts.joinToString(" ")
    }

    fun repoFacts(git: GitStatusDto?, pendingDiffFiles: Int, lastActivityAt: Long?) = RepoFacts(
        branch = git?.branch,
        uncommittedFiles = git?.let { it.modifiedFiles.size + it.addedFiles.size + it.deletedFiles.size },
        pendingDiffFiles = pendingDiffFiles,
        lastActivityAt = lastActivityAt
    )

    /** Header subtitle from GIT_STATUS: "<branch> · <n> uncommitted" or "<branch> · clean". */
    fun repoLine(git: GitStatusDto?, workspacePath: String?): String {
        if (git != null) {
            val n = git.modifiedFiles.size + git.addedFiles.size + git.deletedFiles.size
            return if (n > 0) "${git.branch} · $n uncommitted" else "${git.branch} · clean"
        }
        return workspacePath?.let { workspaceName(it) } ?: "No workspace open"
    }

    fun workspaceName(path: String): String = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

    private fun latestLine(text: String?): String? =
        text?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }?.oneLine(160)
}
