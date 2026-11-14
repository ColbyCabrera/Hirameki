// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.ichi2.anki.ui.DoubleTapListener
import org.robolectric.Shadows

/**
 * Constant to be used - extracted from StackOverflow code
 */
private val uptime = SystemClock.uptimeMillis()

/** Simulates a double tap for a [DoubleTapListener] */
fun View.simulateDoubleTap() {
    fun simulateEvent(
        action: Int,
        delta: Int = 0,
    ) = simulateEvent(this, action, delta)
    simulateEvent(MotionEvent.ACTION_DOWN)
    simulateEvent(MotionEvent.ACTION_UP)
    // delta needs to be > 30 in Robolectric. GestureDetector: DOUBLE_TAP_MIN_TIME
    simulateEvent(MotionEvent.ACTION_DOWN, 50)
    simulateEvent(MotionEvent.ACTION_UP, 50)
}

/**
 * Simulates an unconfirmed single tap for a [DoubleTapListener].
 * Calling this twice will not result in a double-tap
 */
fun View.simulateUnconfirmedSingleTap() {
    fun simulateEvent(action: Int) = simulateEvent(this, action)
    simulateEvent(MotionEvent.ACTION_DOWN)
    simulateEvent(MotionEvent.ACTION_UP)
}

/**
 * https://stackoverflow.com/a/10124199
 */
private fun simulateEvent(
    target: View,
    action: Int,
    delta: Int = 0,
) {
    val event =
        obtainMotionEvent(
            downTime = uptime + delta,
            eventTime = uptime + 100 + delta,
            action = action,
            x = 0.0f,
            y = 0.0f,
            metaState = 0,
        )

    Shadows.shadowOf(target).onTouchListener.onTouch(target, event)
}

/**
 * Kotlin wrapper for [MotionEvent.obtain] allowing named arguments
 * @see MotionEvent.obtain
 */
@Suppress("SameParameterValue")
private fun obtainMotionEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
    metaState: Int,
): MotionEvent =
    MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        x,
        y,
        metaState,
    )!!
