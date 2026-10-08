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
    fun trailingDotFqdn() {
        assertTrue(NetworkAddressClassifier.isLocalNetworkUrl("http://nas.local.:8080"))
    }
}
