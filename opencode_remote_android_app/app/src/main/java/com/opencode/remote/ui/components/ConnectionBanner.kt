package com.opencode.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.network.ConnectionState

@Composable
fun ConnectionBanner(connectionState: ConnectionState) {
    AnimatedVisibility(visible = connectionState is ConnectionState.Reconnecting || connectionState is ConnectionState.Disconnected) {
        when (connectionState) {
            is ConnectionState.Reconnecting -> {
                Banner(
                    text = "Reconnecting (attempt ${connectionState.attempt})...",
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    textColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            is ConnectionState.Disconnected -> {
                Banner(
                    text = "Disconnected",
                    backgroundColor = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.onError
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun Banner(text: String, backgroundColor: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}
