package com.opencode.remote.data.run

import com.opencode.remote.data.dto.TaskDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RunEventKind {
    Prompt, State, Tool, Permission, Decision, Question, Answer,
    Diff, DiffKept, DiffReverted, Task, Git, Finished
}

/**
 * One line of the Run log. [at] is the moment *this phone* received (or sent) the frame
 * that produced the event — the bridge protocol carries no timestamps of its own, so
 * nothing here is back-dated or invented.
 */
data class RunEvent(
    val id: Long,
    val at: Long,
    val kind: RunEventKind,
    val text: String,
    val detail: String? = null,
    val done: Boolean = true,
    val failed: Boolean = false
)

/**
 * Session-lifetime record of genuine agent activity, built only from frames the app
 * actually sees (AGENT_STATE, STREAM_TOOL_CALL/RESULT, PERMISSION_REQUEST, …) and the
 * user's own commands. Bounded; not persisted. A "run" starts at the first non-idle
 * signal observed and ends when the bridge reports an idle state again.
 */
class RunLogRecorder(
    private val clock: () -> Long = System::currentTimeMillis,
    private val capacity: Int = 250
) {
    private val _events = MutableStateFlow<List<RunEvent>>(emptyList())
    val events: StateFlow<List<RunEvent>> = _events.asStateFlow()

    private val _runStartedAt = MutableStateFlow<Long?>(null)
    /** When the current run was first observed by this phone; null while idle. */
    val runStartedAt: StateFlow<Long?> = _runStartedAt.asStateFlow()

    private var nextId = 1L
    private var lastState: String? = null
    private val taskStatus = HashMap<String, String>()

    @Synchronized fun promptSent(text: String) = record(RunEventKind.Prompt, "You asked: " + text.oneLine(120))

    @Synchronized fun agentState(raw: String) {
        if (raw == lastState) return
        lastState = raw
        if (AgentStateText.isIdle(raw)) {
            if (_runStartedAt.value != null) {
                record(RunEventKind.Finished, "Finished — agent is idle")
                _runStartedAt.value = null
            }
        } else {
            ensureRun()
            record(RunEventKind.State, raw.oneLine(120))
        }
    }

    /** Streamed text arriving counts as activity (starts the run clock) but isn't logged
     *  line by line — the text itself lives in the conversation. */
    @Synchronized fun streamActivity() = ensureRun()

    @Synchronized fun toolCall(tool: String, target: String?) {
        ensureRun()
        record(RunEventKind.Tool, tool, target, done = false)
    }

    @Synchronized fun toolResult(tool: String, failed: Boolean) {
        _events.update { list ->
            var idx = list.indexOfLast { it.kind == RunEventKind.Tool && !it.done && it.text == tool }
            if (idx < 0) idx = list.indexOfLast { it.kind == RunEventKind.Tool && !it.done }
            if (idx < 0) list else list.toMutableList().also { it[idx] = it[idx].copy(done = true, failed = failed) }
        }
    }

    @Synchronized fun permissionRequested(tool: String?) {
        ensureRun()
        record(RunEventKind.Permission, "Asked to use ${tool ?: "a tool"}", tool, done = false)
    }

    @Synchronized fun permissionAnswered(label: String, tool: String?) {
        markOpenDone(RunEventKind.Permission)
        record(RunEventKind.Decision, label, tool)
    }

    @Synchronized fun questionAsked(text: String?) {
        ensureRun()
        record(RunEventKind.Question, "Asked: " + (text?.oneLine(120) ?: "a question"), done = false)
    }

    @Synchronized fun questionAnswered(answer: String) {
        markOpenDone(RunEventKind.Question)
        record(RunEventKind.Answer, "You answered: " + answer.oneLine(120))
    }

    @Synchronized fun diffProposed(filePath: String) = record(RunEventKind.Diff, "Proposed changes", filePath)
    @Synchronized fun diffKept(filePath: String) = record(RunEventKind.DiffKept, "You kept the changes", filePath)
    @Synchronized fun diffReverted(filePath: String) = record(RunEventKind.DiffReverted, "You reverted the changes", filePath)

    @Synchronized fun task(task: TaskDto) {
        val previous = taskStatus.put(task.id, task.status)
        if (previous != task.status) record(RunEventKind.Task, "${task.name} · ${task.status.lowercase()}", task.id)
    }

    @Synchronized fun taskRemoved(id: String) { taskStatus.remove(id) }

    @Synchronized fun stopRequested(taskName: String) = record(RunEventKind.Task, "You asked to stop $taskName")

    @Synchronized fun gitCommand(command: String) = record(RunEventKind.Git, "You ran git " + command.oneLine(80))

    private fun ensureRun() {
        if (_runStartedAt.value == null) _runStartedAt.value = clock()
    }

    private fun markOpenDone(kind: RunEventKind) {
        _events.update { list ->
            if (list.none { it.kind == kind && !it.done }) list
            else list.map { if (it.kind == kind && !it.done) it.copy(done = true) else it }
        }
    }

    private fun record(kind: RunEventKind, text: String, detail: String? = null, done: Boolean = true, failed: Boolean = false) {
        val event = RunEvent(nextId++, clock(), kind, text, detail, done, failed)
        _events.update { (it + event).takeLast(capacity) }
    }
}
