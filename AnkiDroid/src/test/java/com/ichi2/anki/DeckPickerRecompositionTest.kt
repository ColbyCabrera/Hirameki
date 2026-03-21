package com.ichi2.anki

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerRecompositionTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @org.junit.Before
    fun setUpTest() = kotlinx.coroutines.runBlocking {
        editPreferences { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        CollectionManager.ensureOpen()
    }

    @Test
    fun deckPickerNavHost_disappearsWhenCollectionClosed() = runTest {
        ActivityScenario.launch(DeckPicker::class.java).use {
            // Wait for Compose to render the default UI with the collection fully open
            composeTestRule.waitForIdle()

            // Verify the open state
            composeTestRule.onNodeWithText("Decks").assertExists()

            // Trigger closure
            CollectionManager.ensureClosed()
            composeTestRule.waitForIdle()

            // Verify disappearance
            composeTestRule.onNodeWithText("Decks").assertDoesNotExist()
        }
    }
}
