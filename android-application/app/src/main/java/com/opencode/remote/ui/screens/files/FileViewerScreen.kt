package com.opencode.remote.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/** Read-only file content from FILE_CONTENT, in the code surface. */
@Composable
fun FileViewerScreen(sessionManager: RemoteSessionManager, onBack: () -> Unit) {
    val c = Oc.colors
    val content by sessionManager.fileContent.collectAsStateWithLifecycle()
    val file = content
    val lines = remember(file) { file?.content?.split("\n").orEmpty() }
    val gutter = lines.size.toString().length

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).bottomRule(c.rule).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "‹",
                style = OcType.pageTitle,
                color = c.ink,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    file?.path?.substringAfterLast('/')?.ifEmpty { file.path } ?: "Loading…",
                    style = OcType.screenTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                file?.path?.let { CodeText(it, color = c.ink2, style = OcType.monoSmall) }
            }
        }
        if (file == null) {
            Text("Waiting for the file from the bridge…", style = OcType.body, color = c.ink3, modifier = Modifier.padding(18.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(c.codeBg).horizontalScroll(rememberScrollState()),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 1.dp)) {
                        Text(
                            (index + 1).toString().padStart(gutter, ' '),
                            style = OcType.code, color = c.ink3,
                            modifier = Modifier.padding(end = 14.dp)
                        )
                        Text(line.ifEmpty { " " }, style = OcType.code, color = c.ink, softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }
}
