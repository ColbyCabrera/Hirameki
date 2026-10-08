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
