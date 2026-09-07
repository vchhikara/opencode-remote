package com.opencode.remote.data.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

data class QrResult(val host: String, val port: Int, val token: String)

private const val DEFAULT_PORT = 8080

/**
 * Parses a scanned QR payload into connection details. Accepts four formats:
 *  - `ws://host:port?token=...` / `wss://...`
 *  - JSON: `{"host":"...","port":8080,"token":"..."}`
 *  - `host:port#token`
 *  - `ip=<ip>;key=<token>` — the format bridge/main.js actually prints (no port, since
 *    the bridge always listens on [DEFAULT_PORT]); this is the format a real scan off
 *    the bridge's own printed QR code produces, and previously wasn't recognized by
 *    any of the other three branches.
 *
 * Pulled out of PairingScreen.kt (which also does CameraX/MLKit plumbing) so this
 * parsing logic can be unit tested on its own — see QrParserTest.
 */
fun parseQrResult(payload: String): QrResult? {
    return try {
        when {
            payload.startsWith("ws://") || payload.startsWith("wss://") -> {
                // java.net.URI, not android.net.Uri: keeps this parser plain-JVM
                // testable (no Robolectric needed for a unit test file).
                val uri = java.net.URI(payload)
                val host = uri.host ?: return null
                val port = if (uri.port != -1) uri.port else DEFAULT_PORT
                val token = uri.query.orEmpty()
                    .split("&")
                    .mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 } }
                    .firstOrNull { (k, _) -> k == "token" }
                    ?.get(1) ?: ""
                QrResult(host, port, token)
            }
            payload.startsWith("{") -> {
                // kotlinx.serialization, not org.json: org.json is an Android SDK
                // stub on the plain-JUnit test classpath (throws at runtime without
                // Robolectric); kotlinx.serialization is a real pure-Kotlin impl and
                // already a project dependency.
                val obj = Json.parseToJsonElement(payload).jsonObject
                val host = obj["host"]?.jsonPrimitive?.content ?: return null
                val port = obj["port"]?.jsonPrimitive?.intOrNull ?: DEFAULT_PORT
                val token = obj["token"]?.jsonPrimitive?.content ?: ""
                QrResult(host, port, token)
            }
            payload.startsWith("ip=") -> {
                val fields = payload.split(";").associate { field ->
                    val (k, v) = field.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    k to v
                }
                val host = fields["ip"]?.takeIf { it.isNotBlank() } ?: return null
                val token = fields["key"] ?: return null
                QrResult(host, DEFAULT_PORT, token)
            }
            payload.contains("#") -> {
                val parts = payload.split("#")
                if (parts.size != 2) return null
                val hostPort = parts[0].split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: DEFAULT_PORT else DEFAULT_PORT
                QrResult(host, port, parts[1])
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
