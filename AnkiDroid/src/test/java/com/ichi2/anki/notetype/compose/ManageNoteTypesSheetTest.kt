/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.notetype.ManageNoteTypeUiModel
import com.ichi2.anki.notetype.ManageNoteTypesUiState
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * the entrance and exit of the note type action sheet.
 *
 * a sheet state that outlives one open and is left expanded re-enters expanded, and material3 puts such
 * a sheet on screen with no animation at all - so the state has to be scoped to the sheet itself.
 *
 * the sheet's buttons close it by sliding it out first and only then dropping it from composition; a
 * button that dropped it straight away would make it vanish, the mirror of the missing entrance.
 *
 * that state is also saved with the screen, so a recreation with the sheet open has to bring the
 * sheet back open too, or the saved "expanded" is left for the next open to pick up.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ManageNoteTypesSheetTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val noteType = ManageNoteTypeUiModel(1L, "Basic", 2)
    private var fieldsShown = 0

    @Test
    fun `the sheet travels on every open, not only the first`() {
        // the rows run an endless wobble transition, so the clock is driven by hand: left to run free
        // nothing on this screen is ever idle
        composeTestRule.mainClock.autoAdvance = false
        setScreenContent()

        tapNoteType()
        val firstStartingTop = sheetTop()
        settle()
        val firstRestingTop = sheetTop()
        // proves the probe can see an entrance at all, so the same check on the reopen means something
        assertThat("the first sheet starts below where it settles", firstStartingTop, greaterThan(firstRestingTop))

        // an action button, not a swipe or a tap on the scrim
        tapFields()
        frames(2)
        assertThat("the fields action fired", fieldsShown, equalTo(1))
        // the button slides the sheet out before it goes, so the reopen waits for that
        settle()
        composeTestRule.onNodeWithText(getResourceString(R.string.fields)).assertDoesNotExist()

        tapNoteType()
        val secondStartingTop = sheetTop()
        settle()
        val secondRestingTop = sheetTop()

        assertThat("the reopened sheet settles where the first one did", secondRestingTop, equalTo(firstRestingTop))
        assertThat(
            "the reopened sheet starts below where it settles, so it animates in",
            secondStartingTop,
            greaterThan(secondRestingTop),
        )
    }

    @Test
    fun `a sheet left open across a recreation still travels on the next open`() {
        composeTestRule.mainClock.autoAdvance = false
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { Screen() }
        frames(2)

        tapNoteType()
        val firstStartingTop = sheetTop()
        settle()
        val restingTop = sheetTop()
        assertThat("the first sheet starts below where it settles", firstStartingTop, greaterThan(restingTop))

        // the tester waits for idle between tearing the screen down and building it back, and with the
        // clock held nothing recomposes, so the teardown would never happen
        composeTestRule.mainClock.autoAdvance = true
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.mainClock.autoAdvance = false
        frames(2)
        // the saved sheet position is only read if the sheet comes back; closed, it lingers for the next open
        composeTestRule.onNodeWithText(getResourceString(R.string.fields)).assertExists("the sheet survives the recreation")

        tapFields()
        settle()
        composeTestRule.onNodeWithText(getResourceString(R.string.fields)).assertDoesNotExist()

        tapNoteType()
        val reopenStartingTop = sheetTop()
        settle()
        assertThat("the reopened sheet settles where the first one did", sheetTop(), equalTo(restingTop))
        assertThat(
            "the reopened sheet starts below where it settles, so it animates in",
            reopenStartingTop,
            greaterThan(restingTop),
        )
    }

    @Test
    fun `an action button slides the sheet out, and a click on the way out runs nothing`() {
        composeTestRule.mainClock.autoAdvance = false
        setScreenContent()
        tapNoteType()
        // gives the entrance its first frame before the clock jumps: an animation takes its start time from that
        // frame, so one first run at the end of the jump starts there and leaves the sheet below the screen
        composeTestRule.waitForIdle()
        settle()
        val restingTop = sheetTop()

        tapFields()
        frames(EXIT_FRAMES)
        assertThat("the fields action fired", fieldsShown, equalTo(1))
        composeTestRule
            .onNodeWithText(getResourceString(R.string.fields))
            .assertExists("the sheet is still on screen mid-exit")
        assertThat("the sheet is sliding down, not gone at once", sheetTop(), greaterThan(restingTop))

        // the button stays clickable while the sheet slides out. a touch on it is mostly cancelled by the button
        // moving away under the finger, an accessibility click never is, so that is the click used here
        composeTestRule
            .onNodeWithText(getResourceString(R.string.fields))
            .performSemanticsAction(SemanticsActions.OnClick)
        frames(EXIT_FRAMES)
        assertThat("a second click on the way out runs nothing", fieldsShown, equalTo(1))

        settle()
        composeTestRule.onNodeWithText(getResourceString(R.string.fields)).assertDoesNotExist()
    }

    /** the top edge of the sheet, which rises as the sheet slides in */
    private fun sheetTop(): Dp =
        composeTestRule
            .onNodeWithText(getResourceString(R.string.fields))
            .getUnclippedBoundsInRoot()
            .top

    /** taps the row, which opens the sheet. only unambiguous while the sheet is closed */
    private fun tapNoteType() {
        composeTestRule.onNodeWithText(noteType.name).performTouchInput { click() }
        // one frame to compose the sheet in, one to place it at the start of its travel
        frames(2)
    }

    /** taps the fields button, and gives the slide-out it starts its first frame before the clock moves */
    private fun tapFields() {
        composeTestRule.onNodeWithText(getResourceString(R.string.fields)).performTouchInput { click() }
        composeTestRule.waitForIdle()
    }

    /** long enough for any sheet entrance or exit to finish */
    private fun settle() = composeTestRule.mainClock.advanceTimeBy(2_000L)

    private fun frames(count: Int) = repeat(count) { composeTestRule.mainClock.advanceTimeByFrame() }

    private fun setScreenContent() {
        composeTestRule.setContent { Screen() }
        frames(2)
    }

    @Composable
    private fun Screen() {
        AnkiDroidTheme {
            ManageNoteTypesScreen(
                uiState = ManageNoteTypesUiState(noteTypes = listOf(noteType)),
                onSearch = {},
                onAddNoteType = { _, _ -> },
                onShowFields = { fieldsShown++ },
                onEditCards = {},
                onRename = {},
                onDeleteRequest = {},
                onDeleteConfirm = {},
                onDeleteDismiss = {},
                onToggleSelection = {},
                onSelectAll = {},
                onDeselectAll = {},
                onDeleteSelected = {},
                onNavigateUp = {},
            )
        }
    }

    private companion object {
        /** ~64ms: into the sheet's exit, short of its ~150ms end */
        const val EXIT_FRAMES = 4
    }
}
