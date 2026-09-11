package com.opencode.remote.ui.screens.files

import com.opencode.remote.data.dto.FileNodeDto
import com.opencode.remote.data.dto.GitStatusDto

enum class GitMarker(val label: String) { Modified("modified"), Added("new"), Deleted("deleted") }

data class FileRow(val node: FileNodeDto, val depth: Int, val expanded: Boolean, val marker: GitMarker?)

/** Flattens the bridge's FILE_TREE into the visible rows for the current expansion
 *  state, so the explorer can use one LazyColumn instead of recursive Columns. */
fun flattenTree(nodes: List<FileNodeDto>, expanded: Set<String>, git: GitStatusDto?): List<FileRow> {
    val out = mutableListOf<FileRow>()
    fun walk(list: List<FileNodeDto>, depth: Int) {
        for (node in list) {
            val isOpen = node.isDirectory && node.path in expanded
            out += FileRow(node, depth, isOpen, if (node.isDirectory) null else gitMarkerFor(node.path, git))
            if (isOpen) node.children?.let { walk(it, depth + 1) }
        }
    }
    walk(nodes, 0)
    return out
}

/**
 * Matches a tree path against GIT_STATUS's repo-relative paths. Only an exact match or
 * a whole-segment suffix match counts, so an unmatched path simply shows no marker
 * rather than a wrong one.
 */
fun gitMarkerFor(path: String, git: GitStatusDto?): GitMarker? {
    if (git == null) return null
    val p = normalize(path)
    fun hit(list: List<String>) = list.any { g -> val n = normalize(g); n.isNotEmpty() && (p == n || p.endsWith("/$n")) }
    return when {
        hit(git.modifiedFiles) -> GitMarker.Modified
        hit(git.addedFiles) -> GitMarker.Added
        hit(git.deletedFiles) -> GitMarker.Deleted
        else -> null
    }
}

private fun normalize(path: String) = path.replace('\\', '/').removePrefix("./").trimEnd('/')
