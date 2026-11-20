// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import android.view.MotionEvent
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.utils.ext.addBinding
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingMapTest {
    @Test
    fun flagAndAnswerDoNotConflict() {
        val processed: MutableList<ViewerCommand> = ArrayList()

        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val bindingMap = BindingMap(sharedPrefs, ViewerCommand.entries) { e: ViewerCommand, _ -> processed.add(e) }
        val event = mock(KeyEvent::class.java)
        whenever(event.unicodeChar).thenReturn(0)
        whenever(event.isCtrlPressed).thenReturn(true)
        whenever(event.getUnicodeChar(0)).thenReturn(49)
        whenever(event.keyCode).thenReturn(KeyEvent.KEYCODE_1)

        assertThat(event.unicodeChar.toChar(), equalTo('\u0000'))
        assertThat(event.getUnicodeChar(0).toChar(), equalTo('1'))
        bindingMap.onKeyDown(event)

        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(processed[0], equalTo(ViewerCommand.TOGGLE_FLAG_RED))
    }

    @Test
    fun `axis event returns true only when a command executes`() {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        ViewerCommand.SHOW_ANSWER.addBinding(
            sharedPrefs,
            ReviewerBinding(Binding.AxisButtonBinding(Axis.Y, 1f), CardSide.QUESTION),
        )
        var shouldExecute = false
        val executed = mutableListOf<ViewerCommand>()
        val bindingMap =
            BindingMap(sharedPrefs, ViewerCommand.entries) { command, _ ->
                if (!shouldExecute) return@BindingMap false
                executed.add(command)
                true
            }

        val pressed = mock(MotionEvent::class.java)
        whenever(pressed.getAxisValue(MotionEvent.AXIS_Y)).thenReturn(1f)
        val released = mock(MotionEvent::class.java)
        whenever(released.getAxisValue(MotionEvent.AXIS_Y)).thenReturn(0f)

        assertFalse(bindingMap.onGenericMotionEvent(pressed), "rejected command should not consume motion event")
        assertThat(executed, hasSize(0))

        // Return below threshold to reset detector debounce, then allow execution
        assertFalse(bindingMap.onGenericMotionEvent(released))
        shouldExecute = true
        assertTrue(bindingMap.onGenericMotionEvent(pressed), "executed command should consume motion event")
        assertThat(executed, equalTo(listOf(ViewerCommand.SHOW_ANSWER)))
    }
}
