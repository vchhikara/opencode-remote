package com.opencode.remote.pairing

import com.opencode.remote.data.pairing.QrResult
import com.opencode.remote.data.pairing.parseQrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QrParserTest {

    @Test
    fun parsesWsUrlFormat() {
        assertEquals(
            QrResult("192.168.1.5", 9090, "abc123"),
            parseQrResult("ws://192.168.1.5:9090?token=abc123")
        )
    }

    @Test
    fun parsesWsUrlWithoutPort() {
        assertEquals(
            QrResult("192.168.1.5", 8080, "abc123"),
            parseQrResult("ws://192.168.1.5?token=abc123")
        )
    }

    @Test
    fun parsesJsonFormat() {
        assertEquals(
            QrResult("10.0.0.2", 8080, "tok"),
            parseQrResult("""{"host":"10.0.0.2","port":8080,"token":"tok"}""")
        )
    }

    @Test
    fun parsesHostPortHashTokenFormat() {
        assertEquals(
            QrResult("10.0.0.2", 9000, "tok"),
            parseQrResult("10.0.0.2:9000#tok")
        )
    }

    // This is the exact format bridge/main.js prints (no port field — the bridge
    // always listens on the default port). Previously none of the three branches
    // above recognized it, so scanning the bridge's own QR code silently failed.
    @Test
    fun parsesBridgeIpKeyFormat() {
        assertEquals(
            QrResult("10.0.0.7", 8080, "deadbeef"),
            parseQrResult("ip=10.0.0.7;key=deadbeef")
        )
    }

    @Test
    fun parsesSecureAndNonDefaultTransport() {
        assertEquals(
            QrResult("bridge.example.org", 443, "tok", "wss"),
            parseQrResult("wss://bridge.example.org:443?token=tok")
        )
        assertEquals(
            QrResult("10.0.0.7", 9443, "tok", "wss"),
            parseQrResult("ip=10.0.0.7;port=9443;scheme=wss;key=tok")
        )
        assertEquals(
            QrResult("host.example", 9090, "tok", "wss"),
            parseQrResult("""{"host":"host.example","port":9090,"token":"tok","scheme":"wss"}""")
        )
        assertNull(parseQrResult("ip=10.0.0.7;port=99999;key=tok"))
        assertNull(parseQrResult("ip=10.0.0.7;scheme=http;key=tok"))
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(parseQrResult("not a recognizable payload"))
    }

    @Test
    fun returnsNullForBlankBridgeIp() {
        assertNull(parseQrResult("ip=;key=deadbeef"))
    }
}
