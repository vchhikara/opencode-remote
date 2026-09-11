package com.opencode.remote.ui.screens.diff

import com.opencode.remote.data.dto.FileDiffDto

enum class DiffLineType {
    ADDITION, DELETION, CONTEXT, HEADER
}

data class DiffLine(val text: String, val type: DiffLineType)

data class ParsedDiff(
    val lines: List<DiffLine>,
    val additions: Int,
    val deletions: Int
)

/**
 * Parses a unified patch (or the bridge's plain +/- line diff) into typed lines.
 * `---`/`+++` file headers and `diff`/`index`/mode lines are headers only *outside* a
 * hunk, so a deleted line that happens to start with "--" is still counted as a
 * deletion; a patch with no `@@` markers at all parses exactly as before.
 */
fun parseDiff(patch: String?): ParsedDiff {
    if (patch.isNullOrBlank()) return ParsedDiff(emptyList(), 0, 0)

    val diffLines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0
    var inHunk = false

    for (line in patch.lines()) {
        when {
            line.startsWith("@@") -> {
                inHunk = true
                diffLines.add(DiffLine(line, DiffLineType.HEADER))
            }
            !inHunk && (line.startsWith("+++") || line.startsWith("---")) -> diffLines.add(DiffLine(line, DiffLineType.HEADER))
            !inHunk && isMetaLine(line) -> diffLines.add(DiffLine(line, DiffLineType.HEADER))
            line.startsWith("+") -> {
                diffLines.add(DiffLine(line, DiffLineType.ADDITION))
                additions++
            }
            line.startsWith("-") -> {
                diffLines.add(DiffLine(line, DiffLineType.DELETION))
                deletions++
            }
            line.startsWith("\\") -> diffLines.add(DiffLine(line, DiffLineType.HEADER)) // "\ No newline at end of file"
            else -> diffLines.add(DiffLine(line, DiffLineType.CONTEXT))
        }
    }
    return ParsedDiff(diffLines, additions, deletions)
}

private fun isMetaLine(line: String) =
    line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file mode") ||
        line.startsWith("deleted file mode") || line.startsWith("similarity index") ||
        line.startsWith("rename from") || line.startsWith("rename to")

enum class FileChange { Modified, Added, Deleted }

/** Summary-first view of one pending diff: counts, hunk count and change kind, all
 *  derived from the patch text itself. */
data class DiffFileSummary(
    val filePath: String,
    val parsed: ParsedDiff,
    val hunks: Int,
    val change: FileChange
)

data class DiffTotals(val files: Int, val additions: Int, val deletions: Int)

fun summarizeDiff(diff: FileDiffDto): DiffFileSummary {
    val parsed = parseDiff(diff.patch)
    val lines = diff.patch.lines()
    val change = when {
        lines.any { it.startsWith("--- /dev/null") || it.startsWith("new file mode") } -> FileChange.Added
        lines.any { it.startsWith("+++ /dev/null") || it.startsWith("deleted file mode") } -> FileChange.Deleted
        else -> FileChange.Modified
    }
    return DiffFileSummary(diff.filePath, parsed, lines.count { it.startsWith("@@") }, change)
}

fun totalsOf(summaries: List<DiffFileSummary>) = DiffTotals(
    files = summaries.size,
    additions = summaries.sumOf { it.parsed.additions },
    deletions = summaries.sumOf { it.parsed.deletions }
)
