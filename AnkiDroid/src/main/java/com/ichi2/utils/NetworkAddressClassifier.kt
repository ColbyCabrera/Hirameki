/*
 Copyright (c) 2026 Colby Cabrera <colby.cabrera@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Classifies URL hosts against the platform's local-network definition
 * (developer.android.com/privacy-and-security/local-network-definition):
 * RFC 1918, link-local, CGNAT, multicast/broadcast, IPv6 link-local/ULA.
 *
 * Pure network domain logic: no Android or app state, so it is JVM-testable.
 */
object NetworkAddressClassifier {
    /**
     * Whether [url] points at a LAN destination that Android 17 gates behind
     * `ACCESS_LOCAL_NETWORK`. Public hosts and loopback return false.
     */
    fun isLocalNetworkUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host =
            url
                .toHttpUrlOrNull()
                ?.host
                ?.lowercase()
                ?.trimEnd('.') ?: return false
        if (host == "localhost" || host == "::1") return false
        if (isLoopbackIpv4(host)) return false
        if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return true
        if (!host.contains(".") && !host.contains(":")) return true // single-label e.g. http://nas:8080
        if (isPrivateIpv4(host)) return true
        if (isPrivateIpv6(host)) return true
        // IPv4-mapped IPv6 (e.g. ::ffff:192.168.1.1) reaches IPv4 space: check the tail.
        if (host.contains(":") && host.contains(".")) {
            return isPrivateIpv4(host.substringAfterLast(":"))
        }
        return false
    }

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal")

    /** 127.0.0.0/8 loopback; hostnames merely starting with "127." (e.g. 127.local) don't count. */
    private fun isLoopbackIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4 || parts[0] != "127") return false
        return parts.all { it.toUByteOrNull() != null }
    }

    /** IPv6 link-local fe80::/10, ULA fc00::/7, multicast ff00::/8; ::1 already exempted. */
    private fun isPrivateIpv6(host: String): Boolean {
        if (!host.contains(":")) return false
        val firstHextet = host.split(":").firstOrNull()?.toIntOrNull(16) ?: return false
        return firstHextet in 0xFE80..0xFEBF ||
            firstHextet in 0xFC00..0xFDFF ||
            firstHextet in 0xFF00..0xFFFF
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toUByteOrNull()?.toInt() ?: return false }
        val (a, b) = octets
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 169 && b == 254 -> true // link-local
            a == 100 && b in 64..127 -> true // CGNAT
            a in 224..239 -> true // multicast
            octets.all { it == 255 } -> true // broadcast
            else -> false
        }
    }
}
