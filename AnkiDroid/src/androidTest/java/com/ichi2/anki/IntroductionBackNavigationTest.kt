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

/*
 * Test to ensure that pressing back on the introduction's setup page
 * returns to the disclaimer page instead of closing the activity.
 */
package com.ichi2.anki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntroductionBackNavigationTest : InstrumentedTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<IntroductionActivity>()

    @Test
    fun backFromSetupScreenReshowsDisclaimer() {
        // The introduction rotates an icon forever, so the test clock must be controlled manually
        composeTestRule.mainClock.autoAdvance = false
        // Let the initial composition and entrance animation lay out the disclaimer
        composeTestRule.mainClock.advanceTimeBy(2_000)
        val continueText =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.intro_continue)

        // The disclaimer is the first page of the introduction and is scrollable
        composeTestRule
            .onNodeWithText(continueText)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)

        // Back from the setup page returns to the disclaimer instead of closing the activity
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.mainClock.advanceTimeBy(1_000)

        composeTestRule
            .onNodeWithText(continueText)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
