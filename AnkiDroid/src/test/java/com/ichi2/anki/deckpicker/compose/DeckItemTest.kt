package com.ichi2.anki.deckpicker.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
            },
            fullDeckName = "Japanese"
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
            onCreateSubdeck = { createSubdeckClicked = true }
        )

        composeTestRule.setContent {
            AnkiDroidTheme {
                DeckItem(deck = deck, actions = actions)
            }
        }

        // The name should be displayed
        composeTestRule.onNodeWithText("Japanese").assertIsDisplayed()

        // Long click to open the dropdown menu
        composeTestRule.onNodeWithText("Japanese").performTouchInput { longClick() }
        
        composeTestRule.waitForIdle()

        // The popup menu should display "Create subdeck" and "Rename"
        composeTestRule.onNodeWithText(createSubdeckLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(renameLabel).assertIsDisplayed()

        // Click "Create subdeck"
        composeTestRule.onNodeWithText(createSubdeckLabel).performClick()

        assertTrue(createSubdeckClicked)
    }
}
