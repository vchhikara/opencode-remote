package com.example.network.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebSocketFrame(
    val eventType: String,
    val payload: JsonElement? = null
)
