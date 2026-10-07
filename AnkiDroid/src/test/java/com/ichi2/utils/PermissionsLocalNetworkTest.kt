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
        assertThat(Permissions.isLocalNetworkUrl("http://myserver:8080"), equalTo(true))
    }
}
