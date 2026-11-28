// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <48384865+criticalAY@users.noreply.github.com>

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DatabaseCheckSummaryTest : RobolectricTest() {
    /** Controls the Compose clock: otherwise the screens' endless animations keep the looper busy */
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun allowTheDeckPickerToOpen() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
    }

    @After
    fun resetBackupUtilities() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `confirming a database check keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
        }

    @Test
    fun `checking the database from a sync check error keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_BASIC_CHECK_ERROR, "")
        }

    @Test
    fun `checking the database from a collection too large to sync keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_COLLECTION_TOO_LARGE, "")
        }

    private fun assertSummaryShownAfterStartingCheck(showDialog: DeckPicker.() -> Unit) {
        addDeck("Deck")
        val picker = startRegularActivity<DeckPicker>()
        picker.showDialog()
        advanceRobolectricLooper()
        assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
        advanceRobolectricLooper()

        assertTrue(
            picker.supportFragmentManager.fragments
                .filterIsInstance<SimpleMessageDialog>()
                .any { it.dialog?.isShowing == true },
            "The database check summary was dismissed with the dialog that started the check",
        )
    }
}
