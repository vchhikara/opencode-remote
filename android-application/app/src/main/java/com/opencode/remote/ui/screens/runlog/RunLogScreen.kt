package com.opencode.remote.ui.screens.runlog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.ChatRole
import com.opencode.remote.data.dto.TaskDto
import com.opencode.remote.data.dto.TaskStatus
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.run.RunEvent
import com.opencode.remote.data.run.RunEventKind
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.Chevron
import com.opencode.remote.ui.components.ElapsedText
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.StatusDot
import com.opencode.remote.ui.components.accentFor
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.rememberAgentPhase
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.navigation.NavRoutes.Destination
import com.opencode.remote.ui.screens.diff.summarizeDiff
import com.opencode.remote.ui.screens.diff.totalsOf
import com.opencode.remote.ui.screens.home.PromptComposer
import com.opencode.remote.ui.state.AgentPhase
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType

/**
 * Run log: the current run in the prototype's hierarchy — big elapsed figure, the
 * trail of genuine agent/tool operations observed on this connection, pending review,
 * running tasks (with the real KILL_TASK stop) and the latest agent output. Offsets
 * are measured from when this phone first saw the run start; nothing is back-filled.
 */
@Composable
fun RunLogScreen(sessionManager: RemoteSessionManager, onNavigate: (Destination) -> Unit) {
    val c = Oc.colors
    val phase by rememberAgentPhase(sessionManager)
    val raw by sessionManager.agentState.collectAsStateWithLifecycle()
    val streaming by sessionManager.streamingMessage.collectAsStateWithLifecycle()
    val permission by sessionManager.pendingPermission.collectAsStateWithLifecycle()
    val question by sessionManager.pendingQuestion.collectAsStateWithLifecycle()
    val events by sessionManager.runEvents.collectAsStateWithLifecycle()
    val runStartedAt by sessionManager.runStartedAt.collectAsStateWithLifecycle()
    val pendingDiffs by sessionManager.pendingDiffs.collectAsStateWithLifecycle()
    val tasks by sessionManager.tasks.collectAsStateWithLifecycle()
    val messages by sessionManager.chatMessages.collectAsStateWithLifecycle()
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    val active = AgentPresentation.isActive(phase)
    val accent = accentFor(phase, c)
    val panelBg by animateColorAsState(
        when (phase) {
            AgentPhase.Idle, AgentPhase.Error -> c.panel
            AgentPhase.Waiting -> c.goldWash
            else -> c.posWash
        },
        tween(OcMotion.STATE_MS, easing = OcMotion.Easing), label = "runPanelBg"
    )
    val copy = AgentPresentation.statusCopy(
        phase, raw, streaming, permission, question,
        AgentPresentation.repoFacts(git, pendingDiffs.size, events.lastOrNull()?.at),
        System.currentTimeMillis()
    )
    val start = runStartedAt
    val trail = remember(events, start) { currentRunTrail(events, start) }
    val totals = remember(pendingDiffs) { totalsOf(pendingDiffs.map { summarizeDiff(it) }) }
    val lastSaid = streaming?.text?.takeIf { it.isNotBlank() }
        ?: messages.lastOrNull { it.role == ChatRole.Assistant }?.content
    val taskList = tasks.values.sortedBy { it.name }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "panel") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(panelBg)
                        .startRule(accent, 2.dp)
                        .bottomRule(c.rule)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(if (phase == AgentPhase.Thinking) "Working" else copy.eyebrow, color = if (phase == AgentPhase.Idle) c.ink2 else accent)
                            Spacer(Modifier.height(12.dp))
                            Text(copy.title, style = OcType.runTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Text(copy.detail, style = OcType.body, color = c.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        ElapsedText(start, active, OcType.bigElapsed, c.ink, idleText = "00:00")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val running = taskList.filter { TaskStatus.of(it.status) == TaskStatus.RUNNING }
                        if (running.size == 1) OcButton("Stop", { sessionManager.killTask(running.first().id) }, height = 36.dp)
                        OcButton("Open terminal", { onNavigate(Destination.Terminal) }, height = 36.dp)
                    }
                    if (active && start != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Timed from when this phone first saw the run.", style = OcType.bodySmall, color = c.ink3)
                    }
                }
            }

            item(key = "trailLabel") {
                Eyebrow(
                    if (start != null) "Run log" else "Recent activity",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                )
            }
            if (trail.isEmpty()) {
                item(key = "trailEmpty") {
                    Text(
                        "No agent activity seen on this connection yet.",
                        style = OcType.body, color = c.ink3,
                        modifier = Modifier.fillMaxWidth().bottomRule(c.rule).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp)
                    )
                }
            } else {
                items(trail, key = { "e" + it.id }) { event ->
                    val isCurrent = start != null && event == trail.last() && active
                    TrailRow(event, start, isCurrent, accent)
                }
                item(key = "trailEnd") { Spacer(Modifier.fillMaxWidth().height(18.dp).bottomRule(c.rule)) }
            }

            item(key = "diffs") {
                if (pendingDiffs.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bottomRule(c.rule)
                            .clickable { onNavigate(Destination.Diffs) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${Formatting.plural(pendingDiffs.size, "file")} waiting for review",
                                style = OcType.rowStrong, color = c.ink
                            )
                            Spacer(Modifier.height(5.dp))
                            Text("+${totals.additions} / −${totals.deletions} across pending diffs", style = OcType.bodySmall.copy(fontFeatureSettings = "tnum"), color = c.ink2)
                        }
                        Chevron(0f)
                    }
                } else {
                    Text(
                        "Nothing waiting for review.",
                        style = OcType.body, color = c.ink3,
                        modifier = Modifier.fillMaxWidth().bottomRule(c.rule).padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            if (taskList.isNotEmpty()) {
                item(key = "tasksLabel") { Eyebrow("Tasks on the bridge", Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) }
                items(taskList, key = { "t" + it.id }) { task -> TaskRow(task, onStop = { sessionManager.killTask(task.id) }) }
            }

            item(key = "lastSaid") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Eyebrow("Last thing it said")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        lastSaid ?: "The agent hasn't said anything in this session yet.",
                        style = OcType.message,
                        color = if (lastSaid != null) c.ink else c.ink3,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.startRule(c.ruleStrong).padding(start = 14.dp)
                    )
                }
            }
        }

        PromptComposer(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = { text ->
                sessionManager.sendPrompt(text)
                draft = ""
            },
            placeholder = "Steer it, or start something new",
            connected = connection is ConnectionState.Connected
        )
    }
}

@Composable
private fun TrailRow(event: RunEvent, runStart: Long?, isCurrent: Boolean, accent: androidx.compose.ui.graphics.Color) {
    val c = Oc.colors
    val time = if (runStart != null && event.at >= runStart) Formatting.elapsed(event.at - runStart) else Formatting.clock(event.at)
    val open = !event.done && runStart != null
    val dotColor = when {
        event.failed -> c.negInk
        isCurrent || open -> accent
        else -> c.ink3
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusDot(dotColor, pulsing = (isCurrent || open) && !event.failed, modifier = Modifier.padding(top = 6.dp))
        Text(time, style = OcType.trailTime, color = c.ink3, modifier = Modifier.width(44.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            val strong = isCurrent || open
            val label = when (event.kind) {
                RunEventKind.Tool -> (if (event.failed) "Failed " else if (open) "Running " else "Ran ") + event.text
                else -> event.text
            }
            Text(
                label,
                style = if (strong) OcType.trail.copy(fontWeight = FontWeight.SemiBold) else OcType.trail,
                color = if (strong) c.ink else c.ink2
            )
            event.detail?.takeIf { event.kind != RunEventKind.Task && event.kind != RunEventKind.Decision }?.let {
                CodeText(it, color = c.ink2, style = OcType.monoSmall)
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskDto, onStop: () -> Unit) {
    val c = Oc.colors
    val status = TaskStatus.of(task.status)
    Row(
        Modifier.fillMaxWidth().bottomRule(c.rule).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.name, style = OcType.rowStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            CodeText(task.id + (task.port?.let { " · port $it" } ?: ""), color = c.ink2, style = OcType.monoSmall)
        }
        Eyebrow(
            task.status,
            style = OcType.tag,
            color = when (status) {
                TaskStatus.RUNNING -> c.posInk
                TaskStatus.FAILED -> c.negInk
                else -> c.ink2
            }
        )
        if (status == TaskStatus.RUNNING) {
            OcButton("Stop", onStop, kind = OcButtonKind.NegativeOutline, height = 34.dp)
        }
    }
}
