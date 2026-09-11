package com.opencode.remote.ui.screens.diff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.Chevron
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.SegmentCell
import com.opencode.remote.ui.components.SegmentedBar
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType

private const val MAX_RENDERED_LINES = 1200

/**
 * Summary-first review of every pending FILE_DIFF: totals, then one compact row per
 * file, with the patch expanded only on demand. Review stays *file-level* because that
 * is what the bridge supports (ACCEPT_DIFF / REJECT_DIFF by filePath) — hunks can be
 * read, but every consequential action applies to the whole file.
 */
@Composable
fun DiffReviewScreen(sessionManager: RemoteSessionManager, onDone: () -> Unit) {
    val c = Oc.colors
    val pendingDiffs by sessionManager.pendingDiffs.collectAsStateWithLifecycle()
    val summaries = remember(pendingDiffs) { pendingDiffs.map { summarizeDiff(it) } }
    val totals = remember(summaries) { totalsOf(summaries) }
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .bottomRule(c.rule)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp)
        ) {
            Eyebrow("Pending review")
            Spacer(Modifier.height(8.dp))
            if (summaries.isEmpty()) {
                Text("Nothing to review", style = OcType.pageTitle, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text("Changes the agent proposes land here, one row per file.", style = OcType.body, color = c.ink2)
            } else {
                Text(
                    "${Formatting.plural(totals.files, "file")}, +${totals.additions} / −${totals.deletions}",
                    style = OcType.pageTitle.copy(fontFeatureSettings = "tnum"),
                    color = c.ink
                )
                Spacer(Modifier.height(6.dp))
                Text("Read the summary first. Open a file only when a number looks wrong.", style = OcType.body, color = c.ink2)
            }
        }

        if (summaries.isEmpty()) {
            Column(Modifier.weight(1f)) {
                EmptyNote("All caught up", "When the agent edits a file you'll be able to keep or revert it here.")
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(summaries, key = { it.filePath }) { summary ->
                    DiffFileRow(
                        summary = summary,
                        open = openPath == summary.filePath,
                        onToggle = { openPath = if (openPath == summary.filePath) null else summary.filePath },
                        onKeep = {
                            sessionManager.acceptDiff(summary.filePath)
                            if (openPath == summary.filePath) openPath = null
                        },
                        onRevert = {
                            sessionManager.rejectDiff(summary.filePath)
                            if (openPath == summary.filePath) openPath = null
                        }
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .topRule(c.rule)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OcButton("Later", onDone, height = 44.dp, textStyle = OcType.buttonLarge, modifier = Modifier.weight(1f))
            OcButton(
                text = when (summaries.size) {
                    0 -> "Nothing to keep"
                    1 -> "Keep the file"
                    else -> "Keep all ${summaries.size}"
                },
                onClick = {
                    summaries.forEach { sessionManager.acceptDiff(it.filePath) }
                    onDone()
                },
                kind = OcButtonKind.Gold,
                enabled = summaries.isNotEmpty(),
                height = 44.dp,
                textStyle = OcType.buttonLargeStrong,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiffFileRow(
    summary: DiffFileSummary,
    open: Boolean,
    onToggle: () -> Unit,
    onKeep: () -> Unit,
    onRevert: () -> Unit
) {
    val c = Oc.colors
    val rotation by animateFloatAsState(if (open) 90f else 0f, tween(250, easing = OcMotion.Easing), label = "chevron")
    Column(Modifier.fillMaxWidth().bottomRule(c.rule)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                CodeText(summary.filePath, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(describe(summary), style = OcType.tag.copy(letterSpacing = OcType.tag.letterSpacing * 0.6f), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("+${summary.parsed.additions}", style = OcType.buttonStrong.copy(fontSize = OcType.chip.fontSize, fontFeatureSettings = "tnum"), color = c.posInk)
                Text("−${summary.parsed.deletions}", style = OcType.buttonStrong.copy(fontSize = OcType.chip.fontSize, fontFeatureSettings = "tnum"), color = c.negInk)
            }
            Chevron(rotation)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(OcMotion.STATE_MS, easing = OcMotion.Easing)) + fadeIn(tween(OcMotion.STATE_MS)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
        ) {
            Column {
                PatchBlock(summary.parsed)
                SegmentedBar(divider = c.rule) {
                    SegmentCell("Revert this file", onRevert, height = 44.dp, color = c.ink2)
                    SegmentCell("Keep this file", onKeep, height = 44.dp, color = c.goldInk, style = OcType.buttonStrong)
                }
            }
        }
    }
}

private fun describe(summary: DiffFileSummary): String {
    val kind = when (summary.change) {
        FileChange.Added -> "New file"
        FileChange.Deleted -> "Deleted file"
        FileChange.Modified -> "Modified"
    }
    return if (summary.hunks > 0) "$kind · ${Formatting.plural(summary.hunks, "hunk")}" else kind
}

/** The patch in the code surface; one horizontal scroll for the whole block so long
 *  lines stay aligned, with each line's wash spanning the full width. */
@Composable
private fun PatchBlock(parsed: ParsedDiff) {
    val c = Oc.colors
    val lines = parsed.lines
    val shown = if (lines.size > MAX_RENDERED_LINES) lines.take(MAX_RENDERED_LINES) else lines
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.codeBg).topRule(c.rule)) {
        val viewport = maxWidth
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = viewport)
                .width(IntrinsicSize.Max)
                .padding(vertical = 4.dp)
        ) {
            shown.forEach { line ->
                val (fg, bg) = when (line.type) {
                    DiffLineType.ADDITION -> c.posInk to c.posWash
                    DiffLineType.DELETION -> c.negInk to c.negWash
                    DiffLineType.HEADER -> c.ink3 to Color.Transparent
                    DiffLineType.CONTEXT -> c.ink2 to Color.Transparent
                }
                Text(
                    text = line.text.ifEmpty { " " },
                    style = OcType.code,
                    color = fg,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().background(bg).padding(PaddingValues(horizontal = 14.dp, vertical = 3.dp))
                )
            }
            if (shown.size < lines.size) {
                Text(
                    "${lines.size - shown.size} more lines not shown here",
                    style = OcType.code, color = c.ink3,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
