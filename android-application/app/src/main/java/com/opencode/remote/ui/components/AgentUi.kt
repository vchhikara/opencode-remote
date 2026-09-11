package com.opencode.remote.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.state.AgentPhase
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.OcColors
import kotlinx.coroutines.delay

/**
 * The agent's presentation phase as Compose state. Derived with [derivedStateOf], so a
 * caller only recomposes when the *category* changes — not on every streamed token.
 */
@Composable
fun rememberAgentPhase(sessionManager: RemoteSessionManager): State<AgentPhase> {
    val raw = sessionManager.agentState.collectAsStateWithLifecycle()
    val streaming = sessionManager.streamingMessage.collectAsStateWithLifecycle()
    val permission = sessionManager.pendingPermission.collectAsStateWithLifecycle()
    val question = sessionManager.pendingQuestion.collectAsStateWithLifecycle()
    return remember {
        derivedStateOf { AgentPresentation.phaseOf(raw.value, streaming.value, permission.value, question.value) }
    }
}

/** Semantic accent per phase: neutral idle, green running, gold decision, red error. */
fun accentFor(phase: AgentPhase, c: OcColors): Color = when (phase) {
    AgentPhase.Idle -> c.ink3
    AgentPhase.Thinking, AgentPhase.Working -> c.posInk
    AgentPhase.Waiting -> c.goldInk
    AgentPhase.Error -> c.negInk
}

/**
 * Ticking "mm:ss" since [startedAt] (the moment this phone first observed the run).
 * The one-second ticker lives inside this composable, so only this Text recomposes.
 */
@Composable
fun ElapsedText(
    startedAt: Long?,
    active: Boolean,
    style: TextStyle,
    color: Color,
    idleText: String,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, active) {
        while (active && startedAt != null) {
            now = System.currentTimeMillis()
            delay((1000L - now % 1000L).coerceAtLeast(50L))
        }
    }
    val text = if (active && startedAt != null) Formatting.elapsed(now - startedAt) else idleText
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1)
}
