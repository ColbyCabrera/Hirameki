/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.annotation.SuppressLint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DirectSystemCurrentTimeMillisUsage")
class DeckPickerTest : InstrumentedTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<DeckPicker>()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        addNoteUsingBasicNoteType()
        disableIntroductionSlide()
        discardPreliminaryViews()
    }

    /*
    @Test
    fun checkIfClickOnCountsLayoutOpensStudyOptionsOnMobile() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())

        // For mobile. If it is not a mobile, then test will be ignored.
        assumeTrue(!TestUtils.isTablet)

        // Go to RecyclerView item having "Test Deck" and click on the counts layout
        tapOnCountLayouts("Default")

        // Check if currently open Activity is StudyOptionsActivity
        assertThat(
            activityInstance,
            instanceOf(StudyOptionsActivity::class.java),
        )
    }
     */

    @Test
    fun checkIfStudyOptionsIsDisplayedOnTablet() {
        // For tablet. If it is not a tablet, then test will be ignored.
        assumeTrue(TestUtils.isTablet)

        // Check if study options are displayed
        // In the new Compose UI, we check for the presence of the "Study" button text.
        composeTestRule.onNodeWithText(testContext.getString(R.string.studyoptions_start))
            .assertIsDisplayed()
    }

    @Test
    fun checkIfDeckCanBeDeleted() {
        val deckName = "Deck to Delete UI Test"
        val deckId = col.decks.id(deckName)
        val undoText = testContext.getString(R.string.undo)

        // Add dummy cards to make the deletion slow enough to show the progress dialog
        val noteType = col.notetypes.basic.apply { did = deckId }
        col.notetypes.save(noteType)
        for (i in 1..200) {
            addNoteUsingBasicNoteType("Front $i", "Back $i")
        }

        // Recreate the activity to force the ViewModel to reload the deck list from the database
        composeTestRule.activityRule.scenario.recreate()

        // Wait for the deck to appear in the UI
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(deckName).fetchSemanticsNodes().isNotEmpty()
        }

        // Assert the deck is displayed
        composeTestRule.onNodeWithText(deckName).assertIsDisplayed()

        // Perform a long-click on the deck to open the Compose context menu
        composeTestRule.onNodeWithText(deckName).performTouchInput { longClick() }

        // Click on the "Delete" option in the context menu
        composeTestRule.onNodeWithText(testContext.getString(R.string.contextmenu_deckpicker_delete_deck))
            .performClick()

        val deletingDeckText = testContext.getString(R.string.delete_deck)

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(deletingDeckText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(deletingDeckText).assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(undoText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(undoText).assertIsDisplayed()

        // Assert that the deck is no longer displayed after deletion completes
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(deckName).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(deckName).assertDoesNotExist()

        composeTestRule.onNodeWithText(undoText).performClick()

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(deckName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(deckName).assertIsDisplayed()
    }
}
