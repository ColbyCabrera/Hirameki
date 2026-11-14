// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
        targetContext
            .sharedPrefs()
            .edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
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
            composeTestRule
                .onNode(
                    hasContentDescription(fabToggleDesc) and hasStateDescription(expandedDesc),
                ).assertIsDisplayed()
        }
    }
}
