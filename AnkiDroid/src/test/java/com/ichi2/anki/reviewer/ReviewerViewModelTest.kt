/*
 *  Copyright (c) 2024 the Anki-Android contributors
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
package com.ichi2.anki.reviewer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [ReviewerViewModel].
 *
 * These tests require [CollectionStorageMode.IN_MEMORY_WITH_MEDIA] because the ViewModel
 * accesses the media directory during card loading.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTest : RobolectricTest() {

    // Must use IN_MEMORY_WITH_MEDIA to provide media directory access
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `initial state has empty counts`() = runTest {
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        // When there are no cards, the review should finish
        val state = viewModel.state.first()
        assertThat("No cards means review is finished", state.isFinished, equalTo(true))
        assertThat("New count should be 0", state.newCount, equalTo(0))
        assertThat("Learn count should be 0", state.learnCount, equalTo(0))
        assertThat("Review count should be 0", state.reviewCount, equalTo(0))
    }

    @Test
    fun `card loads successfully when cards exist`() = runTest {
        // Add a card so there's something to review
        addBasicNote("Front", "Back")

        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        val state = viewModel.state.first()
        assertThat(
            "Review should not be finished when cards exist", state.isFinished, equalTo(false)
        )
        assertThat("New count should be 1", state.newCount, equalTo(1))
    }

    @Test
    fun `video tags render as inline video in reviewer html`() = runTest {
        addBasicNote("Front [sound:test.mp4]", "Back")

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel =
            ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
        testScheduler.advanceUntilIdle()
        advanceRobolectricLooper()

        val state = viewModel.state.first()
        assertThat(
            "Video tags should render as inline video", state.questionHtml, containsString("<video")
        )
        assertThat(
            "Video tags should include the file name for JS playback",
            state.questionHtml,
            containsString("data-file=\"test.mp4\"")
        )
        assertThat(
            "Video tags should report completion through the WebView",
            state.questionHtml,
            containsString("videoended:q:0")
        )
        assertThat(
            "Video tags should not fall back to replay buttons",
            state.questionHtml,
            not(containsString("href=playsound:q:0"))
        )
    }

    @Test
    fun `audio tags remain replay buttons in reviewer html`() = runTest {
        addBasicNote("Front [sound:test.mp3]", "Back")

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel =
            ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
        testScheduler.advanceUntilIdle()
        advanceRobolectricLooper()

        val state = viewModel.state.first()
        assertThat(
            "Audio tags should still render replay links",
            state.questionHtml,
            containsString("playsound:q:0")
        )
        assertThat(
            "Audio tags should not render inline video",
            state.questionHtml,
            not(containsString("<video"))
        )
    }

    @Test
    fun `video autoplay emits javascript when autoplay is enabled`() = runTest {
        addBasicNote("Front [sound:test.mp4]", "Back")

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel =
            ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

        viewModel.evalCommand.test {
            assertThat("No JavaScript should be queued before the scheduler runs", awaitItem().isEmpty(), equalTo(true))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val script = awaitItem().single().script
            assertThat(
                "Autoplay should wait until the DOM is ready",
                script,
                containsString("document.readyState")
            )
            assertThat(
                "Autoplay should target the inline video element",
                script,
                containsString("video.play();")
            )
            assertThat(
                "Autoplay should target the card video file", script, containsString("test.mp4")
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typed answer is updated via event`() = runTest {
        addBasicNote()
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        viewModel.onEvent(ReviewerEvent.OnTypedAnswerChanged("test answer"))

        val state = viewModel.state.first()
        assertThat("Typed answer should be updated", state.typedAnswer, equalTo("test answer"))
    }

    @Test
    fun `whiteboard state is updated via event`() = runTest {
        addBasicNote()
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        viewModel.onEvent(ReviewerEvent.OnWhiteboardStateChanged(true))

        val state = viewModel.state.first()
        assertThat("Whiteboard should be enabled", state.isWhiteboardEnabled, equalTo(true))

        viewModel.onEvent(ReviewerEvent.OnWhiteboardStateChanged(false))

        val state2 = viewModel.state.first()
        assertThat("Whiteboard should be disabled", state2.isWhiteboardEnabled, equalTo(false))
    }

    @Test
    fun `voice playback state is updated via event`() = runTest {
        addBasicNote()
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        viewModel.onEvent(ReviewerEvent.OnVoicePlaybackStateChanged(true))

        val state = viewModel.state.first()
        assertThat("Voice playback should be enabled", state.isVoicePlaybackEnabled, equalTo(true))
    }

    @Test
    fun `showAnswer updates state correctly`() = runTest {
        addBasicNote("Front", "Back")
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        // Initially answer should not be shown
        var state = viewModel.state.first()
        assertThat("Answer should not be shown initially", state.isAnswerShown, equalTo(false))

        viewModel.onEvent(ReviewerEvent.ShowAnswer)
        advanceRobolectricLooper()

        state = viewModel.state.first()
        assertThat(
            "Answer should be shown after ShowAnswer event", state.isAnswerShown, equalTo(true)
        )
        assertThat(
            "Next times should be populated", state.nextTimes.any { it.isNotEmpty() }, equalTo(true)
        )
    }

    @Test
    fun `rateCard loads next card`() = runTest {
        // Add two cards so we can verify navigation to next
        addBasicNote("Front1", "Back1")
        addBasicNote("Front2", "Back2")

        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        var state = viewModel.state.first()
        assertThat("Should have 2 new cards", state.newCount, equalTo(2))

        // Show answer first (required before rating)
        viewModel.onEvent(ReviewerEvent.ShowAnswer)
        advanceRobolectricLooper()

        // Rate the card
        viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
        advanceRobolectricLooper()

        state = viewModel.state.first()
        // After rating, we should be on the next card with answer hidden
        assertThat("Answer should be hidden after rating", state.isAnswerShown, equalTo(false))
        assertThat("New count should decrease", state.newCount, equalTo(1))
    }

    @Test
    fun `confirmDeleteNote deletes current note and loads next card`() = runTest {
        addBasicNote("Front1", "Back1")
        addBasicNote("Front2", "Back2")

        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        val initialState = viewModel.state.first()
        assertThat(
            "Initial card should be loaded before deleting",
            initialState.questionHtml,
            containsString("Front1")
        )
        assertThat("Should have 2 new cards before deleting", initialState.newCount, equalTo(2))

        viewModel.confirmDeleteNote()
        advanceRobolectricLooper()

        val state = viewModel.state.first()
        assertThat("Reviewer should continue with the next card", state.isFinished, equalTo(false))
        assertThat(
            "New count should decrease after deleting the current note", state.newCount, equalTo(1)
        )
        assertThat("The next card should be loaded", state.questionHtml, containsString("Front2"))
    }

    @Test
    fun `undoDelete restores note when undo is requested immediately from delete result`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            var deletedCount: Int? = null
            launch {
                viewModel.flowOfDeleteResult.collect { count ->
                    deletedCount = count
                    viewModel.undoDelete()
                    cancel()
                }
            }
            advanceUntilIdle()

            viewModel.confirmDeleteNote()
            advanceUntilIdle()
            advanceRobolectricLooper()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat("Delete result should report one deleted note", deletedCount, equalTo(1))
            assertThat("Undo should restore the deleted note", col.noteCount(), equalTo(2))
            assertThat("Review should continue after undo", state.isFinished, equalTo(false))
        }

    @Test
    fun `undoDelete restores note after deleting the final card`() = runTest {
        addBasicNote("Front1", "Back1")

        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        var deletedCount: Int? = null
        launch {
            viewModel.flowOfDeleteResult.collect { count ->
                deletedCount = count
                viewModel.undoDelete()
                cancel()
            }
        }
        advanceUntilIdle()

        viewModel.confirmDeleteNote()
        advanceUntilIdle()
        advanceRobolectricLooper()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat("Delete result should report one deleted note", deletedCount, equalTo(1))
        assertThat(
            "Undo should restore the deleted note even after finishing review",
            col.noteCount(),
            equalTo(1)
        )
        assertThat("Review should resume after undo", state.isFinished, equalTo(false))
    }

    @Test
    fun `card actions are blocked when review is finished`() = runTest {
        // Create a ViewModel with no cards (will be finished immediately)
        val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
        advanceRobolectricLooper()

        val state = viewModel.state.first()
        assertThat("Review should be finished with no cards", state.isFinished, equalTo(true))

        // Try to show answer - should have no effect since isFinished is true
        viewModel.onEvent(ReviewerEvent.ShowAnswer)
        advanceRobolectricLooper()

        val stateAfter = viewModel.state.first()
        assertThat("State should remain finished", stateAfter.isFinished, equalTo(true))
        assertThat("Answer should not be shown", stateAfter.isAnswerShown, equalTo(false))
    }
}
