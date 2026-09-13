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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.GlobalSessionDto
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType
import java.io.File

/**
 * ADR-0002: every OpenCode session across every workspace, read directly from
 * OpenCode's own on-disk session database on the bridge — not scoped to
 * [RemoteSessionManager.activeWorkspace] the way [SessionsScreen] is.
 * Reached from SessionsScreen's header, mirroring its own layout
 * (ScreenHeader / LazyColumn / row-per-session) rather than introducing a
 * tab or filter on that screen — see docs/plans/global-session-search-tasks.md
 * Phase 4.1 for why.
 */
@Composable
fun GlobalSessionsScreen(sessionManager: RemoteSessionManager, onOpened: () -> Unit) {
    val sessions by sessionManager.allSessions.collectAsStateWithLifecycle()
    val loading by sessionManager.allSessionsLoading.collectAsStateWithLifecycle()
    val error by sessionManager.allSessionsError.collectAsStateWithLifecycle()
    val nextCursor by sessionManager.allSessionsNextCursor.collectAsStateWithLifecycle()
    val opening by sessionManager.openGlobalSessionLoading.collectAsStateWithLifecycle()
    val openError by sessionManager.openGlobalSessionError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { sessionManager.fetchAllSessions() }

    // OPEN_SESSION_GLOBAL restarts opencode-serve on the bridge — not
    // instant, unlike SWITCH_SESSION — so this screen waits for the
    // loading flag to actually clear (SESSION_OPENED or ERROR both do that;
    // only navigate on the success case, openError == null) rather than
    // navigating immediately on tap the way SessionsScreen's plain
    // switchSession() does. Tracks the previous `opening` value locally to
    // fire onOpened() exactly once per completed open, not on every
    // recomposition where opening happens to already be false.
    var wasOpening by remember { mutableStateOf(false) }
    LaunchedEffect(opening, openError) {
        if (wasOpening && !opening && openError == null) onOpened()
        wasOpening = opening
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("All workspaces") {
            HeaderAction("Refresh", enabled = !loading, onClick = { sessionManager.fetchAllSessions() })
        }
        when {
            error != null -> EmptyNote("Couldn't load sessions", error!!)
            openError != null -> EmptyNote("Couldn't open session", openError!!)
            loading && sessions.isEmpty() -> EmptyNote("Loading…", "Fetching every session known to OpenCode.")
            sessions.isEmpty() -> EmptyNote("No sessions found", "OpenCode has no recorded sessions on this machine yet.")
            else -> {
                val ordered = sessions.distinctBy { it.id }.sortedByDescending { it.updatedAt }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ordered, key = { it.id }) { session ->
                        GlobalSessionRow(
                            session = session,
                            opening = opening,
                            now = System.currentTimeMillis(),
                            onOpen = {
                                if (session.reachable && !opening) {
                                    sessionManager.openGlobalSession(session.id, session.worktree)
                                }
                            }
                        )
                    }
                    if (nextCursor != null) {
                        item(key = "load-more") {
                            LoadMoreRow(loading = loading, onClick = { sessionManager.fetchAllSessions(nextCursor) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    val c = Oc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.height(16.dp)) else Text("Load more", style = OcType.chip, color = c.ink)
    }
}

@Composable
private fun GlobalSessionRow(session: GlobalSessionDto, opening: Boolean, now: Long, onOpen: () -> Unit) {
    val c = Oc.colors
    val disabled = !session.reachable
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (disabled) Modifier.background(c.panel) else Modifier)
            .bottomRule(c.rule)
            .clickable(enabled = !disabled && !opening, onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                session.title.takeIf { it.isNotBlank() } ?: "Untitled session",
                style = OcType.rowTitle, color = if (disabled) c.ink3 else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Eyebrow(Formatting.compactAge(Formatting.normalizeEpoch(session.updatedAt), now), style = OcType.tag)
        }
        Spacer(Modifier.height(6.dp))
        CodeText(File(session.worktree).name.ifBlank { session.worktree }, color = c.ink2, style = OcType.monoSmall)
        Spacer(Modifier.height(4.dp))
        CodeText(session.id, color = c.ink3, style = OcType.monoSmall)
        if (disabled) {
            Spacer(Modifier.height(10.dp))
            Text("Workspace not found: ${session.worktree}", style = OcType.tag, color = c.ink3)
        }
    }
}
