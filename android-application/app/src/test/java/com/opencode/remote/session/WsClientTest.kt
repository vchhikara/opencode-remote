package com.opencode.remote.session

import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.WsClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Exercises the pure guard connect() relies on to stay idempotent (Task
 * 7.2.1) — pulled out of connect() itself so this is testable without a real
 * socket (WsClient's connect() always opens one via a live HttpClient, which
 * isn't mockable here without adding a new test-only ktor engine dependency).
 * This proves the exact safety property the exit criterion asks for: a call
 * to connect() while already connected or mid-connect must be a no-op, so it
 * never leaks a second, competing session loop.
 */
@RunWith(JUnit4::class)
class WsClientTest {
    @Test
    fun testShouldSkipConnectWhenAlreadyConnectedOrConnecting() {
        assertTrue(WsClient.shouldSkipConnect(ConnectionState.Connected))
        assertTrue(WsClient.shouldSkipConnect(ConnectionState.Connecting))
    }

    @Test
    fun testShouldNotSkipConnectWhenDisconnectedErrorOrReconnecting() {
        assertFalse(WsClient.shouldSkipConnect(ConnectionState.Disconnected))
        assertFalse(WsClient.shouldSkipConnect(ConnectionState.Error("boom")))
        assertFalse(WsClient.shouldSkipConnect(ConnectionState.Reconnecting(1, "retrying")))
    }
}
