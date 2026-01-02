/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 * Copyright (c) 2025 Colby Cabrera <colbycabrera@gmail.com>                            *
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
package com.ichi2.anki.noteeditor.compose

import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.DiscardChangesDialog
import com.ichi2.anki.dialogs.compose.TagsDialog
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.noteeditor.ToolbarButtonModel
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * Data class representing the state of a note editor field
 */
data class NoteFieldState(
    val name: String,
    val value: TextFieldValue,
    val isSticky: Boolean = false,
    val hint: String = "",
    val index: Int,
)

/**
 * Data class representing the complete note editor state
 */
data class NoteEditorState(
    val fields: List<NoteFieldState>,
    val tags: List<String>,
    val selectedDeckName: String,
    val selectedNoteTypeName: String,
    val isAddingNote: Boolean = true,
    val isClozeType: Boolean = false,
    val isImageOcclusion: Boolean = false,
    val cardsInfo: String = "",
    val focusedFieldIndex: Int? = null,
    val isTagsButtonEnabled: Boolean = true,
    val isCardsButtonEnabled: Boolean = true,
)

/**
 * Main Note Editor Screen with Material 3 Components
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    modifier: Modifier = Modifier,
    state: NoteEditorState,
    availableDecks: List<String>,
    availableNoteTypes: List<String>,
    onFieldValueChange: (Int, TextFieldValue) -> Unit,
    onFieldFocus: (Int) -> Unit,
    onCardsClick: () -> Unit,
    onDeckSelected: (String) -> Unit,
    onNoteTypeSelected: (String) -> Unit,
    onMultimediaClick: (Int) -> Unit,
    onToggleStickyClick: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onHorizontalRuleClick: () -> Unit = {},
    onHeadingClick: () -> Unit = {},
    onFontSizeClick: () -> Unit = {},
    onMathjaxClick: () -> Unit = {},
    onMathjaxLongClick: (() -> Unit)? = null,
    onClozeClick: () -> Unit,
    onClozeIncrementClick: () -> Unit,
    onCustomButtonClick: (ToolbarButtonModel) -> Unit,
    onCustomButtonLongClick: (ToolbarButtonModel) -> Unit,
    onAddCustomButtonClick: () -> Unit,
    customToolbarButtons: List<ToolbarButtonModel>,
    isToolbarVisible: Boolean,
    onImageOcclusionSelectImage: () -> Unit = {},
    onImageOcclusionPasteImage: () -> Unit = {},
    onImageOcclusionEdit: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    topBar: (@Composable () -> Unit)? = null,
    allTags: TagsState,
    deckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onAddTag: (String) -> Unit,
    showDiscardChangesDialog: Boolean,
    onDiscardChanges: () -> Unit,
    onKeepEditing: () -> Unit,
    noClozeDialogMessage: String?,
    onSaveAnywayClick: () -> Unit,
    onDismissNoClozeDialog: () -> Unit,
) {
    // Observe keyboard state for auto-scrolling
    val imeState = rememberImeState()
    val scrollState = rememberScrollState()
    var showTagsDialog by remember { mutableStateOf(false) }
    var filterByDeck by remember { mutableStateOf(true) }

    if (showTagsDialog) {
        TagsDialog(
            onDismissRequest = { showTagsDialog = false },
            onConfirm = { checked, _ ->
                onUpdateTags(checked)
                showTagsDialog = false
            },
            allTags = allTags,
            initialSelection = state.tags.toSet(),
            initialIndeterminate = emptySet(),
            initialFilterByDeck = filterByDeck,
            deckTags = deckTags,
            showFilterByDeckToggle = true,
            title = stringResource(id = R.string.note_editor_tags_title),
            confirmButtonText = stringResource(id = R.string.dialog_ok),
            onAddTag = onAddTag,
            onFilterByDeckChanged = { filterByDeck = it },
        )
    }

    if (showDiscardChangesDialog) {
        DiscardChangesDialog(
            onDismissRequest = onKeepEditing, // Tapping outside is same as keeping
            onConfirm = onDiscardChanges,
            onKeepEditing = onKeepEditing,
        )
    }

    // Show no-cloze confirmation dialog when message is non-null
    noClozeDialogMessage?.let { message ->
        NoClozeConfirmationDialog(
            message = message,
            onDismissRequest = onDismissNoClozeDialog,
            onSaveAnyway = onSaveAnywayClick,
        )
    }

    // Auto-scroll when keyboard appears
    LaunchedEffect(key1 = imeState.value) {
        if (imeState.value) {
            scrollState.animateScrollTo(scrollState.maxValue, tween(300))
        }
    }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            topBar?.invoke()
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
        bottomBar = {
            NoteEditorToolbar(
                isClozeType = state.isClozeType,
                onBoldClick = onBoldClick,
                onItalicClick = onItalicClick,
                onUnderlineClick = onUnderlineClick,
                onHorizontalRuleClick = onHorizontalRuleClick,
                onHeadingClick = onHeadingClick,
                onFontSizeClick = onFontSizeClick,
                onMathjaxClick = onMathjaxClick,
                onMathjaxLongClick = onMathjaxLongClick,
                onClozeClick = onClozeClick,
                onClozeIncrementClick = onClozeIncrementClick,
                onCustomButtonClick = onCustomButtonClick,
                onCustomButtonLongClick = onCustomButtonLongClick,
                onAddCustomButtonClick = onAddCustomButtonClick,
                customButtons = customToolbarButtons,
                isVisible = isToolbarVisible,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Note Type Selector
                    NoteTypeSelector(
                        selectedNoteType = state.selectedNoteTypeName,
                        availableNoteTypes = availableNoteTypes,
                        onNoteTypeSelected = onNoteTypeSelected,
                    )

                    // Deck Selector
                    DeckSelector(
                        selectedDeck = state.selectedDeckName,
                        availableDecks = availableDecks,
                        onDeckSelected = onDeckSelected,
                    )
                }
            }

            // Fields Editor
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.fields.forEach { field ->
                        NoteFieldEditor(
                            field = field,
                            onValueChange = { newValue ->
                                onFieldValueChange(field.index, newValue)
                            },
                            onMultimediaClick = { onMultimediaClick(field.index) },
                            onToggleStickyClick = { onToggleStickyClick(field.index) },
                            showStickyButton = state.isAddingNote,
                            onFocus = { onFieldFocus(field.index) },
                            isFocused = state.focusedFieldIndex == field.index,
                        )
                    }
                }
            }

            // Image Occlusion Buttons (if applicable)
            if (state.isImageOcclusion) {
                if (state.isAddingNote) {
                    ImageOcclusionButtons(
                        onSelectImage = onImageOcclusionSelectImage,
                        onPasteImage = onImageOcclusionPasteImage,
                    )
                } else {
                    Button(
                        onClick = onImageOcclusionEdit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Text(stringResource(R.string.edit_occlusions))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Tags Button
                Button(
                    onClick = { showTagsDialog = true },
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f),
                    enabled = state.isTagsButtonEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(
                        text = if (state.tags.isEmpty()) {
                            stringResource(R.string.add_tag)
                        } else {
                            stringResource(
                                R.string.note_editor_tags_list,
                                state.tags.joinToString(", "),
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Cards Button
                Button(
                    onClick = onCardsClick,
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(max = 164.dp),
                    enabled = state.isCardsButtonEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(
                        text = state.cardsInfo.ifEmpty { stringResource(R.string.CardEditorCards) },
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Note Type Selector Dropdown
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteTypeSelector(
    selectedNoteType: String,
    availableNoteTypes: List<String>,
    onNoteTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Ensure dropdown is dismissed when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            expanded = false
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedNoteType,
            label = { Text(stringResource(R.string.CardEditorModel)) },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor(
                    type = PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            availableNoteTypes.forEach { noteType ->
                DropdownMenuItem(text = { Text(noteType) }, onClick = {
                    onNoteTypeSelected(noteType)
                    expanded = false
                })
            }
        }
    }
}

/**
 * Deck Selector Dropdown
 */

/**
 * Deck selector dropdown with hierarchy support (matches Note Type Selector style)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeckSelector(
    selectedDeck: String,
    availableDecks: List<String>,
    onDeckSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Ensure dropdown is dismissed when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            expanded = false
        }
    }

    // Build deck hierarchy from flat list
    val deckHierarchy = remember(availableDecks) {
        buildDeckHierarchy(availableDecks)
    }

    val expandedDecks = remember { mutableStateMapOf<String, Boolean>() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedDeck,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.CardEditorNoteDeck)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor(
                    type = PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DeckHierarchyMenuItems(
                deckHierarchy = deckHierarchy,
                expandedDecks = expandedDecks,
                onDeckSelected = { deckName ->
                    onDeckSelected(deckName)
                    expanded = false
                },
            )
        }
    }
}

/**
 * Build a hierarchical map of decks from a flat list
 */
private fun buildDeckHierarchy(deckNames: List<String>): Map<String, List<String>> {
    val hierarchy = mutableMapOf<String, MutableList<String>>()
    val topLevelDecks = mutableListOf<String>()

    for (deckName in deckNames) {
        val parts = deckName.split("::")
        if (parts.size > 1) {
            val parentName = parts.dropLast(1).joinToString("::")
            hierarchy.getOrPut(parentName) { mutableListOf() }.add(deckName)
        } else {
            topLevelDecks.add(deckName)
        }
    }

    hierarchy[""] = topLevelDecks
    return hierarchy
}

/**
 * Recursive composable to render deck hierarchy with expand/collapse
 */
@Composable
private fun DeckHierarchyMenuItems(
    deckHierarchy: Map<String, List<String>>,
    expandedDecks: MutableMap<String, Boolean>,
    onDeckSelected: (String) -> Unit,
    parentName: String = "",
) {
    val children = deckHierarchy[parentName] ?: return

    for (deckName in children) {
        val isExpanded = expandedDecks[deckName] ?: false
        val hasChildren = deckHierarchy.containsKey(deckName)

        DropdownMenuItem(
            text = { Text(deckName.substringAfterLast("::")) },
            onClick = { onDeckSelected(deckName) },
            trailingIcon = {
                if (hasChildren) {
                    IconButton(onClick = { expandedDecks[deckName] = !isExpanded }) {
                        Icon(
                            painter = if (isExpanded) {
                                painterResource(R.drawable.keyboard_arrow_down_24px)
                            } else {
                                painterResource(R.drawable.keyboard_arrow_right_24px)
                            },
                            contentDescription = if (isExpanded) {
                                stringResource(R.string.collapse)
                            } else {
                                stringResource(R.string.expand)
                            },
                        )
                    }
                }
            },
        )
        if (isExpanded && hasChildren) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                DeckHierarchyMenuItems(deckHierarchy, expandedDecks, onDeckSelected, deckName)
            }
        }
    }
}

/**
 * Individual Field Editor with multimedia and sticky support
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteFieldEditor(
    field: NoteFieldState,
    onValueChange: (TextFieldValue) -> Unit,
    onMultimediaClick: () -> Unit,
    onToggleStickyClick: () -> Unit,
    showStickyButton: Boolean,
    onFocus: () -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Row {
                IconButton(onClick = onMultimediaClick) {
                    Icon(
                        imageVector = Icons.Default.Attachment,
                        contentDescription = stringResource(R.string.multimedia_editor_attach_tooltip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showStickyButton) {
                    IconButton(onClick = onToggleStickyClick) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.note_editor_toggle_sticky_field),
                            tint = if (field.isSticky) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = field.value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                    }
                },
            placeholder = { Text(field.hint) },
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            maxLines = 10,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.tertiaryContainer,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.tertiary,
            ),
        )
    }
}

/**
 * Image Occlusion specific buttons
 */
@Composable
fun ImageOcclusionButtons(
    onSelectImage: () -> Unit,
    onPasteImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSelectImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.choose_an_image))
        }
        Button(
            onClick = onPasteImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.paste_image_from_clipboard))
        }
    }
}

/**
 * Preview for development
 */
@Preview(showBackground = true)
@Composable
fun NoteEditorScreenPreview() {
    val snackbarHostState = remember { SnackbarHostState() }
    AnkiDroidTheme {
        NoteEditorScreen(
            state = NoteEditorState(
                fields = listOf(
                    NoteFieldState(
                        name = "Front",
                        value = TextFieldValue("Sample front text"),
                        index = 0,
                    ),
                    NoteFieldState(
                        name = "Back",
                        value = TextFieldValue("Sample back text"),
                        index = 1,
                    ),
                ),
                tags = listOf("Tag1", "Tag2"),
                selectedDeckName = "Default",
                selectedNoteTypeName = "Basic",
                cardsInfo = "Cards: 1",
            ),
            availableDecks = listOf("Default", "Deck 2", "Deck 3"),
            availableNoteTypes = listOf("Basic", "Basic (and reversed card)", "Cloze"),
            onFieldValueChange = { _, _ -> },
            onFieldFocus = {},
            onCardsClick = { },
            onDeckSelected = { },
            onNoteTypeSelected = { },
            onMultimediaClick = { },
            onToggleStickyClick = { },
            onSaveClick = { },
            onPreviewClick = { },
            onBoldClick = {},
            onItalicClick = {},
            onUnderlineClick = {},
            onHorizontalRuleClick = {},
            onHeadingClick = {},
            onFontSizeClick = {},
            onMathjaxClick = {},
            onMathjaxLongClick = {},
            onClozeClick = {},
            onClozeIncrementClick = {},
            onCustomButtonClick = {},
            onCustomButtonLongClick = {},
            onAddCustomButtonClick = {},
            customToolbarButtons = emptyList(),
            isToolbarVisible = true,
            snackbarHostState = snackbarHostState,
            allTags = TagsState.Loaded(listOf("Tag1", "Tag2", "Tag3")),
            deckTags = setOf("Tag1", "Tag2"),
            onUpdateTags = {},
            onAddTag = {},
            showDiscardChangesDialog = false,
            onDiscardChanges = {},
            onKeepEditing = {},
            noClozeDialogMessage = null,
            onSaveAnywayClick = {},
            onDismissNoClozeDialog = {},
        )
    }
}
