package com.ichi2.anki.reviewer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTimeboxTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `timebox dialog does not appear because startTimebox is called during LoadInitialCard`() =
        runTest {
            // Mock time to 1000 seconds.
            // If startTimebox hadn't been called, elapsed would be 1000 - 0 = 1000 > 300.
            val mockTime = object : Time() {
                override fun intTimeMS(): Long = 1000000L
            }
            TimeManager.resetWith(mockTime)

            try {
                // Given we have a card and a time limit of 5 minutes (300 seconds)
                addBasicNote("Front", "Back")
                col.config.set("timeLim", 300)

                // And the ViewModel is initialized
                var timeboxDialogEmitCount = 0
                val viewModel = ReviewerViewModel(ApplicationProvider.getApplicationContext())

                val job = launch {
                    viewModel.effect.collect { effect ->
                        if (effect is ReviewerEffect.ShowTimeboxReachedDialog) {
                            timeboxDialogEmitCount++
                        }
                    }
                }

                // When we process the initial load
                advanceRobolectricLooper()
                advanceUntilIdle()

                // Then no timebox dialog should have been emitted because startTimebox() was called at t=1000
                // and the timer was reset to the current time.
                assertEquals(
                    "Timebox dialog should not appear initially",
                    0,
                    timeboxDialogEmitCount
                )
                job.cancel()
            } finally {
                TimeManager.reset()
            }
        }
}
