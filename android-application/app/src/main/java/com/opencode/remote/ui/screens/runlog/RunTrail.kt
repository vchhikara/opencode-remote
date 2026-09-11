package com.opencode.remote.ui.screens.runlog

import com.opencode.remote.data.run.RunEvent
import com.opencode.remote.data.run.RunEventKind

internal const val TRAIL_LIMIT = 40

/** Events of the current run, including the prompt that kicked it off (sent just
 *  before the agent's first signal); while idle, the most recent activity. */
internal fun currentRunTrail(events: List<RunEvent>, start: Long?): List<RunEvent> {
    if (start == null) return events.takeLast(12)
    var from = events.indexOfFirst { it.at >= start }
    if (from < 0) return emptyList()
    val prompt = (from - 1 downTo 0).firstOrNull { i ->
        events[i].kind == RunEventKind.Prompt || events[i].kind == RunEventKind.Finished
    }
    if (prompt != null && events[prompt].kind == RunEventKind.Prompt) from = prompt
    return events.subList(from, events.size).takeLast(TRAIL_LIMIT)
}
