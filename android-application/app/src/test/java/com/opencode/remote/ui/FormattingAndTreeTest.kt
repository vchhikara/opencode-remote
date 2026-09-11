package com.opencode.remote.ui

import com.opencode.remote.data.dto.FileDiffDto
import com.opencode.remote.data.dto.FileNodeDto
import com.opencode.remote.data.dto.GitStatusDto
import com.opencode.remote.ui.screens.diff.FileChange
import com.opencode.remote.ui.screens.diff.summarizeDiff
import com.opencode.remote.ui.screens.diff.totalsOf
import com.opencode.remote.ui.screens.files.GitMarker
import com.opencode.remote.ui.screens.files.flattenTree
import com.opencode.remote.ui.screens.files.gitMarkerFor
import com.opencode.remote.ui.screens.git.GitCommands
import com.opencode.remote.ui.state.Formatting
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingAndTreeTest {

    @Test
    fun elapsedFormatsMinutesAndHours() {
        assertEquals("00:00", Formatting.elapsed(0))
        assertEquals("01:05", Formatting.elapsed(65_000))
        assertEquals("1:00:01", Formatting.elapsed(3_601_000))
    }

    @Test
    fun epochSecondsAreNormalisedToMillis() {
        assertEquals(1_700_000_000_000L, Formatting.normalizeEpoch(1_700_000_000L))
        assertEquals(1_700_000_000_000L, Formatting.normalizeEpoch(1_700_000_000_000L))
    }

    @Test
    fun isoAuditTimestampsParseOrReturnNull() {
        assertEquals(0L, Formatting.parseIsoUtc("1970-01-01T00:00:00.000Z"))
        assertEquals(1000L, Formatting.parseIsoUtc("1970-01-01T00:00:01Z"))
        assertNull(Formatting.parseIsoUtc("yesterday"))
    }

    @Test
    fun auditDetailFlattensObjects() {
        val detail = buildJsonObject {
            put("command", JsonPrimitive("status"))
            put("exitCode", JsonPrimitive(0))
        }
        assertEquals("status · exitCode 0", Formatting.auditDetail(detail))
        assertEquals("", Formatting.auditDetail(null))
    }

    @Test
    fun diffSummaryCountsHunksAndDetectsNewFiles() {
        val patch = """
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,2 @@
            +one
            +two
        """.trimIndent()
        val s = summarizeDiff(FileDiffDto("new.txt", patch))
        assertEquals(FileChange.Added, s.change)
        assertEquals(1, s.hunks)
        assertEquals(2, s.parsed.additions)
        val totals = totalsOf(listOf(s, summarizeDiff(FileDiffDto("b", "@@ -1 +1 @@\n-x\n+y"))))
        assertEquals(2, totals.files)
        assertEquals(3, totals.additions)
        assertEquals(1, totals.deletions)
    }

    @Test
    fun deletedLineStartingWithDashesInsideHunkIsADeletion() {
        val s = summarizeDiff(FileDiffDto("a.sql", "--- a/a.sql\n+++ b/a.sql\n@@ -1 +1 @@\n--- old comment\n+-- new comment"))
        assertEquals(1, s.parsed.deletions)
        assertEquals(1, s.parsed.additions)
    }

    @Test
    fun treeFlattensOnlyExpandedDirectories() {
        val tree = listOf(
            FileNodeDto("src", "src", true, listOf(FileNodeDto("a.kt", "src/a.kt", false))),
            FileNodeDto("README.md", "README.md", false)
        )
        assertEquals(listOf("src", "README.md"), flattenTree(tree, emptySet(), null).map { it.node.name })
        val open = flattenTree(tree, setOf("src"), GitStatusDto(modifiedFiles = listOf("src/a.kt")))
        assertEquals(listOf("src", "a.kt", "README.md"), open.map { it.node.name })
        assertEquals(1, open[1].depth)
        assertEquals(GitMarker.Modified, open[1].marker)
    }

    @Test
    fun gitMarkersRequireWholeSegmentMatches() {
        val git = GitStatusDto(modifiedFiles = listOf("bridge/main.js"), addedFiles = listOf("x.kt"))
        assertEquals(GitMarker.Modified, gitMarkerFor("/home/me/repo/bridge/main.js", git))
        assertEquals(GitMarker.Added, gitMarkerFor("x.kt", git))
        assertNull(gitMarkerFor("/home/me/repo/notbridge/main.jsx", git))
        assertNull(gitMarkerFor("ax.kt", git))
    }

    @Test
    fun gitCommandStringsAreUnchangedFromThePreviousScreen() {
        assertEquals("commit -m \"Fix resize\"", GitCommands.commit("Fix resize"))
        assertEquals(listOf("fetch", "pull", "push"), listOf(GitCommands.FETCH, GitCommands.PULL, GitCommands.PUSH))
    }
}
