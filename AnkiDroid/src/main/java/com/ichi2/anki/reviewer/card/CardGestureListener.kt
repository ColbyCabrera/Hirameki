// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import com.ichi2.anki.reviewer.compose.CardTouchGesture
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
    private var lastXScrollTime = 0L
    private var lastYScrollTime = 0L

    /** A second finger touched during the current gesture, e.g. pinch-to-zoom. */
    private var wasMultiTouch = false

    /**
     * The card and side shown when the touch started. A single tap is only confirmed after the
     * double tap timeout, by which time an answer button may have moved on to the next card.
     */
    private var displayAtTouchDown: CardDisplay? = null

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wasMultiTouch = false
            displayAtTouchDown = currentDisplay()
        }
        if (event.pointerCount > 1) wasMultiTouch = true
        detector.onTouchEvent(event)
    }

    fun onScrollChanged(
        dx: Int,
        dy: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        if (abs(dx) > abs(dy)) lastXScrollTime = now else lastYScrollTime = now
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (!wasMultiTouch && !isOnInteractiveElement()) {
            send(CardTouchGesture.Tap(e.x, e.y, cardLayout.width, cardLayout.height))
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!wasMultiTouch && !isOnInteractiveElement()) {
            send(CardTouchGesture.DoubleTap)
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
        send(
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

    /** #6141: a tap on a link, a replay button or an input belongs to the card, not to a gesture. */
    private fun isOnInteractiveElement(): Boolean =
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
    }
}
