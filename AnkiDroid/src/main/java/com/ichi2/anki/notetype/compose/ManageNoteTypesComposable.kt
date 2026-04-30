/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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
package com.ichi2.anki.notetype.compose

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.notetype.ManageNoteTypeUiModel
import com.ichi2.anki.notetype.ManageNoteTypesUiState
import com.ichi2.anki.ui.compose.components.AnkiSearchBar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun ManageNoteTypesScreen(
    uiState: ManageNoteTypesUiState,
    onSearch: (String) -> Unit,
    onAddNoteType: (String, com.ichi2.anki.notetype.AddNotetypeUiModel) -> Unit,
    onShowFields: (ManageNoteTypeUiModel) -> Unit,
    onEditCards: (ManageNoteTypeUiModel) -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    onDeleteRequest: (ManageNoteTypeUiModel) -> Unit,
    onDeleteConfirm: (ManageNoteTypeUiModel) -> Unit,
    onDeleteDismiss: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onNavigateUp: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass? = null,
) {
    val context = LocalContext.current
    val widthSizeClass = windowWidthSizeClass ?: (context as? Activity)?.let {
        calculateWindowSizeClass(it).widthSizeClass
    }
    val isExpanded =
        widthSizeClass == WindowWidthSizeClass.Expanded || widthSizeClass == WindowWidthSizeClass.Medium

    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedNoteType by remember { mutableStateOf<ManageNoteTypeUiModel?>(null) }

    BackHandler(isSearchOpen) {
        onSearch("")
        isSearchOpen = false
    }

    // Exit multiselect mode on back press
    BackHandler(uiState.isInMultiSelectMode) {
        onDeselectAll()
    }

    val sheetState = rememberModalBottomSheetState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var noteTypeToRename by remember { mutableStateOf<ManageNoteTypeUiModel?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                ManageNoteTypesTopAppBar(
                    searchQuery = uiState.searchQuery,
                    isSearchOpen = isSearchOpen,
                    onSearchOpenChange = { isSearchOpen = it },
                    onSearchQueryChange = onSearch,
                    onNavigateUp = onNavigateUp,
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (!uiState.isInMultiSelectMode) {
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.add_24px),
                            contentDescription = stringResource(id = R.string.cd_manage_notetypes_add),
                        )
                    }
                }
            },
        ) { padding ->
            ManageNoteTypesContent(
                noteTypes = uiState.noteTypes,
                isExpanded = isExpanded,
                padding = padding,
                selectedNoteTypeIds = uiState.selectedNoteTypeIds,
                isInMultiSelectMode = uiState.isInMultiSelectMode,
                onNoteTypeClick = { noteType ->
                    if (uiState.isInMultiSelectMode) {
                        onToggleSelection(noteType.id)
                    } else {
                        selectedNoteType = noteType
                    }
                },
                onNoteTypeLongClick = { noteType ->
                    if (!uiState.isInMultiSelectMode) {
                        onToggleSelection(noteType.id)
                    }
                },
            )

            if (!uiState.isInMultiSelectMode) {
                selectedNoteType?.let { noteType ->
                    NoteTypeActionBottomSheet(
                        noteType = noteType,
                        sheetState = sheetState,
                        onDismissRequest = { selectedNoteType = null },
                        onShowFields = { onShowFields(noteType) },
                        onEditCards = { onEditCards(noteType) },
                        onRename = { noteTypeToRename = noteType },
                        onDelete = {
                            onDeleteRequest(noteType)
                            selectedNoteType = null
                        })
                }
            }

            noteTypeToRename?.let { noteType ->
                RenameNoteTypeDialog(
                    noteType = noteType,
                    onDismissRequest = { noteTypeToRename = null },
                    onRename = onRename
                )
            }

            uiState.deleteConfirmationNoteType?.let { noteType ->
                DeleteNoteTypeDialog(
                    noteType = noteType,
                    onDismissRequest = onDeleteDismiss,
                    onDelete = onDeleteConfirm
                )
            }

            if (showAddDialog) {
                AddNoteTypeDialog(
                    uiState = uiState,
                    onDismissRequest = { showAddDialog = false },
                    onConfirm = { name, option ->
                        onAddNoteType(name, option)
                    })
            }
        }

        // Floating selection toolbar — shown at bottom when in multiselect mode
        if (uiState.isInMultiSelectMode) {
            NoteTypeSelectionToolbar(
                onDeselectAll = onDeselectAll,
                onSelectAll = onSelectAll,
                onDeleteSelected = onDeleteSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -ScreenOffset),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteTypeSelectionToolbar(
    onDeselectAll: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onDeselectAll,
                shape = FloatingActionButtonDefaults.smallShape,
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.deselect_all)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.deselect_24px),
                        contentDescription = stringResource(R.string.deselect_all),
                    )
                }
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip { Text(stringResource(R.string.card_browser_select_all)) }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        painter = painterResource(R.drawable.select_all_24px),
                        contentDescription = stringResource(R.string.card_browser_select_all),
                    )
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip { Text(stringResource(R.string.model_browser_delete)) }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.model_browser_delete),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageNoteTypesTopAppBar(
    searchQuery: String,
    isSearchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val searchAnim by animateFloatAsState(
        targetValue = if (isSearchOpen) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "searchAnim"
    )

    LargeFlexibleTopAppBar(
        modifier = modifier, title = {
        if (!isSearchOpen) {
            Text(
                stringResource(R.string.model_browser_label),
                style = MaterialTheme.typography.displayMediumEmphasized,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - searchAnim
                })
        }
    }, navigationIcon = {
        if (!isSearchOpen) {
            FilledIconButton(
                onClick = onNavigateUp,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        }
    }, actions = {
        if (isSearchOpen) {
            AnkiSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = { /* Done as user types */ },
                onActiveChange = onSearchOpenChange,
                placeholder = stringResource(R.string.card_browser_search_hint),
                focusRequester = searchFocusRequester,
                searchAnim = searchAnim,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp, bottom = 8.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        } else {
            FilledIconButton(
                onClick = { onSearchOpenChange(true) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search_24px),
                    contentDescription = stringResource(id = R.string.menu_search)
                )
            }
        }
    }, scrollBehavior = scrollBehavior
    )
}

@Composable
fun ManageNoteTypesContent(
    noteTypes: List<ManageNoteTypeUiModel>,
    isExpanded: Boolean,
    padding: PaddingValues,
    selectedNoteTypeIds: Set<Long>,
    isInMultiSelectMode: Boolean,
    onNoteTypeClick: (ManageNoteTypeUiModel) -> Unit,
    onNoteTypeLongClick: (ManageNoteTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val combinedPadding = PaddingValues(
        start = 8.dp,
        top = padding.calculateTopPadding() + 24.dp,
        end = 8.dp,
        bottom = padding.calculateBottomPadding() + if (isInMultiSelectMode) 96.dp else 24.dp
    )

    if (isExpanded) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = combinedPadding,
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(noteTypes, key = { it.id }) { noteType ->
                NoteTypeItem(
                    noteType = noteType,
                    onClick = { onNoteTypeClick(noteType) },
                    isSelected = selectedNoteTypeIds.contains(noteType.id),
                    isInMultiSelectMode = isInMultiSelectMode,
                    onLongClick = { onNoteTypeLongClick(noteType) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = combinedPadding
        ) {
            items(noteTypes, key = { it.id }) { noteType ->
                NoteTypeItem(
                    noteType = noteType,
                    onClick = { onNoteTypeClick(noteType) },
                    isSelected = selectedNoteTypeIds.contains(noteType.id),
                    isInMultiSelectMode = isInMultiSelectMode,
                    onLongClick = { onNoteTypeLongClick(noteType) },
                )
            }
        }
    }
}

@Composable
fun RenameNoteTypeDialog(
    noteType: ManageNoteTypeUiModel,
    onDismissRequest: () -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by remember { mutableStateOf(noteType.name) }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.rename_model)) },
        text = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.note_type_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(noteType.copy(name = newName))
                    onDismissRequest()
                }, enabled = newName.isNotBlank() && newName != noteType.name
            ) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        })
}

@Composable
fun DeleteNoteTypeDialog(
    noteType: ManageNoteTypeUiModel,
    onDismissRequest: () -> Unit,
    onDelete: (ManageNoteTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.model_browser_delete)) },
        text = {
            Text(stringResource(R.string.model_delete_warning))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete(noteType)
                    onDismissRequest()
                }) {
                Text(stringResource(R.string.dialog_positive_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        })
}

@Composable
fun DeleteSelectedNoteTypesDialog(
    count: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.model_browser_delete)) },
        text = {
            Text(pluralStringResource(R.plurals.model_delete_selected_warning, count, count))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                }) {
                Text(stringResource(R.string.dialog_positive_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteTypeDialog(
    uiState: ManageNoteTypesUiState,
    onDismissRequest: () -> Unit,
    onConfirm: (String, com.ichi2.anki.notetype.AddNotetypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf(uiState.addOptions.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedOption?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.note_type_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable, true
                            )
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded, onDismissRequest = { expanded = false }) {
                        uiState.addOptions.forEach { option ->
                            DropdownMenuItem(text = {
                                val prefixRes = if (option.isStandard) {
                                    R.string.model_browser_add_add
                                } else {
                                    R.string.model_browser_add_clone
                                }
                                Text(stringResource(prefixRes, option.name))
                            }, onClick = {
                                selectedOption = option
                                expanded = false
                                if (newName.isEmpty()) {
                                    newName = option.name + "-new"
                                }
                            })
                        }
                    }
                }

                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.note_type_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedOption?.let { onConfirm(newName, it) }
                    onDismissRequest()
                }, enabled = newName.isNotBlank() && selectedOption != null
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        })
}

@Preview
@Composable
fun PreviewManageNoteTypesScreen() {
    val uiState = ManageNoteTypesUiState(
        noteTypes = listOf(
            ManageNoteTypeUiModel(0, "Basic", 1),
            ManageNoteTypeUiModel(1, "Basic (and reversed card)", 2),
            ManageNoteTypeUiModel(2, "Cloze", 3),
        )
    )
    AnkiDroidTheme {
        ManageNoteTypesScreen(
            uiState = uiState,
            onSearch = {},
            onAddNoteType = { _, _ -> },
            onShowFields = {},
            onEditCards = {},
            onRename = {},
            onDeleteRequest = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onToggleSelection = {},
            onSelectAll = {},
            onDeselectAll = {},
            onDeleteSelected = {},
            onNavigateUp = {},
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun PreviewManageNoteTypesScreenExpanded() {
    val uiState = ManageNoteTypesUiState(
        noteTypes = listOf(
            ManageNoteTypeUiModel(0, "Basic", 1),
            ManageNoteTypeUiModel(1, "Basic (and reversed card)", 2),
            ManageNoteTypeUiModel(2, "Cloze", 3),
            ManageNoteTypeUiModel(3, "Japanese Basic", 4),
            ManageNoteTypeUiModel(4, "Medical Note", 5),
            ManageNoteTypeUiModel(5, "Anatomy", 6),
        )
    )
    AnkiDroidTheme {
        ManageNoteTypesScreen(
            uiState = uiState,
            onSearch = {},
            onAddNoteType = { _, _ -> },
            onShowFields = {},
            onEditCards = {},
            onRename = {},
            onDeleteRequest = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onToggleSelection = {},
            onSelectAll = {},
            onDeselectAll = {},
            onDeleteSelected = {},
            onNavigateUp = {},
            windowWidthSizeClass = WindowWidthSizeClass.Expanded
        )
    }
}
