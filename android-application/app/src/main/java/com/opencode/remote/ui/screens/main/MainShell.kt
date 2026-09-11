package com.opencode.remote.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.components.endRule
import com.opencode.remote.ui.components.rememberAgentPhase
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.navigation.NavRoutes.Destination
import com.opencode.remote.ui.screens.devices.DevicesScreen
import com.opencode.remote.ui.screens.diff.DiffReviewScreen
import com.opencode.remote.ui.screens.files.FileExplorerScreen
import com.opencode.remote.ui.screens.git.GitScreen
import com.opencode.remote.ui.screens.home.HomeScreen
import com.opencode.remote.ui.screens.runlog.RunLogScreen
import com.opencode.remote.ui.screens.sessions.SessionsScreen
import com.opencode.remote.ui.screens.settings.SettingsScreen
import com.opencode.remote.ui.screens.terminal.TerminalScreen
import com.opencode.remote.ui.screens.workspace.WorkspaceSwitchScreen
import com.opencode.remote.ui.state.AgentPhase
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.domain.repository.SettingsRepository
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The redesigned main application: one Home surface plus a drawer, replacing the old
 * eight-tab ScrollableTabRow dashboard. Uses the single app-level [sessionManager]
 * passed down from MainActivity — no destination creates its own.
 */
@Composable
fun MainShell(
    sessionManager: RemoteSessionManager,
    tokenStorage: TokenStorage,
    settingsRepository: SettingsRepository,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onOpenFileViewer: () -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val issuedToken by sessionManager.issuedToken.collectAsStateWithLifecycle()
    val connectionState by sessionManager.connectionState.collectAsStateWithLifecycle()

    // Task 8.1 close-the-loop (carried over from the old dashboard): when the bridge
    // issues a fresh per-device token in place of the pairing secret we connected with,
    // persist it — otherwise a later reconnect would strand this device.
    LaunchedEffect(issuedToken) {
        val fresh = issuedToken ?: return@LaunchedEffect
        tokenStorage.getLastCredentials()?.let { creds ->
            tokenStorage.saveCredentials(creds.host, creds.port, fresh, creds.deviceName)
        }
    }

    // Reconnect-on-resume (Task 7.2.1, carried over): re-drive connect() on every
    // ON_RESUME; WsClient's idempotency guard makes this a no-op when already connected.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sessionManager.connect()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Drawer counts come from real lists; ask for them once per (re)connection.
    LaunchedEffect(connectionState is ConnectionState.Connected) {
        if (connectionState is ConnectionState.Connected) {
            sessionManager.listSessions()
            sessionManager.listDevices()
        }
    }

    val isDark = Oc.colors.isDark
    val credentials = remember { tokenStorage.getLastCredentials() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route) ?: Destination.Home

    val go: (Destination) -> Unit = { dest ->
        scope.launch { drawerState.close() }
        navController.navigateTo(dest)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Oc.colors.scrim,
        drawerContent = {
            AppDrawer(
                sessionManager = sessionManager,
                current = current,
                bridgeAddress = credentials?.let { "${it.host}:${it.port}" },
                onGo = go
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            ShellHeader(
                sessionManager = sessionManager,
                onMenu = { scope.launch { drawerState.open() } },
                onWorkspace = { go(Destination.Workspace) },
                isDark = isDark,
                onToggleTheme = { onAppearanceChange(if (isDark) Appearance.Light else Appearance.Dark) }
            )
            BridgeErrorStrip(sessionManager)
            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enterTransition = { fadeIn(tween(220, easing = OcMotion.Easing)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220, easing = OcMotion.Easing)) },
                popExitTransition = { fadeOut(tween(160)) }
            ) {
                composable(Destination.Home.route) { HomeScreen(sessionManager, onNavigate = go) }
                composable(Destination.RunLog.route) { RunLogScreen(sessionManager, onNavigate = go) }
                composable(Destination.Terminal.route) { TerminalScreen(sessionManager) }
                composable(Destination.Diffs.route) { DiffReviewScreen(sessionManager, onDone = { go(Destination.Home) }) }
                composable(Destination.Git.route) { GitScreen(sessionManager) }
                composable(Destination.Files.route) { FileExplorerScreen(sessionManager, onFileSelected = onOpenFileViewer) }
                composable(Destination.Sessions.route) { SessionsScreen(sessionManager, onOpened = { go(Destination.Home) }) }
                composable(Destination.Devices.route) { DevicesScreen(sessionManager) }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        sessionManager = sessionManager,
                        tokenStorage = tokenStorage,
                        settingsRepository = settingsRepository,
                        appearance = appearance,
                        onAppearanceChange = onAppearanceChange,
                        onOpenDevices = { go(Destination.Devices) },
                        onDisconnect = onDisconnect
                    )
                }
                composable(Destination.Workspace.route) { WorkspaceSwitchScreen(sessionManager, onOpened = { go(Destination.Home) }) }
            }
        }
    }

    // Registered after the NavHost so it takes precedence over the NavController's own
    // back callback while the drawer is open: back closes the drawer first.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
}

/** Drawer navigation: Home pops back to the start destination; everything else is a
 *  single-top entry above Home with saved/restored state, so back always lands on Home. */
private fun NavHostController.navigateTo(dest: Destination) {
    if (dest == Destination.Home) {
        if (!popBackStack(Destination.Home.route, inclusive = false)) navigate(Destination.Home.route)
        return
    }
    navigate(dest.route) {
        popUpTo(Destination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ShellHeader(
    sessionManager: RemoteSessionManager,
    onMenu: () -> Unit,
    onWorkspace: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val c = Oc.colors
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    val workspace by sessionManager.activeWorkspace.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .bottomRule(c.rule)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hamburger: 20 / 20 / 13 px bars, 1.5px thick, per the prototype.
        Column(
            Modifier
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = "Open navigation" }
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.width(20.dp).height(1.5.dp).background(c.ink))
            Box(Modifier.width(20.dp).height(1.5.dp).background(c.ink))
            Box(Modifier.width(13.dp).height(1.5.dp).background(c.ink))
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onWorkspace)
                .padding(vertical = 6.dp)
        ) {
            Text("OpenCode Remote", style = OcType.screenTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Eyebrow(AgentPresentation.repoLine(git, workspace), style = OcType.tag, color = c.ink2)
        }
        Box(
            Modifier
                .border(1.dp, c.ruleStrong)
                .clickable(role = Role.Button, onClick = onToggleTheme)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Eyebrow(if (isDark) "Light" else "Dark", style = OcType.eyebrow.copy(letterSpacing = OcType.tag.letterSpacing), color = c.ink2)
        }
    }
}

/** Surfaces post-handshake ERROR frames (rejected git subcommand, path escape…) that
 *  were previously only logged. Auto-dismisses after a few seconds. */
@Composable
private fun BridgeErrorStrip(sessionManager: RemoteSessionManager) {
    val c = Oc.colors
    val error by sessionManager.lastError.collectAsStateWithLifecycle()
    LaunchedEffect(error?.id) {
        if (error != null) {
            delay(8000)
            sessionManager.clearLastError()
        }
    }
    AnimatedVisibility(
        visible = error != null,
        enter = expandVertically(tween(OcMotion.STATE_MS, easing = OcMotion.Easing)) + fadeIn(),
        exit = shrinkVertically(tween(OcMotion.STATE_MS)) + fadeOut()
    ) {
        val message = error?.message.orEmpty()
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.negWash)
                .bottomRule(c.rule)
                .clickable { sessionManager.clearLastError() }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Bridge refused", color = c.negInk)
                Spacer(Modifier.height(4.dp))
                Text(message, style = OcType.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("Dismiss", style = OcType.chip, color = c.ink2)
        }
    }
}

@Composable
private fun AppDrawer(
    sessionManager: RemoteSessionManager,
    current: Destination,
    bridgeAddress: String?,
    onGo: (Destination) -> Unit
) {
    val c = Oc.colors
    val phase by rememberAgentPhase(sessionManager)
    val pendingDiffs by sessionManager.pendingDiffs.collectAsStateWithLifecycle()
    val git by sessionManager.gitStatus.collectAsStateWithLifecycle()
    val sessions by sessionManager.sessions.collectAsStateWithLifecycle()
    val devices by sessionManager.devices.collectAsStateWithLifecycle()
    val workspace by sessionManager.activeWorkspace.collectAsStateWithLifecycle()
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    val active = AgentPresentation.isActive(phase)
    val changed = git?.let { it.modifiedFiles.size + it.addedFiles.size + it.deletedFiles.size } ?: 0

    fun countFor(d: Destination): String = when (d) {
        Destination.Home -> if (active) "live" else ""
        Destination.RunLog -> if (active) "live" else ""
        Destination.Diffs -> if (pendingDiffs.isNotEmpty()) "${pendingDiffs.size} pending" else ""
        Destination.Git -> if (changed > 0) "$changed changed" else ""
        Destination.Sessions -> if (sessions.isNotEmpty()) "${sessions.size}" else ""
        Destination.Devices -> if (devices.isNotEmpty()) "${devices.size}" else ""
        else -> ""
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(288.dp)
            .background(c.panel)
            .endRule(c.ruleStrong)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .bottomRule(c.rule)
                .clickable { onGo(Destination.Workspace) }
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
        ) {
            Text("OpenCode Remote", style = OcType.drawerTitle, color = c.ink)
            Spacer(Modifier.height(9.dp))
            Eyebrow("Workspace")
            Spacer(Modifier.height(5.dp))
            Text(
                workspace?.let { AgentPresentation.workspaceName(it) } ?: "None open — tap to choose",
                style = OcType.monoSmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            git?.let {
                Text(AgentPresentation.repoLine(it, null), style = OcType.monoSmall, color = c.ink2, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Destination.drawerItems.forEach { dest ->
                val selected = dest == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.background(c.goldWash).startRule(c.gold, 2.dp) else Modifier)
                        .bottomRule(c.rule)
                        .clickable(role = Role.Button) { onGo(dest) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        dest.label,
                        style = if (selected) OcType.drawerItem.copy(fontWeight = FontWeight.SemiBold) else OcType.drawerItem,
                        color = c.ink,
                        modifier = Modifier.weight(1f)
                    )
                    val count = countFor(dest)
                    if (count.isNotEmpty()) Eyebrow(count, style = OcType.tag, color = if (selected) c.goldInk else c.ink2)
                }
            }
        }
        Text(
            text = bridgeFooter(connection, bridgeAddress),
            style = OcType.bodySmall,
            color = c.ink2,
            modifier = Modifier
                .fillMaxWidth()
                .topRule(c.rule)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

/** Footer line built from the stored credential and live socket state only. */
private fun bridgeFooter(state: ConnectionState, address: String?): String {
    val where = address?.let { "Bridge on $it" } ?: "Bridge"
    return when (state) {
        ConnectionState.Connected -> "$where · connected"
        ConnectionState.Connecting -> "$where · connecting…"
        is ConnectionState.Reconnecting -> "$where · reconnecting (attempt ${state.attempt})"
        is ConnectionState.Error -> "$where · connection lost, retrying"
        ConnectionState.Disconnected -> "$where · disconnected"
    }
}
