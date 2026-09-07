package com.opencode.remote.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class WebSocketFrame(
    val eventType: String,
    val payload: JsonElement? = null
)

inline fun <reified T> WebSocketFrame.decodePayload(json: Json): T? {
    if (payload == null) return null
    return try {
        json.decodeFromJsonElement<T>(payload)
    } catch (e: Exception) {
        null
    }
}

inline fun <reified T> createFrame(eventType: String, payload: T, json: Json): WebSocketFrame {
    return WebSocketFrame(
        eventType = eventType,
        payload = json.encodeToJsonElement(payload)
    )
}
