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

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
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
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.ui.compose.components.ADD_DECK_FAB_TAG
import com.ichi2.anki.ui.compose.components.GET_SHARED_FAB_TAG
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    /** the host activity's dispatcher, captured by [setDeckPickerContent] */
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** a scope from the composition, captured by [setDeckPickerContent]; drawer animations need its frame clock */
    private lateinit var compositionScope: CoroutineScope

    @Test
    fun searchOpenInputAndCloseRoutesQueryChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDecksLabel = context.getString(R.string.search_decks)
        val closeLabel = context.getString(R.string.close)
        val queryEvents = mutableListOf<String>()

        setDeckPickerContent(
            onSearchQueryChanged = {
                queryEvents += it
            })

        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("spanish")
        composeTestRule.onNodeWithText("spanish").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(closeLabel).performClick()

        assertEquals(listOf("spanish", ""), queryEvents)
    }

    @Test
    fun backClosesTheOpenSearch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDecksLabel = context.getString(R.string.search_decks)
        val queryEvents = mutableListOf<String>()

        setDeckPickerContent(onSearchQueryChanged = { queryEvents += it })

        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("spanish")
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()
        assertEquals("back clears the query and closes search", listOf("spanish", ""), queryEvents)
    }

    @Test
    fun backClosesTheDrawerBeforeTheSearchBehindIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDecksLabel = context.getString(R.string.search_decks)
        val queryEvents = mutableListOf<String>()
        val drawerState = DrawerState(DrawerValue.Closed)

        setDeckPickerContent(onSearchQueryChanged = { queryEvents += it }, drawerState = drawerState)

        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("spanish")
        // the menu button is hidden while searching, but on a phone the drawer can still be dragged open
        composeTestRule.runOnIdle { compositionScope.launch { drawerState.open() } }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertEquals("back closes the drawer on top", DrawerValue.Closed, drawerState.currentValue)
        composeTestRule.onNodeWithTag("search_field").assertExists()
        assertEquals("the search behind the drawer keeps its query", listOf("spanish"), queryEvents)

        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()
    }

    @Test
    fun backClosesTheFabMenuBeforeTheSearchBehindIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDecksLabel = context.getString(R.string.search_decks)
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val queryEvents = mutableListOf<String>()

        setDeckPickerContent(onSearchQueryChanged = { queryEvents += it })

        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("spanish")
        // the fab sits over the scaffold, so the menu still opens while the search bar is up
        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        assertFabMenu(expanded = true)
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertFabMenu(expanded = false)
        composeTestRule.onNodeWithTag("search_field").assertExists()
        assertEquals("the search behind the menu keeps its query", listOf("spanish"), queryEvents)

        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()
    }

    @Test
    fun backClosesTheDrawerBeforeTheFabMenuBehindIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        val drawerState = DrawerState(DrawerValue.Closed)

        setDeckPickerContent(drawerState = drawerState)

        composeTestRule.onNodeWithContentDescription(fabMenuToggleLabel).performClick()
        composeTestRule.waitForIdle()
        // the drawer's drag modifier sits on an ancestor of the fab scrim, so it can still be pulled open
        composeTestRule.runOnIdle { compositionScope.launch { drawerState.open() } }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertEquals("back closes the drawer on top", DrawerValue.Closed, drawerState.currentValue)
        assertFabMenu(expanded = true)

        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertFabMenu(expanded = false)
    }

    /**
     * the menu items stay composed at zero size while the menu is closed, so whether it is open is
     * read from the toggle's state description rather than from the items' presence
     */
    private fun assertFabMenu(expanded: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = context.getString(if (expanded) R.string.fab_menu_expanded else R.string.fab_menu_collapsed)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.fab_menu_toggle))
            .assert(hasStateDescription(state))
    }

    @Test
    fun fabMenuInvokesGetSharedCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fabMenuToggleLabel = context.getString(R.string.fab_menu_toggle)
        var callbackInvoked = false

        setDeckPickerContent(
            fabActions = emptyFabActions().copy(onAddSharedDeck = { callbackInvoked = true })
        )

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

        setDeckPickerContent(
            fabActions = emptyFabActions().copy(onAddFilteredDeck = { callbackInvoked = true })
        )

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

        setDeckPickerContent(
            fabActions = emptyFabActions().copy(onAddDeck = { callbackInvoked = true })
        )

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

        setDeckPickerContent(
            fabActions = emptyFabActions().copy(onAddNote = { callbackInvoked = true })
        )

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

        setDeckPickerContent(deck = displayDeck(deckName))

        openContextMenu(deckName)

        composeTestRule.onNodeWithText(renameLabel).assertIsDisplayed()
    }

    @Test
    fun clickRenameDeckInContextMenuInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckName = "Japanese"
        val renameLabel = context.getString(R.string.rename_deck)
        var callbackInvoked = false

        setDeckPickerContent(
            deck = displayDeck(deckName),
            deckRowActions = emptyDeckRowActions().copy(onRename = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName),
            deckRowActions = emptyDeckRowActions().copy(onExportDeck = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName),
            deckRowActions = emptyDeckRowActions().copy(onCustomStudy = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName, filtered = true),
            deckRowActions = emptyDeckRowActions().copy(onRebuild = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName, filtered = true),
            deckRowActions = emptyDeckRowActions().copy(onEmpty = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName),
            deckRowActions = emptyDeckRowActions().copy(onCreateSubdeck = {
                callbackInvoked = true
            })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName),
            deckRowActions = emptyDeckRowActions().copy(onDelete = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            deck = displayDeck(deckName, collapsed = true),
            deckRowActions = emptyDeckRowActions().copy(onExpandClick = { callbackInvoked = true })
        )

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

        setDeckPickerContent(
            deck = displayDeck(deckName, hasBuried = true),
            deckRowActions = emptyDeckRowActions().copy(onUnbury = { callbackInvoked = true })
        )

        openContextMenu(deckName)

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

        setDeckPickerContent(
            moreOptionsMenuActions = emptyMoreOptionsMenuActions().copy(onDeleteEmptyCards = {
                callbackInvoked = true
            })
        )

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

        setDeckPickerContent(
            moreOptionsMenuActions = emptyMoreOptionsMenuActions().copy(onCheckDatabase = {
                callbackInvoked = true
            })
        )

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

        setDeckPickerContent(
            moreOptionsMenuActions = emptyMoreOptionsMenuActions().copy(onExport = {
                callbackInvoked = true
            })
        )

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(exportLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun moreOptionsMenuInvokesManageNoteTypesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsLabel = context.getString(R.string.more_options)
        val manageNoteTypesLabel = context.getString(R.string.model_browser_label)
        var callbackInvoked = false

        setDeckPickerContent(
            moreOptionsMenuActions = emptyMoreOptionsMenuActions().copy(onManageNoteTypes = {
                callbackInvoked = true
            })
        )

        composeTestRule.onNodeWithContentDescription(moreOptionsLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(manageNoteTypesLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun initialStateShowsEmptyCollectionMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val emptyMessage = context.getString(R.string.no_cards_placeholder_title)

        setDeckPickerContent()

        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun clickSyncInvokesRefreshCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncLabel = context.getString(R.string.sync_now)
        var callbackInvoked = false

        setDeckPickerContent(onRefresh = { callbackInvoked = true })

        composeTestRule.onNodeWithContentDescription(syncLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun clickNavigationIconInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openDrawerLabel = context.getString(R.string.navigation_drawer_open)
        var callbackInvoked = false

        setDeckPickerContent(onNavigationIconClick = { callbackInvoked = true })

        composeTestRule.onNodeWithContentDescription(openDrawerLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, callbackInvoked)
    }

    @Test
    fun collapsingADeckAnimatesItsSubdecksAwayAndExpandingShowsThemAtOnce() {
        val expandLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.expand)
        val decks = setDeckListContent(parentWithChild(collapsed = false))
        composeTestRule.onNodeWithText("Child").assertIsDisplayed()

        // frame by frame: the collapse animation must not finish between the checks
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { decks.value = parentWithChild(collapsed = true) }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithContentDescription(expandLabel).assertExists() // the frame composed the collapse
        // collapsing drops the subdeck from the list, but it stays composed while it animates away
        composeTestRule.onNodeWithText("Child").assertExists()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Child").assertDoesNotExist()

        expandParentAndAssertChildOnThatFrame(decks)
    }

    @Test
    fun aDeckFirstShownCollapsedShowsItsSubdecksOnTheFrameThatExpandsIt() {
        // no expanded frame came before, so no subdecks saved from one can stand in for the current ones
        val decks = setDeckListContent(parentWithChild(collapsed = true))
        composeTestRule.onNodeWithText("Child").assertDoesNotExist()

        expandParentAndAssertChildOnThatFrame(decks)
    }

    /** the deck list alone, showing [initial]; set the returned state to change the decks */
    private fun setDeckListContent(initial: List<DisplayDeckNode>): MutableState<List<DisplayDeckNode>> {
        val decks = mutableStateOf(initial)
        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckPickerContent(
                    decks = decks.value,
                    onRefresh = {},
                    listState = rememberLazyListState(),
                    deckRowActions = emptyDeckRowActions(),
                    onAddDeck = {},
                    onAddSharedDeck = {},
                    isInInitialState = false,
                )
            }
        }
        return decks
    }

    /**
     * expands "Parent" one frame at a time and checks "Child" is on the frame that composes the expand. subdecks saved
     * after composition would only arrive on the next frame, which the stopped clock never runs
     */
    private fun expandParentAndAssertChildOnThatFrame(decks: MutableState<List<DisplayDeckNode>>) {
        val collapseLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.collapse)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { decks.value = parentWithChild(collapsed = false) }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithContentDescription(collapseLabel).assertExists() // the frame composed the expand
        composeTestRule.onNodeWithText("Child").assertExists()
        composeTestRule.mainClock.autoAdvance = true
    }

    /** the flattened rows of a top level "Parent" deck with one subdeck, "Child" */
    private fun parentWithChild(collapsed: Boolean): List<DisplayDeckNode> {
        val child =
            deckTreeNode {
                name = "Child"
                deckId = 2L
                level = 2
            }
        val parent =
            deckTreeNode {
                name = "Parent"
                deckId = 1L
                level = 1
                this.collapsed = collapsed
                children.add(child)
            }
        val root =
            DeckNode(
                node =
                    deckTreeNode {
                        level = 0
                        children.add(parent)
                    },
                fullDeckName = "",
            )
        return root.filterAndFlattenDisplay(filter = null, selectedDeckId = 0L, decksWithBuried = emptySet())
    }

    private fun displayDeck(
        deckName: String,
        filtered: Boolean = false,
        collapsed: Boolean = false,
        hasBuried: Boolean = false
    ): DisplayDeckNode {
        val deckNode = DeckNode(
            node = deckTreeNode {
                name = deckName
                deckId = 1L
                level = 1
                this.filtered = filtered
                this.collapsed = collapsed
                if (collapsed) {
                    children.add(deckTreeNode { name = "Child"; deckId = 2L; level = 2 })
                }
            }, fullDeckName = deckName
        )
        return DisplayDeckNode.from(deckNode, collapsed, 0L, hasBuried)
    }

    private fun setDeckPickerContent(
        deck: DisplayDeckNode? = null,
        decks: List<DisplayDeckNode> = deck?.let { listOf(it) } ?: emptyList(),
        deckRowActions: DeckRowActions = emptyDeckRowActions(),
        fabActions: FabActions = emptyFabActions(),
        moreOptionsMenuActions: MoreOptionsMenuActions = emptyMoreOptionsMenuActions(),
        onRefresh: () -> Unit = {},
        onNavigationIconClick: () -> Unit = {},
        searchQuery: String = "",
        onSearchQueryChanged: (String) -> Unit = {},
        isInInitialState: Boolean = decks.isEmpty(),
        drawerState: DrawerState? = null,
    ) {
        composeTestRule.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            compositionScope = rememberCoroutineScope()
            var currentSearchQuery by remember { mutableStateOf(searchQuery) }
            val screen: @Composable (isDrawerOpen: Boolean) -> Unit = { isDrawerOpen ->
                DeckPickerScreen(
                    fragmented = false,
                    decks = decks,
                    isSyncing = false,
                    onRefresh = onRefresh,
                    searchQuery = currentSearchQuery,
                    onSearchQueryChanged = {
                        onSearchQueryChanged(it)
                        currentSearchQuery = it
                    },
                    deckRowActions = deckRowActions,
                    fabActions = fabActions,
                    moreOptionsMenuActions = moreOptionsMenuActions,
                    onNavigationIconClick = onNavigationIconClick,
                    onStartStudy = {},
                    onCustomStudy = {},
                    studyOptionsData = null,
                    requestSearchFocus = false,
                    onSearchFocusRequested = {},
                    syncState = SyncIconState.Normal,
                    isInInitialState = isInInitialState,
                    isDrawerOpen = isDrawerOpen,
                )
            }

            AnkiDroidTheme {
                if (drawerState == null) {
                    screen(false)
                } else {
                    // the drawer the deck picker host wraps the screen in, so its own back handler takes part
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = { ModalDrawerSheet(drawerState = drawerState) { Text("drawer") } },
                    ) {
                        screen(drawerState.targetValue == DrawerValue.Open)
                    }
                }
            }
        }
    }

    private fun openContextMenu(deckName: String) {
        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
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
        onManageNoteTypes = {},
    )
}