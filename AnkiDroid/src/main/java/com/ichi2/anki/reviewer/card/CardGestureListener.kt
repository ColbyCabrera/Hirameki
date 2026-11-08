/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com> 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
package com.ichi2.anki.reviewer.card

import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.reviewer.compose.HAS_TEXT_SELECTION_SCRIPT
import com.ichi2.anki.reviewer.compose.interactiveElementScript
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlin.math.abs

/**
 * Detects taps, double taps and swipes on the [cardLayout] for the user's gesture settings (#185).
 */
internal class CardGestureListener(
    private val cardLayout: SafeWebViewLayout,
    /** Identifies the card and side being shown, see [displayAtTouchDown]. */
    private val currentDisplay: () -> CardDisplay?,
    private val onGesture: (CardTouchGesture) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    private val detector = GestureDetector(cardLayout.context, this)
    private val edgeMarginPx = NO_GESTURE_BORDER_DP * cardLayout.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(cardLayout.context).scaledTouchSlop
    private var lastXScrollTime = 0L
    private var lastYScrollTime = 0L

    /** A second finger touched during the current gesture, e.g. pinch-to-zoom. */
    private var wasMultiTouch = false

    // Multi-finger taps, which GestureDetector doesn't report. Matches upstream's new study screen.
    private var maxPointers = 0
    private var touchDownTime = 0L
    private var movedPastSlop = false
    private val pointerStarts = mutableMapOf<Int, Pair<Float, Float>>()

    /**
     * The card and side shown when the touch started. A single tap is only confirmed after the
     * double tap timeout, by which time an answer button may have moved on to the next card.
     */
    private var displayAtTouchDown: CardDisplay? = null

    /**
     * The card had a text selection when the current touch started. Checked on touch down
     * because the tap itself clears the selection before the tap is confirmed.
     */
    private var hadTextSelection = false

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wasMultiTouch = false
            displayAtTouchDown = currentDisplay()
            hadTextSelection = false
            cardLayout.evaluateJavascript(HAS_TEXT_SELECTION_SCRIPT) { hadTextSelection = it == "true" }
        }
        if (event.pointerCount > 1) wasMultiTouch = true
        trackMultiFingerTap(event)
        detector.onTouchEvent(event)
    }

    private fun trackMultiFingerTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maxPointers = 1
                touchDownTime = event.eventTime
                movedPastSlop = false
                pointerStarts.clear()
                pointerStarts[event.getPointerId(0)] = event.x to event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                pointerStarts[event.getPointerId(index)] = event.getX(index) to event.getY(index)
                maxPointers = maxOf(maxPointers, event.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> {
                if (movedPastSlop) return
                movedPastSlop =
                    (0 until event.pointerCount).any { index ->
                        val (startX, startY) = pointerStarts[event.getPointerId(index)] ?: return@any false
                        abs(event.getX(index) - startX) > touchSlop || abs(event.getY(index) - startY) > touchSlop
                    }
            }
            MotionEvent.ACTION_UP -> {
                val isMultiFingerTap =
                    maxPointers in 2..4 &&
                        event.eventTime - touchDownTime <= MULTI_FINGER_TAP_TIMEOUT_MS &&
                        !movedPastSlop
                if (isMultiFingerTap && !hadTextSelection) send(CardTouchGesture.MultiFingerTap(maxPointers))
            }
            MotionEvent.ACTION_CANCEL -> maxPointers = 0
        }
    }

    fun onScrollChanged(
        dx: Int,
        dy: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        if (abs(dx) > abs(dy)) lastXScrollTime = now else lastYScrollTime = now
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (!wasMultiTouch) {
            sendUnlessInteractive(e, CardTouchGesture.Tap(e.x, e.y, cardLayout.width, cardLayout.height))
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!wasMultiTouch) {
            sendUnlessInteractive(e, CardTouchGesture.DoubleTap)
        }
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        // #5741: a swipe from the screen edge is a system gesture
        if (e1 == null || wasMultiTouch || isTouchingEdge(e1)) return false
        val now = SystemClock.uptimeMillis()
        sendUnlessInteractive(
            e1,
            CardTouchGesture.Fling(
                dx = e2.x - e1.x,
                dy = e2.y - e1.y,
                velocityX = velocityX,
                velocityY = velocityY,
                isXScrolling = now - lastXScrollTime < SCROLL_TIMEOUT_MS,
                isYScrolling = now - lastYScrollTime < SCROLL_TIMEOUT_MS,
            ),
        )
        return false
    }

    /** Sends [gesture] unless the card or side changed since the touch started. */
    private fun send(gesture: CardTouchGesture) {
        if (currentDisplay() != displayAtTouchDown) return
        onGesture(gesture)
    }

    private fun isTouchingEdge(e: MotionEvent): Boolean =
        e.x < edgeMarginPx ||
            e.y < edgeMarginPx ||
            cardLayout.width - e.x < edgeMarginPx ||
            cardLayout.height - e.y < edgeMarginPx

    /**
     * Sends [gesture] unless the touch which started at [start] belongs to the card: text was
     * selected, or it is on a link, a button, an input or another element which handles touches
     * itself (#6141). The element check runs in the card, so the gesture is sent asynchronously.
     */
    private fun sendUnlessInteractive(
        start: MotionEvent,
        gesture: CardTouchGesture,
    ) {
        if (hadTextSelection || isOnLinkOrInput() || cardLayout.width == 0 || cardLayout.height == 0) return
        val script = interactiveElementScript(start.x / cardLayout.width, start.y / cardLayout.height)
        cardLayout.evaluateJavascript(script) { isInteractive ->
            if (isInteractive != "true" && !hadTextSelection) send(gesture)
        }
    }

    /** Checked natively as well, in case the card's JavaScript doesn't run. */
    private fun isOnLinkOrInput(): Boolean =
        when (cardLayout.hitTestResult?.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            WebView.HitTestResult.EDIT_TEXT_TYPE,
            -> true
            else -> false
        }

    companion object {
        /** Matches the legacy reviewer's NO_GESTURE_BORDER_DIP. */
        private const val NO_GESTURE_BORDER_DP = 24

        /** A scroll within this window means a fling scrolled the card instead of swiping. */
        private const val SCROLL_TIMEOUT_MS = 300L

        /** All fingers must be lifted within this time. Upstream's MULTI_TOUCH_TIMEOUT. */
        private const val MULTI_FINGER_TAP_TIMEOUT_MS = 300L
    }
}
