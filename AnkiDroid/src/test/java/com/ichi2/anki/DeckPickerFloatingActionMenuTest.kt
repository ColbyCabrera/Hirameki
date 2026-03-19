/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeckPickerFloatingActionMenuTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun bypassIntro() {
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
    }

    @Test
    fun fabMenuIsDisplayed() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            var fabToggleDesc = ""
            scenario.onActivity { activity ->
                fabToggleDesc = activity.getString(R.string.fab_menu_toggle)
            }
            composeTestRule.onNodeWithContentDescription(fabToggleDesc).assertIsDisplayed()
        }
    }

    @Test
    fun fabMenuExpandsOnClick() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            var fabToggleDesc = ""
            var expandedDesc = ""
            scenario.onActivity { activity ->
                fabToggleDesc = activity.getString(R.string.fab_menu_toggle)
                expandedDesc = activity.getString(R.string.fab_menu_expanded)
            }
            
            // Click the main FAB to expand the menu
            composeTestRule.onNodeWithContentDescription(fabToggleDesc).performClick()

            // Check if the state description is updated to "expanded"
            composeTestRule.onNode(
                androidx.compose.ui.test.hasContentDescription(fabToggleDesc) and 
                androidx.compose.ui.test.hasStateDescription(expandedDesc)
            ).assertIsDisplayed()
        }
    }
}