package com.opencode.remote.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/**
 * Compact hierarchical view of the bridge's FILE_TREE. Opening a file goes through the
 * existing fetchFile() → FILE_CONTENT pathway and the existing FileViewer route; the
 * host filesystem is never touched from Android directly.
 */
@Composable
fun FileExplorerScreen(sessionManager: RemoteSessionManager, onFileSelected: () -> Unit) {
    val c = Oc.colors
    val tree by sessionManager.fileTree.collectAsStateWithLifecycle()
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val rows = remember(tree, expanded, git) { flattenTree(tree, expanded.toSet(), git) }

    LaunchedEffect(Unit) {
        if (tree.isEmpty()) sessionManager.fetchFileTree()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Files") {
            HeaderAction("Refresh", onClick = { sessionManager.fetchFileTree() })
        }
        if (rows.isEmpty()) {
            EmptyNote("No files yet", "The tree arrives from the bridge once a workspace is open. Refresh to ask again.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { row ->
                    val node = row.node
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bottomRule(c.rule)
                            .clickable {
                                if (node.isDirectory) {
                                    expanded = if (node.path in expanded) expanded - node.path else expanded + node.path
                                } else {
                                    sessionManager.fetchFile(node.path)
                                    onFileSelected()
                                }
                            }
                            .padding(start = (18 + row.depth * 18).dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (node.isDirectory) {
                            Text(if (row.expanded) "▾" else "▸", style = OcType.bodySmall, color = c.ink3)
                            Text(node.name, style = OcType.dirName, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            val marker = row.marker
                            Text(
                                node.name,
                                style = OcType.mono.copy(lineHeight = OcType.dirName.lineHeight),
                                color = if (marker != null) c.goldInk else c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (marker != null) Text("·  ${marker.label}", style = OcType.monoSmall, color = c.goldInk, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
