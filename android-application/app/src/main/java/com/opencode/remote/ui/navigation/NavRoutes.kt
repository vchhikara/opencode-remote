package com.opencode.remote.ui.navigation

/**
 * Every screen in the app, in one place: the outer flow (pairing → workspace picker →
 * main shell → file viewer) and the drawer destinations nested inside [Main]'s shell.
 * The drawer is generated from [Destination.drawerItems] rather than a parallel list.
 */
sealed class NavRoutes(val route: String) {
    object Pairing : NavRoutes("pairing")
    object Workspaces : NavRoutes("workspaces")
    object Main : NavRoutes("main")
    object FileViewer : NavRoutes("fileViewer")

    /** Destinations inside the main shell. [Home] is the start destination. */
    sealed class Destination(route: String, val label: String) : NavRoutes(route) {
        object Home : Destination("home", "Home")
        object RunLog : Destination("runlog", "Run log")
        object Terminal : Destination("terminal", "Terminal")
        object Diffs : Destination("diffs", "Diffs")
        object Git : Destination("git", "Git")
        object Files : Destination("files", "Files")
        object Sessions : Destination("sessions", "Sessions")
        object Devices : Destination("devices", "Paired devices")
        object Settings : Destination("settings", "Settings")
        /** Reached from the header / drawer title, not listed as a drawer row. */
        object Workspace : Destination("workspace", "Switch workspace")

        companion object {
            // A getter, not a stored list: a stored list in the companion would be built
            // during Destination's static init and could capture a not-yet-initialised
            // subclass object (null) depending on which object is touched first.
            val drawerItems: List<Destination>
                get() = listOf(Home, RunLog, Terminal, Diffs, Git, Files, Sessions, Devices, Settings)

            fun fromRoute(route: String?): Destination? =
                (drawerItems + Workspace).firstOrNull { it.route == route }
        }
    }
}
