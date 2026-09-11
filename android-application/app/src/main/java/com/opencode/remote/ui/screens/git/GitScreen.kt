package com.opencode.remote.ui.screens.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.SectionLabel
import com.opencode.remote.ui.components.SegmentCell
import com.opencode.remote.ui.components.SegmentedBar
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/**
 * Git: real GIT_STATUS (branch, modified/added/deleted) and the same four bridge
 * operations as before — fetch, pull, push, commit — all through runGit().
 */
@Composable
fun GitScreen(sessionManager: RemoteSessionManager) {
    val c = Oc.colors
    val status by sessionManager.gitStatus.collectAsStateWithLifecycle()
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    val connected = connection is ConnectionState.Connected

    val git = status
    if (git == null) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp)) {
                Eyebrow("Branch")
                Spacer(Modifier.height(8.dp))
                Text("No git status yet", style = OcType.pageTitle, color = c.ink)
            }
            EmptyNote("Waiting for the bridge", "Status arrives once a workspace inside a git repository is open.")
        }
        return
    }

    val changed = git.modifiedFiles.size + git.addedFiles.size + git.deletedFiles.size
    val commit = {
        val text = message.trim()
        if (text.isNotEmpty() && connected) {
            sessionManager.runGit(GitCommands.commit(text))
            message = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp)) {
            Eyebrow("Branch")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(git.branch, style = OcType.pageTitle, color = c.ink, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                Eyebrow(
                    if (changed > 0) "$changed uncommitted" else "clean",
                    style = OcType.tag,
                    color = if (changed > 0) c.goldInk else c.ink2,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            val remote = listOfNotNull(
                if (git.canPull) "pull available" else null,
                if (git.canPush) "push available" else null
            )
            if (remote.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(remote.joinToString(" · ").replaceFirstChar { it.uppercaseChar() }, style = OcType.bodySmall, color = c.ink2)
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (git.isClean) {
                item { EmptyNote("Nothing to commit", "The working tree matches the last commit.") }
            }
            fileGroup("Modified", git.modifiedFiles)
            fileGroup("Added", git.addedFiles)
            fileGroup("Deleted", git.deletedFiles)
            item {
                Column(Modifier.padding(18.dp)) {
                    Eyebrow("Commit message")
                    Spacer(Modifier.height(10.dp))
                    OcTextField(
                        value = message,
                        onValueChange = { message = it.replace('\n', ' ') },
                        placeholder = "Describe the change",
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = OcType.bodyLarge,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() })
                    )
                }
            }
        }

        SegmentedBar {
            SegmentCell("Fetch", { sessionManager.runGit(GitCommands.FETCH) }, enabled = connected)
            SegmentCell("Pull", { sessionManager.runGit(GitCommands.PULL) }, enabled = connected)
            SegmentCell("Push", { sessionManager.runGit(GitCommands.PUSH) }, enabled = connected)
            val canCommit = connected && message.isNotBlank()
            SegmentCell(
                text = if (changed > 0) "Commit ${if (changed == 1) "1 file" else "$changed files"}" else "Commit",
                onClick = commit,
                weight = 1.4f,
                background = if (canCommit) c.gold else c.gold.copy(alpha = 0.35f),
                color = c.onGold,
                style = OcType.buttonStrong,
                enabled = canCommit
            )
        }
    }
}

private fun LazyListScope.fileGroup(label: String, files: List<String>) {
    if (files.isEmpty()) return
    item { SectionLabel(label) }
    items(files) { file ->
        val c = Oc.colors
        CodeText(
            file,
            color = c.ink,
            style = OcType.mono.copy(lineHeight = OcType.body.lineHeight),
            modifier = Modifier.fillMaxWidth().bottomRule(c.rule).padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}
