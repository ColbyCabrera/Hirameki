// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.workarounds.SafeWebViewLayout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.instanceOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class CardGestureListenerTest : RobolectricTest() {
    private var display: CardDisplay? = card(index = 1)
    private val probedPoints = mutableListOf<Pair<Float, Float>>()
    private val unansweredProbes = ArrayDeque<(CardTouchProbe) -> Unit>()
    private val gestures = mutableListOf<CardTouchGesture>()
    private lateinit var listener: CardGestureListener

    @Before
    fun createListener() {
        val cardLayout = SafeWebViewLayout(targetContext).apply { layout(0, 0, WIDTH, HEIGHT) }
        listener =
            CardGestureListener(
                cardLayout,
                currentDisplay = { display },
                probeTouch = { fractionX, fractionY, onProbed ->
                    probedPoints += fractionX to fractionY
                    unansweredProbes += onProbed
                },
                onGesture = { gestures += it },
            )
    }

    @Test
    fun `the page is asked about the point where the touch started`() {
        tap(x = 250f, y = 1500f)

        assertThat(probedPoints, contains(0.25f to 0.75f))
    }

    @Test
    fun `a tap waits for the page to say what it landed on`() {
        tap()
        awaitTapConfirmation()
        assertThat(gestures, empty())

        answerProbe(PLAIN_CONTENT)

        assertThat(gestures, contains<CardTouchGesture>(CardTouchGesture.Tap(CENTRE_X, CENTRE_Y, WIDTH, HEIGHT)))
    }

    @Test
    fun `a tap on an interactive element belongs to the card`() {
        tap()
        answerProbe(ON_INTERACTIVE_ELEMENT)
        awaitTapConfirmation()

        assertThat(gestures, empty())
    }

    @Test
    fun `a tap while text is selected only ends the selection`() {
        tap()
        answerProbe(TEXT_SELECTED)
        awaitTapConfirmation()

        assertThat(gestures, empty())
    }

    @Test
    fun `a tap is dropped once the card has changed`() {
        tap()
        answerProbe(PLAIN_CONTENT)
        display = card(index = 2)
        awaitTapConfirmation()

        assertThat(gestures, empty())
    }

    @Test
    fun `two taps on plain content are a double tap`() {
        doubleTap()
        answerProbe(PLAIN_CONTENT)
        answerProbe(PLAIN_CONTENT)
        awaitTapConfirmation()

        assertThat(gestures, contains<CardTouchGesture>(CardTouchGesture.DoubleTap))
    }

    @Test
    fun `a double tap whose first tap was on an interactive element belongs to the card`() {
        doubleTap()
        answerProbe(ON_INTERACTIVE_ELEMENT)
        answerProbe(PLAIN_CONTENT)
        awaitTapConfirmation()

        assertThat(gestures, empty())
    }

    @Test
    fun `a double tap whose second tap was on an interactive element belongs to the card`() {
        doubleTap()
        answerProbe(PLAIN_CONTENT)
        answerProbe(ON_INTERACTIVE_ELEMENT)
        awaitTapConfirmation()

        assertThat(gestures, empty())
    }

    @Test
    fun `a late answer about an earlier touch does not affect the next one`() {
        tap(x = 200f, y = 400f)
        awaitTapConfirmation()
        tap(x = 800f, y = 1600f)
        awaitTapConfirmation()

        unansweredProbes.removeLast()(PLAIN_CONTENT)
        unansweredProbes.removeFirst()(ON_INTERACTIVE_ELEMENT)

        assertThat(gestures, contains<CardTouchGesture>(CardTouchGesture.Tap(800f, 1600f, WIDTH, HEIGHT)))
    }

    @Test
    fun `a swipe over plain content is sent`() {
        swipe()
        answerProbe(PLAIN_CONTENT)

        assertThat(gestures.single(), instanceOf(CardTouchGesture.Fling::class.java))
    }

    @Test
    fun `a swipe from an interactive element belongs to the card`() {
        swipe()
        answerProbe(ON_INTERACTIVE_ELEMENT)

        assertThat(gestures, empty())
    }

    @Test
    fun `a swipe while text is selected belongs to the card`() {
        swipe()
        answerProbe(TEXT_SELECTED)

        assertThat(gestures, empty())
    }

    private fun tap(
        x: Float = CENTRE_X,
        y: Float = CENTRE_Y,
    ) {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime, x to y)
        advanceTime(TAP_HOLD_MS)
        touch(MotionEvent.ACTION_UP, downTime, x to y)
    }

    private fun doubleTap() {
        tap()
        advanceTime(BETWEEN_TAPS_MS)
        tap()
    }

    private fun swipe() {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime, CENTRE_X to CENTRE_Y)
        for (step in 1..SWIPE_STEPS) {
            advanceTime(SWIPE_STEP_MS)
            touch(MotionEvent.ACTION_MOVE, downTime, CENTRE_X + step * SWIPE_STEP_PX to CENTRE_Y)
        }
        advanceTime(SWIPE_STEP_MS)
        touch(MotionEvent.ACTION_UP, downTime, CENTRE_X + SWIPE_STEPS * SWIPE_STEP_PX to CENTRE_Y)
    }

    private fun touch(
        action: Int,
        downTime: Long,
        point: Pair<Float, Float>,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        listener.onTouchEvent(event)
        event.recycle()
    }

    private fun answerProbe(probe: CardTouchProbe) = unansweredProbes.removeFirst()(probe)

    private fun awaitTapConfirmation() = advanceTime(ViewConfiguration.getDoubleTapTimeout() + 1L)

    private fun advanceTime(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun card(index: Long) = CardDisplay(index, false, "<p>Q</p>", "<p>A</p>", "card card1", "http://127.0.0.1:1/")

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        const val CENTRE_X = 500f
        const val CENTRE_Y = 1000f
        const val TAP_HOLD_MS = 50L
        const val BETWEEN_TAPS_MS = 100L
        const val SWIPE_STEPS = 4
        const val SWIPE_STEP_MS = 10L
        const val SWIPE_STEP_PX = 100f

        val PLAIN_CONTENT = CardTouchProbe()
        val ON_INTERACTIVE_ELEMENT = CardTouchProbe(isOnInteractiveElement = true)
        val TEXT_SELECTED = CardTouchProbe(hasTextSelection = true)
    }
}
