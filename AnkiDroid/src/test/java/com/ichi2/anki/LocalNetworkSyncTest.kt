// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkSyncTest {
    @Test
    fun gateRequiresCustomEnabledEnforcedUngrantedLan() {
        val lan = "http://192.168.1.10:8080"
        val public = "https://sync.ankiweb.net"
        // Blocked only when all four hold.
        assertTrue(shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = true, endpoint = lan))
        assertFalse(shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = true, endpoint = public))
        assertFalse(shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = true, isCustomSyncEnabled = true, endpoint = lan))
        assertFalse(shouldBlockLocalNetworkSync(sdkInt = 37, hasGrant = false, isCustomSyncEnabled = false, endpoint = lan))
        assertFalse(shouldBlockLocalNetworkSync(sdkInt = 36, hasGrant = false, isCustomSyncEnabled = true, endpoint = lan))
    }
}
