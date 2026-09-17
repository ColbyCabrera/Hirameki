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

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CongratsActivityTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun `the screen shows before the collection is read, then counts down to the next day`() {
        // the countdown ticks once a second until the next day: the test clock must not run it
        composeTestRule.mainClock.autoAdvance = false
        // a main dispatcher that runs nothing until told to, so the collection read started in onCreate waits.
        // RobolectricTest's own dispatcher runs it at once, before the screen is set, as a synchronous read would
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)

        val activity = startRegularActivity<CongratsActivity>()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(activity.getString(R.string.next_review_in)).assertExists()
        composeTestRule.onNodeWithText("00:00:00").assertExists() // shown while the collection is still unread

        main.scheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        // the day cutoff arrived: the countdown now starts from it
        composeTestRule.onNodeWithText("00:00:00").assertDoesNotExist()
    }
}
