/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.compose.components

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnkiToggleViewTest {
    @Test
    fun testInitialStateAndSetChecked() {
        val toggleView = AnkiToggleView(ApplicationProvider.getApplicationContext())
        assertFalse(toggleView.isChecked)

        var listenerCalled = false
        var lastCheckedValue = false
        toggleView.setOnCheckedChangeListener { _, checked ->
            listenerCalled = true
            lastCheckedValue = checked
        }

        toggleView.isChecked = true
        assertTrue(toggleView.isChecked)
        assertTrue(listenerCalled)
        assertTrue(lastCheckedValue)
    }

    @Test
    fun testToggle() {
        val toggleView = AnkiToggleView(ApplicationProvider.getApplicationContext())
        assertFalse(toggleView.isChecked)
        toggleView.toggle()
        assertTrue(toggleView.isChecked)
        toggleView.toggle()
        assertFalse(toggleView.isChecked)
    }
}
