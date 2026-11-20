// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding

/**
 * Runs the [ViewerCommand]s bound to keyboard keys, gamepad buttons and joystick or pedal axes,
 * for the card side being shown. The gesture counterpart is [GestureProcessor].
 *
 * @param currentSide the side of the card being shown: [CardSide.QUESTION] or [CardSide.ANSWER]
 */
class HardwareInputProcessor(
    private val processor: ViewerCommand.CommandProcessor,
    private val currentSide: () -> CardSide,
) {
    private var bindingMap: BindingMap<ReviewerBinding, ViewerCommand>? = null

    private val keysHandledOnPress = mutableSetOf<Int>()

    fun init(preferences: SharedPreferences) {
        clearHandledKeys()
        bindingMap =
            BindingMap(preferences, ViewerCommand.entries) { command, binding ->
                binding.side.appliesTo(currentSide()) && processor.executeCommand(command, fromGesture = null)
            }
    }

    /** Drops any in-flight pressed keys, e.g. when the window loses focus before ACTION_UP arrives. */
    fun clearHandledKeys() {
        keysHandledOnPress.clear()
    }

    /** A handled press also consumes its repeats and release, so the release can't fall back to Back. */
    fun onKeyEvent(event: KeyEvent): Boolean =
        when {
            event.action == KeyEvent.ACTION_UP -> keysHandledOnPress.remove(event.keyCode)
            event.action != KeyEvent.ACTION_DOWN -> false
            event.repeatCount > 0 -> event.keyCode in keysHandledOnPress
            else -> runBoundCommand(event).also { handled -> trackPress(event.keyCode, handled) }
        }

    private fun runBoundCommand(press: KeyEvent): Boolean = bindingMap?.onKeyDown(press) == true

    private fun trackPress(
        keyCode: Int,
        handled: Boolean,
    ) {
        if (handled) keysHandledOnPress += keyCode else keysHandledOnPress -= keyCode
    }

    fun onGenericMotionEvent(event: MotionEvent?): Boolean = bindingMap?.onGenericMotionEvent(event) == true
}
