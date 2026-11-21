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

    fun init(preferences: SharedPreferences) {
        bindingMap =
            BindingMap(preferences, ViewerCommand.entries) { command, binding ->
                binding.side.appliesTo(currentSide()) && processor.executeCommand(command, fromGesture = null)
            }
    }

    fun onKeyDown(event: KeyEvent): Boolean = bindingMap?.onKeyDown(event) == true

    fun onGenericMotionEvent(event: MotionEvent?): Boolean = bindingMap?.onGenericMotionEvent(event) == true
}
