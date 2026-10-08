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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class PermissionsLocalNetworkTest {
    @Test
    fun publicHostsAreNotLocalNetwork() {
        assertThat(Permissions.isLocalNetworkUrl("https://sync.ankiweb.net"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("https://sync.example.com:8080/path"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl(null), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl(""), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("not a url"), equalTo(false))
    }

    @Test
    fun loopbackIsExempt() {
        assertThat(Permissions.isLocalNetworkUrl("http://localhost:8080"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("http://127.0.0.1:8080"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("http://127.0.0.2:8080"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("http://[::1]:8080"), equalTo(false))
    }

    @Test
    fun privateIpv4IsLocalNetwork() {
        assertThat(Permissions.isLocalNetworkUrl("http://192.168.1.10:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://10.0.0.5/sync"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://172.16.4.2"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://172.31.255.255"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://172.32.0.1"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("http://8.8.8.8"), equalTo(false))
    }

    @Test
    fun mdnsAndSingleLabelAreLocalNetwork() {
        assertThat(Permissions.isLocalNetworkUrl("http://myserver.local:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://nas.lan"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://router.home"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://router.internal"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://router.home.arpa"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://myserver:8080"), equalTo(true))
    }

    @Test
    fun loopbackPrefixDoesNotExemptHostnames() {
        // "127." prefix alone must not exempt non-IP hosts like 127.local
        assertThat(Permissions.isLocalNetworkUrl("http://127.local:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://127.0.0.1.evil.com"), equalTo(false))
    }

    @Test
    fun subnetBoundaries() {
        // CGNAT 100.64.0.0/10 (Tailscale)
        assertThat(Permissions.isLocalNetworkUrl("http://100.64.0.1:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://100.127.255.255"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://100.128.0.1:8080"), equalTo(false))
        // IPv4 link-local 169.254.0.0/16
        assertThat(Permissions.isLocalNetworkUrl("http://169.254.1.1:8080"), equalTo(true))
    }

    @Test
    fun ipv6Handling() {
        assertThat(Permissions.isLocalNetworkUrl("http://[fe80::1]:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://[fe90::1]:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://[febf:ffff::1]:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://[fec0::1]:8080"), equalTo(false))
        assertThat(Permissions.isLocalNetworkUrl("http://[fd12:3456:789a::1]:8080"), equalTo(true))
        assertThat(Permissions.isLocalNetworkUrl("http://[2001:db8::1]:8080"), equalTo(false))
    }
}
