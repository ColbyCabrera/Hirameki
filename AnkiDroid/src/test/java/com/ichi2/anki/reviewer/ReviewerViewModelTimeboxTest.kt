package com.ichi2.anki.reviewer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTimeboxTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `timebox dialog does not appear because startTimebox is called during LoadInitialCard`() =
        runTest {
            // Given we have a card
            addBasicNote("Front", "Back")

            // And a time limit of 5 minutes (300 seconds)
            col.config.set("timeLim", 300)

            // And we mock the current time to 1000 seconds
            collectionTime.addS(1000)

            // When the ViewModel is initialized
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = ReviewerViewModel(app)

            // Then no timebox dialog should have been emitted because startTimebox()
            // is called during LoadInitialCard, which resets the timer to current time.
            viewModel.effect.test {
                // Process the LoadInitialCard event triggered in init
                advanceRobolectricLooper()

                // expectNoEvents() ensures that the flow is silent and no Timebox dialog was emitted.
                expectNoEvents()
            }
        }
}
