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

class PermissionsLocalNetworkTest {
    @Test
    fun publicHostsAreNotLocalNetwork() {
        assertFalse(Permissions.isLocalNetworkUrl("https://sync.ankiweb.net"))
        assertFalse(Permissions.isLocalNetworkUrl("https://sync.example.com:8080/path"))
        assertFalse(Permissions.isLocalNetworkUrl("http://8.8.8.8"))
        assertFalse(Permissions.isLocalNetworkUrl("http://172.32.0.1"))
        assertFalse(Permissions.isLocalNetworkUrl("http://EXAMPLE.COM"))
    }

    @Test
    fun invalidInputsAreNotLocalNetwork() {
        assertFalse(Permissions.isLocalNetworkUrl(null))
        assertFalse(Permissions.isLocalNetworkUrl(""))
        assertFalse(Permissions.isLocalNetworkUrl("not a url"))
    }

    @Test
    fun loopbackIsExempt() {
        assertFalse(Permissions.isLocalNetworkUrl("http://localhost:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://127.0.0.1:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://127.0.0.2:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://[::1]:8080"))
    }

    @Test
    fun privateIpv4IsLocalNetwork() {
        assertTrue(Permissions.isLocalNetworkUrl("http://192.168.1.10:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://10.0.0.5/sync"))
        assertTrue(Permissions.isLocalNetworkUrl("http://172.16.4.2"))
        assertTrue(Permissions.isLocalNetworkUrl("http://172.31.255.255"))
    }

    @Test
    fun localNamesAreLocalNetwork() {
        assertTrue(Permissions.isLocalNetworkUrl("http://myserver.local:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://NAS.LOCAL:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://nas.lan"))
        assertTrue(Permissions.isLocalNetworkUrl("http://router.home"))
        assertTrue(Permissions.isLocalNetworkUrl("http://router.internal"))
        assertTrue(Permissions.isLocalNetworkUrl("http://router.home.arpa"))
        assertTrue(Permissions.isLocalNetworkUrl("http://myserver:8080"))
    }

    @Test
    fun loopbackPrefixDoesNotExemptHostnames() {
        // "127." prefix alone must not exempt non-IP hosts like 127.local
        assertTrue(Permissions.isLocalNetworkUrl("http://127.local:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://127.0.0.1.evil.com"))
    }

    @Test
    fun cgnatRange() {
        // CGNAT 100.64.0.0/10 (Tailscale)
        assertTrue(Permissions.isLocalNetworkUrl("http://100.64.0.1:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://100.127.255.255"))
        assertFalse(Permissions.isLocalNetworkUrl("http://100.128.0.1:8080"))
    }

    @Test
    fun ipv4LinkLocal() {
        assertTrue(Permissions.isLocalNetworkUrl("http://169.254.1.1:8080"))
    }

    @Test
    fun ipv6LocalRanges() {
        assertTrue(Permissions.isLocalNetworkUrl("http://[fe80::1]:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://[fe90::1]:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://[febf:ffff::1]:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://[fec0::1]:8080"))
        assertTrue(Permissions.isLocalNetworkUrl("http://[fd12:3456:789a::1]:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://[2001:db8::1]:8080"))
    }

    @Test
    fun ipv4MappedIpv6() {
        assertTrue(Permissions.isLocalNetworkUrl("http://[::ffff:192.168.1.1]:8080"))
        assertFalse(Permissions.isLocalNetworkUrl("http://[::ffff:8.8.8.8]:8080"))
    }

    @Test
    fun trailingDotFqdn() {
        assertTrue(Permissions.isLocalNetworkUrl("http://nas.local.:8080"))
    }

    @Test
    fun gateRequiresCustomEnabledEnforcedUngrantedLan() {
        val lan = "http://192.168.1.10:8080"
        val public = "https://sync.ankiweb.net"
        // Blocked only when all four hold.
        assertTrue(Permissions.shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = true, endpoint = lan))
        assertFalse(Permissions.shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = true, endpoint = public))
        assertFalse(Permissions.shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = true, isCustomSyncEnabled = true, endpoint = lan))
        assertFalse(Permissions.shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = false, endpoint = lan))
        assertFalse(Permissions.shouldBlockLocalNetworkSync(sdkInt = 36, hasGrant = false, isCustomSyncEnabled = true, endpoint = lan))
    }
}
