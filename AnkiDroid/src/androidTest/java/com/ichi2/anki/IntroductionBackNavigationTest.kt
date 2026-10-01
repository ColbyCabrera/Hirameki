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

/*
 * Test to ensure that pressing back on the introduction's setup page
 * returns to the disclaimer page instead of closing the activity.
 */
package com.ichi2.anki

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class IntroductionBackNavigationTest : InstrumentedTest() {
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @Test
    fun backFromSetupScreenReshowsDisclaimer() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val continueText =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.intro_continue)

        // The disclaimer is scrollable, so the button may start below the fold
        val continueButton = device.findOrScrollTo(continueText)
        assertNotNull(continueButton, "Continue button should be visible")
        continueButton.click()
        device.waitForIdle()

        // Wait until the setup page is shown so its back handler is registered
        val getStartedText =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.intro_get_started)
        assertNotNull(device.findOrScrollTo(getStartedText), "Setup page should be shown")

        // Back from the setup page returns to the disclaimer instead of closing the activity
        device.pressBack()

        val continueButtonAgain = device.findOrScrollTo(continueText)
        assertNotNull(continueButtonAgain, "Continue button should be visible after pressing back")
    }

    private fun UiDevice.findOrScrollTo(text: String): UiObject2? {
        var obj = wait(Until.findObject(By.text(text)), 2_000)
        if (obj == null) {
            swipe(
                displayWidth / 2,
                (displayHeight * 0.75).toInt(),
                displayWidth / 2,
                (displayHeight * 0.25).toInt(),
                20,
            )
            obj = wait(Until.findObject(By.text(text)), 2_000)
        }
        return obj
    }
}
