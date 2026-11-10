package com.opencode.remote.data.network

import java.util.Locale

/**
 * Android Network Security Config cannot whitelist arbitrary private CIDR ranges.
 * Enforce the plaintext exception at the application connection boundary.
 *
 * This host-string check is not proof that a private address is trustworthy.
 * Require WSS for public DNS and internet-routable addresses.
 */
object TransportPolicy {
    fun permitsPlaintext(host: String): Boolean {
        val normalized = host.trim().lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized.endsWith(".local")) return true
        val parts = normalized.split(".")
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val (a, b) = octets
            return a == 10 || a == 127 || (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) || (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        if (normalized.contains(":")) {
            if (normalized.any { it !in "0123456789abcdef:" }) return false
            return normalized == "::1" || normalized.startsWith("fc") ||
                normalized.startsWith("fd") ||
                (normalized.startsWith("fe") &&
                    normalized.drop(2).firstOrNull()?.digitToIntOrNull(16)?.let { it in 8..11 } == true)
        }
        return !normalized.contains(".") && normalized.matches(Regex("[a-z0-9-]{1,63}"))
    }
}
