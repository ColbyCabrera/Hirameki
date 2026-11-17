// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.pages.PageFragment
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerImportTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun allowTheDeckPickerToOpen() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
    }

    @Test
    fun importAddShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    @Test
    fun replaceShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    @Test
    fun emptyClipboardDoesNotLaunchCsvImporter() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        val clipboard = deckPicker.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        val cacheFile = File(deckPicker.cacheDir, CLIPBOARD_IMPORT_FILENAME).apply { delete() }

        deckPicker.onSelectedClipboardForImport()

        assertNull(Shadows.shadowOf(deckPicker).nextStartedActivity)
        assertFalse(cacheFile.exists())
    }

    @Test
    fun blankClipboardDoesNotLaunchCsvImporter() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        val clipboard = deckPicker.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("blank", "   \n\t  "))
        val cacheFile = File(deckPicker.cacheDir, CLIPBOARD_IMPORT_FILENAME).apply { delete() }

        deckPicker.onSelectedClipboardForImport()

        assertNull(Shadows.shadowOf(deckPicker).nextStartedActivity)
        assertFalse(cacheFile.exists())
    }

    @Test
    fun clipboardTextWritesCacheFileAndLaunchesCsvImporter() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        val csvContent = "front,back\nhello,world"
        val clipboard = deckPicker.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("csv", csvContent))
        val cacheFile = File(deckPicker.cacheDir, CLIPBOARD_IMPORT_FILENAME).apply { delete() }

        deckPicker.onSelectedClipboardForImport()

        assertTrue(cacheFile.exists())
        assertEquals(csvContent, cacheFile.readText(Charsets.UTF_8))
        val startedIntent = assertNotNull(Shadows.shadowOf(deckPicker).nextStartedActivity)
        assertEquals(SingleFragmentActivity::class.java.name, startedIntent.component?.className)
        assertEquals(
            CsvImporter::class.java.name,
            startedIntent.getStringExtra(SingleFragmentActivity.FRAGMENT_NAME_EXTRA),
        )
        assertEquals(
            "import-csv${cacheFile.absolutePath}",
            startedIntent.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)?.getString(PageFragment.PATH_ARG_KEY),
        )
    }

    private class DeckPickerImport : DeckPicker() {
        private var dialogFragment: AsyncDialogFragment? = null

        fun getAsyncDialogFragmentClass(): Class<*> {
            if (dialogFragment == null) {
                fail("No async fragment shown")
            }
            return dialogFragment!!.javaClass
        }

        override fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
            dialogFragment = newFragment
            super.showAsyncDialogFragment(newFragment)
        }
    }
}
