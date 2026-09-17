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
package com.ichi2.anki.ui.compose.components

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * the deck menu of the card browser and statistics top bars, whose content has to outlive its dismiss so the
 * exit [MenuExitMotion] plays is not an emptying, resizing popup.
 */
@RunWith(AndroidJUnit4::class)
class DeckSelectorTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a closing deck menu keeps its search on screen, and the next open starts fresh`() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckSelector(
                    selectedDeck = SelectableDeck.AllDecks,
                    availableDecks = listOf(SelectableDeck.Deck(1L, "Default"), SelectableDeck.Deck(2L, "Japanese")),
                    onDeckSelected = {},
                )
            }
        }
        openMenu()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("jap")
        composeTestRule.onNodeWithText("Default").assertDoesNotExist()

        // held, so the menu can be looked at partway through its exit
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Japanese").performClick()
        composeTestRule.mainClock.advanceTimeBy(MID_EXIT_MS)
        composeTestRule.onNode(hasSetTextAction()).assert(hasText("jap"))
        composeTestRule.onNodeWithText("Default").assertDoesNotExist()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        openMenu()
        composeTestRule.onNode(hasSetTextAction()).assert(hasText("jap").not())
        composeTestRule.onNodeWithText("Default").assertExists()
    }

    private fun openMenu() {
        composeTestRule.onNodeWithContentDescription(getResourceString(R.string.select_deck)).performClick()
        composeTestRule.waitForIdle()
    }

    private companion object {
        /** the menu's exit settles at ~184ms */
        const val MID_EXIT_MS = 100L
    }
}
