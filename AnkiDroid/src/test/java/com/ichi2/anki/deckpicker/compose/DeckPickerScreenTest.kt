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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.ui.compose.components.ADD_DECK_FAB_TAG
import com.ichi2.anki.ui.compose.components.GET_SHARED_FAB_TAG
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
    fun searchOpenInputAndCloseRoutesQueryChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDecksLabel = context.getString(R.string.search_decks)
        val closeLabel = context.getString(R.string.close)
        val queryEvents = mutableListOf<String>()

        composeTestRule.setContent {
            var searchQuery by remember { mutableStateOf("") }

            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = searchQuery,
                    onSearchQueryChanged = {
                        queryEvents += it
                        searchQuery = it
                    },
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("spanish")
        composeTestRule.onNodeWithText("spanish").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(closeLabel).performClick()

        assertEquals(listOf("spanish", ""), queryEvents)
    }


    @Test
    fun fabMenuInvokesGetSharedCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        var callbackInvoked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = FabActions(
                        onAddNote = {},
                        onAddDeck = {},
                        onAddSharedDeck = { callbackInvoked = true },
                        onAddFilteredDeck = {},
                        onImport = {},
                    ),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(GET_SHARED_FAB_TAG).assertExists().performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun fabMenuInvokesAddFilteredDeckCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val newDynamicDeckLabel = context.getString(R.string.new_dynamic_deck)
        var callbackInvoked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = FabActions(
                        onAddNote = {},
                        onAddDeck = {},
                        onAddSharedDeck = {},
                        onAddFilteredDeck = { callbackInvoked = true },
                        onImport = {},
                    ),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(newDynamicDeckLabel)[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun fabMenuInvokesAddDeckCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        var callbackInvoked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = FabActions(
                        onAddNote = {},
                        onAddDeck = { callbackInvoked = true },
                        onAddSharedDeck = {},
                        onAddFilteredDeck = {},
                        onImport = {},
                    ),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ADD_DECK_FAB_TAG).assertExists().performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun fabMenuInvokesAddNoteCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val addCardLabel = context.getString(R.string.add_card)
        var callbackInvoked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = FabActions(
                        onAddNote = { callbackInvoked = true },
                        onAddDeck = {},
                        onAddSharedDeck = {},
                        onAddFilteredDeck = {},
                        onImport = {},
                    ),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(addCardLabel)[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    private fun emptyDeckRowActions() = DeckRowActions(
        onDeckClick = {},
        onExpandClick = {},
        onDeckOptions = {},
        onRename = {},
        onCustomStudy = {},
        onUnbury = {},
        onExportDeck = {},
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
        onImport = {},
    )

    private fun emptyMoreOptionsMenuActions() = MoreOptionsMenuActions(
        onStartStudy = {},
        onDeleteEmptyCards = {},
        onCheckDatabase = {},
        onExport = {},
    )
}