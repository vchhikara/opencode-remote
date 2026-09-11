package com.opencode.remote.ui.screens.sessions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.SessionDto
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/** opencode sessions from SESSION_LIST: switch, fork, or start a new one. */
@Composable
fun SessionsScreen(sessionManager: RemoteSessionManager, onOpened: () -> Unit) {
    val sessions by sessionManager.sessions.collectAsStateWithLifecycle()
    val activeId by sessionManager.activeSessionId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { sessionManager.listSessions() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sessions") {
            HeaderAction("New", onClick = {
                sessionManager.newSession()
                onOpened()
            })
        }
        if (sessions.isEmpty()) {
            EmptyNote("No sessions yet", "Start one with New — the bridge lists every session in this workspace here.")
        } else {
            val now = System.currentTimeMillis()
            // Most recently updated first when the bridge reports times; otherwise its order.
            val unique = sessions.distinctBy { it.id }
            val ordered = if (unique.any { it.updatedAt != null }) unique.sortedByDescending { it.updatedAt ?: Long.MIN_VALUE } else unique
            LazyColumn(Modifier.fillMaxSize()) {
                items(ordered, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        active = session.id == activeId,
                        now = now,
                        onOpen = {
                            sessionManager.switchSession(session.id)
                            onOpened()
                        },
                        onFork = { sessionManager.forkSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionDto, active: Boolean, now: Long, onOpen: () -> Unit, onFork: () -> Unit) {
    val c = Oc.colors
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.background(c.goldWash).startRule(c.gold, 2.dp) else Modifier)
            .bottomRule(c.rule)
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                style = OcType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            session.updatedAt?.let { Eyebrow(Formatting.compactAge(Formatting.normalizeEpoch(it), now), style = OcType.tag) }
        }
        Spacer(Modifier.height(6.dp))
        CodeText(session.id, color = c.ink2, style = OcType.monoSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active) Eyebrow("Active", style = OcType.tag, color = c.goldInk)
            Text(
                "Fork",
                style = OcType.chip,
                color = c.ink,
                modifier = Modifier.clickable(onClick = onFork).padding(vertical = 4.dp)
            )
        }
    }
}
