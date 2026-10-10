/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationStateTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `entries follow the back stack in use`() {
        lateinit var navigator: Navigator
        var entries: List<NavEntry<NavKey>> = emptyList()
        composeTestRule.setContent {
            val state = rememberNavigationState(startRoute = DeckPickerScreen, topLevelRoutes = setOf(DeckPickerScreen))
            navigator = remember { Navigator(state) }
            entries = state.toEntries { key -> NavEntry(key, contentKey = key.toString()) {} }
        }

        composeTestRule.runOnIdle { assertEquals(listOf(DeckPickerScreen.toString()), entries.map { it.contentKey }) }

        composeTestRule.runOnIdle { navigator.navigate(HelpScreen) }
        composeTestRule.runOnIdle {
            assertEquals(
                "a pushed route is shown on top",
                listOf(DeckPickerScreen.toString(), HelpScreen.toString()),
                entries.map { it.contentKey },
            )
        }

        composeTestRule.runOnIdle { navigator.goBack() }
        composeTestRule.runOnIdle {
            assertEquals("back pops it", listOf(DeckPickerScreen.toString()), entries.map { it.contentKey })
        }
    }
}
