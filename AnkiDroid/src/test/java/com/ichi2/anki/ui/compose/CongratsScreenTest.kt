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
package com.ichi2.anki.ui.compose

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CongratsScreenTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `congrats leaves back to its host`() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        composeTestRule.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            // 0: the countdown loop does not start, so the test clock can go idle
            CongratsScreen(onNavigateUp = {}, onDeckOptions = {}, onCustomStudy = {}, timeUntilNextDay = 0)
        }

        composeTestRule.runOnIdle {
            // an enabled handler here overrides the nav3 predictive pop and the system
            // cross-activity animation of CongratsActivity
            assertFalse("congrats must not intercept back", backDispatcher.hasEnabledCallbacks())
        }
    }
}
