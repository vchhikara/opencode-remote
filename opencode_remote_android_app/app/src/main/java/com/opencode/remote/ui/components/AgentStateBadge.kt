package com.opencode.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// True pill/stadium shape (corner radius = half the shorter side, so it stays a
// full pill regardless of the badge's text length) — matches the rest of the
// app's pilled buttons/chips instead of AgentStateBadge's old fixed 12dp corner.
private val PillShape = RoundedCornerShape(percent = 50)

/**
 * [agentState] is the bridge's raw status text ("Idle", "Thinking...", or whatever the
 * wrapped CLI happens to emit — see RemoteSessionManager.agentState). There's no fixed
 * vocabulary or casing contract, so this matches case-insensitively by substring
 * instead of an exact `when` on lowercase literals, which silently fell through to a
 * generic style for the bridge's actual "Idle"/"Thinking..." casing.
 */
@Composable
fun AgentStateBadge(agentState: String, modifier: Modifier = Modifier) {
    val normalized = agentState.lowercase()
    // Theme-derived container/on-container pairs instead of hardcoded RGB, so this
    // follows the app's actual color scheme (and dark mode) rather than a fixed
    // palette that clashed with it.
    val (backgroundColor, textColor) = when {
        normalized.contains("idle") -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        normalized.contains("think") -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        normalized.contains("run") || normalized.contains("execut") -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        normalized.contains("error") -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = agentState.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
