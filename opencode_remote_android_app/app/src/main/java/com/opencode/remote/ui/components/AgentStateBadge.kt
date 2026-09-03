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
    val (backgroundColor, textColor) = when {
        normalized.contains("idle") -> Color.Gray to Color.White
        normalized.contains("think") -> Color.Blue to Color.White
        normalized.contains("run") || normalized.contains("execut") -> Color(0xFFFFA500) to Color.White // Orange
        normalized.contains("error") -> Color(0xFFF44336) to Color.White
        else -> Color.LightGray to Color.Black
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = agentState.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
