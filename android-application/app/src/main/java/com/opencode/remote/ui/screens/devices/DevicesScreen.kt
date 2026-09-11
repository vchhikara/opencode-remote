package com.opencode.remote.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.remote.data.dto.AuditLogEntryDto
import com.opencode.remote.data.dto.DeviceDto
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.EmptyNote
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.HeaderAction
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.ScreenHeader
import com.opencode.remote.ui.components.bottomRule
import com.opencode.remote.ui.state.Formatting
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType
import kotlinx.coroutines.delay

private const val AUDIT_SHOWN = 40

/**
 * Paired devices from DEVICE_LIST with REVOKE_TOKEN, plus recent AUDIT_LOG entries.
 * "This phone" is marked only when the CONNECTED handshake supplied a deviceId that
 * matches — never inferred from the display name.
 */
@Composable
fun DevicesScreen(sessionManager: RemoteSessionManager) {
    val c = Oc.colors
    val devices by sessionManager.devices.collectAsStateWithLifecycle()
    val audit by sessionManager.auditLog.collectAsStateWithLifecycle()
    val currentId by sessionManager.currentDeviceId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sessionManager.listDevices()
        sessionManager.fetchAuditLog()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                "Paired devices",
                subtitle = "Each phone holds its own credential. Revoking one leaves the rest signed in."
            ) {
                HeaderAction("Refresh", onClick = {
                    sessionManager.listDevices()
                    sessionManager.fetchAuditLog()
                })
            }
        }
        if (devices.isEmpty()) {
            item { EmptyNote("No devices listed", "The bridge hasn't reported any paired devices yet.") }
        }
        items(devices.distinctBy { it.deviceId }, key = { it.deviceId }) { device ->
            DeviceRow(device, isCurrent = currentId != null && device.deviceId == currentId) {
                sessionManager.revokeToken(device.deviceId)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Eyebrow("Recent audit entries")
                Spacer(Modifier.height(12.dp))
                if (audit.isEmpty()) {
                    Text("No audit entries yet.", style = OcType.body, color = c.ink3)
                }
            }
        }
        items(audit.asReversed().take(AUDIT_SHOWN)) { entry -> AuditRow(entry) }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun DeviceRow(device: DeviceDto, isCurrent: Boolean, onRevoke: () -> Unit) {
    val c = Oc.colors
    // Two-step revoke: the first tap arms it for a few seconds.
    var armed by remember(device.deviceId) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    Row(
        Modifier.fillMaxWidth().bottomRule(c.rule).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.deviceName + if (isCurrent) " · this phone" else "",
                style = OcType.rowStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val now = System.currentTimeMillis()
            CodeText(
                "${device.deviceId} · paired ${Formatting.shortDate(Formatting.normalizeEpoch(device.issuedAt), now)}",
                color = c.ink2, style = OcType.monoSmall
            )
        }
        when {
            isCurrent -> OcButton("Current", {}, enabled = false, height = 34.dp)
            armed -> OcButton("Confirm revoke", { armed = false; onRevoke() }, kind = OcButtonKind.Destructive, height = 34.dp)
            else -> OcButton("Revoke", { armed = true }, kind = OcButtonKind.NegativeOutline, height = 34.dp)
        }
    }
}

@Composable
private fun AuditRow(entry: AuditLogEntryDto) {
    val c = Oc.colors
    val time = Formatting.parseIsoUtc(entry.timestamp)?.let { Formatting.clock(it) } ?: entry.timestamp
    val detail = Formatting.auditDetail(entry.detail)
    val who = entry.deviceName ?: entry.deviceId
    val text = buildString {
        append(time).append(' ').append(entry.kind)
        if (detail.isNotEmpty()) append(" · ").append(detail)
        if (who != null) append(" · ").append(who)
    }
    Text(
        text,
        style = OcType.monoList, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
    )
}
