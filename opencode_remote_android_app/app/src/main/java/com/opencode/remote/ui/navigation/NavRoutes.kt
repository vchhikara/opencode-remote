package com.opencode.remote.ui.navigation

/** Every screen in the app, in one place — the outer flow (pairing → workspace picker
 *  → main dashboard → file viewer) and the seven tabs nested inside [Main]. Previously
 *  the outer 4 routes lived here while the 7 tab routes were raw strings duplicated
 *  across three parallel lists in MainDashboardScreen; this is the single source of
 *  truth for both, so adding a screen means adding one route, not synchronizing three
 *  places by hand. */
sealed class NavRoutes(val route: String) {
    object Pairing : NavRoutes("pairing")
    object Workspaces : NavRoutes("workspaces")
    object Main : NavRoutes("main")
    object FileViewer : NavRoutes("fileViewer")

    /** Tabs nested inside [Main]'s dashboard. */
    sealed class Tab(route: String) : NavRoutes(route) {
        object Chat : Tab("chat")
        object Files : Tab("files")
        object Diff : Tab("diff")
        object Terminal : Tab("terminal")
        object Tasks : Tab("tasks")
        object Sessions : Tab("sessions")
        object Git : Tab("git")
        object Settings : Tab("settings")
    }
}
