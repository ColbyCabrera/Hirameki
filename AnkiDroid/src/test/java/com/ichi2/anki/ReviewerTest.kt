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
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.cardviewer.Gesture
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

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
        val actualAnimation = BundleCompat.getParcelable(
            fragmentBundle!!,
            AnkiActivity.FINISH_ANIMATION_EXTRA,
            ActivityTransitionAnimation.Direction::class.java,
        )
        val expectedAnimation = ActivityTransitionAnimation.getInverseTransition(
            AbstractFlashcardViewer.getAnimationTransitionFromGesture(fromGesture),
        )

        assertEquals(
            "Animation from swipe should be inverse to the finishing one",
            expectedAnimation,
            actualAnimation
        )
    }

    companion object {
        fun startReviewer(testClass: RobolectricTest): Reviewer =
            startReviewer(testClass, Reviewer::class.java)

        fun <T : Reviewer?> startReviewer(
            testClass: RobolectricTest,
            clazz: Class<T>,
        ): T = startActivityNormallyOpenCollectionWithIntent(testClass, clazz, Intent()).apply {
            this?.onCollectionLoaded(testClass.col)
            advanceRobolectricLooper()
        }
    }
}

