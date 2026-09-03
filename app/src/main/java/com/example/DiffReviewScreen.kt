package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.architecture.*
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class Hunk(
    val index: Int,
    val header: String,
    val lines: List<String>
)

fun parseHunks(diffText: String): List<Hunk> {
    val hunks = mutableListOf<Hunk>()
    var currentHeader = ""
    var currentLines = mutableListOf<String>()
    var hunkIndex = 0

    diffText.split("\n").forEach { line ->
        if (line.startsWith("@@")) {
            if (currentLines.isNotEmpty()) {
                hunks.add(Hunk(hunkIndex++, currentHeader, currentLines))
                currentLines = mutableListOf()
            }
            currentHeader = line
        } else if (currentHeader.isNotEmpty()) {
            currentLines.add(line)
        }
    }
    if (currentLines.isNotEmpty() || currentHeader.isNotEmpty()) {
        hunks.add(Hunk(hunkIndex, currentHeader, currentLines))
    }
    return hunks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffReviewScreen(viewModel: MainViewModel, onAction: (AppAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Diff Review", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { onAction(NavigationAction.OpenDrawer) }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { padding ->
        val fileDiffs by viewModel.fileDiffs.collectAsStateWithLifecycle(emptyList())
        
        if (fileDiffs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No pending diffs", color = TextSecondary)
            }
            return@Scaffold
        }
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val isWide = maxWidth > 600.dp
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(fileDiffs) { diff ->
                    val hunks = remember(diff.diffText) { parseHunks(diff.diffText) }
                    
                    Column {
                        Text(diff.fileName, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        
                        if (hunks.isEmpty()) {
                            // Render raw diff if no hunks detected
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TerminalBackground, RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    diff.diffText.split("\n").forEach { line ->
                                        val color = when {
                                            line.startsWith("+") -> TerminalTextSuccess
                                            line.startsWith("-") -> TerminalTextError
                                            line.startsWith("@@") -> TextSecondary
                                            else -> TerminalTextDefault
                                        }
                                        Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            hunks.forEach { hunk ->
                                Column(modifier = Modifier.padding(bottom = 16.dp).background(Surface, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    Text(hunk.header, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                                    
                                    if (isWide) {
                                        // Side-by-side view
                                        Row(modifier = Modifier.fillMaxWidth().background(TerminalBackground, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                hunk.lines.filter { !it.startsWith("+") }.forEach { line ->
                                                    val color = if (line.startsWith("-")) TerminalTextError else TerminalTextDefault
                                                    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                hunk.lines.filter { !it.startsWith("-") }.forEach { line ->
                                                    val color = if (line.startsWith("+")) TerminalTextSuccess else TerminalTextDefault
                                                    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        // Stacked view
                                        Box(modifier = Modifier.fillMaxWidth().background(TerminalBackground, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                            Column {
                                                hunk.lines.forEach { line ->
                                                    val color = when {
                                                        line.startsWith("+") -> TerminalTextSuccess
                                                        line.startsWith("-") -> TerminalTextError
                                                        else -> TerminalTextDefault
                                                    }
                                                    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Hunk-level accept/reject is not supported — the bridge has no
                                    // per-hunk apply (E2). Use file-level Accept/Reject below instead.
                                    Text(
                                        "Not supported — use file-level accept/reject",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { },
                                            enabled = false,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = TerminalTextError)
                                        ) {
                                            Text("Reject Hunk")
                                        }
                                        Button(
                                            onClick = { },
                                            enabled = false,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = TerminalTextSuccess)
                                        ) {
                                            Text("Accept Hunk")
                                        }
                                    }
                                }
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { onAction(DiffAction.RejectFileDiff(diff.fileName)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalTextError)
                            ) {
                                Text("Reject Entire File")
                            }
                            Button(
                                onClick = { onAction(DiffAction.AcceptFileDiff(diff.fileName)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalTextSuccess)
                            ) {
                                Text("Accept Entire File")
                            }
                        }
                    }
                }
            }
        }
    }
}
