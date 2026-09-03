package com.opencode.remote.ui.screens.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.RemoteSessionManager

enum class DiffLineType {
    ADDITION, DELETION, CONTEXT, HEADER
}

data class DiffLine(val text: String, val type: DiffLineType)

data class ParsedDiff(
    val lines: List<DiffLine>,
    val additions: Int,
    val deletions: Int
)

fun parseDiff(patch: String?): ParsedDiff {
    if (patch.isNullOrBlank()) return ParsedDiff(emptyList(), 0, 0)
    
    val lines = patch.lines()
    val diffLines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0

    for (line in lines) {
        when {
            line.startsWith("+++") || line.startsWith("---") -> {
                diffLines.add(DiffLine(line, DiffLineType.HEADER))
            }
            line.startsWith("+") -> {
                diffLines.add(DiffLine(line, DiffLineType.ADDITION))
                additions++
            }
            line.startsWith("-") -> {
                diffLines.add(DiffLine(line, DiffLineType.DELETION))
                deletions++
            }
            line.startsWith("@@") -> {
                diffLines.add(DiffLine(line, DiffLineType.HEADER))
            }
            else -> {
                diffLines.add(DiffLine(line, DiffLineType.CONTEXT))
            }
        }
    }
    return ParsedDiff(diffLines, additions, deletions)
}

@Composable
fun DiffReviewScreen(sessionManager: RemoteSessionManager) {
    val pendingDiff by sessionManager.pendingDiff.collectAsState()

    if (pendingDiff == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No pending diffs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "You are all caught up!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val diff = pendingDiff!!
        val parsedDiff = parseDiff(diff.patch)

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = diff.filePath,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(containerColor = Color(0xFF4CAF50)) {
                        Text("+${parsedDiff.additions}", color = Color.White)
                    }
                    Badge(containerColor = Color(0xFFF44336)) {
                        Text("-${parsedDiff.deletions}", color = Color.White)
                    }
                }
            }

            // Diff Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val horizontalScroll = rememberScrollState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                ) {
                    items(parsedDiff.lines) { line ->
                        val backgroundColor = when (line.type) {
                            DiffLineType.ADDITION -> Color(0x334CAF50) // Green tint
                            DiffLineType.DELETION -> Color(0x33F44336) // Red tint
                            DiffLineType.HEADER -> Color(0x11000000)
                            DiffLineType.CONTEXT -> Color.Transparent
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { sessionManager.rejectDiff(diff.filePath) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF44336)
                    )
                ) {
                    Text("Reject Changes")
                }
                Button(
                    onClick = { sessionManager.acceptDiff(diff.filePath) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Accept Changes")
                }
            }
        }
    }
}
