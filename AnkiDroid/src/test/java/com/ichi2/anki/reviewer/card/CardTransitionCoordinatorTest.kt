// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import androidx.compose.ui.graphics.ImageBitmap
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardTransitionCoordinatorTest {
    private val stage = FakeStage()
    private var isCrossfadeEnabled = true
    private val coordinator = CardTransitionCoordinator(stage, isCrossfadeEnabled = { isCrossfadeEnabled })

    private val first = display(1)
    private val second = display(2)
    private val third = display(3)

    @Test
    fun `the first card is shown without an overlay`() =
        runTransitions {
            coordinator.setTarget(first)
            runCurrent()

            assertThat(stage.events, contains("show 1"))
            assertThat(coordinator.overlay, nullValue())
        }

    @Test
    fun `the frame is captured and on screen before the card is swapped`() =
        runTransitions {
            startFromShownCard()

            coordinator.setTarget(second)
            runCurrent()

            assertThat(stage.events, contains("capture", "overlay drawn", "show 2"))
            assertThat(coordinator.overlay?.frame, sameInstance(stage.frame))
            assertThat(coordinator.overlay?.opacity, equalTo(1f))
        }

    @Test
    fun `the overlay fades only once the new card is shown`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            runCurrent()
            assertThat("still waiting", stage.events.contains("fade"), equalTo(false))

            stage.shown.value = second
            runCurrent()

            assertThat(stage.events.last(), equalTo("fade"))
        }

    @Test
    fun `the overlay is removed when the fade ends`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            stage.shown.value = second
            runCurrent()

            stage.sendFadeProgress(0.4f)
            runCurrent()
            assertThat(coordinator.overlay?.opacity, equalTo(0.4f))
            stage.finishFade()
            runCurrent()

            assertThat(coordinator.overlay, nullValue())
        }

    @Test
    fun `a card that never reports being shown still fades`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            runCurrent()

            advanceTimeBy(1499)
            runCurrent()
            assertThat(stage.events.contains("fade"), equalTo(false))
            advanceTimeBy(2)
            runCurrent()

            assertThat(stage.events.last(), equalTo("fade"))
        }

    @Test
    fun `a new card while swapping reuses the overlay and waits for the latest card`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            runCurrent()

            coordinator.setTarget(third)
            runCurrent()
            stage.shown.value = second
            runCurrent()
            assertThat("a stale report does not start the fade", stage.events.contains("fade"), equalTo(false))

            stage.shown.value = third
            runCurrent()

            assertThat(stage.events, contains("capture", "overlay drawn", "show 2", "overlay drawn", "show 3", "fade"))
        }

    @Test
    fun `a new card during capture shows only the latest`() =
        runTransitions {
            startFromShownCard()
            stage.pendingCapture = CompletableDeferred()
            coordinator.setTarget(second)
            runCurrent()

            coordinator.setTarget(third)
            stage.pendingCapture = null
            runCurrent()

            assertThat(stage.events, contains("capture", "capture", "overlay drawn", "show 3"))
        }

    @Test
    fun `a new card during the fade starts again from a capture of what is on screen`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            stage.shown.value = second
            runCurrent()
            stage.sendFadeProgress(0.5f)
            runCurrent()
            val midFadeFrame = mockk<ImageBitmap>()
            stage.frame = midFadeFrame

            coordinator.setTarget(third)
            runCurrent()

            assertThat(coordinator.overlay?.frame, sameInstance(midFadeFrame))
            assertThat(coordinator.overlay?.opacity, equalTo(1f))
            assertThat(stage.events.takeLast(3), contains("capture", "overlay drawn", "show 3"))
        }

    @Test
    fun `a failed capture swaps without a fade`() =
        runTransitions {
            startFromShownCard()
            stage.frame = null

            coordinator.setTarget(second)
            runCurrent()

            assertThat(stage.events, contains("capture", "show 2"))
            assertThat(coordinator.overlay, nullValue())
        }

    @Test
    fun `a failed capture during the fade removes the overlay`() =
        runTransitions {
            startFromShownCard()
            coordinator.setTarget(second)
            stage.shown.value = second
            runCurrent()
            stage.sendFadeProgress(0.5f)
            runCurrent()
            stage.frame = null

            coordinator.setTarget(third)
            runCurrent()

            assertThat(coordinator.overlay, nullValue())
            assertThat(stage.events.last(), equalTo("show 3"))
        }

    @Test
    fun `with the crossfade off, cards swap without a capture`() =
        runTransitions {
            startFromShownCard()
            isCrossfadeEnabled = false

            coordinator.setTarget(second)
            runCurrent()

            assertThat(stage.events, contains("show 2"))
        }

    private fun runTransitions(test: suspend TestScope.() -> Unit) =
        runTest {
            backgroundScope.launch { coordinator.run() }
            runCurrent()
            test()
        }

    private fun TestScope.startFromShownCard() {
        stage.hasShownCard = true
        coordinator.setTarget(first)
        stage.frame = mockk()
        runCurrent()
        stage.shown.value = first
        runCurrent()
        stage.finishFade()
        runCurrent()
        stage.events.clear()
        stage.restartFade()
    }

    private fun display(index: Long) = CardDisplay(index, false, "Q$index", "A$index", "card", "http://127.0.0.1:1/")

    private class FakeStage : CardTransitionStage {
        val events = mutableListOf<String>()
        override var hasShownCard = false
        var frame: ImageBitmap? = null
        var pendingCapture: CompletableDeferred<Unit>? = null
        val shown = MutableStateFlow<CardDisplay?>(null)
        private var fadeProgress = Channel<Float>(Channel.UNLIMITED)

        fun sendFadeProgress(opacity: Float) {
            fadeProgress.trySend(opacity)
        }

        fun finishFade() {
            fadeProgress.close()
        }

        fun restartFade() {
            fadeProgress = Channel(Channel.UNLIMITED)
        }

        override suspend fun captureFrame(): ImageBitmap? {
            events += "capture"
            pendingCapture?.await()
            return frame
        }

        override suspend fun awaitOverlayDrawn() {
            events += "overlay drawn"
        }

        override fun show(display: CardDisplay) {
            events += "show ${display.cardDisplayIndex}"
        }

        override suspend fun awaitShown(display: CardDisplay) {
            shown.first { it == display }
        }

        override suspend fun fade(onOpacity: (Float) -> Unit) {
            events += "fade"
            for (opacity in fadeProgress) onOpacity(opacity)
        }
    }
}
