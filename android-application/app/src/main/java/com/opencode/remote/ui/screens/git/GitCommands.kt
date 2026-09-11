package com.opencode.remote.ui.screens.git

/**
 * The git command strings sent through RemoteSessionManager.runGit(). These are exactly
 * what the pre-redesign Git screen sent; the bridge keeps its own subcommand allowlist
 * and validation, so the redesign adds no new commands and no Android-side shell.
 */
object GitCommands {
    const val FETCH = "fetch"
    const val PULL = "pull"
    const val PUSH = "push"

    fun commit(message: String): String = "commit -m \"$message\""
}
