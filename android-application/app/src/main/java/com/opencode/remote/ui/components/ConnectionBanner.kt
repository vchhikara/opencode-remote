package com.opencode.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType

/**
 * Thin connection strip shown whenever the socket isn't healthy — Reconnecting,
 * Disconnected and (new) the transient Error state WsClient enters between retries,
 * which previously showed nothing. Same information as the old coloured banner,
 * expressed in the editorial language: a dot, a line of text, a hairline.
 */
@Composable
fun ConnectionBanner(connectionState: ConnectionState, modifier: Modifier = Modifier) {
    val visible = connectionState is ConnectionState.Reconnecting ||
        connectionState is ConnectionState.Disconnected ||
        connectionState is ConnectionState.Error
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(OcMotion.STATE_MS, easing = OcMotion.Easing)) + fadeIn(tween(OcMotion.STATE_MS)),
        exit = shrinkVertically(tween(OcMotion.STATE_MS, easing = OcMotion.Easing)) + fadeOut(tween(OcMotion.STATE_MS))
    ) {
        val c = Oc.colors
        val (text, dotColor, pulsing) = when (connectionState) {
            is ConnectionState.Reconnecting -> Triple("Reconnecting · attempt ${connectionState.attempt}", c.ink3, true)
            is ConnectionState.Error -> Triple("Connection lost · ${connectionState.message}", c.negInk, false)
            else -> Triple("Disconnected", c.negInk, false)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.panel)
                .bottomRule(c.rule)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            StatusDot(dotColor, pulsing)
            Text(text, style = OcType.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
