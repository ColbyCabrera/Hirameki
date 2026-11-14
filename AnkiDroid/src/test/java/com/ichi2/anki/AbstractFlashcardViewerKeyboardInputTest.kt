// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.view.KeyEvent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerKeyboardInputTest : RobolectricTest() {
    @Test
    fun spaceShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE)

        assertThat("Space should display answer on any card viewer", underTest.didDisplayAnswer())
    }

    @Test
    fun enterShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_ENTER)

        assertThat("Enter should display answer on any card viewer", underTest.didDisplayAnswer())
    }

    @Test
    fun numPadEnterShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_NUMPAD_ENTER)

        assertThat(
            "NumPad Enter should display answer on any card viewer",
            underTest.didDisplayAnswer(),
        )
    }

    @Test
    fun spaceDoesNotShowAnswerIfTextFieldFocused() {
        val underTest = KeyboardInputTestCardViewer.create()
        underTest.focusTextField()

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE)

        assertThat(
            "When text field is focused, space should not display answer",
            !underTest.didDisplayAnswer(),
        )
    }

    private class KeyboardInputTestCardViewer : AbstractFlashcardViewer() {
        private var displayAnswer = false
        private var focusedView: android.view.View? = null

        override fun getCurrentFocus(): android.view.View? = focusedView

        override fun performReload() {
            // intentionally blank
        }

        override fun displayCardAnswer() {
            displayAnswer = true
        }

        fun didDisplayAnswer(): Boolean = displayAnswer

        fun handleKeyPress(keycode: Int) {
            // COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            try {
                onKeyDown(keycode, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
            } catch (e: Exception) {
                Timber.e(e)
            }
            try {
                onKeyUp(keycode, KeyEvent(KeyEvent.ACTION_UP, keycode))
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun focusTextField() {
            focusedView =
                mockk<EditText>(relaxed = true) {
                    every { onCheckIsTextEditor() } returns true
                }
        }

        companion object {
            fun create(): KeyboardInputTestCardViewer = KeyboardInputTestCardViewer()
        }
    }
}
