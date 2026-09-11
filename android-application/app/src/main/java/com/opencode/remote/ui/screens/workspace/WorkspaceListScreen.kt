package com.opencode.remote.ui.screens.workspace

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.GitStatusDto
import com.opencode.remote.data.dto.WorkspaceDto
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/**
 * First screen after pairing (top-level route). Shares [WorkspaceList] with the in-app
 * switcher, so both read the same WORKSPACE_LIST and use the same OPEN_WORKSPACE /
 * ADD_WORKSPACE commands — one workspace state, two entry points.
 */
@Composable
fun WorkspaceListScreen(
    sessionManager: RemoteSessionManager,
    onWorkspaceSelected: () -> Unit,
    onDisconnect: () -> Unit
) {
    val c = Oc.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Connected", color = c.posInk)
                Spacer(Modifier.height(8.dp))
                Text("Pick a workspace", style = OcType.pageTitle, color = c.ink)
            }
            HeaderAction("Disconnect", onClick = {
                sessionManager.disconnect()
                onDisconnect()
            })
        }
        WorkspaceList(sessionManager, Modifier.weight(1f), onOpened = onWorkspaceSelected)
    }
}

/** In-app switcher reached from the shell header / drawer title. */
@Composable
fun WorkspaceSwitchScreen(sessionManager: RemoteSessionManager, onOpened: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Switch workspace")
        WorkspaceList(sessionManager, Modifier.weight(1f), onOpened = onOpened)
    }
}

@Composable
private fun WorkspaceList(sessionManager: RemoteSessionManager, modifier: Modifier, onOpened: () -> Unit) {
    val c = Oc.colors
    val workspaces by sessionManager.workspaces.collectAsStateWithLifecycle()
    val active by sessionManager.activeWorkspace.collectAsStateWithLifecycle()
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var newPath by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { sessionManager.fetchWorkspaces() }

    val submit = {
        if (newPath.isNotBlank()) sessionManager.addWorkspace(newPath.trim())
        newPath = ""
        adding = false
    }

    LazyColumn(modifier.fillMaxWidth()) {
        if (workspaces.isEmpty()) {
            item { EmptyNote("No workspaces yet", "Add a directory on the bridge host below; it appears here once the bridge accepts it.") }
        }
        items(workspaces) { ws ->
            WorkspaceRow(
                workspace = ws,
                active = ws.path == active,
                git = git,
                onClick = {
                    sessionManager.openWorkspace(ws.path)
                    onOpened()
                }
            )
        }
        item {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                if (adding) {
                    Eyebrow("Add a workspace")
                    Spacer(Modifier.height(10.dp))
                    OcTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        placeholder = "/path/to/project on the bridge host",
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = OcType.mono,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OcButton("Cancel", { adding = false; newPath = "" }, modifier = Modifier.weight(1f))
                        OcButton("Add", submit, enabled = newPath.isNotBlank(), modifier = Modifier.weight(1f))
                    }
                } else {
                    OcButton("Add a workspace", { adding = true }, modifier = Modifier.fillMaxWidth(), height = 46.dp, textStyle = OcType.buttonLarge)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(workspace: WorkspaceDto, active: Boolean, git: GitStatusDto?, onClick: () -> Unit) {
    val c = Oc.colors
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.background(c.goldWash).startRule(c.gold, 2.dp) else Modifier)
            .bottomRule(c.rule)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(workspace.name, style = OcType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        CodeText(workspace.path, color = c.ink2, style = OcType.monoSmall)
        if (active) {
            Spacer(Modifier.height(8.dp))
            // Git status is only known for the open workspace, so only it gets a repo line.
            val line = listOfNotNull(git?.let { AgentPresentation.repoLine(it, null) }, "active").joinToString(" · ")
            Eyebrow(line, style = OcType.tag, color = c.goldInk)
        }
    }
}
