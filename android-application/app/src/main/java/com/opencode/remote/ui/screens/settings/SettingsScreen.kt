package com.opencode.remote.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.opencode.remote.BuildConfig
import com.opencode.remote.data.dto.TaskStatus
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.domain.model.UserSettings
import com.opencode.remote.domain.repository.SettingsRepository
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.domain.model.Appearance
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

/**
 * Connection info straight from TokenStorage and the live socket, appearance, paired
 * devices, disconnect, and the destructive forget flow (TokenStorage.clear() →
 * disconnect → back to pairing), exactly as before.
 */
@Composable
fun SettingsScreen(
    sessionManager: RemoteSessionManager,
    tokenStorage: TokenStorage,
    settingsRepository: SettingsRepository,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onOpenDevices: () -> Unit,
    onDisconnect: () -> Unit
) {
    val c = Oc.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentials = remember { tokenStorage.getLastCredentials() }
    val connection by sessionManager.connectionState.collectAsStateWithLifecycle()
    val workspace by sessionManager.activeWorkspace.collectAsStateWithLifecycle()
    val deviceId by sessionManager.currentDeviceId.collectAsStateWithLifecycle()
    val tasks by sessionManager.tasks.collectAsStateWithLifecycle()
    val runningTasks = tasks.values.filter { TaskStatus.of(it.status) == TaskStatus.RUNNING }
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = UserSettings(appearance = appearance)
    )
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var killArmed by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsRepository.setNotificationsEnabled(granted) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings")

        Column(Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp)) {
            Eyebrow("Bridge")
            Spacer(Modifier.height(10.dp))
            val rows = listOfNotNull(
                "Host" to (credentials?.host ?: "—"),
                "Port" to (credentials?.port?.toString() ?: "—"),
                "Device name" to (credentials?.deviceName ?: "—"),
                deviceId?.let { "Device ID" to it },
                "Status" to connectionLabel(connection),
                workspace?.let { "Workspace" to it }
            )
            rows.forEach { (label, value) ->
                Text(
                    "$label: $value",
                    style = OcType.mono.copy(lineHeight = OcType.mono.fontSize * 1.8f),
                    color = c.ink
                )
            }
        }

        if (runningTasks.isNotEmpty()) {
            LaunchedEffect(killArmed) {
                if (killArmed) {
                    delay(4000)
                    killArmed = false
                }
            }
            Row(
                Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${runningTasks.size} task${if (runningTasks.size == 1) "" else "s"} running",
                    style = OcType.rowStrong, color = c.ink, modifier = Modifier.weight(1f)
                )
                OcButton(
                    if (killArmed) "Confirm kill all" else "Kill all",
                    onClick = {
                        if (killArmed) {
                            runningTasks.forEach { sessionManager.killTask(it.id) }
                            killArmed = false
                        } else {
                            killArmed = true
                        }
                    },
                    kind = OcButtonKind.Destructive,
                    height = 34.dp
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Appearance", style = OcType.rowStrong, color = c.ink, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Appearance.entries.forEach { option ->
                    val selected = option == appearance
                    Eyebrow(
                        option.name,
                        style = if (selected) OcType.tag.copy(fontWeight = FontWeight.SemiBold) else OcType.tag,
                        color = if (selected) c.goldInk else c.ink2,
                        modifier = Modifier
                            .clickable(role = Role.RadioButton) { onAppearanceChange(option) }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }

        BooleanSettingRow(
            label = "True black (OLED)",
            checked = settings.trueBlackEnabled,
            enabled = appearance != Appearance.Light,
            onCheckedChange = { enabled -> scope.launch { settingsRepository.setTrueBlackEnabled(enabled) } }
        )

        BooleanSettingRow(
            label = "Notifications",
            checked = settings.notificationsEnabled,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { settingsRepository.setNotificationsEnabled(enabled) }
                }
            }
        )

        BooleanSettingRow(
            label = "Diagnostic trace (Logcat)",
            checked = settings.diagnosticTraceEnabled,
            onCheckedChange = { enabled -> scope.launch { settingsRepository.setDiagnosticTraceEnabled(enabled) } }
        )

        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OcButton("Paired devices", onOpenDevices, modifier = Modifier.fillMaxWidth(), height = 46.dp, textStyle = OcType.buttonLarge)
            OcButton(
                "Disconnect",
                onClick = {
                    sessionManager.disconnect()
                    onDisconnect()
                },
                modifier = Modifier.fillMaxWidth(), height = 46.dp, textStyle = OcType.buttonLarge
            )
            OcButton(
                "Forget this bridge",
                onClick = { confirmForget = true },
                kind = OcButtonKind.Destructive,
                modifier = Modifier.fillMaxWidth(), height = 46.dp, textStyle = OcType.buttonLargeStrong
            )
        }

        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Eyebrow("About")
            Spacer(Modifier.height(10.dp))
            Text(
                "OpenCode Remote · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = OcType.monoSmall, color = c.ink3
            )
        }
    }

    if (confirmForget) {
        Dialog(onDismissRequest = { confirmForget = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.panel)
                    .border(1.dp, c.ruleStrong)
                    .padding(20.dp)
            ) {
                Eyebrow("Forget this bridge", color = c.negInk)
                Spacer(Modifier.height(12.dp))
                Text("Erase the saved credentials?", style = OcType.statusTitle, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The host, token and device name stored on this phone are deleted. You'll need to pair again to reconnect.",
                    style = OcType.body, color = c.ink2
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OcButton("Cancel", { confirmForget = false }, modifier = Modifier.weight(1f))
                    OcButton(
                        "Forget",
                        onClick = {
                            tokenStorage.clear()
                            confirmForget = false
                            sessionManager.disconnect()
                            onDisconnect()
                        },
                        kind = OcButtonKind.Destructive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** On/off chip pair, same visual language as the Appearance row's radio chips. */
@Composable
private fun BooleanSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val c = Oc.colors
    Row(
        Modifier.fillMaxWidth().bottomRule(c.rule).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = OcType.rowStrong, color = if (enabled) c.ink else c.ink3, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(true, false).forEach { option ->
                val selected = option == checked
                Eyebrow(
                    if (option) "On" else "Off",
                    style = if (selected) OcType.tag.copy(fontWeight = FontWeight.SemiBold) else OcType.tag,
                    color = when {
                        !enabled -> c.ink3
                        selected -> c.goldInk
                        else -> c.ink2
                    },
                    modifier = Modifier
                        .clickable(enabled = enabled, role = Role.RadioButton) { onCheckedChange(option) }
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Connected -> "Connected"
    ConnectionState.Connecting -> "Connecting…"
    is ConnectionState.Reconnecting -> "Reconnecting · attempt ${state.attempt}"
    is ConnectionState.Error -> "Connection lost · ${state.message}"
    ConnectionState.Disconnected -> "Disconnected"
}
