/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.reviewer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.Event
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTimeboxTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `timebox dialog does not appear because startTimebox is called during LoadInitialCard`() =
        runTest {
            // Given we have a card and a 5-minute limit
            addBasicNote("Front", "Back")
            col.config.set("timeLim", 300)

            // And we mock the current time to 1000 seconds BEFORE initialization
            collectionTime.addS(1000)

            // When the ViewModel is initialized
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = ReviewerViewModel(app)

            // Then no timebox dialog should have been emitted because startTimebox()
            // is called during LoadInitialCard, resetting the start time to 1000.
            viewModel.effect.test {
                advanceUntilIdle()
                advanceRobolectricLooper()

                val events = cancelAndConsumeRemainingEvents()
                val hasDialog =
                    events
                        .filterIsInstance<Event.Item<ReviewerEffect>>()
                        .any { it.value is ReviewerEffect.ShowTimeboxReachedDialog }
                assertFalse("Timebox dialog should NOT appear initially", hasDialog)
            }
        }

    @Test
    fun `timebox dialog appears when limit is reached after initialization`() =
        runTest {
            // Given we have a card and a 5-minute limit
            addBasicNote("Front", "Back")
            col.config.set("timeLim", 300)

            // Initialize ViewModel (resets timebox start time to current collectionTime)
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = ReviewerViewModel(app)
            advanceUntilIdle()

            // When we advance time significantly (1000 seconds)
            collectionTime.addS(1000)

            // And trigger a card reload which checks the timebox status
            viewModel.effect.test {
                viewModel.onEvent(ReviewerEvent.ReloadCard)

                // Ensure all coroutines and looper tasks complete
                advanceUntilIdle()
                advanceRobolectricLooper()

                // We use a loop with awaitItem to find the specific effect.
                // This is more robust than cancelAndConsumeRemainingEvents() which might miss the event
                // if it hasn't been emitted yet (even with advanceUntilIdle).
                // ReviewerViewModel might emit other effects (like ReplayMedia) during reload.
                // Turbine will time out if we wait too long without receiving the expected event.
                while (awaitItem() !is ReviewerEffect.ShowTimeboxReachedDialog) {
                    // Ignore other effects until we find the dialog reached event
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
