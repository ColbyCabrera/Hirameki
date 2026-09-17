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
package com.ichi2.anki.reviewer.compose

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.ReviewerViewModel
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The whole reviewer screen on a real card, revealed and rated through what the user touches, for every
 * combination of the card view's gestures. each combination lays out the answer bar differently.
 *
 * With one gesture on and the other off the card performs one step and the answer bar the other, so the bar
 * is laid out on a step it is not shown on. Measured there without being placed, the reveal crashed the
 * reviewer with "LayoutNode N not found in RectList"; these two combinations are the ones that catch it.
 *
 * Placing it instead leaves a real touch strip below the card, so every covered step is also pressed where
 * the bar sits: the cure for the crash must not be an unseen control that answers the card.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReviewerContentGestureTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `tap to flip without drag to grade reveals and rates`() = revealAndRate(cardView = true, tapToFlip = true, dragToGrade = false)

    @Test
    fun `drag to grade without tap to flip reveals and rates`() = revealAndRate(cardView = true, tapToFlip = false, dragToGrade = true)

    @Test
    fun `card view with both gestures off reveals and rates`() = revealAndRate(cardView = true, tapToFlip = false, dragToGrade = false)

    @Test
    fun `card view with both gestures on reveals and rates`() = revealAndRate(cardView = true, tapToFlip = true, dragToGrade = true)

    @Test
    fun `classic view reveals and rates`() = revealAndRate(cardView = false, tapToFlip = true, dragToGrade = true)

    @Test
    fun `the overflow menu stays in the top bar on a card the card view hands to the classic layout`() =
        runTest {
            setGestures(cardView = true, tapToFlip = true, dragToGrade = true)
            // a typed answer is served by the classic layout, even with the card view on
            addBasicWithTypingNote("Hello", "World")
            val viewModel = showReviewer()
            assertThat("the classic layout serves this card", viewModel.state.value.showTypeInAnswer, equalTo(true))

            val moreOptions = composeTestRule.onAllNodesWithContentDescription(getResourceString(R.string.more_options))
            moreOptions.assertCountEquals(1)
            val screen = composeTestRule.onRoot().getBoundsInRoot()
            // decided by the card, the button moved down into the answer bar here and back up on the next card
            assertThat(
                "in the top bar, where it is on every other card",
                moreOptions[0].getBoundsInRoot().bottom,
                lessThan(screen.bottom / 2),
            )
        }

    @Test
    fun `a grade made through the card is read out to a screen reader`() =
        runTest {
            val accessibility = ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)
            shadowOf(accessibility).setTouchExplorationEnabled(true)
            setGestures(cardView = true, tapToFlip = true, dragToGrade = true)
            addBasicNote("Hello", "World")
            addBasicNote("Second", "Card")
            val viewModel = showReviewer()
            cardAction(getResourceString(R.string.show_answer))
            settle()
            assertThat("answer shown", viewModel.state.value.isAnswerShown, equalTo(true))

            val good = getResourceString(R.string.ease_button_good)
            composeTestRule.mainClock.autoAdvance = false
            cardAction(good)
            testScheduler.advanceUntilIdle()
            repeat(3) { composeTestRule.mainClock.advanceTimeByFrame() }

            // the corner that confirms a dragged grade can be neither seen nor dragged to by a screen reader, which rates
            // through the card's actions: the notice of the grade is shown to it, and read out as it appears
            val liveGradeNotice =
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
                    hasAnyDescendant(hasText(good))
            composeTestRule.onNode(liveGradeNotice).assertExists()
        }

    private fun setGestures(
        cardView: Boolean,
        tapToFlip: Boolean,
        dragToGrade: Boolean,
    ) = editPreferences {
        putBoolean(getResourceString(R.string.card_view_reviewer_key), cardView)
        putBoolean(getResourceString(R.string.card_tap_to_flip_key), tapToFlip)
        putBoolean(getResourceString(R.string.card_drag_to_grade_key), dragToGrade)
    }

    private fun TestScope.showReviewer(): ReviewerViewModel {
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
        settle()
        composeTestRule.setContent {
            AnkiDroidTheme {
                ReviewerContent(viewModel = viewModel, whiteboardViewModel = null, voicePlaybackViewModel = null)
            }
        }
        settle()
        return viewModel
    }

    private fun revealAndRate(
        cardView: Boolean,
        tapToFlip: Boolean,
        dragToGrade: Boolean,
    ) = runTest {
        editPreferences {
            putBoolean(getResourceString(R.string.card_view_reviewer_key), cardView)
            putBoolean(getResourceString(R.string.card_tap_to_flip_key), tapToFlip)
            putBoolean(getResourceString(R.string.card_drag_to_grade_key), dragToGrade)
        }
        addBasicNote("Hello", "World")
        addBasicNote("Second", "Card")
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
        settle()

        composeTestRule.setContent {
            AnkiDroidTheme {
                ReviewerContent(viewModel = viewModel, whiteboardViewModel = null, voicePlaybackViewModel = null)
            }
        }
        settle()
        val firstCard = viewModel.state.value.cardDisplayIndex

        val showAnswer = getResourceString(R.string.show_answer)
        val showAnswerButton = hasText(showAnswer)
        if (cardView && tapToFlip) {
            // the card turns this step itself, so the bar below it is laid out for its room alone
            assertCoveredBarIsInert(showAnswerButton, isLaidOut = !dragToGrade) {
                viewModel.state.value.isAnswerShown
            }
            // what a tap on the card does once it has arrived; robolectric's webview never reports a paint or
            // answers the tap probe, so the card is let in by its paint timeout and turned by its own action
            cardAction(showAnswer)
        } else {
            composeTestRule.onNode(showAnswerButton).performClick()
        }
        settle()
        assertThat("answer shown", viewModel.state.value.isAnswerShown, equalTo(true))

        val good = getResourceString(R.string.ease_button_good)
        val goodButton = hasContentDescription("$good,", substring = true)
        if (cardView && dragToGrade) {
            // the card rates this step itself; a live rating button here would grade from a touch on nothing
            assertCoveredBarIsInert(goodButton, isLaidOut = !tapToFlip) {
                viewModel.state.value.cardDisplayIndex != firstCard
            }
            // a screen reader's stand-in for dragging the card into the good corner, the same rating call
            cardAction(good)
        } else {
            composeTestRule.onNode(goodButton).performClick()
        }
        settle()
        assertThat("the next card is up", viewModel.state.value.cardDisplayIndex, equalTo(firstCard + 1))
        assertThat("its question is up", viewModel.state.value.isAnswerShown, equalTo(false))
    }

    /**
     * The answer bar on a step the card performs itself. It is laid out under the card so the card's room
     * never changes across the reveal, but it must be gone from the screen reader's tree and deaf to a touch
     * in the strip it occupies between the card and the navigation bar: only `enabled = !cardCoversThisStep`
     * keeps an invisible control from answering the card on a tap the user cannot see they made.
     *
     * [isLaidOut] is false only with both card gestures on, where the bar is composed on neither step and
     * there is nothing left to touch. The touch is aimed through the unmerged tree, which still holds the
     * node whose semantics were cleared and so still knows where the invisible bar sits; [actedOn] reports
     * whether it answered.
     */
    private fun TestScope.assertCoveredBarIsInert(
        matcher: SemanticsMatcher,
        isLaidOut: Boolean,
        actedOn: () -> Boolean,
    ) {
        composeTestRule.onAllNodes(matcher).assertCountEquals(0)
        if (!isLaidOut) {
            composeTestRule.onAllNodes(matcher, useUnmergedTree = true).assertCountEquals(0)
            return
        }
        composeTestRule.onNode(matcher, useUnmergedTree = true).performClick()
        settle()
        assertThat("a touch on the covered bar does nothing", actedOn(), equalTo(false))
    }

    private fun cardAction(label: String) {
        composeTestRule
            .onNode(
                SemanticsMatcher("has custom action $label") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
                },
            ).performCustomAccessibilityActionWithLabel(label)
    }

    /** runs the view model's card actions and lets the screen catch up, past the card's paint timeout */
    private fun TestScope.settle() {
        testScheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(CardMotionSpec.Default.paintWaitMillis * 2L)
        composeTestRule.waitForIdle()
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
    }
}
