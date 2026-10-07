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
package com.ichi2.anki.preferences

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableBackupPrompt
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.utils.isWindowCompact
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies navigation from the DeckPicker drawer into the preferences screen.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesNavigationTest : InstrumentedTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<DeckPicker>()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        // Dismiss the Get Started screen and backup prompt if they appeared on launch,
        // otherwise the drawer button stays unreachable and the wait below times out.
        discardPreliminaryViews()
    }

    companion object {
        /** Applies before the test rule launches the DeckPicker */
        @BeforeClass
        @JvmStatic
        fun setUpPreferences() {
            disableIntroductionSlide()
            disableBackupPrompt()
        }
    }

    @Test
    fun drawerNavigatesFromDeckPickerToSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.resources.isWindowCompact())

        val drawerButtonDescription = context.getString(R.string.navigation_drawer_open)
        // The DeckPicker top bar is only composed once the collection has opened
        // (DeckPicker returns early from setContent while it is closed), which can take a
        // while on a cold emulator: 10s timed out on a slow runner, and 30s still flaked
        // once while the duplicate run passed. Wait up to 60s for the worst case.
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule
                .onAllNodesWithContentDescription(drawerButtonDescription)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithContentDescription(drawerButtonDescription)
            .performClick()
        // The drawer opens with an animation: wait for the entry before clicking it.
        val settingsText = context.getString(R.string.settings)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule
                .onAllNodesWithText(settingsText)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(settingsText)
            .performClick()

        // The drawer entry launches the preferences activity
        onView(withId(R.id.settings_container)).checkWithTimeout(matches(isDisplayed()))
    }
}
