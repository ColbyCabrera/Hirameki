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

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class StudyOptionsScreenTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsProgressIndicatorWhileStudyOptionsLoading() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                StudyOptionsScreen(
                    studyOptionsData = null,
                    onStartStudy = {},
                    onCustomStudy = {},
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertExists()
    }

    @Test
    fun showsEmptyDeckViewForEmptyRegularDeck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val emptyDeckLabel = context.getString(R.string.empty_deck)
        val customStudyLabel = context.getString(R.string.custom_study)

        composeTestRule.setContent {
            AnkiDroidTheme {
                StudyOptionsScreen(
                    studyOptionsData = defaultStudyOptionsData(totalCards = 0),
                    onStartStudy = {},
                    onCustomStudy = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeTestRule.onNodeWithText(emptyDeckLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(customStudyLabel).assertDoesNotExist()
    }

    @Test
    fun showsCongratsViewAndInvokesCustomStudyForFinishedRegularDeck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val congratsLabel = context.getString(R.string.studyoptions_congrats_finished)
        val customStudyLabel = context.getString(R.string.custom_study)
        var customStudyDeckId: Long? = null

        composeTestRule.setContent {
            AnkiDroidTheme {
                StudyOptionsScreen(
                    studyOptionsData = defaultStudyOptionsData(
                        newCount = 0,
                        lrnCount = 0,
                        revCount = 0,
                        totalCards = 10,
                    ),
                    onStartStudy = {},
                    onCustomStudy = { customStudyDeckId = it },
                )
            }
        }

        composeTestRule.onNodeWithText(congratsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(customStudyLabel).performClick()

        assertEquals(42L, customStudyDeckId)
    }

    @Test
    fun hidesCustomStudyButtonForFinishedFilteredDeck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val congratsLabel = context.getString(R.string.studyoptions_congrats_finished)
        val customStudyLabel = context.getString(R.string.custom_study)

        composeTestRule.setContent {
            AnkiDroidTheme {
                StudyOptionsScreen(
                    studyOptionsData = defaultStudyOptionsData(
                        newCount = 0,
                        lrnCount = 0,
                        revCount = 0,
                        totalCards = 10,
                        isFiltered = true,
                    ),
                    onStartStudy = {},
                    onCustomStudy = {},
                )
            }
        }

        composeTestRule.onNodeWithText(congratsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(customStudyLabel).assertDoesNotExist()
    }

    @Test
    fun showsStudyOptionsViewAndInvokesStartStudy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val buriedInfoLabel =
            context.getString(R.string.buried_cards_are_not_included_in_counts_above)
        val startStudyLabel = context.getString(R.string.studyoptions_start)
        val totalNewLabel = context.getString(R.string.total_new)
        val totalCardsLabel = context.getString(R.string.studyoptions_total_label)
        var startedStudy = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                StudyOptionsScreen(
                    studyOptionsData = defaultStudyOptionsData(),
                    onStartStudy = { startedStudy = true },
                    onCustomStudy = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visit docs").assertIsDisplayed()
        composeTestRule.onNodeWithText(buriedInfoLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(totalNewLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(totalCardsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(startStudyLabel).performClick()

        assertTrue(startedStudy)
    }

    private fun defaultStudyOptionsData(
        newCount: Int = 5,
        lrnCount: Int = 2,
        revCount: Int = 8,
        totalCards: Int = 20,
        isFiltered: Boolean = false,
    ) = StudyOptionsData(
        deckId = 42L,
        deckName = "Spanish",
        deckDescription = "Visit <a href=\"https://example.com\">docs</a>",
        newCount = newCount,
        lrnCount = lrnCount,
        revCount = revCount,
        buriedNew = 1,
        buriedLrn = 1,
        buriedRev = 2,
        totalNewCards = 12,
        totalCards = totalCards,
        isFiltered = isFiltered,
        haveBuried = true,
    )
}