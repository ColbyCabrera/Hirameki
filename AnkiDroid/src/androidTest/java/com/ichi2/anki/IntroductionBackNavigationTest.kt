/*
 * Test to ensure going back from DeckPicker returns to the introduction screen
 * and the "Before continuing!" text is visible again.
 */
package com.ichi2.anki

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.grantPermissions
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntroductionBackNavigationTest : InstrumentedTest() {
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    @Test
    fun backFromDeckPickerReshowsFirstThingsFirst() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val continueText = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.intro_continue)

        // Wait for and click the "Continue" button by its localized text
        val continueButton = device.wait(Until.findObject(By.text(continueText)), 5000)
        assertNotNull("Continue button should be visible", continueButton)
        continueButton.click()

        // Wait a moment for navigation
        device.waitForIdle()

        // Press back to return to the IntroductionActivity
        device.pressBack()

        // The "Continue" button should be visible again after pressing back
        val continueButtonAgain = device.wait(Until.findObject(By.text(continueText)), 5000)
        assertNotNull("Continue button should be visible after pressing back", continueButtonAgain)
    }
}