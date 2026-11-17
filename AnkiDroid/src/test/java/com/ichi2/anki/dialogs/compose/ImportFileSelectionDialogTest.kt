// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportEntry
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportFileType
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImportFileSelectionDialogTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEntriesAndTriggersCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries =
            listOf(
                ImportEntry(
                    titleRes = R.string.import_deck_package,
                    analyticsId = UsageAnalytics.Actions.IMPORT_APKG_FILE,
                    type = ImportFileType.APKG,
                ),
                ImportEntry(
                    titleRes = R.string.import_collection_package,
                    analyticsId = UsageAnalytics.Actions.IMPORT_COLPKG_FILE,
                    type = ImportFileType.COLPKG,
                ),
                ImportEntry(
                    titleRes = R.string.import_csv,
                    analyticsId = UsageAnalytics.Actions.IMPORT_CSV_FILE,
                    type = ImportFileType.CSV,
                ),
                ImportEntry(
                    titleRes = R.string.import_clipboard,
                    analyticsId = UsageAnalytics.Actions.IMPORT_CLIPBOARD,
                    type = ImportFileType.CLIPBOARD,
                ),
            )
        var clickedEntry: ImportEntry? = null
        var dismissed = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                ImportFileSelectionDialog(
                    entries = entries,
                    onDismissRequest = { dismissed = true },
                    onEntryClick = { clickedEntry = it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.menu_import)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.import_deck_package)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.import_collection_package)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.import_csv)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.import_clipboard)).assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.import_csv)).performClick()
        assertEquals(ImportFileType.CSV, clickedEntry?.type)

        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun buildImportEntriesForFullImportOptionsDoesNotAllowMultiple() {
        val fragment =
            ImportFileSelectionFragment.newInstance(
                ImportOptions(
                    importColpkg = true,
                    importApkg = true,
                    importTextFile = true,
                ),
            )

        val entries = fragment.buildImportEntries()

        assertEquals(
            listOf(
                ImportFileType.APKG,
                ImportFileType.COLPKG,
                ImportFileType.CSV,
                ImportFileType.CLIPBOARD,
            ),
            entries.map { it.type },
        )
        entries.forEach { entry ->
            assertFalse(entry.multiple, "${entry.type} should not allow multiple files")
        }
    }

    @Test
    fun buildImportEntriesForRestoreBackupOnlyIncludesColpkgSingleFile() {
        val fragment =
            ImportFileSelectionFragment.newInstance(
                ImportOptions(
                    importColpkg = true,
                    importApkg = false,
                    importTextFile = false,
                ),
            )

        val entries = fragment.buildImportEntries()

        assertEquals(listOf(ImportFileType.COLPKG), entries.map { it.type })
        assertFalse(entries.single().multiple)
    }

    @Test
    fun buildImportEntriesOmitsClipboardWhenDisabledExplicitly() {
        val fragment =
            ImportFileSelectionFragment.newInstance(
                ImportOptions(
                    importColpkg = false,
                    importApkg = false,
                    importTextFile = true,
                    importClipboard = false,
                ),
            )

        val entries = fragment.buildImportEntries()

        assertEquals(listOf(ImportFileType.CSV), entries.map { it.type })
    }
}
