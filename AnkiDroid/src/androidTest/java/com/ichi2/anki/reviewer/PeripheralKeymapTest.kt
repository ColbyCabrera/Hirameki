// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeripheralKeymapTest : InstrumentedTest() {
    @Test
    fun testNumpadAction() {
        // #7736 Ensures that a numpad key is passed through (mostly testing num lock)
        val processed: MutableList<ViewerCommand> = ArrayList()

        val sharedPrefs = testContext.sharedPrefs()
        val bindingMap =
            BindingMap(sharedPrefs, ViewerCommand.entries) { e: ViewerCommand, _ -> processed.add(e) }

        bindingMap.onKeyDown(
            getNumpadEvent(KeyEvent.KEYCODE_NUMPAD_1),
        )
        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(
            processed[0],
            equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1),
        )
    }

    private fun getNumpadEvent(keycode: Int): KeyEvent = KeyEvent(0, 0, KeyEvent.ACTION_UP, keycode, 0, KeyEvent.META_NUM_LOCK_ON)
}
