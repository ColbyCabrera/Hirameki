// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.noteeditor.compose

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class NoteEditorImageOcclusionTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val clicked = mutableListOf<String>()

    @Test
    fun `adding an image occlusion note offers gallery, camera and paste`() {
        showEditor(isAddingNote = true)

        listOf(galleryLabel, cameraLabel, pasteLabel).forEach {
            composeTestRule.onNodeWithText(it).performScrollTo().performClick()
        }

        assertEquals(listOf("gallery", "camera", "paste"), clicked)
        composeTestRule.onNodeWithText(editOcclusionsLabel).assertDoesNotExist()
    }

    @Test
    fun `editing an image occlusion note offers only edit occlusions`() {
        showEditor(isAddingNote = false)

        composeTestRule.onNodeWithText(editOcclusionsLabel).assertExists()
        composeTestRule.onNodeWithText(cameraLabel).assertDoesNotExist()
    }

    @Test
    fun `fields that are not visible are not shown`() {
        showEditor(isAddingNote = true)

        composeTestRule.onNodeWithText(HIDDEN_FIELD).assertDoesNotExist()
        composeTestRule.onNodeWithText(SHOWN_FIELD).assertExists()
    }

    private val galleryLabel get() = targetContext.getString(R.string.multimedia_editor_image_field_editing_galery)
    private val cameraLabel get() = targetContext.getString(R.string.multimedia_editor_image_field_editing_photo)
    private val pasteLabel get() = targetContext.getString(R.string.paste_image_from_clipboard)
    private val editOcclusionsLabel get() = targetContext.getString(R.string.edit_occlusions)

    private fun showEditor(isAddingNote: Boolean) {
        composeTestRule.setContent {
            AnkiDroidTheme {
                NoteEditorScreen(
                    state =
                        NoteEditorState(
                            fields =
                                listOf(
                                    NoteFieldState(name = HIDDEN_FIELD, value = TextFieldValue(""), index = 0, isVisible = false),
                                    NoteFieldState(name = SHOWN_FIELD, value = TextFieldValue(""), index = 1),
                                ),
                            tags = emptyList(),
                            selectedDeckName = "Default",
                            selectedNoteTypeName = "Image Occlusion",
                            isAddingNote = isAddingNote,
                            isImageOcclusion = true,
                        ),
                    availableDecks = listOf("Default"),
                    availableNoteTypes = listOf("Image Occlusion"),
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
                    onImageOcclusionSelectImage = { clicked += "gallery" },
                    onImageOcclusionTakePhoto = { clicked += "camera" },
                    onImageOcclusionPasteImage = { clicked += "paste" },
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
        const val HIDDEN_FIELD = "Occlusion"
        const val SHOWN_FIELD = "Header"
    }
}
