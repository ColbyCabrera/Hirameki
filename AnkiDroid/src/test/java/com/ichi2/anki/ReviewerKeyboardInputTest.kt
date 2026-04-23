/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.os.Looper
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_1
import android.view.KeyEvent.KEYCODE_2
import android.view.KeyEvent.KEYCODE_3
import android.view.KeyEvent.KEYCODE_4
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_BUTTON_Y
import android.view.KeyEvent.KEYCODE_E
import android.view.KeyEvent.KEYCODE_F5
import android.view.KeyEvent.KEYCODE_R
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_Z
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ibm.icu.impl.Assert
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.utils.ext.addBinding
import io.mockk.every
import io.mockk.spyk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class ReviewerKeyboardInputTest : RobolectricTest() {
    @Test
    fun whenDisplayingQuestionTyping1DoesNothing() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        assertThat("Answer should not be displayed", !underTest.didDisplayAnswer())
        assertThat("Answer should not be performed", !underTest.hasBeenAnswered())
    }

    @Test
    fun whenDisplayingAnswerTyping1AnswersFarLeftButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(Rating.AGAIN))
    }

    @Test
    fun whenDisplayingAnswerTyping2AnswersSecondButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_2)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(Rating.HARD))
    }

    @Test
    fun whenDisplayingAnswerTyping3AnswersThirdButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_3)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(Rating.GOOD))
    }

    @Test
    fun whenDisplayingAnswerTyping4AnswersFarRightButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_4)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(Rating.EASY))
    }

    /** START: DEFAULT IS "GOOD"  */
    @Test
    fun spaceAnswersThirdButtonWhenFourButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleSpacebar()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(Rating.GOOD))
    }

    /** END: DEFAULT IS "GOOD"  */
    @Test
    fun gamepadAAnswerFourthButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_A, Rating.EASY)
    }

    @Test
    fun gamepadBAnswersThirdButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_B, Rating.GOOD)
    }

    @Test
    fun gamepadXAnswersSecondButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_X, Rating.HARD)
    }

    @Test
    fun gamepadYAnswersFirstButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_Y, Rating.AGAIN)
    }

    @Test
    fun pressingEWillEditCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_E)
        assertThat("Edit Card was called", underTest.editCardCalled)
    }

    @Test
    fun pressingStarWillMarkCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleUnicodeKeyPress('*')
        assertThat("Mark Card was called", underTest.markCardCalled)
    }

    @Test
    fun pressingEqualsWillBuryNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleUnicodeKeyPress('=')
        assertThat("Bury Note should be called", underTest.buryNoteCalled)
    }

    @Test
    fun pressingAtWillSuspendCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleUnicodeKeyPress('@')
        assertThat("Suspend Card should be called", underTest.suspendCardCalled)
    }

    @Test
    fun pressingExclamationWillSuspendNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleUnicodeKeyPress('!')
        assertThat("Suspend Note should be called", underTest.suspendNoteCalled)
    }

    @Test
    fun pressingRShouldReplayMedia() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_R)
        assertThat("Replay Media should be called", underTest.replayMediaCalled)
    }

    @Test
    fun pressingF5ShouldReplayMedia() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleKeyPress(KEYCODE_F5, '\u0000')
        assertThat("Replay Media should be called", underTest.replayMediaCalled)
    }

    @Test
    fun pressingZShouldUndoIfAvailable() {
        ViewerCommand.UNDO.addBinding(
            sharedPrefs(),
            ReviewerBinding(keyCode(KEYCODE_Z, ModifierKeys.none()), CardSide.BOTH),
        )
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(true)
        underTest.handleAndroidKeyPress(KEYCODE_Z)
        assertThat("Undo should be called", underTest.undoCalled)
    }

    @Test
    fun pressingZShouldNotUndoIfNotAvailable() {
        ViewerCommand.UNDO.addBinding(
            sharedPrefs(),
            ReviewerBinding(keyCode(KEYCODE_Z, ModifierKeys.none()), CardSide.BOTH),
        )
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(false)
        underTest.handleUnicodeKeyPress('z')
        assertThat("Undo is not available so should not be called", !underTest.undoCalled)
    }

    @Test
    fun pressingSpaceShouldDoNothingIfFocused() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion().focusTextField()
        underTest.handleSpacebar()
        assertThat(
            "When text field is focused, space should not display answer",
            !underTest.didDisplayAnswer(),
        )
    }

    @Test
    fun defaultKeyboardInputsFlipAndAnswersCard() {
        // Issue 14214
        val underTest = KeyboardInputTestReviewer.displayingQuestion()

        underTest.handleSpacebar()

        assertThat(
            "After a keypress the answer should be displayed", underTest.testIsDisplayingAnswer()
        )

        underTest.handleSpacebar()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(
            "After a second keypress the question should be displayed",
            !underTest.testIsDisplayingAnswer()
        )
    }

    private fun assertGamepadButtonAnswers(
        keycodeButton: Int,
        rating: Rating,
    ) {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        assertThat("Assume: Initially should not display answer", !underTest.didDisplayAnswer())
        underTest.handleGamepadPress(keycodeButton)
        assertThat("Initial button should display answer", underTest.didDisplayAnswer())
        underTest.displayAnswerForTest()
        underTest.handleGamepadPress(keycodeButton)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(underTest.processedAnswer(), equalTo(rating))
    }

    internal class KeyboardInputTestReviewer : BindingProcessor<ReviewerBinding, ViewerCommand> {
        private var answered: Rating? = null
        var editCardCalled = false
            private set
        var markCardCalled = false
            private set
        var undoCalled = false
            private set
        var replayMediaCalled = false
            private set

        private val cardFlips = mutableListOf<String>()
        private var displayAnswer = false
        private var isTextInputFocused = false

        fun displayAnswerForTest() {
            displayAnswer = true
        }

        fun displayQuestionForTest() {
            displayAnswer = false
        }

        var processor: BindingMap<ReviewerBinding, ViewerCommand> =
            BindingMap(sharedPrefs(), ViewerCommand.entries, this)

        override fun processAction(action: ViewerCommand, binding: ReviewerBinding): Boolean {
            val currentSide = if (displayAnswer) CardSide.ANSWER else CardSide.QUESTION
            if (binding.side != CardSide.BOTH && binding.side != currentSide) {
                return false
            }
            if (action == ViewerCommand.UNDO && !isUndoAvailable) {
                return false
            }
            executeCommand(action)
            return true
        }

        private fun displayCardAnswer() {
            cardFlips.add("answer")
            displayAnswer = true
        }

        private fun displayCardQuestion() {
            cardFlips.add("question")
            displayAnswer = false
        }

        private fun flipOrAnswerCard(cardOrdinal: Rating) {
            if (displayAnswer) {
                answerCard(cardOrdinal)
                displayCardQuestion()
            } else {
                displayCardAnswer()
            }
        }

        fun didDisplayAnswer() = cardFlips.contains("answer")

        fun testIsDisplayingAnswer() = cardFlips.last() == "answer"

        fun handleUnicodeKeyPress(unicodeChar: Char) {
            val downEvent = createUnicodeKeyEvent(unicodeChar)
            try {
                if (!processor.onKeyDown(downEvent)) {
                    onUnhandledKeyDown(0)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun handleKeyPress(
            keycode: Int,
            unicodeChar: Char,
        ) {
            // COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            val e = createKeyEvent(keycode, unicodeChar)
            try {
                if (!processor.onKeyDown(e)) {
                    onUnhandledKeyDown(keycode)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        // useful to obtain Unicode for keycode if run under AndroidJUnit4.
        fun handleAndroidKeyPress(keycode: Int) {
            val downEvent = createKeyEvent(keycode)
            try {
                if (!processor.onKeyDown(downEvent)) {
                    onUnhandledKeyDown(keycode)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        private fun createKeyEvent(
            keycode: Int,
            unicodeChar: Char = '\u0000',
        ): KeyEvent = spyk(KeyEvent(ACTION_DOWN, keycode)) {
            every { getUnicodeChar(any()) } returns unicodeChar.code
        }

        private fun createUnicodeKeyEvent(
            unicodeChar: Char,
        ): KeyEvent = spyk(KeyEvent(ACTION_DOWN, 0)) {
            every { getUnicodeChar(any()) } returns unicodeChar.code
        }

        fun focusTextField(): KeyboardInputTestReviewer {
            isTextInputFocused = true
            return this
        }

        private fun answerCard(rating: Rating) {
            answered = rating
        }

        fun processedAnswer(): Rating {
            if (answered == null) {
                Assert.fail("No card was answered")
            }
            return answered!!
        }

        fun handleSpacebar() {
            handleKeyPress(KEYCODE_SPACE, ' ')
        }

        fun handleGamepadPress(buttonCode: Int) {
            // Tested under Robolectric - unicode is null
            handleKeyPress(buttonCode, '\u0000')
        }

        private fun undo() {
            undoCalled = true
        }

        var suspendNoteCalled: Boolean = false
        var buryNoteCalled: Boolean = false

        var suspendCardCalled: Boolean = false

        private var isUndoAvailable: Boolean = false

        fun withUndoAvailable(value: Boolean): KeyboardInputTestReviewer {
            isUndoAvailable = value
            return this
        }

        fun hasBeenAnswered(): Boolean = answered != null

        private fun onUnhandledKeyDown(keyCode: Int): Boolean {
            if (!displayAnswer && !isTextInputFocused) {
                if (keyCode == KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    displayCardAnswer()
                    return true
                }
            }
            return false
        }

        private fun executeCommand(which: ViewerCommand): Boolean {
            return when (which) {
                ViewerCommand.SHOW_ANSWER -> {
                    if (displayAnswer) {
                        false
                    } else {
                        displayCardAnswer()
                        true
                    }
                }

                ViewerCommand.FLIP_OR_ANSWER_EASE1 -> {
                    flipOrAnswerCard(Rating.AGAIN)
                    true
                }

                ViewerCommand.FLIP_OR_ANSWER_EASE2 -> {
                    flipOrAnswerCard(Rating.HARD)
                    true
                }

                ViewerCommand.FLIP_OR_ANSWER_EASE3 -> {
                    flipOrAnswerCard(Rating.GOOD)
                    true
                }

                ViewerCommand.FLIP_OR_ANSWER_EASE4 -> {
                    flipOrAnswerCard(Rating.EASY)
                    true
                }

                ViewerCommand.EDIT -> {
                    editCardCalled = true
                    true
                }

                ViewerCommand.MARK -> {
                    markCardCalled = true
                    true
                }

                ViewerCommand.BURY_NOTE -> {
                    buryNoteCalled = true
                    true
                }

                ViewerCommand.SUSPEND_CARD -> {
                    suspendCardCalled = true
                    true
                }

                ViewerCommand.SUSPEND_NOTE -> {
                    suspendNoteCalled = true
                    true
                }

                ViewerCommand.PLAY_MEDIA -> {
                    replayMediaCalled = true
                    true
                }

                ViewerCommand.UNDO -> {
                    undo()
                    true
                }

                else -> false
            }
        }

        companion object {
            @CheckResult
            fun displayingAnswer(): KeyboardInputTestReviewer {
                val keyboardInputTestReviewer = KeyboardInputTestReviewer()
                keyboardInputTestReviewer.displayAnswerForTest()
                return keyboardInputTestReviewer
            }

            @CheckResult
            fun displayingQuestion(): KeyboardInputTestReviewer {
                val keyboardInputTestReviewer = KeyboardInputTestReviewer()
                keyboardInputTestReviewer.displayQuestionForTest()
                return keyboardInputTestReviewer
            }
        }
    }
}
