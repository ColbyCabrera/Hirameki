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
        bindingMap =
            BindingMap(preferences, ViewerCommand.entries) { command, binding ->
                binding.side.appliesTo(currentSide()) && processor.executeCommand(command, fromGesture = null)
            }
    }

    /**
     * @return whether [event] was consumed: a press which ran a command, or the repeats and
     * release of such a press, so the release can't fall back to a system action such as Back
     */
    fun onKeyEvent(event: KeyEvent): Boolean =
        when {
            event.action == KeyEvent.ACTION_UP -> keysHandledOnPress.remove(event.keyCode)
            event.action != KeyEvent.ACTION_DOWN -> false
            event.repeatCount > 0 -> event.keyCode in keysHandledOnPress
            else -> runBoundCommand(event).also { handled -> if (handled) keysHandledOnPress += event.keyCode }
        }

    private fun runBoundCommand(press: KeyEvent): Boolean = bindingMap?.onKeyDown(press) == true

    /** @return whether a bound joystick or pedal axis ran a command */
    fun onGenericMotionEvent(event: MotionEvent?): Boolean = bindingMap?.onGenericMotionEvent(event) == true
}
