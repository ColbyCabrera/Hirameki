// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressClassifierTest {
    @Test
    fun publicHostsAreNotLocalNetwork() {
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("https://sync.ankiweb.net"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("https://sync.example.com:8080/path"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://8.8.8.8"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://172.32.0.1"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://EXAMPLE.COM"))
    }

    @Test
    fun invalidInputsAreNotLocalNetwork() {
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl(null))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl(""))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("not a url"))
    }

    @Test
    fun loopbackIsExempt() {
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://localhost:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://127.0.0.1:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://127.0.0.2:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[::1]:8080"))
    }

    @Test
    fun privateIpv4IsLocalNetwork() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://192.168.1.10:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://10.0.0.5/sync"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://172.16.4.2"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://172.31.255.255"))
    }

    @Test
    fun localNamesAreLocalNetwork() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://myserver.local:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://NAS.LOCAL:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://nas.lan"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://router.home"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://router.internal"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://router.home.arpa"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://myserver:8080"))
    }

    @Test
    fun loopbackPrefixDoesNotExemptHostnames() {
        // "127." prefix alone must not exempt non-IP hosts like 127.local
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://127.local:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://127.0.0.1.evil.com"))
    }

    @Test
    fun cgnatRange() {
        // CGNAT 100.64.0.0/10 (Tailscale)
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://100.64.0.1:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://100.127.255.255"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://100.128.0.1:8080"))
    }

    @Test
    fun ipv4LinkLocal() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://169.254.1.1:8080"))
    }

    @Test
    fun ipv6LocalRanges() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[fe80::1]:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[fe90::1]:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[febf:ffff::1]:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[fec0::1]:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[fd12:3456:789a::1]:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[2001:db8::1]:8080"))
    }

    @Test
    fun ipv4MappedIpv6() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[::ffff:192.168.1.1]:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[::ffff:8.8.8.8]:8080"))
    }

    @Test
    fun ipv6Multicast() {
        // ff00::/8, parsed as a hextet range like the other IPv6 checks
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[ff02::1]:8080"))
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://[fff0::1]:8080"))
        // First hextets below ff00 are not multicast: "ff" is 0x00ff and "ff0" is 0x0ff0
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[ff::1]:8080"))
        assertFalse(NetworkAddressClassifier.isLocalNetworkUrl("http://[ff0::1]:8080"))
    }

    @Test
    fun trailingDotFqdn() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://nas.local.:8080"))
    }
}
