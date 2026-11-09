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

package com.ichi2.anki.noteeditor.compose

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.noteeditor.ToolbarButtonModel
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorImageOcclusionTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `an image occlusion note hides text editing`() {
        showEditor(isImageOcclusion = true)

        composeTestRule.onNodeWithText(FIELD_NAME).assertDoesNotExist()
        composeTestRule.onNodeWithText(addTagLabel).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(fontSizeLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(editOcclusionsLabel).assertExists()
        composeTestRule.onNodeWithText(CARDS_INFO).assertExists()
    }

    @Test
    fun `other notes keep text editing`() {
        showEditor(isImageOcclusion = false)

        composeTestRule.onNodeWithText(FIELD_NAME).assertExists()
        composeTestRule.onNodeWithText(addTagLabel).assertExists()
        composeTestRule.onNodeWithContentDescription(fontSizeLabel).assertExists()
        composeTestRule.onNodeWithText(editOcclusionsLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(CARDS_INFO).assertExists()
    }

    private val addTagLabel get() = targetContext.getString(R.string.add_tag)
    private val fontSizeLabel get() = targetContext.getString(R.string.format_font_size)
    private val editOcclusionsLabel get() = targetContext.getString(R.string.edit_occlusions)

    private fun showEditor(isImageOcclusion: Boolean) {
        composeTestRule.setContent {
            AnkiDroidTheme {
                NoteEditorScreen(
                    state =
                        NoteEditorState(
                            fields = listOf(NoteFieldState(name = FIELD_NAME, value = TextFieldValue(""), index = 0)),
                            tags = emptyList(),
                            selectedDeckName = "Default",
                            selectedNoteTypeName = "Note type",
                            isAddingNote = false,
                            isImageOcclusion = isImageOcclusion,
                            cardsInfo = CARDS_INFO,
                        ),
                    availableDecks = listOf("Default"),
                    availableNoteTypes = listOf("Note type"),
                    onFieldValueChange = { _, _ -> },
                    onFieldFocus = {},
                    onCardsClick = {},
                    onDeckSelected = {},
                    onNoteTypeSelected = {},
                    onMultimediaClick = {},
                    onToggleStickyClick = {},
                    onSaveClick = {},
                    onPreviewClick = {},
                    onBoldClick = {},
                    onItalicClick = {},
                    onUnderlineClick = {},
                    onClozeClick = {},
                    onClozeIncrementClick = {},
                    onCustomButtonClick = { _: ToolbarButtonModel -> },
                    onCustomButtonLongClick = { _: ToolbarButtonModel -> },
                    onAddCustomButtonClick = {},
                    customToolbarButtons = emptyList(),
                    isToolbarVisible = true,
                    allTags = TagsState.Loaded(emptyList()),
                    deckTags = emptySet(),
                    onUpdateTags = {},
                    onAddTag = {},
                    showDiscardChangesDialog = false,
                    onDiscardChanges = {},
                    onKeepEditing = {},
                    noClozeDialogMessage = null,
                    onSaveAnywayClick = {},
                    onDismissNoClozeDialog = {},
                    showTagsDialog = false,
                    onShowTagsDialogChange = {},
                    showFontSizeDialog = false,
                    onShowFontSizeDialogChange = {},
                    showHeadingDialog = false,
                    onShowHeadingDialogChange = {},
                    showMathJaxDialog = false,
                    onShowMathJaxDialogChange = {},
                    showDeckSelectionDialog = false,
                    onShowDeckSelectionDialogChange = {},
                    onApplyFormatter = { _, _ -> },
                )
            }
        }
    }

    private companion object {
        const val FIELD_NAME = "Header"
        const val CARDS_INFO = "Cards: 1"
    }
}
