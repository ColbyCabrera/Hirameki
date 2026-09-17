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

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.anki.servicelayer.NoteService
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import net.ankiweb.rsdroid.BackendException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFailsWith

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
    fun `initial state has empty counts`() =
        runTest {
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
    fun `card loads successfully when cards exist`() =
        runTest {
            // Add a card so there's something to review
            addBasicNote("Front", "Back")

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            val state = viewModel.state.first()
            assertThat(
                "Review should not be finished when cards exist",
                state.isFinished,
                equalTo(false),
            )
            assertThat("New count should be 1", state.newCount, equalTo(1))
        }

    @Test
    fun `video tags render as inline video in reviewer html`() =
        runTest {
            addBasicNote("Front [sound:test.mp4]", "Back")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.first()
            assertThat(
                "Video tags should render as inline video",
                state.questionHtml,
                containsString("<video"),
            )
            assertThat(
                "Video tags should include the file name for JS playback",
                state.questionHtml,
                containsString("data-file=\"test.mp4\""),
            )
            assertThat(
                "Video tags should report completion through the WebView",
                state.questionHtml,
                containsString("videoended:q:0"),
            )
            assertThat(
                "Video tags should not fall back to replay buttons",
                state.questionHtml,
                not(containsString("href=playsound:q:0")),
            )
        }

    @Test
    fun `audio tags remain replay buttons in reviewer html`() =
        runTest {
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
                containsString("href=\"playsound:q:0\""),
            )
            assertThat(
                "Audio tags should render the shared replay button wrapper",
                state.questionHtml,
                containsString("class=\"replay-button soundLink\""),
            )
            assertThat(
                "Audio tags should render the historical triangle play icon",
                state.questionHtml,
                containsString("class=\"play-action\""),
            )
            assertThat(
                "Audio tags should paint the icon from currentColor",
                state.questionHtml,
                containsString("fill=\"currentColor\""),
            )
            assertThat(
                "Audio tags should not hardcode icon colors in the markup",
                state.questionHtml,
                not(containsString("fill=\"black\"")),
            )
            assertThat(
                "Audio tags should not render inline video",
                state.questionHtml,
                not(containsString("<video")),
            )
        }

    @Test
    fun `video autoplay emits javascript when autoplay is enabled`() =
        runTest {
            addBasicNote("Front [sound:test.mp4]", "Back")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            viewModel.evalCommand.test {
                assertThat(
                    "No JavaScript should be queued before the scheduler runs",
                    awaitItem().isEmpty(),
                    equalTo(true),
                )
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()

                val script = awaitItem().single().script
                assertThat(
                    "Autoplay should wait until the DOM is ready",
                    script,
                    containsString("document.readyState"),
                )
                assertThat(
                    "Autoplay should target the inline video element",
                    script,
                    containsString("video.play();"),
                )
                assertThat(
                    "Autoplay should target the card video file",
                    script,
                    containsString("test.mp4"),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `typed answer is updated via event`() =
        runTest {
            addBasicNote()
            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.OnTypedAnswerChanged("test answer"))

            val state = viewModel.state.first()
            assertThat("Typed answer should be updated", state.typedAnswer, equalTo("test answer"))
        }

    @Test
    fun `whiteboard state is updated via event`() =
        runTest {
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
    fun `initial card load sets cardDisplayIndex to 1`() =
        runTest {
            addBasicNote("Front 1", "Back 1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.first()
            assertThat(
                "Initial card load should set cardDisplayIndex to 1",
                state.cardDisplayIndex,
                equalTo(1L),
            )
        }

    @Test
    fun `answering a card increments cardDisplayIndex`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Initial cardDisplayIndex is 1",
                viewModel.state.first().cardDisplayIndex,
                equalTo(1L),
            )

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.EASY))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Answering card increments cardDisplayIndex to 2",
                viewModel.state.first().cardDisplayIndex,
                equalTo(2L),
            )
        }

    @Test
    fun `rating a card before its answer is shown is ignored`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // a drag that began on the previous card can land after this one loads
            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.EASY))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.first()
            assertThat("the unseen card is not graded", state.cardDisplayIndex, equalTo(1L))
            assertThat("no new card was answered", state.newCount, equalTo(2))
        }

    @Test
    fun `a rating is recorded while the question's playback is still letting go`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            addBasicNote("Front 2", "Back 2")
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { autoplay = true }
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            // the question's playback is held in its last step: a stand-in for a sound that is slow to stop, such as
            // one the media thread is still preparing when the card is turned over
            val questionPlaying = CountDownLatch(1)
            val letGo = CountDownLatch(1)
            val held = AtomicBoolean(false)
            viewModel.cardMediaPlayer.setOnMediaGroupCompletedListener {
                if (held.compareAndSet(false, true)) {
                    questionPlaying.countDown()
                    letGo.await()
                }
            }
            try {
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                assertThat("the question is playing", questionPlaying.await(10, TimeUnit.SECONDS), equalTo(true))

                viewModel.onEvent(ReviewerEvent.ShowAnswer)
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.EASY))
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()

                // the reveal used to wait for the question's playback to stop, so its card action was still running
                // with the answer already up, and the rating was dropped
                val state = viewModel.state.value
                assertThat("the rating is recorded", col.getCard(firstCardId).reps, equalTo(1))
                assertThat("the next card is shown", state.cardDisplayIndex, equalTo(2L))
                assertThat("with its answer hidden", state.isAnswerShown, equalTo(false))
            } finally {
                letGo.countDown()
            }
        }

    @Test
    fun `a reveal and a rating complete while every thread the player plays on is busy`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            addBasicNote("Front 2", "Back 2")
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { autoplay = true }
            // CardMediaPlayer plays on Dispatchers.IO, which runs this many blocking tasks at once by default
            // (kotlinx.coroutines: 64, or the core count if higher). a stalled machine starves it the same way, and
            // that is how the full test run lost ratings: the question's playback had not even started
            val slots = maxOf(64, Runtime.getRuntime().availableProcessors())
            val allBusy = CountDownLatch(slots)
            val letGo = CountDownLatch(1)
            try {
                repeat(slots) {
                    Dispatchers.IO.asExecutor().execute {
                        allBusy.countDown()
                        letGo.await()
                    }
                }
                assertThat("every player thread is busy", allBusy.await(10, TimeUnit.SECONDS), equalTo(true))

                val viewModel =
                    ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                viewModel.onEvent(ReviewerEvent.ShowAnswer)
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.EASY))
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()

                val state = viewModel.state.value
                assertThat("the rating is recorded", col.getCard(firstCardId).reps, equalTo(1))
                assertThat("the next card is shown", state.cardDisplayIndex, equalTo(2L))
            } finally {
                letGo.countDown()
            }
        }

    @Test
    fun `a rating made while the reveal is still finishing is recorded`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            addBasicNote("Front 2", "Back 2")
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            sendInsideTheReveal(viewModel, ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // the reveal's card action was still running, and launchCardAction dropped the rating without a word
            val state = viewModel.state.value
            assertThat("the rating is recorded", col.getCard(firstCardId).reps, equalTo(1))
            assertThat("the next card is shown", state.cardDisplayIndex, equalTo(2L))
            assertThat("with its answer hidden", state.isAnswerShown, equalTo(false))
        }

    @Test
    fun `a second rating waiting behind the first does not grade the next card`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            val secondCardId = addBasicNote("Front 2", "Back 2").firstCard().id
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            // a double tap: both ratings wait, and by the time the second runs the first has loaded the next card
            sendInsideTheReveal(
                viewModel,
                ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD),
                ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD),
            )

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.value
            assertThat("the rated card is graded once", col.getCard(firstCardId).reps, equalTo(1))
            assertThat("the next card is not graded unseen", col.getCard(secondCardId).reps, equalTo(0))
            assertThat("the next card is shown", state.cardDisplayIndex, equalTo(2L))
            assertThat("with its answer hidden", state.isAnswerShown, equalTo(false))
        }

    @Test
    fun `a rating waiting behind the reveal is not recorded once the answer is hidden again`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            addBasicNote("Front 2", "Back 2")
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            // hiding the answer is not a card action, so it lands while the rating still waits for the reveal
            sendInsideTheReveal(
                viewModel,
                ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD),
                ReviewerEvent.UnanswerCard,
            )

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.value
            assertThat("a card whose answer was hidden again is not graded", col.getCard(firstCardId).reps, equalTo(0))
            assertThat("the card stays on screen", state.cardDisplayIndex, equalTo(1L))
            assertThat("with its answer hidden", state.isAnswerShown, equalTo(false))
        }

    @Test
    fun `a reload that finds another card leading the queue shows that card`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            val secondCardId = addBasicNote("Front 2", "Back 2").firstCard().id
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            assertThat("the first card is shown", viewModel.state.value.cardId, equalTo(firstCardId))

            // a due date set from the reviewer's menu moves the card on screen out of today's queue, then reloads it
            col.sched.setDueDate(listOf(firstCardId), SetDueDateDays("5"))
            viewModel.onEvent(ReviewerEvent.SetDueDateConfirmed(1))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // the reload kept the first card on screen with the second card's queue: its answer showed the second
            // card's intervals, and a rating graded the second card unseen
            val state = viewModel.state.value
            assertThat("the card leading the queue is shown", state.cardId, equalTo(secondCardId))
            assertThat("as a new card on screen", state.cardDisplayIndex, equalTo(2L))
        }

    @Test
    fun `a rating is not recorded for a card another load has put at the top of the queue`() =
        runTest {
            val firstCardId = addBasicNote("Front 1", "Back 1").firstCard().id
            val secondCardId = addBasicNote("Front 2", "Back 2").firstCard().id
            // the player is mocked below; with autoplay on, a playback on another thread calls into it while the mock
            // is set up or taken down, and mockk fails that call
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { autoplay = false }
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // the screen loads a card outside the view model's card actions after a collection op it did not run
            // itself (Reviewer.updateCurrentCard). this load is held after it has stored the second card's queue
            // and before it publishes that card, while the card's sounds load
            col.sched.suspendCards(listOf(firstCardId))
            val player = viewModel.cardMediaPlayer
            val releaseLoad = CompletableDeferred<Unit>()
            var loadHeld = false
            mockkObject(player)
            try {
                coEvery { player.loadCardAvTags(any()) } coAnswers {
                    if (!loadHeld) {
                        loadHeld = true
                        releaseLoad.await()
                    }
                    callOriginal()
                }
                val outsideLoad = launch(StandardTestDispatcher(testScheduler)) { viewModel.loadCardSuspend() }
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                assertThat("the load is held", loadHeld, equalTo(true))
                assertThat("the first card is still on screen", viewModel.state.value.cardId, equalTo(firstCardId))

                viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()

                // every check on the card on screen passes, but the answer grades the queue's top card
                assertThat("the second card is not graded unseen", col.getCard(secondCardId).reps, equalTo(0))
                assertThat("the card that would be graded is shown", viewModel.state.value.cardId, equalTo(secondCardId))

                releaseLoad.complete(Unit)
                testScheduler.advanceUntilIdle()
                advanceRobolectricLooper()
                assertThat("the held load finishes", outsideLoad.isCompleted, equalTo(true))
            } finally {
                unmockkObject(player)
            }
        }

    @Test
    fun `answering same card in 1-card deck increments cardDisplayIndex`() =
        runTest {
            addBasicNote("Front 1", "Back 1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Initial cardDisplayIndex is 1",
                viewModel.state.first().cardDisplayIndex,
                equalTo(1L),
            )

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // Rating AGAIN keeps the same card in the learning queue
            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.AGAIN))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Repeating same card increments cardDisplayIndex to 2",
                viewModel.state.first().cardDisplayIndex,
                equalTo(2L),
            )
        }

    @Test
    fun `reloading card retains cardDisplayIndex`() =
        runTest {
            addBasicNote("Front 1", "Back 1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Initial cardDisplayIndex is 1",
                viewModel.state.first().cardDisplayIndex,
                equalTo(1L),
            )

            viewModel.onEvent(ReviewerEvent.ReloadCard)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat(
                "Reloading same card keeps cardDisplayIndex at 1",
                viewModel.state.first().cardDisplayIndex,
                equalTo(1L),
            )
        }

    @Test
    fun `voice playback state is updated via event`() =
        runTest {
            addBasicNote()
            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.OnVoicePlaybackStateChanged(true))

            val state = viewModel.state.first()
            assertThat("Voice playback should be enabled", state.isVoicePlaybackEnabled, equalTo(true))
        }

    @Test
    fun `toggleMark updates reviewer state after note is marked`() =
        runTest {
            val note = addBasicNote("Front", "Back")
            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            assertThat("Note should start unmarked", NoteService.isMarked(note), equalTo(false))
            assertThat("State should start unmarked", viewModel.state.value.isMarked, equalTo(false))

            viewModel.onEvent(ReviewerEvent.ToggleMark)
            advanceRobolectricLooper()

            val reloadedNote = col.getNote(note.id)
            assertThat(
                "Note should be marked after toggle",
                NoteService.isMarked(reloadedNote),
                equalTo(true),
            )
            assertThat(
                "State should reflect marked note",
                viewModel.state.value.isMarked,
                equalTo(true),
            )
        }

    @Test
    fun `toggleMark uses canonical mark state instead of flipping reviewer state`() =
        runTest {
            val note = addBasicNote("Front", "Back")
            NoteService.toggleMark(note)

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            assertThat("State should start marked", viewModel.state.value.isMarked, equalTo(true))

            mockkObject(NoteService)
            try {
                coEvery {
                    NoteService.toggleMark(note = any(), handler = viewModel)
                } answers { }
                coEvery { NoteService.isMarked(note = any()) } returns true

                viewModel.onEvent(ReviewerEvent.ToggleMark)
                advanceRobolectricLooper()

                assertThat(
                    "State should follow the canonical note mark state",
                    viewModel.state.value.isMarked,
                    equalTo(true),
                )
            } finally {
                unmockkObject(NoteService)
            }
        }

    @Test
    fun `toggleMark leaves reviewer state unchanged when note toggle fails`() =
        runTest {
            val note = addBasicNote("Front", "Back")
            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            mockkObject(NoteService)
            try {
                coEvery {
                    NoteService.toggleMark(note = any(), handler = viewModel)
                } throws IllegalStateException("toggle failed")

                viewModel.onEvent(ReviewerEvent.ToggleMark)
                advanceRobolectricLooper()

                val reloadedNote = col.getNote(note.id)
                assertThat(
                    "Note should remain unmarked after failure",
                    NoteService.isMarked(reloadedNote),
                    equalTo(false),
                )
                assertThat(
                    "State should remain unchanged after failure",
                    viewModel.state.value.isMarked,
                    equalTo(false),
                )
            } finally {
                unmockkObject(NoteService)
            }
        }

    @Test
    fun `showAnswer updates state correctly`() =
        runTest {
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
                "Answer should be shown after ShowAnswer event",
                state.isAnswerShown,
                equalTo(true),
            )
            assertThat(
                "Next times should be populated",
                state.nextTimes.any { it.isNotEmpty() },
                equalTo(true),
            )
        }

    @Test
    fun `rateCard loads next card`() =
        runTest {
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
    fun `confirmDeleteNote deletes current note and loads next card`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            val initialState = viewModel.state.first()
            assertThat(
                "Initial card should be loaded before deleting",
                initialState.questionHtml,
                containsString("Front1"),
            )
            assertThat("Should have 2 new cards before deleting", initialState.newCount, equalTo(2))

            viewModel.confirmDeleteNote()
            advanceRobolectricLooper()

            val state = viewModel.state.first()
            assertThat("Reviewer should continue with the next card", state.isFinished, equalTo(false))
            assertThat(
                "New count should decrease after deleting the current note",
                state.newCount,
                equalTo(1),
            )
            assertThat("The next card should be loaded", state.questionHtml, containsString("Front2"))
        }

    @Test
    fun `undoDelete restores note when undo is requested immediately from delete result`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
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
    fun `undoDelete restores note after deleting the final card`() =
        runTest {
            addBasicNote("Front1", "Back1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
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
                equalTo(1),
            )
            assertThat("Review should resume after undo", state.isFinished, equalTo(false))
        }

    @Test
    fun `card actions are blocked when review is finished`() =
        runTest {
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

    @Test
    fun `a replay tap with no sound to play still reports that it finished`() =
        runTest {
            addBasicNote("Front", "Back")
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.PlayAudio("q", 0))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // the page dims a replay button the moment it is tapped and only this count restores it, so a
            // tap that finds no tag to play must report too, or the button stays dimmed
            assertThat("the tap reported", viewModel.state.value.replayFinished, equalTo(1))
        }

    @Test
    fun `a replay tap that another tap superseded does not report`() =
        runTest {
            addBasicNote("Front", "Back")
            // taps are queued rather than run where they are made: on a device the first is still looking up
            // its tag when the second arrives, while the unconfined main of a test runs each one to its end
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // back to back, so the second tap is counted while the first is still looking up its tag
            viewModel.onEvent(ReviewerEvent.PlayAudio("q", 0))
            viewModel.onEvent(ReviewerEvent.PlayAudio("q", 0))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // an older playback reporting would restore the button while the newer sound is still playing
            assertThat("only the latest tap reported", viewModel.state.value.replayFinished, equalTo(1))
        }

    @Test
    fun `the state names the card whose answer and typed text it holds`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // what android saves is built from this state alone: the card's id is written in the same update
            // as its unrevealed answer and empty typed text, so a save during a load cannot pair one card's
            // id with the last card's revealed answer
            assertThat("the loaded card is named", viewModel.state.value.cardId, equalTo(viewModel.currentCard?.id))

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val next = viewModel.state.value
            assertThat("the card that followed is named", next.cardId, equalTo(viewModel.currentCard?.id))
            assertThat("with its own answer hidden", next.isAnswerShown, equalTo(false))
        }

    @Test
    fun `an empty queue leaves no card named in the state`() =
        runTest {
            addBasicNote("Front", "Back")
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            assertThat("a card is on screen", viewModel.state.value.cardId, notNullValue())

            viewModel.confirmDeleteNote()
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.value
            assertThat("the review is over", state.isFinished, equalTo(true))
            // otherwise android would save the card that is gone as the one on screen
            assertThat("no card is named", state.cardId, nullValue())
        }

    @Test
    fun `a revealed answer and typed text come back after process death`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val first = startHost()
            val before = reviewerIn(first)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            before.onEvent(ReviewerEvent.OnTypedAnswerChanged("typed"))
            before.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            val shownCardId = before.currentCard!!.id

            val after = reviewerIn(processDeath(first))
            val autoplays = recordAutoplays(after)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = after.state.value
            assertThat("the same card is shown", after.currentCard?.id, equalTo(shownCardId))
            assertThat("the answer is still revealed", state.isAnswerShown, equalTo(true))
            assertThat("answer buttons are labelled", state.nextTimes.any { it.isNotEmpty() }, equalTo(true))
            assertThat("the typed answer is kept", state.typedAnswer, equalTo("typed"))
            // a question autoplay first would start its audio, then the answer's would cut it off
            assertThat("only the answer side autoplays", autoplays, equalTo(listOf(true)))
        }

    @Test
    fun `the question side is kept when another card leads the queue after process death`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val first = startHost()
            val before = reviewerIn(first)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            before.onEvent(ReviewerEvent.OnTypedAnswerChanged("typed"))
            before.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            val savedCardId = before.currentCard!!.id
            val second = processDeath(first)

            // the saved card leaves the top of the queue while the app is away
            col.sched.answerCard(col.sched.currentQueueState()!!, anki.scheduler.CardAnswer.Rating.EASY)

            val after = reviewerIn(second)
            val autoplays = recordAutoplays(after)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = after.state.value
            assertThat("another card leads", after.currentCard?.id, not(equalTo(savedCardId)))
            assertThat("its answer is not revealed", state.isAnswerShown, equalTo(false))
            assertThat("the other card's typed answer is not applied", state.typedAnswer, equalTo(""))
            // the load held its question autoplay back for the restore, which plays it instead
            assertThat("the question side autoplays once", autoplays, equalTo(listOf(false)))
        }

    @Test
    fun `leaving again while a restored reviewer is still loading keeps what was on screen`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val first = startHost()
            val before = reviewerIn(first)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            before.onEvent(ReviewerEvent.OnTypedAnswerChanged("typed"))
            before.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            val shownCardId = before.currentCard!!.id

            // android saves the rebuilt reviewer before its first load has run (the new process is still
            // opening the collection), then kills that process too
            val second = processDeath(first)
            reviewerIn(second)
            val after = reviewerIn(processDeath(second))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = after.state.value
            assertThat("the same card is shown", after.currentCard?.id, equalTo(shownCardId))
            assertThat("the answer is still revealed", state.isAnswerShown, equalTo(true))
            assertThat("the typed answer is kept", state.typedAnswer, equalTo("typed"))
        }

    @Test
    fun `a rating refused for a stale queue reloads instead of crashing`() =
        runTest {
            val cardId = addBasicNote("Front 1", "Back 1").firstCard().id

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            val stale = viewModel.queueStateFlow.value!!

            // the card changes behind the reviewer's back, like a day cutoff passing while the app is
            // in the background: the reviewer's queue state no longer matches
            col.sched.answerCard(col.sched.currentQueueState()!!, anki.scheduler.CardAnswer.Rating.GOOD)
            val refusal =
                assertFailsWith<BackendException> {
                    col.sched.answerCard(stale, anki.scheduler.CardAnswer.Rating.GOOD)
                }
            assertThat("the text isStaleQueueAnswer matches", refusal.message, containsString("card was modified"))

            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.value
            assertThat("the refused rating is not recorded", col.getCard(cardId).reps, equalTo(1))
            assertThat("the queue's current card was reloaded", state.cardDisplayIndex, equalTo(2L))
            assertThat("the reloaded card shows its question", state.isAnswerShown, equalTo(false))
        }

    @Test
    fun `a rating refused because another card leads the queue reloads instead of crashing`() =
        runTest {
            addBasicNote("Front 1", "Back 1")
            addBasicNote("Front 2", "Back 2")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                ReviewerViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            val stale = viewModel.queueStateFlow.value!!
            val shownCardId = stale.topCard.id

            // the shown card itself is unchanged, but another card now leads the backend's queue (in real
            // use, a learning card that fell due while the app was away). suspending the shown card and
            // rebuilding the queue gets there without changing its scheduling state
            col.sched.suspendCards(listOf(shownCardId))
            col.sched.currentQueueState()
            val refusal =
                assertFailsWith<BackendException> {
                    col.sched.answerCard(stale, anki.scheduler.CardAnswer.Rating.GOOD)
                }
            assertThat("the text isStaleQueueAnswer matches", refusal.message, containsString("not at top of queue"))
            // the refusal also dropped the backend's cached queue, and without one the backend accepts the
            // answer; rebuild it so the other card leads again when the reviewer rates
            col.sched.currentQueueState()

            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            val state = viewModel.state.value
            assertThat("the refused rating is not recorded", col.getCard(shownCardId).reps, equalTo(0))
            assertThat("the card now leading is shown", viewModel.currentCard?.id, not(equalTo(shownCardId)))
            assertThat("it shows its question", state.isAnswerShown, equalTo(false))
        }

    // col.updateNote returns OpChanges the test has no use for, as RobolectricTest.updateOp does
    @SuppressLint("CheckResult")
    @Test
    fun `deck tags hold every tag of the deck, and only that deck's`() =
        runTest {
            val otherDeck = addDeck("Other")
            addBasicNote("Other front", "Other back").also { note ->
                note.addTag("gamma")
                col.updateNote(note)
                col.setDeck(note.cardIds(col), otherDeck)
            }

            val reviewedDeck = addDeck("Reviewed", setAsSelected = true)
            for ((front, tag) in listOf("First" to "alpha", "Second" to "beta")) {
                addBasicNote(front, "Back").also { note ->
                    note.addTag(tag)
                    col.updateNote(note)
                    col.setDeck(note.cardIds(col), reviewedDeck)
                }
            }

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            viewModel.onEvent(ReviewerEvent.EditTags)
            advanceRobolectricLooper()

            assertThat(
                "the tags of every note in the deck, read in one query rather than a capped walk",
                viewModel.deckTags.value,
                equalTo(setOf("alpha", "beta")),
            )
        }

    @Test
    fun `the card page is served by the reviewer's own server`() =
        runTest {
            addBasicNote("Front", "Back")

            val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())
            advanceRobolectricLooper()

            val baseUrl = viewModel.state.value.baseUrl
            assertThat("the card loads from the loopback server", baseUrl, containsString("http://127.0.0.1:"))
            assertThat(
                "a bound port: a server that never started listens on -1, and the card would stay blank",
                baseUrl,
                not(containsString(":-1/")),
            )
        }

    @Test
    fun `a card page that could not be served is never graded`() =
        runTest {
            val cardId = addBasicNote("Front", "Back").cardIds(col).single()

            val viewModel =
                ReviewerViewModel(
                    ApplicationProvider.getApplicationContext(),
                    StandardTestDispatcher(testScheduler),
                    serverFactory = { handler -> UnstartableServer(handler) },
                )
            val effects = mutableListOf<ReviewerEffect>()
            // subscribed before the load runs: effects are not replayed to a later collector
            val collectingEffects =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effect.collect { effects += it }
                }
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat("no url for the card to load from", viewModel.state.value.baseUrl, equalTo(""))
            assertThat("the failure is said out loud", effects.any { it is ReviewerEffect.ShowSnackbar }, equalTo(true))

            viewModel.onEvent(ReviewerEvent.ShowAnswer)
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            viewModel.onEvent(ReviewerEvent.RateCard(anki.scheduler.CardAnswer.Rating.GOOD))
            testScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            // with no server the webview draws nothing, or still the card before this one, so a rating would
            // schedule a card the user never saw
            assertThat("the unseen card keeps its scheduling", col.getCard(cardId).reps, equalTo(0))
            collectingEffects.cancel()
        }

    /**
     * whether the answer was shown at each autoplay of [viewModel]'s card from now on. deck autoplay is
     * turned off, so the player reports each autoplay at once instead of starting playback
     */
    private fun recordAutoplays(viewModel: ReviewerViewModel): List<Boolean> {
        updateDeckConfig(Consts.DEFAULT_DECK_ID) { autoplay = false }
        val answerShownAtAutoplay = mutableListOf<Boolean>()
        viewModel.cardMediaPlayer.setOnMediaGroupCompletedListener {
            answerShownAtAutoplay += viewModel.state.value.isAnswerShown
        }
        return answerShownAtAutoplay
    }

    /**
     * sends [events] to [viewModel] from inside the reveal's card action, just after it has put the answer up. deck
     * autoplay is turned off, so the player reports the reveal's autoplay at once, within that action
     */
    private fun sendInsideTheReveal(
        viewModel: ReviewerViewModel,
        vararg events: ReviewerEvent,
    ) {
        updateDeckConfig(Consts.DEFAULT_DECK_ID) { autoplay = false }
        var sent = false
        viewModel.cardMediaPlayer.setOnMediaGroupCompletedListener {
            if (viewModel.state.value.isAnswerShown && !sent) {
                sent = true
                events.forEach { viewModel.onEvent(it) }
            }
        }
    }

    /** an activity hosting the view model, as Reviewer does; [savedState] rebuilds one after process death */
    private fun startHost(savedState: Bundle? = null): ActivityController<ComponentActivity> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        saveControllerForCleanup(controller)
        return if (savedState == null) controller.setup() else controller.setup(savedState)
    }

    /** the view model built the way Reviewer builds it: its factory, given the host's saved state */
    private fun TestScope.reviewerIn(host: ActivityController<ComponentActivity>): ReviewerViewModel =
        ViewModelProvider(
            host.get(),
            ReviewerViewModel.factory(StandardTestDispatcher(testScheduler)),
        )[ReviewerViewModel::class.java]

    /**
     * android killing the backgrounded app: the host saves its state and dies with its view model, then a
     * new host is created from that state after a trip through a parcel, as in a new process
     */
    private fun processDeath(host: ActivityController<ComponentActivity>): ActivityController<ComponentActivity> {
        val saved = Bundle()
        host
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(saved)
            parcel.setDataPosition(0)
            return startHost(requireNotNull(parcel.readBundle(javaClass.classLoader)))
        } finally {
            parcel.recycle()
        }
    }
}

/**
 * a card server whose port never binds. a real one fails this way only when no port is free or the socket is
 * refused, which a test cannot provoke, and [ReviewerViewModel] has to survive it rather than crash.
 */
private class UnstartableServer(
    postHandler: PostRequestHandler,
) : AnkiServer(postHandler) {
    override fun start() {
        throw IOException("no port to bind")
    }
}
