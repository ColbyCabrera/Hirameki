/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.anki

import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.utils.ext.addBinding
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [Reviewer] activity.
 *
 * Note: Many tests were removed because they were written for the legacy View-based Reviewer
 * and don't work with the new Compose-based architecture. Tests should be added to
 * [com.ichi2.anki.reviewer.ReviewerViewModelTest] for ViewModel behavior or use
 * Compose testing for UI behavior.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun testAddNoteAnimation() {
        // Arrange
        val reviewer = startRegularActivity<Reviewer>()
        val fromGesture = Gesture.SWIPE_DOWN

        // Act
        reviewer.addNote(fromGesture)

        // Assert
        val shadowApplication =
            Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val intent = shadowApplication.nextStartedActivity
        val fragmentBundle = intent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
        val actualAnimation =
            BundleCompat.getParcelable(
                fragmentBundle!!,
                AnkiActivity.FINISH_ANIMATION_EXTRA,
                ActivityTransitionAnimation.Direction::class.java,
            )
        val expectedAnimation =
            ActivityTransitionAnimation.getInverseTransition(
                AbstractFlashcardViewer.getAnimationTransitionFromGesture(fromGesture),
            )

        assertEquals(
            "Animation from swipe should be inverse to the finishing one",
            expectedAnimation,
            actualAnimation,
        )
    }

    @Test
    fun `the compose reviewer builds no legacy card WebView`() {
        addBasicNote("Front", "Back")

        val reviewer = startReviewer(this)

        // #143: it has no card frame to attach one to, and a detached WebView's renderer-loss
        // handler crashed the app
        assertNull(reviewer.webView, "no detached legacy WebView")
    }

    @Test
    fun `card commands are safe without a legacy card WebView`() {
        addBasicNote("Front", "Back")
        val reviewer = startReviewer(this)

        // each of these dereferenced the legacy card WebView, which the compose reviewer no longer builds
        listOf(
            ViewerCommand.PAGE_UP,
            ViewerCommand.PAGE_DOWN,
            ViewerCommand.SHOW_HINT,
            ViewerCommand.SHOW_ALL_HINTS,
            ViewerCommand.USER_ACTION_1,
        ).forEach { reviewer.executeCommand(it) }
        for (keyCode in listOf(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN)) {
            reviewer.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        reviewer.scrollCurrentCardBy(100)
        reviewer.tapOnCurrentCard(10, 10)
        advanceRobolectricLooper()
    }

    @Test
    fun `a bound gesture reaches the compose reviewer`() =
        runTest {
            // #185: gestures were never connected to the compose reviewer, so none of them did anything
            val note = addBasicNote("Front", "Back")
            ViewerCommand.FLIP_OR_ANSWER_EASE3.addBinding(getPreferences(), ReviewerBinding.fromGesture(Gesture.DOUBLE_TAP))
            val reviewer = startReviewer(this@ReviewerTest)
            waitForCard(reviewer)
            val firstCardIndex = reviewer.viewModel.state.value.cardDisplayIndex

            reviewer.onCardTouchGesture(CardTouchGesture.DoubleTap)
            advanceRobolectricLooper()
            assertTrue(reviewer.viewModel.state.value.isAnswerShown, "first double tap shows the answer")

            reviewer.onCardTouchGesture(CardTouchGesture.DoubleTap)
            advanceRobolectricLooper()
            assertEquals("second double tap answers the card", 1, note.firstCard(col).reps)
            assertNotEquals(firstCardIndex, reviewer.viewModel.state.value.cardDisplayIndex)
        }

    @Test
    fun `gestures work although the hidden 'Enable gestures' switch is off`() =
        runTest {
            // #185: settings hide the switch but still let users bind gestures, which never fired
            addBasicNote("Front", "Back")
            getPreferences().edit { putBoolean(GestureProcessor.PREF_KEY, false) }
            ViewerCommand.SHOW_ANSWER.addBinding(getPreferences(), ReviewerBinding.fromGesture(Gesture.DOUBLE_TAP))
            val reviewer = startReviewer(this@ReviewerTest)
            waitForCard(reviewer)

            reviewer.onCardTouchGesture(CardTouchGesture.DoubleTap)
            advanceRobolectricLooper()

            assertTrue(reviewer.viewModel.state.value.isAnswerShown)
        }

    @Test
    fun `an unbound gesture does nothing`() =
        runTest {
            addBasicNote("Front", "Back")
            val reviewer = startReviewer(this@ReviewerTest)
            waitForCard(reviewer)

            reviewer.onCardTouchGesture(CardTouchGesture.DoubleTap)
            reviewer.onCardTouchGesture(CardTouchGesture.Tap(x = 50f, y = 50f, width = 100, height = 100))
            advanceRobolectricLooper()

            assertFalse(reviewer.viewModel.state.value.isAnswerShown)
        }

    @Test
    fun `show answer command updates the compose reviewer`() =
        runTest {
            // #185: shakes and the space key ran the legacy reviewer's 'show answer', which the compose UI never saw
            addBasicNote("Front", "Back")
            val reviewer = startReviewer(this@ReviewerTest)
            waitForCard(reviewer)

            assertTrue(reviewer.executeCommand(ViewerCommand.SHOW_ANSWER))
            advanceRobolectricLooper()
            assertTrue(reviewer.viewModel.state.value.isAnswerShown)

            assertFalse(reviewer.executeCommand(ViewerCommand.SHOW_ANSWER), "answer already shown")
        }

    private fun waitForCard(reviewer: Reviewer) {
        advanceRobolectricLooper()
        assertTrue(
            reviewer.viewModel.state.value.questionHtml
                .isNotEmpty(),
            "card loaded",
        )
    }

    companion object {
        fun startReviewer(testClass: RobolectricTest): Reviewer = startReviewer(testClass, Reviewer::class.java)

        fun <T : Reviewer?> startReviewer(
            testClass: RobolectricTest,
            clazz: Class<T>,
        ): T =
            startActivityNormallyOpenCollectionWithIntent(testClass, clazz, Intent()).apply {
                this?.onCollectionLoaded(testClass.col)
                advanceRobolectricLooper()
            }
    }
}
