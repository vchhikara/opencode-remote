package com.opencode.remote.data.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class Reconnecting(val attempt: Int, val reason: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Owns only the WebSocket connection lifecycle: connect, reconnect/backoff, and raw
 * text send. Knows nothing about the app's domain events or frame format — that
 * routing lives in [RemoteSessionManager], which drives this class with a session
 * callback. Kept separate so a reconnect bug is readable in one small file, and so
 * the connect loop can be swapped for a fake in a repository test without a real
 * socket.
 */
class WsClient(private val scope: CoroutineScope) {
    private val client = HttpClient(OkHttp) {
        install(WebSockets) { pingIntervalMillis = 15000 }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var sessionJob: Job? = null
    private var sendAction: (suspend (String) -> Unit)? = null

    /**
     * Starts the connect/backoff loop. [onSession] runs once per successfully opened
     * socket with the session as its receiver (so `send`/`incoming`/`close` are
     * available); call the passed `markConnected` once your handshake succeeds to
     * flip [connectionState] to Connected and reset the backoff counter.
     */
    fun connect(
        host: String,
        port: Int,
        onSession: suspend DefaultClientWebSocketSession.(markConnected: () -> Unit) -> Unit
    ) {
        if (_connectionState.value is ConnectionState.Connected || _connectionState.value is ConnectionState.Connecting) return
        sessionJob?.cancel()
        sessionJob = scope.launch {
            var attempt = 0
            val backoffDelays = listOf(1000L, 2000L, 4000L, 8000L, 30000L)
            while (true) {
                try {
                    _connectionState.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt, "Retrying")
                    client.webSocket(host = host, port = port, path = "/ws") {
                        sendAction = { text -> send(Frame.Text(text)) }
                        onSession {
                            _connectionState.value = ConnectionState.Connected
                            attempt = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WsClient", "Connection error", e)
                    _connectionState.value = ConnectionState.Error(e.message ?: "Disconnected")
                } finally {
                    sendAction = null
                }
                attempt++
                delay(backoffDelays[minOf(attempt - 1, backoffDelays.size - 1)])
            }
        }
    }

    suspend fun send(text: String) {
        sendAction?.invoke(text)
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        sendAction = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
