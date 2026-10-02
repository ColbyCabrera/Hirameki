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
import kotlin.test.fail

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
        val getStartedText =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.intro_get_started)

        // The disclaimer is scrollable, so the button may start below the fold. Scrolling can still
        // be settling when the button is found, which would make the tap miss it entirely: retry
        // until the setup page is actually reached rather than trusting a single click.
        clickAndWaitFor(device, continueText, getStartedText, "Setup page should be shown")

        // Back from the setup page returns to the disclaimer instead of closing the activity
        device.pressBack()

        assertNotNull(
            device.findOrScrollTo(continueText),
            "Continue button should be visible after pressing back",
        )
    }

    /**
     * Taps the button labelled [buttonText] and waits for [expectedText] to appear, retrying while
     * the button cannot be reached.
     */
    private fun clickAndWaitFor(
        device: UiDevice,
        buttonText: String,
        expectedText: String,
        failureMessage: String,
    ) {
        repeat(CLICK_ATTEMPTS) {
            device.findOrScrollTo(buttonText)?.click()
            if (device.wait(Until.hasObject(By.text(expectedText)), EXPECTED_TIMEOUT_MS)) return
        }
        fail(failureMessage)
    }

    private fun UiDevice.findOrScrollTo(
        text: String,
        attempts: Int = 5,
    ): UiObject2? {
        repeat(attempts) { attempt ->
            // let any in-flight scroll settle so the bounds used for a click are current
            waitForIdle()
            wait(Until.findObject(By.text(text)), 2_000)?.let { return it }
            // The introduction screens may be taller than the emulator display,
            // and page transitions can be slow there: scroll and retry
            if (attempt < attempts - 1) {
                swipe(
                    displayWidth / 2,
                    (displayHeight * 0.75).toInt(),
                    displayWidth / 2,
                    (displayHeight * 0.25).toInt(),
                    20,
                )
            }
        }
        return null
    }

    companion object {
        private const val CLICK_ATTEMPTS = 5
        private const val EXPECTED_TIMEOUT_MS = 2_000L
    }
}
