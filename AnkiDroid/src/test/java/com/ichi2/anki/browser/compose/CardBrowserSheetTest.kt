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
package com.ichi2.anki.browser.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * the card browser's more options sheet leaves the way it came in: a row slides it out, and the sheet is only
 * dropped from composition once it is hidden. before, the rows' callbacks dropped it straight away, so it
 * vanished.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CardBrowserSheetTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var open by mutableStateOf(false)
    private var cardInfoShown = 0

    @Test
    fun `a row slides the more options sheet out before it is dropped`() {
        composeTestRule.mainClock.autoAdvance = false
        setSheetContent()
        open = true
        frames(2)
        val startingTop = rowTop()
        settle()
        val restingTop = rowTop()
        // a sheet that never came up is already hidden, and would leave at once whatever the rows do
        assertThat("the sheet rose on screen", restingTop, lessThan(startingTop))

        composeTestRule.onNodeWithText(cardInfo()).performTouchInput { click() }
        frames(EXIT_FRAMES)
        assertThat("the card info action fired", cardInfoShown, equalTo(1))
        assertThat("the sheet is still wanted mid-exit", open, equalTo(true))
        assertThat("the sheet is sliding down, not gone at once", rowTop(), greaterThan(restingTop))

        settle()
        assertThat("the sheet asked to be dropped once hidden", open, equalTo(false))
        composeTestRule.onNodeWithText(cardInfo()).assertDoesNotExist()
    }

    private fun cardInfo() = getResourceString(R.string.card_info_title)

    /** the top edge of a row, which moves with the sheet */
    private fun rowTop(): Dp =
        composeTestRule
            .onNodeWithText(cardInfo())
            .getUnclippedBoundsInRoot()
            .top

    /** long enough for any sheet entrance or exit to finish */
    private fun settle() = composeTestRule.mainClock.advanceTimeBy(2_000L)

    // one frame at a time, idling in between, so the sheet composes, lays out and gives its animation a first
    // frame. an animation takes its start time from that first frame: one first run at the end of a clock jump
    // starts there, and the sheet is left where it was
    private fun frames(count: Int) =
        repeat(count) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
        }

    private fun setSheetContent() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                // the same shape as the card browser screen: the flag decides whether the sheet is composed
                if (open) {
                    MoreOptionsBottomSheet(
                        onDismissRequest = { open = false },
                        onChangeDisplayOrder = {},
                        onCreateFilteredDeck = {},
                        selectionCount = 1,
                        cardsOrNotes = CardsOrNotes.CARDS,
                        onEditNote = {},
                        onDeleteNote = {},
                        onCardInfo = { cardInfoShown++ },
                        onToggleSuspend = {},
                        onToggleBury = {},
                        onChangeDeck = {},
                        onReposition = {},
                        onSetDueDate = {},
                        onEditTags = {},
                        onGradeNow = {},
                        onResetProgress = {},
                        onExportCard = {},
                        onUndoDeleteNote = {},
                    )
                }
            }
        }
        frames(1)
    }

    private companion object {
        /** ~64ms: into the sheet's exit, short of its ~150ms end */
        const val EXIT_FRAMES = 4
    }
}
