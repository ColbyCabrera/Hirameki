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

package com.ichi2.anki

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerRecompositionTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @org.junit.Before
    fun setUpTest() = runBlocking {
        editPreferences { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        CollectionManager.ensureOpen()
    }

    @Test
    fun deckPickerNavHost_disappearsWhenCollectionClosed() = runTest {
        ActivityScenario.launch(DeckPicker::class.java).use {
            // Wait for Compose to render the default UI with the collection fully open
            composeTestRule.waitForIdle()

            // Verify the open state
            composeTestRule.onNodeWithText("Decks").assertExists()

            // Trigger closure
            CollectionManager.ensureClosed()
            composeTestRule.waitForIdle()

            // Verify disappearance
            composeTestRule.onNodeWithText("Decks").assertDoesNotExist()
        }
    }
}
