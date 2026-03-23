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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class DeckPickerScreenTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun studyOptionsMenuShowsUnburyAndInvokesCallbackWhenDeckHasBuriedCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
        val unburyLabel = context.getString(R.string.unbury)
        var unburiedDeckId: Long? = null

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = true,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = emptyFabActions(),
                    studyOptionsPanelActions = StudyOptionsPanelActions(
                        onStartStudy = {},
                        onRebuildDeck = {},
                        onEmptyDeck = {},
                        onCustomStudy = {},
                        onDeckOptionsItemSelected = {},
                        onUnbury = { unburiedDeckId = it },
                    ),
                    onNavigationIconClick = {},
                    studyOptionsData = studyOptionsData(haveBuried = true),
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.onNodeWithText(unburyLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(unburyLabel).performClick()

        assertEquals(42L, unburiedDeckId)
    }

    @Test
    fun studyOptionsMenuHidesUnburyWhenDeckHasNoBuriedCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
        val unburyLabel = context.getString(R.string.unbury)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = true,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = emptyFabActions(),
                    studyOptionsPanelActions = StudyOptionsPanelActions(
                        onStartStudy = {},
                        onRebuildDeck = {},
                        onEmptyDeck = {},
                        onCustomStudy = {},
                        onDeckOptionsItemSelected = {},
                        onUnbury = {},
                    ),
                    onNavigationIconClick = {},
                    studyOptionsData = studyOptionsData(haveBuried = false),
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.onNodeWithText(unburyLabel).assertDoesNotExist()
    }

    private fun emptyDeckRowActions() = DeckRowActions(
        onDeckClick = {},
        onExpandClick = {},
        onDeckOptions = {},
        onRename = {},
        onExport = {},
        onDelete = {},
        onRebuild = {},
        onEmpty = {},
        onCreateSubdeck = {},
    )

    private fun emptyFabActions() = FabActions(
        onAddNote = {},
        onAddDeck = {},
        onAddSharedDeck = {},
        onAddFilteredDeck = {},
        onCheckDatabase = {},
    )

    private fun studyOptionsData(haveBuried: Boolean) = StudyOptionsData(
        deckId = 42L,
        deckName = "Spanish",
        deckDescription = "",
        newCount = 5,
        lrnCount = 2,
        revCount = 8,
        buriedNew = if (haveBuried) 1 else 0,
        buriedLrn = if (haveBuried) 1 else 0,
        buriedRev = if (haveBuried) 2 else 0,
        totalNewCards = 12,
        totalCards = 20,
        isFiltered = false,
        haveBuried = haveBuried,
    )
}