/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.annotation.SuppressLint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DirectSystemCurrentTimeMillisUsage")
class DeckPickerTest : InstrumentedTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<DeckPicker>()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        addNoteUsingBasicNoteType()
        disableIntroductionSlide()
        discardPreliminaryViews()
    }

    /*
    @Test
    fun checkIfClickOnCountsLayoutOpensStudyOptionsOnMobile() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())

        // For mobile. If it is not a mobile, then test will be ignored.
        assumeTrue(!TestUtils.isTablet)

        // Go to RecyclerView item having "Test Deck" and click on the counts layout
        tapOnCountLayouts("Default")

        // Check if currently open Activity is StudyOptionsActivity
        assertThat(
            activityInstance,
            instanceOf(StudyOptionsActivity::class.java),
        )
    }
     */

    @Test
    fun checkIfStudyOptionsIsDisplayedOnTablet() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())

        // For tablet. If it is not a tablet, then test will be ignored.
        assumeTrue(TestUtils.isTablet)

        // Check if study options are displayed
        // In the new Compose UI, we check for the presence of the "Study" button text.
        composeTestRule.onNodeWithText(testContext.getString(R.string.studyoptions_start)).assertIsDisplayed()
    }
}
