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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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
    fun fabMenuInvokesCheckDatabaseCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val checkDatabaseLabel = context.getString(R.string.check_db)
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
                        onAddFilteredDeck = {},
                        onCheckDatabase = { callbackInvoked = true },
                    ),
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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
        composeTestRule.onAllNodesWithText(checkDatabaseLabel)[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun fabMenuInvokesGetSharedCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val getSharedLabel = context.getString(R.string.get_shared)
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
                        onCheckDatabase = {},
                    ),
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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
        composeTestRule.onNode(
                hasText(getSharedLabel) and hasClickAction() and SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Button
                ),
            ).assertExists().performClick()
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
                        onCheckDatabase = {},
                    ),
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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
        val newDeckLabel = context.getString(R.string.new_deck)
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
                        onCheckDatabase = {},
                    ),
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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
        composeTestRule.onNode(
                hasText(newDeckLabel) and hasClickAction() and SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Button
                ),
            ).assertExists().performClick()
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
                        onCheckDatabase = {},
                    ),
                    studyOptionsPanelActions = emptyStudyOptionsActions(),
                    onNavigationIconClick = {},
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

    private fun emptyStudyOptionsActions() = StudyOptionsPanelActions(
        onStartStudy = {},
        onRebuildDeck = {},
        onEmptyDeck = {},
        onCustomStudy = {},
        onDeckOptionsItemSelected = {},
        onUnbury = {},
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