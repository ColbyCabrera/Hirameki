package com.ichi2.anki.deckpicker.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import anki.decks.deckTreeNode
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class DeckItemTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsCreateSubdeckOptionAndInvokesAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val createSubdeckLabel = context.getString(R.string.create_subdeck)
        val renameLabel = context.getString(R.string.rename_deck)

        var createSubdeckClicked = false

        val node = DeckNode(
            node = deckTreeNode {
                name = "Japanese"
                deckId = 1L
                level = 1
                reviewCount = 10
                newCount = 5
                learnCount = 2
                filtered = false
            }, fullDeckName = "Japanese"
        )
        val deck = DisplayDeckNode.from(node, matchesSearchOrChild = true, selectedDeckId = 0L)

        val actions = DeckItemActions(
            onDeckClick = {},
            onExpandClick = {},
            onDeckOptions = {},
            onRename = {},
            onExport = {},
            onDelete = {},
            onRebuild = {},
            onEmpty = {},
            onCreateSubdeck = { createSubdeckClicked = true })

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckItem(deck = deck, actions = actions)
            }
        }

        // The name should be displayed
        composeTestRule.onNodeWithText("Japanese").assertIsDisplayed()

        // Long click to open the dropdown menu
        composeTestRule.onNodeWithText("Japanese")
            .performSemanticsAction(SemanticsActions.OnLongClick)

        composeTestRule.waitForIdle()

        // The popup menu should display "Create subdeck" and "Rename"
        composeTestRule.onNodeWithText(createSubdeckLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(renameLabel).assertIsDisplayed()

        // Click "Create subdeck"
        composeTestRule.onNodeWithText(createSubdeckLabel).performClick()

        assertTrue(createSubdeckClicked)
    }

    @Test
    fun showsDeckOptionsAndDeleteAndInvokesCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deckOptionsLabel = context.getString(R.string.deck_options)
        val deleteLabel = context.getString(R.string.contextmenu_deckpicker_delete_deck)

        var deckOptionsClicked = false
        var deleteClicked = false

        val node = DeckNode(
            node = deckTreeNode {
                name = "Spanish"
                deckId = 2L
                level = 1
                reviewCount = 4
                newCount = 3
                learnCount = 1
                filtered = false
            }, fullDeckName = "Spanish"
        )
        val deck = DisplayDeckNode.from(node, matchesSearchOrChild = true, selectedDeckId = 0L)

        val actions = DeckItemActions(
            onDeckClick = {},
            onExpandClick = {},
            onDeckOptions = { deckOptionsClicked = true },
            onRename = {},
            onExport = {},
            onDelete = { deleteClicked = true },
            onRebuild = {},
            onEmpty = {},
            onCreateSubdeck = {})

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckItem(deck = deck, actions = actions)
            }
        }

        composeTestRule.onNodeWithText("Spanish")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(deckOptionsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(deleteLabel).assertIsDisplayed()

        composeTestRule.onNodeWithText(deckOptionsLabel).performClick()
        assertTrue(deckOptionsClicked)

        composeTestRule.onNodeWithText("Spanish")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(deleteLabel).performClick()
        assertTrue(deleteClicked)
    }

    @Test
    fun showsFilteredDeckActionsAndInvokesCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rebuildLabel = context.getString(R.string.rebuild_cram_label)
        val emptyLabel = context.getString(R.string.empty_cram_label)

        var rebuildClicked = false
        var emptyClicked = false

        val node = DeckNode(
            node = deckTreeNode {
                name = "Filtered"
                deckId = 3L
                level = 1
                reviewCount = 0
                newCount = 0
                learnCount = 0
                filtered = true
            }, fullDeckName = "Filtered"
        )
        val deck = DisplayDeckNode.from(node, matchesSearchOrChild = true, selectedDeckId = 0L)

        val actions = DeckItemActions(
            onDeckClick = {},
            onExpandClick = {},
            onDeckOptions = {},
            onRename = {},
            onExport = {},
            onDelete = {},
            onRebuild = { rebuildClicked = true },
            onEmpty = { emptyClicked = true },
            onCreateSubdeck = {})

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckItem(deck = deck, actions = actions)
            }
        }

        composeTestRule.onNodeWithText("Filtered")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(rebuildLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(emptyLabel).assertIsDisplayed()

        composeTestRule.onNodeWithText(emptyLabel).performClick()
        assertTrue(emptyClicked)

        composeTestRule.onNodeWithText("Filtered")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(rebuildLabel).performClick()
        assertTrue(rebuildClicked)
    }
}
