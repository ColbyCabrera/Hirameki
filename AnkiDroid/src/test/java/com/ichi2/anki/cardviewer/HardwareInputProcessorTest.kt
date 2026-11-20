// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.cardviewer

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.reviewer.Axis
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.utils.ext.addBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HardwareInputProcessorTest : ViewerCommand.CommandProcessor {
    private var side = CardSide.QUESTION
    private val executedCommands = mutableListOf<ViewerCommand>()
    private val sut =
        HardwareInputProcessor(this) { side }.apply { init(SPMockBuilder().createSharedPreferences()) }

    override fun executeCommand(
        which: ViewerCommand,
        fromGesture: Gesture?,
    ): Boolean {
        executedCommands.add(which)
        return true
    }

    @Test
    fun `a bound gamepad button runs its command`() {
        assertTrue(sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B)))

        assertEquals(listOf(ViewerCommand.FLIP_OR_ANSWER_EASE3), executedCommands)
    }

    @Test
    fun `the repeats and release of a handled press are consumed`() {
        sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B))

        assertTrue(sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B, repeatCount = 1)), "repeat")
        assertTrue(sut.onKeyEvent(release(KeyEvent.KEYCODE_BUTTON_B)), "release")
        assertEquals(1, executedCommands.size)
    }

    @Test
    fun `a release is not consumed twice`() {
        sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B))
        sut.onKeyEvent(release(KeyEvent.KEYCODE_BUTTON_B))

        assertFalse(sut.onKeyEvent(release(KeyEvent.KEYCODE_BUTTON_B)))
    }

    @Test
    fun `clearHandledKeys drops in-flight presses`() {
        sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B))
        sut.clearHandledKeys()

        assertFalse(sut.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B, repeatCount = 1)), "repeat")
        assertFalse(sut.onKeyEvent(release(KeyEvent.KEYCODE_BUTTON_B)), "release")
    }

    @Test
    fun `an unbound key passes through`() {
        assertFalse(sut.onKeyEvent(press(KeyEvent.KEYCODE_DPAD_UP)), "press")
        assertFalse(sut.onKeyEvent(release(KeyEvent.KEYCODE_DPAD_UP)), "release")
        assertTrue(executedCommands.isEmpty())
    }

    @Test
    fun `an answer-side binding is ignored on the question`() {
        assertFalse(sut.onKeyEvent(press(KeyEvent.KEYCODE_3)))

        side = CardSide.ANSWER
        assertTrue(sut.onKeyEvent(press(KeyEvent.KEYCODE_3)))
        assertEquals(listOf(ViewerCommand.FLIP_OR_ANSWER_EASE3), executedCommands)
    }

    @Test
    fun `an answer-side axis binding does not consume events on the question`() {
        val prefs = SPMockBuilder().createSharedPreferences()
        ViewerCommand.FLIP_OR_ANSWER_EASE3.addBinding(
            prefs,
            ReviewerBinding(Binding.AxisButtonBinding(Axis.Y, 1f), CardSide.ANSWER),
        )
        val processor = HardwareInputProcessor(this) { side }.apply { init(prefs) }
        val pushed = mock(MotionEvent::class.java).apply { whenever(getAxisValue(MotionEvent.AXIS_Y)).thenReturn(1f) }
        val neutral = mock(MotionEvent::class.java).apply { whenever(getAxisValue(MotionEvent.AXIS_Y)).thenReturn(0f) }

        assertFalse(processor.onGenericMotionEvent(pushed))
        assertTrue(executedCommands.isEmpty())

        processor.onGenericMotionEvent(neutral)
        side = CardSide.ANSWER
        assertTrue(processor.onGenericMotionEvent(pushed))
        assertEquals(listOf(ViewerCommand.FLIP_OR_ANSWER_EASE3), executedCommands)
    }

    @Test
    fun `keys pass through before the bindings are loaded`() {
        val uninitialized = HardwareInputProcessor(this) { side }

        assertFalse(uninitialized.onKeyEvent(press(KeyEvent.KEYCODE_BUTTON_B)))
    }

    private fun press(
        keyCode: Int,
        repeatCount: Int = 0,
    ) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, repeatCount)

    private fun release(keyCode: Int) = KeyEvent(KeyEvent.ACTION_UP, keyCode)
}
