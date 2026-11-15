// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlin.math.abs

/**
 * Detects taps, double taps, multi-finger taps and swipes on the [cardLayout] for the user's
 * gesture settings (#185).
 */
internal class CardGestureListener(
    private val cardLayout: SafeWebViewLayout,
    private val currentDisplay: () -> CardDisplay?,
    /** Asks the page what is at a point, given as fractions of the card's width and height. */
    private val probeTouch: (fractionX: Float, fractionY: Float, onProbed: (CardTouchProbe) -> Unit) -> Unit,
    private val onGesture: (CardTouchGesture) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    private val detector = GestureDetector(cardLayout.context, this)
    private val edgeMarginPx = NO_GESTURE_BORDER_DP * cardLayout.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(cardLayout.context).scaledTouchSlop
    private var lastXScrollTime = 0L
    private var lastYScrollTime = 0L

    private var touch = TouchStart(display = null)
    private var previousTouch = touch

    /** A second finger touched during the current gesture, e.g. pinch-to-zoom. */
    private var wasMultiTouch = false

    // Multi-finger taps, which GestureDetector doesn't report. Matches upstream's new study screen.
    private var maxPointers = 0
    private var touchDownTime = 0L
    private var movedPastSlop = false
    private val pointerStarts = mutableMapOf<Int, Pair<Float, Float>>()

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wasMultiTouch = false
            startTouch(event)
        }
        if (event.pointerCount > 1) wasMultiTouch = true
        trackMultiFingerTap(event)
        detector.onTouchEvent(event)
    }

    /** The page is asked now, as the card's own click handler may have changed it by the time a tap is confirmed. */
    private fun startTouch(down: MotionEvent) {
        val started = TouchStart(currentDisplay())
        previousTouch = touch
        touch = started
        probeTouch(down.x / cardLayout.width, down.y / cardLayout.height, started::onProbed)
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
                // Like upstream, the element under the first finger does not matter
                if (isMultiFingerTap) touch.sendUnless(CardTouchGesture.MultiFingerTap(maxPointers)) { it.hasTextSelection }
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
        if (!wasMultiTouch && !isOnLinkOrInput()) {
            touch.sendUnless(CardTouchGesture.Tap(e.x, e.y, cardLayout.width, cardLayout.height)) { it.claimsTaps }
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!wasMultiTouch && !isOnLinkOrInput()) {
            val firstTap = previousTouch
            val secondTap = touch
            firstTap.whenProbed { first ->
                if (!first.claimsTaps && firstTap.display == secondTap.display) {
                    secondTap.sendUnless(CardTouchGesture.DoubleTap) { it.claimsTaps }
                }
            }
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
        val fling =
            CardTouchGesture.Fling(
                dx = e2.x - e1.x,
                dy = e2.y - e1.y,
                velocityX = velocityX,
                velocityY = velocityY,
                isXScrolling = now - lastXScrollTime < SCROLL_TIMEOUT_MS,
                isYScrolling = now - lastYScrollTime < SCROLL_TIMEOUT_MS,
            )
        touch.sendUnless(fling) { it.claimsSwipes }
        return false
    }

    /** A tap is only confirmed after the double tap timeout, by which time an answer button may have changed the card. */
    private fun TouchStart.sendUnless(
        gesture: CardTouchGesture,
        isClaimedByCard: (CardTouchProbe) -> Boolean,
    ) = whenProbed { probe ->
        if (!isClaimedByCard(probe) && currentDisplay() == display) onGesture(gesture)
    }

    private fun isTouchingEdge(e: MotionEvent): Boolean =
        e.x < edgeMarginPx ||
            e.y < edgeMarginPx ||
            cardLayout.width - e.x < edgeMarginPx ||
            cardLayout.height - e.y < edgeMarginPx

    /** #6141: checked natively as well, in case the page cannot answer. */
    private fun isOnLinkOrInput(): Boolean =
        when (cardLayout.hitTestResult?.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            WebView.HitTestResult.EDIT_TEXT_TYPE,
            -> true
            else -> false
        }

    /** The card and side shown when the first finger went down, and what the page found under it. */
    private class TouchStart(
        val display: CardDisplay?,
    ) {
        private var probe: CardTouchProbe? = null
        private val awaitingProbe = mutableListOf<(CardTouchProbe) -> Unit>()

        fun onProbed(result: CardTouchProbe) {
            probe = result
            awaitingProbe.forEach { it(result) }
            awaitingProbe.clear()
        }

        fun whenProbed(action: (CardTouchProbe) -> Unit) {
            val known = probe
            if (known != null) action(known) else awaitingProbe += action
        }
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
