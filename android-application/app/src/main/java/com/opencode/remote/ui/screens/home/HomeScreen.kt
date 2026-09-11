package com.opencode.remote.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.ChatMessageDto
import com.opencode.remote.data.dto.ChatRole
import com.opencode.remote.data.dto.StreamingMessageDto
import com.opencode.remote.data.dto.TaskStatus
import com.opencode.remote.data.dto.ToolStep
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.run.RunEvent
import com.opencode.remote.data.run.RunEventKind
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.ElapsedText
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.StatusDot
import com.opencode.remote.ui.components.accentFor
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.rememberAgentPhase
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.navigation.NavRoutes.Destination
import com.opencode.remote.ui.screens.diff.summarizeDiff
import com.opencode.remote.ui.screens.diff.totalsOf
import com.opencode.remote.ui.state.AgentPhase
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType

private enum class HomeSheet { Permission, Question }

/**
 * Home: the one surface that says what the agent is doing — live status card, the
 * streaming conversation, a sticky "Waiting on you" bar for decisions, and the prompt
 * bar. Everything is bound to RemoteSessionManager's real flows.
 */
@Composable
fun HomeScreen(sessionManager: RemoteSessionManager, onNavigate: (Destination) -> Unit) {
    val phase by rememberAgentPhase(sessionManager)
    val permission by sessionManager.pendingPermission.collectAsStateWithLifecycle()
    val question by sessionManager.pendingQuestion.collectAsStateWithLifecycle()
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    val pendingDiffs by sessionManager.pendingDiffs.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        StatusCard(sessionManager, phase, onNavigate)

        Conversation(sessionManager, Modifier.weight(1f).fillMaxWidth())

        val perm = permission
        AnimatedVisibility(
            visible = perm != null && sheet != HomeSheet.Permission,
            enter = slideInVertically(tween(OcMotion.ENTER_MS, easing = OcMotion.Easing)) { it / 2 } + fadeIn(tween(OcMotion.ENTER_MS)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200))
        ) {
            WaitingBar(
                eyebrow = "Waiting on you",
                text = "Allow the agent to use ${perm?.tool ?: "a tool"}",
                action = "Review",
                onAction = { sheet = HomeSheet.Permission }
            )
        }
        val q = question
        AnimatedVisibility(
            visible = q != null && perm == null && sheet != HomeSheet.Question,
            enter = slideInVertically(tween(OcMotion.ENTER_MS, easing = OcMotion.Easing)) { it / 2 } + fadeIn(tween(OcMotion.ENTER_MS)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200))
        ) {
            WaitingBar(
                eyebrow = "Question for you",
                text = q?.text ?: "The agent is waiting for an answer",
                action = "Answer",
                onAction = { sheet = HomeSheet.Question }
            )
        }

        PromptComposer(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = { text ->
                sessionManager.sendPrompt(text)
                draft = ""
            },
            placeholder = if (AgentPresentation.isActive(phase)) "Steer it, or start something new" else "Tell the agent what to do",
            connected = connection is ConnectionState.Connected,
            chips = listOf(
                ComposerChip(if (pendingDiffs.isEmpty()) "What changed?" else "What changed? · ${pendingDiffs.size}") { onNavigate(Destination.Diffs) },
                ComposerChip("Commit this") { onNavigate(Destination.Git) },
                ComposerChip("Run log") { onNavigate(Destination.RunLog) },
                ComposerChip("Terminal") { onNavigate(Destination.Terminal) }
            )
        )
    }

    // Sheets render from the *current* pending request; if it is answered or cleared
    // elsewhere the sheet closes rather than acting on something stale.
    LaunchedEffect(permission == null, question == null) {
        if (sheet == HomeSheet.Permission && permission == null) sheet = null
        if (sheet == HomeSheet.Question && question == null) sheet = null
    }
    val perm = permission
    if (sheet == HomeSheet.Permission && perm != null) {
        PermissionSheet(
            request = perm,
            onDecision = { id, decision ->
                sessionManager.replyPermission(id, decision.wire)
                sheet = null
            },
            onDismiss = { sheet = null }
        )
    }
    val q = question
    if (sheet == HomeSheet.Question && q != null) {
        QuestionSheet(
            request = q,
            onAnswer = { id, answer ->
                sessionManager.replyQuestion(id, answer)
                sheet = null
            },
            onDismiss = { sheet = null }
        )
    }
}

@Composable
private fun StatusCard(sessionManager: RemoteSessionManager, phase: AgentPhase, onNavigate: (Destination) -> Unit) {
    val c = Oc.colors
    val raw by sessionManager.agentState.collectAsStateWithLifecycle()
    val streaming by sessionManager.streamingMessage.collectAsStateWithLifecycle()
    val permission by sessionManager.pendingPermission.collectAsStateWithLifecycle()
    val question by sessionManager.pendingQuestion.collectAsStateWithLifecycle()
    val pendingDiffs by sessionManager.pendingDiffs.collectAsStateWithLifecycle()
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    val tasks by sessionManager.tasks.collectAsStateWithLifecycle()
    val runStartedAt by sessionManager.runStartedAt.collectAsStateWithLifecycle()
    val runEvents by sessionManager.runEvents.collectAsStateWithLifecycle()

    val active = AgentPresentation.isActive(phase)
    val busy = phase == AgentPhase.Working || phase == AgentPhase.Thinking
    val accent = accentFor(phase, c)
    val background by animateColorAsState(if (busy) c.posWash else c.panel, tween(OcMotion.STATE_MS, easing = OcMotion.Easing), label = "statusBg")
    val summaries = remember(pendingDiffs) { pendingDiffs.map { summarizeDiff(it) } }
    val totals = remember(summaries) { totalsOf(summaries) }
    val lastActivity = runEvents.lastOrNull()?.at
    val copy = AgentPresentation.statusCopy(
        phase, raw, streaming, permission, question,
        AgentPresentation.repoFacts(git, pendingDiffs.size, lastActivity),
        System.currentTimeMillis()
    )
    val runningTasks = tasks.values.filter { TaskStatus.of(it.status) == TaskStatus.RUNNING }

    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .then(if (phase != AgentPhase.Idle) Modifier.startRule(accent, 2.dp) else Modifier)
            .bottomRule(c.rule)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusDot(accent, pulsing = phase != AgentPhase.Idle && phase != AgentPhase.Error)
                Eyebrow(copy.eyebrow, color = if (phase == AgentPhase.Idle) c.ink2 else accent)
            }
            ElapsedText(runStartedAt, active, OcType.elapsed, c.ink2, idleText = "—")
        }
        Spacer(Modifier.height(10.dp))
        Text(copy.title, style = OcType.statusTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(copy.detail, style = OcType.body, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)

        if (phase == AgentPhase.Idle) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.rule)
                    .background(c.rule)
                    .padding(0.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                StatCell("Pending", Modifier.weight(1f)) {
                    Text("${pendingDiffs.size}", style = OcType.stat, color = c.ink)
                }
                StatCell("Lines", Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("+${totals.additions}", style = OcType.stat, color = c.posInk)
                        Text("−${totals.deletions}", style = OcType.stat, color = c.negInk)
                    }
                }
                StatCell("Uncommitted", Modifier.weight(1f)) {
                    val n = git?.let { it.modifiedFiles.size + it.addedFiles.size + it.deletedFiles.size }
                    Text(n?.toString() ?: "—", style = OcType.stat, color = c.ink)
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    runningTasks.size == 1 -> OcButton("Stop", { sessionManager.killTask(runningTasks.first().id) }, height = 34.dp)
                    runningTasks.size > 1 -> OcButton("Stop…", { onNavigate(Destination.RunLog) }, height = 34.dp)
                }
                if (pendingDiffs.isNotEmpty()) {
                    OcButton(if (pendingDiffs.size == 1) "Watch the diff" else "Watch ${pendingDiffs.size} diffs", { onNavigate(Destination.Diffs) }, height = 34.dp)
                } else {
                    OcButton("Open run log", { onNavigate(Destination.RunLog) }, height = 34.dp)
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val c = Oc.colors
    Column(modifier.background(c.panel).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Eyebrow(label, style = OcType.statLabel, color = c.ink2)
        value()
    }
}

@Composable
fun WaitingBar(eyebrow: String, text: String, action: String, onAction: () -> Unit) {
    val c = Oc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.goldWash)
            .topRule(c.gold)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow, color = c.goldInk)
            Spacer(Modifier.height(5.dp))
            Text(text, style = OcType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OcButton(action, onAction, kind = OcButtonKind.Gold, height = 36.dp)
    }
}

// ---------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------

private sealed interface ConversationEntry {
    val key: String
    val at: Long
    data class Message(val message: ChatMessageDto, val occurrence: Int) : ConversationEntry {
        // The bridge supplies message ids; if it ever repeats one, the occurrence suffix
        // keeps LazyColumn keys unique (duplicate keys crash) while staying stable.
        override val key get() = if (occurrence == 0) "m:" + message.id else "m:" + message.id + "#" + occurrence
        override val at get() = message.timestamp
    }
    /** A decision the user actually made (from the run log), shown inline like the
     *  prototype's "Answered" card. */
    data class Decision(val event: RunEvent) : ConversationEntry {
        override val key get() = "d:" + event.id
        override val at get() = event.at
    }
}

@Composable
private fun Conversation(sessionManager: RemoteSessionManager, modifier: Modifier) {
    val c = Oc.colors
    val messages by sessionManager.chatMessages.collectAsStateWithLifecycle()
    val runEvents by sessionManager.runEvents.collectAsStateWithLifecycle()
    // Kept as a State (not delegated) so token-by-token updates only recompose the
    // streaming entry, never this list.
    val streaming = sessionManager.streamingMessage.collectAsStateWithLifecycle()
    val isStreaming by remember { derivedStateOf { streaming.value != null } }

    val entries = remember(messages, runEvents) {
        val decisions = runEvents.filter { it.kind == RunEventKind.Decision || it.kind == RunEventKind.Answer }
        val seen = HashMap<String, Int>()
        val messageEntries = messages.map { m ->
            val n = seen[m.id] ?: 0
            seen[m.id] = n + 1
            ConversationEntry.Message(m, n)
        }
        (messageEntries + decisions.map { ConversationEntry.Decision(it) })
            .sortedBy { it.at }
            .asReversed() // reverseLayout: index 0 sits at the bottom
    }

    if (entries.isEmpty() && !isStreaming) {
        Column(modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("Nothing said yet in this session.", style = OcType.body, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            Text("Messages and tool calls appear here as the agent works.", style = OcType.body, color = c.ink3)
        }
        return
    }

    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isStreaming) {
            item(key = "streaming") {
                StreamingEntry(streaming, Modifier.animateItem(fadeInSpec = tween(OcMotion.ENTER_MS, easing = OcMotion.Easing), fadeOutSpec = null))
            }
        }
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is ConversationEntry.Message -> {
                    val user = entry.message.role == ChatRole.User
                    // Assistant finals replace the streaming entry in place, so they
                    // appear without a fade (no flash of already-visible text).
                    val anim = Modifier.animateItem(
                        fadeInSpec = if (user) tween(OcMotion.ENTER_MS, easing = OcMotion.Easing) else null,
                        fadeOutSpec = null
                    )
                    if (user) UserMessage(entry.message, anim) else AgentMessage(entry.message.content, entry.message.tools, false, anim)
                }
                is ConversationEntry.Decision -> DecisionCard(entry.event, Modifier.animateItem(fadeInSpec = tween(OcMotion.ENTER_MS), fadeOutSpec = null))
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessageDto, modifier: Modifier) {
    val c = Oc.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.fillMaxWidth(0.86f), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.border(1.dp, c.ruleStrong).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Eyebrow("You", style = OcType.speaker, color = c.ink3)
                Spacer(Modifier.height(7.dp))
                Text(message.content, style = OcType.message, color = c.ink)
            }
        }
    }
}

@Composable
private fun AgentMessage(text: String, tools: List<ToolStep>, live: Boolean, modifier: Modifier) {
    val c = Oc.colors
    Column(
        modifier
            .fillMaxWidth(0.94f)
            .startRule(c.ruleStrong)
            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Agent", style = OcType.speaker, color = c.ink3)
            if (live) StatusDot(c.posInk, pulsing = true, size = 5.dp)
        }
        Spacer(Modifier.height(7.dp))
        if (text.isNotEmpty()) {
            Text(text, style = OcType.message, color = c.ink)
        } else if (live && tools.isEmpty()) {
            Text("Thinking…", style = OcType.message, color = c.ink3)
        }
        if (tools.isNotEmpty()) {
            val shown = tools.takeLast(4)
            if (tools.size > shown.size) {
                Spacer(Modifier.height(8.dp))
                Text("${tools.size - shown.size} earlier tool calls in the run log", style = OcType.bodySmall, color = c.ink3)
            }
            shown.forEach { step -> ToolRow(step, running = live && !step.done) }
        }
    }
}

@Composable
private fun ToolRow(step: ToolStep, running: Boolean) {
    val c = Oc.colors
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.startRule(c.ruleStrong).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val verbColor: Color = when {
            step.failed -> c.negInk
            running -> c.posInk
            else -> c.ink2
        }
        Eyebrow(step.tool, style = OcType.tag, color = verbColor)
        step.target?.let { CodeText(it, color = c.ink2, style = OcType.monoSmall) }
    }
}

@Composable
private fun StreamingEntry(streaming: State<StreamingMessageDto?>, modifier: Modifier) {
    val value = streaming.value ?: return
    AgentMessage(value.text, value.toolSteps, live = true, modifier = modifier)
}

@Composable
private fun DecisionCard(event: RunEvent, modifier: Modifier) {
    val c = Oc.colors
    Column(
        modifier
            .fillMaxWidth(0.94f)
            .border(1.dp, c.goldLine)
            .background(c.goldWash)
            .padding(12.dp)
    ) {
        Eyebrow("Answered", color = c.goldInk)
        Spacer(Modifier.height(6.dp))
        val text = if (event.kind == RunEventKind.Decision && event.detail != null) "${event.text} · ${event.detail}" else event.text
        Text(text, style = OcType.body, color = c.ink)
    }
}
