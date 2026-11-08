/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.view.ViewConfiguration
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureProcessorTest : ViewerCommand.CommandProcessor {
    private var side = CardSide.QUESTION
    private val sut = GestureProcessor(this) { side }
    private val executedCommands: MutableList<ViewerCommand> = ArrayList()

    override fun executeCommand(
        which: ViewerCommand,
        fromGesture: Gesture?,
    ): Boolean {
        executedCommands.add(which)
        return true
    }

    private fun singleResult(): ViewerCommand {
        assertThat<List<ViewerCommand>>(executedCommands, hasSize(1))
        return executedCommands[0]
    }

    @Test
    fun integrationTest() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(ViewerCommand.SHOW_ANSWER.preferenceKey, null) } returns
            listOf(ReviewerBinding.fromGesture(Gesture.TAP_CENTER))
                .toPreferenceString()
        every { prefs.getBoolean("gestureCornerTouch", any()) } returns true
        sut.init(prefs)
        sut.onTap(100, 100, 50f, 50f)
        assertThat(singleResult(), equalTo(ViewerCommand.SHOW_ANSWER))
    }

    @Test
    fun `a gesture runs the command bound for the side being shown`() {
        bindShake(ViewerCommand.FLIP_OR_ANSWER_EASE1 to CardSide.ANSWER, ViewerCommand.BURY_CARD to CardSide.QUESTION)

        side = CardSide.ANSWER
        sut.onShake()
        side = CardSide.QUESTION
        sut.onShake()

        assertThat(executedCommands, equalTo(listOf(ViewerCommand.FLIP_OR_ANSWER_EASE1, ViewerCommand.BURY_CARD)))
    }

    @Test
    fun `a gesture bound to one side does nothing on the other side`() {
        bindShake(ViewerCommand.FLIP_OR_ANSWER_EASE3 to CardSide.ANSWER)

        side = CardSide.QUESTION

        assertFalse(sut.onShake())
        assertThat(executedCommands, hasSize(0))
        assertTrue(sut.isBound(Gesture.SHAKE), "a gesture bound on the other side is still bound")
    }

    private fun bindShake(vararg commands: Pair<ViewerCommand, CardSide>) {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getBoolean(GestureProcessor.PREF_KEY, any()) } returns true
        for ((command, side) in commands) {
            every { prefs.getString(command.preferenceKey, null) } returns
                listOf(ReviewerBinding(Binding.GestureInput(Gesture.SHAKE), side)).toPreferenceString()
        }
        sut.init(prefs)
    }

    companion object {
        @BeforeClass
        @JvmStatic // required for @BeforeClass
        fun before() {
            mockkStatic(ViewConfiguration::class)
            every { ViewConfiguration.get(any()) } answers { mockk(relaxed = true) }

            AnkiDroidApp.internalSetInstanceValue(mockk(relaxed = true))
        }

        @JvmStatic // required for @AfterClass
        @AfterClass
        fun after() {
            unmockkStatic(ViewConfiguration::class)
            AnkiDroidApp.simulateRestoreFromBackup()
        }
    }
}
