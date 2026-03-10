package com.ichi2.anki.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.compose.ApkgExportState
import com.ichi2.anki.dialogs.compose.CardsExportState
import com.ichi2.anki.dialogs.compose.CollectionExportState
import com.ichi2.anki.dialogs.compose.ExportDialog
import com.ichi2.anki.dialogs.compose.NotesExportState
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class ExportDialogFragmentTest : RobolectricTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun legacyExportCheckboxShownOnlyForCollectionAndApkg() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exportFormats = listOf("Collection", "Apkg", "Notes", "Cards")
        val selectedFormat = mutableStateOf(exportFormats[0])
        val legacyLabel = context.getString(R.string.exporting_support_older_anki_versions)

        composeTestRule.setContent {
            AnkiDroidTheme {
                ExportDialog(
                    exportFormats = exportFormats,
                    selectedFormat = selectedFormat.value,
                    onFormatSelected = { selectedFormat.value = it },
                    decks = listOf(DeckNameId("Default", 1)),
                    selectedDeck = DeckNameId("Default", 1),
                    onDeckSelected = {},
                    decksLoading = false,
                    showDeckSelector = true,
                    selectedItemsLabelRes = null,
                    collectionState = CollectionExportState(),
                    onCollectionStateChanged = {},
                    apkgState = ApkgExportState(),
                    onApkgStateChanged = {},
                    notesState = NotesExportState(),
                    onNotesStateChanged = {},
                    cardsState = CardsExportState(),
                    onCardsStateChanged = {},
                    onDismissRequest = {},
                    onConfirm = {},
                )
            }
        }

        // Check Collection (default)
        composeTestRule.onNodeWithText(legacyLabel).assertIsDisplayed()

        // Switch to Apkg
        selectedFormat.value = exportFormats[1]
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(legacyLabel).assertIsDisplayed()

        // Switch to Notes
        selectedFormat.value = exportFormats[2]
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(legacyLabel).assertIsNotDisplayed()

        // Switch to Cards
        selectedFormat.value = exportFormats[3]
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(legacyLabel).assertIsNotDisplayed()
    }
}
