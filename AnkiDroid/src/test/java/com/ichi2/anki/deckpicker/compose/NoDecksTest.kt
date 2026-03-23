/*
 *  Copyright (c) 2026 Colby Cabrera <gdthyispro@gmail.com>
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
package com.ichi2.anki.deckpicker.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class NoDecksTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsEmptyStateCopyAndButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.no_cards_placeholder_title)
        val description = context.getString(R.string.no_cards_placeholder_description)
        val newDeckLabel = context.getString(R.string.new_deck)
        val sharedDecksLabel = context.getString(R.string.get_shared)

        composeTestRule.setContent {
            AnkiDroidTheme {
                NoDecks(onCreateDeck = {}, onGetSharedDecks = {})
            }
        }

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
        composeTestRule.onNodeWithText(newDeckLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(sharedDecksLabel).assertIsDisplayed()
    }

    @Test
    fun invokesEmptyStateCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val newDeckLabel = context.getString(R.string.new_deck)
        val sharedDecksLabel = context.getString(R.string.get_shared)
        var createDeckClicked = false
        var sharedDecksClicked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                NoDecks(
                    onCreateDeck = { createDeckClicked = true },
                    onGetSharedDecks = { sharedDecksClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText(newDeckLabel).performClick()
        composeTestRule.onNodeWithText(sharedDecksLabel).performClick()

        assertTrue(createDeckClicked)
        assertTrue(sharedDecksClicked)
    }
}