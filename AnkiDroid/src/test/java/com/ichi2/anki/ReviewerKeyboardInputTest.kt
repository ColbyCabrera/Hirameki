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
import android.view.KeyEvent.ACTION_UP
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
import android.widget.EditText
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ibm.icu.impl.Assert
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.utils.ext.addBinding
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Job
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
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(4)
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
        underTest.currentCard = addBasicNote("a", "").firstCard()
        underTest.handleUnicodeKeyPress('*')
        assertThat("Mark Card was called", underTest.markCardCalled)
    }

    @Test
    fun pressingEqualsWillBuryNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addBasicNote("a", "").firstCard()
        underTest.handleUnicodeKeyPress('=')
        assertThat("Bury Note should be called", underTest.buryNoteCalled)
    }

//    override fun suspend

    @Test
    fun pressingAtWillSuspendCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addBasicNote("a", "").firstCard()
        underTest.handleUnicodeKeyPress('@')
        assertThat("Suspend Card should be called", underTest.suspendCardCalled)
    }

    @Test
    fun pressingExclamationWillSuspendNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addBasicNote("a", "").firstCard()
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

    internal class KeyboardInputTestReviewer : Reviewer(),
        BindingProcessor<ReviewerBinding, ViewerCommand> {
        private var answered: Rating? = null
        private var answerButtonCount = 4
        var editCardCalled = false
            private set
        var markCardCalled = false
            private set
        var undoCalled = false
            private set
        var replayMediaCalled = false
            private set

        private var _currentCard: Card? = mockk<Card>(relaxed = true)
        override var currentCard: Card?
            get() = _currentCard
            set(value) {
                _currentCard = value
            }

        private val cardFlips = mutableListOf<String>()
        override val isDrawerOpen: Boolean
            get() = false

        private var focusedView: android.view.View? = null
        override fun getCurrentFocus(): android.view.View? = focusedView

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

        override fun displayCardAnswer() {
            cardFlips.add("answer")
            displayAnswer = true
        }

        override fun displayCardQuestion() {
            cardFlips.add("question")
            displayAnswer = false
        }

        override fun flipOrAnswerCard(cardOrdinal: Rating) {
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
            val downEvent = createUnicodeKeyEvent(ACTION_DOWN, unicodeChar)
            try {
                if (!processor.onKeyDown(downEvent)) {
                    onKeyDown(0, downEvent)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
            val upEvent = createUnicodeKeyEvent(ACTION_UP, unicodeChar)
            try {
                onKeyUp(0, upEvent)
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
            val e = createKeyEvent(ACTION_DOWN, keycode, unicodeChar)
            try {
                if (!processor.onKeyDown(e)) {
                    onKeyDown(keycode, e)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
            val upEvent = createKeyEvent(ACTION_UP, keycode, unicodeChar)
            try {
                onKeyUp(keycode, upEvent)
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        // useful to obtain Unicode for keycode if run under AndroidJUnit4.
        fun handleAndroidKeyPress(keycode: Int) {
            val downEvent = createKeyEvent(ACTION_DOWN, keycode)
            try {
                if (!processor.onKeyDown(downEvent)) {
                    onKeyDown(keycode, downEvent)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
            try {
                onKeyUp(keycode, createKeyEvent(ACTION_UP, keycode))
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        private fun createKeyEvent(
            action: Int,
            keycode: Int,
            unicodeChar: Char = '\u0000',
        ): KeyEvent = spyk(KeyEvent(action, keycode)) {
            every { getUnicodeChar(any()) } returns unicodeChar.code
        }

        private fun createUnicodeKeyEvent(
            action: Int,
            unicodeChar: Char,
        ): KeyEvent = spyk(KeyEvent(action, 0)) {
            every { getUnicodeChar(any()) } returns unicodeChar.code
        }

        fun focusTextField(): KeyboardInputTestReviewer {
            focusedView = mockk<EditText>(relaxed = true) {
                every { onCheckIsTextEditor() } returns true
            }
            return this
        }

        override fun answerCard(rating: Rating) {
            super.answerCard(rating)
            answered = rating
        }

        fun processedAnswer(): Rating {
            if (answered == null) {
                Assert.fail("No card was answered")
            }
            return answered!!
        }

        fun withButtons(answerButtonCount: Int): KeyboardInputTestReviewer {
            this.answerButtonCount = answerButtonCount
            return this
        }

        fun handleSpacebar() {
            handleKeyPress(KEYCODE_SPACE, ' ')
        }

        fun handleGamepadPress(buttonCode: Int) {
            // Tested under Robolectric - unicode is null
            handleKeyPress(buttonCode, '\u0000')
        }

        override fun undo(): Job {
            undoCalled = true
            return launchCatchingTask { }
        }

        var suspendNoteCalled: Boolean = false
        var buryNoteCalled: Boolean = false

        override fun editCard(fromGesture: Gesture?) {
            editCardCalled = true
        }

        override fun onMark(card: Card?) {
            markCardCalled = true
        }

        var suspendCardCalled: Boolean = false

        override fun suspendCard(): Boolean {
            suspendCardCalled = true
            return true
        }

        override fun playMedia(doMediaReplay: Boolean) {
            replayMediaCalled = true
        }

        override fun buryNote(): Boolean {
            buryNoteCalled = true
            return true
        }

        override fun suspendNote(): Boolean {
            suspendNoteCalled = true
            return true
        }

        private var isUndoAvailable: Boolean = false

        fun withUndoAvailable(value: Boolean): KeyboardInputTestReviewer {
            isUndoAvailable = value
            return this
        }

        fun hasBeenAnswered(): Boolean = answered != null

        override fun performClickWithVisualFeedback(rating: Rating) {
            answerCard(rating)
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
