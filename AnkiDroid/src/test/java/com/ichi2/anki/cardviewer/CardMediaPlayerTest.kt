/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardUtils
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.cardviewer.SingleCardSide.BACK
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.TestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class CardMediaPlayerTest : JvmTest() {
    internal val tagPlayer: SoundTagPlayer = mockk<SoundTagPlayer>().also {
        every { it.stop() } just runs
    }
    internal val ttsPlayer: TtsPlayer = mockk<TtsPlayer>()
    internal val onMediaGroupCompleted: () -> Unit = mockk<() -> Unit>().also {
        every { it.invoke() } answers { }
    }

    @Test
    fun `no sounds fires completed listener`() = runSoundPlayerTest(
        answers = emptyList(),
        questions = emptyList(),
    ) {
        playAllAndWait(BACK)

        verifyNoSoundsPlayed()
    }

    @Test
    fun singleSoundSuccess() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("abc.mp3")),
    ) {
        playAllAndWait()

        coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("abc.mp3"), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `back is not played on front`() = runSoundPlayerTest(
        answers = listOf(SoundOrVideoTag("abc.mp3")),
    ) {
        playAllAndWait()

        verifyNoSoundsPlayed()
    }

    @Test
    fun `front is not played on back`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("abc.mp3")),
    ) {
        playAllAndWait(BACK)

        verifyNoSoundsPlayed()
    }

    @Test
    fun `replay - front may be played on back`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("front.mp3")),
        answers = listOf(SoundOrVideoTag("back.mp3")),
        replayQuestion = true,
    ) {
        replayAllAndWait(BACK)

        coVerifyOrder {
            tagPlayer.play(SoundOrVideoTag("front.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
        }
    }

    @Test
    fun `replay when replayQuestion is false`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("front.mp3")),
        answers = listOf(SoundOrVideoTag("back.mp3")),
        replayQuestion = false,
    ) {
        replayAllAndWait(BACK)

        coVerifyOrder {
            tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
        }
    }

    @Test
    fun `onMediaGroupCompleted is called after exception`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3")),
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws TestException("test")

        playAllAndWait()

        coVerify(exactly = 1) { tagPlayer.play(any(), any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `replay calls play twice`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

        playAllAndWait()

        coVerifySequence {
            tagPlayer.stop()
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
        }

        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `stop stops playback and calls completed listener`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws MediaException(STOP_MEDIA)

        playAllAndWait()

        coVerifySequence {
            tagPlayer.stop()
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
        }

        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `continue continues playback and calls completed listener`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws MediaException(CONTINUE_MEDIA)

        playAllAndWait()

        coVerifySequence {
            tagPlayer.stop()
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
        }

        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `retry playing single sound`() = runSoundPlayerTest {
        coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

        playOneAndWait(SoundOrVideoTag("a.mp3"))

        coVerifySequence {
            tagPlayer.stop()
            tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
        }
    }

    @Test
    fun `video respects autoplay off`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("video.mp4")),
        autoplay = false,
    ) {
        autoplayAllForSide(SingleCardSide.FRONT.toCardSide())
        playAvTagsJob?.join()

        coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `video respects autoplay on`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("video.mp4")),
        autoplay = true,
    ) {
        autoplayAllForSide(SingleCardSide.FRONT.toCardSide())
        playAvTagsJob?.join()

        coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("video.mp4"), any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `manual replay plays video even if autoplay is off`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("video.mp4")),
        autoplay = false,
    ) {
        replayAllAndWait(SingleCardSide.FRONT)

        coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("video.mp4"), any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `playAllForSide manual plays video even if autoplay is off`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("video.mp4")),
        autoplay = false,
    ) {
        playAllAndWait(SingleCardSide.FRONT, isAutomaticPlayback = false)

        coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("video.mp4"), any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    @Test
    fun `playOne hands back the job for that playback alone`() =
        runSoundPlayerTest {
            val firstPlaying = CompletableDeferred<Unit>()
            val secondPlaying = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("a.mp3"), any()) } coAnswers { firstPlaying.await() }
            coEvery { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) } coAnswers { secondPlaying.await() }

            val first = requireNotNull(playOne(SoundOrVideoTag("a.mp3")))
            val second = requireNotNull(playOne(SoundOrVideoTag("b.mp3")))

            // the second replay cancelled the first, so the first tap's own wait is over. awaitIdle() waits
            // on whatever is playing when it is called, which here is the playback that replaced this one
            first.join()
            assertThat("the replaced playback is over", first.isCompleted, equalTo(true))
            // otherwise the player reports nothing playing while the replay plays on
            assertThat("the replaced playback does not clear its successor", playAvTagsJob, sameInstance(second))
            assertThat("the one that replaced it plays on", second.isCompleted, equalTo(false))

            secondPlaying.complete(Unit)
            second.join()
        }

    @Test
    fun `a playback replaced before it ran still stops the one it replaced`() {
        val threads = ManualDispatcher()
        runSoundPlayerTest(playbackDispatcher = threads.dispatcher) {
            coEvery { tagPlayer.play(SoundOrVideoTag("a.mp3"), any()) } coAnswers { awaitCancellation() }
            coEvery { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) } just runs
            coEvery { tagPlayer.play(SoundOrVideoTag("c.mp3"), any()) } just runs

            val first = requireNotNull(playOne(SoundOrVideoTag("a.mp3")))
            threads.runAll()
            playOne(SoundOrVideoTag("b.mp3"))
            val last = requireNotNull(playOne(SoundOrVideoTag("c.mp3")))
            assertThat("both replays wait for a thread", threads.waiting, equalTo(2))
            // the last replay runs before the second has run at all, as a thread pool may order them. the second then
            // never stops the first, which keeps the playback lock, so the last has to stop every older playback
            threads.runNewest()
            threads.runAll()

            assertThat("the first playback was stopped", first.isCancelled, equalTo(true))
            coVerify(exactly = 0) { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) }
            coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("c.mp3"), any()) }
            assertThat("the last playback played to its end", last.isCompleted, equalTo(true))
        }
    }

    @Test
    fun `stop stops a playback whose replacement has not started`() {
        val threads = ManualDispatcher()
        runSoundPlayerTest(playbackDispatcher = threads.dispatcher) {
            coEvery { tagPlayer.play(SoundOrVideoTag("a.mp3"), any()) } coAnswers { awaitCancellation() }
            coEvery { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) } just runs

            val first = requireNotNull(playOne(SoundOrVideoTag("a.mp3")))
            threads.runAll()
            playOne(SoundOrVideoTag("b.mp3"))
            // the replay has no thread yet, so it has not stopped the first playback. stopping only the latest one
            // left the first to play on through its remaining sounds after the reviewer stopped media
            stop()

            assertThat("the first playback is stopped", first.isCancelled, equalTo(true))
            threads.runAll()
            coVerify(exactly = 0) { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) }
            assertThat("nothing plays", isPlaying, equalTo(false))
        }
    }

    @Test
    fun `a replay starts only once the playback it replaced has let go`() {
        val threads = ManualDispatcher()
        runSoundPlayerTest(playbackDispatcher = threads.dispatcher) {
            val firstLetGo = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("a.mp3"), any()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    // still stopping after it was cancelled, as a sound is while the media thread releases it
                    withContext(NonCancellable) { firstLetGo.await() }
                }
            }
            coEvery { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) } just runs

            val first = requireNotNull(playOne(SoundOrVideoTag("a.mp3")))
            threads.runAll()
            val second = requireNotNull(playOne(SoundOrVideoTag("b.mp3")))
            threads.runAll()

            assertThat("the first playback is stopping", first.isCancelled, equalTo(true))
            // SoundTagPlayer drives one MediaPlayer, so two playbacks at once would fight over it
            coVerify(exactly = 0) { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) }

            firstLetGo.complete(Unit)
            threads.runAll()
            coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("b.mp3"), any()) }
            assertThat("the replay played to its end", second.isCompleted, equalTo(true))
        }
    }

    private fun verifyNoSoundsPlayed() {
        coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    private fun ensureOnMediaGroupCompletedCalled() {
        verify(exactly = 1) { onMediaGroupCompleted.invoke() }
    }

    private suspend fun CardMediaPlayer.playAllAndWait(
        side: SingleCardSide = SingleCardSide.FRONT,
        isAutomaticPlayback: Boolean = false,
    ) {
        this.playAllForSide(side.toCardSide(), isAutomaticPlayback)
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.replayAllAndWait(side: SingleCardSide) {
        this.replayAll(side)
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.playOneAndWait(tag: AvTag) {
        playOne(tag)
        playAvTagsJob?.join()
    }

    suspend fun CardMediaPlayer.setup(
        questions: List<AvTag>,
        answers: List<AvTag>,
        replayQuestion: Boolean?,
        autoplay: Boolean?,
    ) {
        val card = addBasicNote().firstCard()
        mockkObject(card)

        every { card.renderOutput(any()) } answers {
            TemplateManager.TemplateRenderContext.TemplateRenderOutput(
                questionText = "",
                answerText = "",
                questionAvTags = questions,
                answerAvTags = answers,
                css = "",
            )
        }

        if (replayQuestion != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                replayq = replayQuestion
            }
        }
        if (autoplay != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                this.autoplay = autoplay
            }
        }

        this.loadCardAvTags(card)
    }
}

/**
 *
 * @param autoplay [CardSoundConfig.autoplay]
 * @param replayQuestion [CardSoundConfig.replayQuestion]
 */
fun CardMediaPlayerTest.runSoundPlayerTest(
    questions: List<AvTag> = emptyList(),
    answers: List<AvTag> = emptyList(),
    replayQuestion: Boolean? = null,
    autoplay: Boolean? = null,
    playbackDispatcher: CoroutineDispatcher = Dispatchers.IO,
    testBody: suspend CardMediaPlayer.() -> Unit,
) = runTest {
    val cardMediaPlayer = CardMediaPlayer(
        soundTagPlayer = tagPlayer,
        ttsPlayer = CompletableDeferred(ttsPlayer),
        mediaErrorListener = mockk(),
        playbackDispatcher = playbackDispatcher,
    )
    cardMediaPlayer.setOnMediaGroupCompletedListener(onMediaGroupCompleted)
    assertThat("can play sounds", cardMediaPlayer.isEnabled)
    cardMediaPlayer.setup(questions, answers, replayQuestion, autoplay)
    testBody(cardMediaPlayer)
}

/**
 * runs the player's playbacks only when a test says so, one task at a time on the test thread. on Dispatchers.IO the
 * threads decide which of two waiting playbacks runs first, and whether one has run at all, so a test there can only
 * hope to reach the order it is about
 */
private class ManualDispatcher {
    private val tasks = ConcurrentLinkedDeque<Runnable>()
    val dispatcher = Executor { tasks.addLast(it) }.asCoroutineDispatcher()
    val waiting get() = tasks.size

    /** runs the task dispatched last, ahead of every older one */
    fun runNewest() = tasks.removeLast().run()

    /** runs tasks oldest first, and the tasks they dispatch, until none is left */
    fun runAll() {
        while (true) {
            val task = tasks.pollFirst() ?: return
            task.run()
        }
    }
}
