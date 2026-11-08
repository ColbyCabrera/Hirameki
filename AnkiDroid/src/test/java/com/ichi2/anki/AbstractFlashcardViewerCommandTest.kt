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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerRefresh
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Tests [AbstractFlashcardViewer.refreshIfRequired]. Command routing is tested in `ReviewerCommandRoutingTest`. */
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    companion object {
        val updateCard: ViewerRefresh = ViewerRefresh(queues = true, note = true, card = true)
    }

    @Test
    fun testRefreshIfRequired() {
        // Create an ActivityController instance for AbstractFlashcardViewer
        Robolectric.buildActivity(AbstractFlashcardViewerTest.NonAbstractFlashcardViewer::class.java).use { controller ->
            // Case 1: Activity is resuming, refreshRequired is not null
            with(controller.create().start().get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = true)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 2: Activity is not resuming, lifecycle is at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 3: Activity is not resuming, lifecycle is not at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.pause() // Ensure the activity is paused before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is not set to null
                assertThat(refreshRequired, notNullValue())
            }
        }
    }
}
