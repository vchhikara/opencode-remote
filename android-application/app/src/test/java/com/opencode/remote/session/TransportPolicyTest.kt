package com.opencode.remote.session

import com.opencode.remote.data.network.TransportPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun localIPv4AndPrivateTunnelRangesPermitPlainWebSocket() {
        listOf(
            "127.0.0.1", "10.0.2.2", "10.255.0.1", "192.168.1.25",
            "172.16.0.1", "172.31.255.255", "169.254.4.2",
            "100.64.0.1", "100.127.255.254"
        ).forEach { assertTrue("Should permit $it", TransportPolicy.permitsPlaintext(it)) }
    }

    @Test
    fun publicIPsAndRemoteDNSNeedWss() {
        listOf(
            "8.8.8.8", "1.1.1.1", "172.15.255.255", "172.32.0.1",
            "100.63.255.255", "100.128.0.1", "0.0.0.0",
            "example.com", "bridge.example.com", "public.ts.net",
            "999.22.1.1", "", "8.8.8", "192.168.1.999"
        ).forEach { assertFalse("Must require WSS: $it", TransportPolicy.permitsPlaintext(it)) }
    }

    @Test
    fun localDnsAndIPv6PrivateRangesAreSupported() {
        listOf(
            "localhost", "bridge.local", "localbridge",
            "::1", "[::1]", "fd7a:115c:a1e0::1", "fc00::2", "fe80::1"
        ).forEach { assertTrue("Should permit $it", TransportPolicy.permitsPlaintext(it)) }
        listOf("2001:4860:4860::8888", "fe70::1", "bad.example.net").forEach {
            assertFalse("Must require WSS: $it", TransportPolicy.permitsPlaintext(it))
        }
    }
}
