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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import anki.decks.deckTreeNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.deckpicker.DisplayDeckNode
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
                    onStartStudy = {},
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
                    onStartStudy = {},
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
                    onStartStudy = {},
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
                    onStartStudy = {},
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
                    onStartStudy = {},
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

    @Test
    fun longClickDeckShowsContextMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val renameLabel = context.getString(R.string.rename_deck)

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(renameLabel).assertIsDisplayed()
    }

    @Test
    fun clickRenameDeckInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val renameLabel = context.getString(R.string.rename_deck)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onRename = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(renameLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickExportDeckInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val exportLabel = context.getString(R.string.export_deck)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onExportDeck = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(exportLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickCustomStudyInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val customStudyLabel = context.getString(R.string.custom_study)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onCustomStudy = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(customStudyLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickRebuildInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Filtered Deck"
        val rebuildLabel = context.getString(R.string.rebuild_cram_label)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
                filtered = true
            }, fullDeckName = deckName
        )
        // Mark as filtered
        val deck = DisplayDeckNode.from(deckNode, false, 0L, true)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onRebuild = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(rebuildLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickEmptyInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Filtered Deck"
        val emptyLabel = context.getString(R.string.empty_cram_label)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
                filtered = true
            }, fullDeckName = deckName
        )
        // Mark as filtered
        val deck = DisplayDeckNode.from(deckNode, false, 0L, true)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onEmpty = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(emptyLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickCreateSubdeckInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val createSubdeckLabel = context.getString(R.string.create_subdeck)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onCreateSubdeck = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(createSubdeckLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickDeleteDeckInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val deleteLabel = context.getString(R.string.contextmenu_deckpicker_delete_deck)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        val deck = DisplayDeckNode.from(deckNode, false, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = DeckRowActions(
                        onDeckClick = {},
                        onExpandClick = {},
                        onDeckOptions = {},
                        onRename = {},
                        onCustomStudy = {},
                        onUnbury = {},
                        onExportDeck = {},
                        onDelete = { callbackInvoked = true },
                        onRebuild = {},
                        onEmpty = {},
                        onCreateSubdeck = {},
                    ),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(deleteLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickExpandToggleInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val expandLabel = context.getString(R.string.expand)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
                collapsed = true
                children.add(deckTreeNode { name = "Child"; deckId = 2L; level = 2 })
            }, fullDeckName = deckName
        )
        // Mark as collapsed and canCollapse
        val deck = DisplayDeckNode.from(deckNode, true, 0L, false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onExpandClick = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(expandLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickUnburyInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val unburyLabel = context.getString(R.string.unbury)
        var callbackInvoked = false

        val deckNode = com.ichi2.anki.libanki.sched.DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
            }, fullDeckName = deckName
        )
        // Mark as having buried cards
        val deck = DisplayDeckNode.from(deckNode, false, 0L, true)

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = listOf(deck),
                    isSyncing = false,
                    onRefresh = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions().copy(
                        onUnbury = { callbackInvoked = true }),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = false,
                )
            }
        }

        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(unburyLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun moreOptionsMenuInvokesDeleteEmptyCardsCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
        val deleteEmptyCardsLabel = TR.actionsEmptyCards()
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
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = MoreOptionsMenuActions(
                        onDeleteEmptyCards = { callbackInvoked = true },
                        onCheckDatabase = {},
                        onExport = {},
                    ),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(deleteEmptyCardsLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun moreOptionsMenuInvokesCheckDatabaseCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
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
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = MoreOptionsMenuActions(
                        onDeleteEmptyCards = {},
                        onCheckDatabase = { callbackInvoked = true },
                        onExport = {},
                    ),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(checkDatabaseLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun moreOptionsMenuInvokesExportCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
        val exportLabel = TR.actionsExport()
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
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = MoreOptionsMenuActions(
                        onDeleteEmptyCards = {},
                        onCheckDatabase = {},
                        onExport = { callbackInvoked = true },
                    ),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(exportLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun initialStateShowsEmptyCollectionMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val emptyMessage = context.getString(R.string.no_cards_placeholder_title)

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
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun clickSyncInvokesRefreshCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncLabel = context.getString(R.string.sync_now)
        var callbackInvoked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerScreen(
                    fragmented = false,
                    decks = emptyList(),
                    isSyncing = false,
                    onRefresh = { callbackInvoked = true },
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    deckRowActions = emptyDeckRowActions(),
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = {},
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(syncLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickNavigationIconInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openDrawerLabel = context.getString(R.string.navigation_drawer_open)
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
                    fabActions = emptyFabActions(),
                    moreOptionsMenuActions = emptyMoreOptionsMenuActions(),
                    onNavigationIconClick = { callbackInvoked = true },
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(openDrawerLabel).performClick()
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
        onDeleteEmptyCards = {},
        onCheckDatabase = {},
        onExport = {},
    )
}