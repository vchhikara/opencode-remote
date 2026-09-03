package com.example.network

import androidx.compose.ui.graphics.vector.ImageVector

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val actionDescription: String? = null,
    val actionIcon: ImageVector? = null,
    val hasDetails: Boolean = false
)
