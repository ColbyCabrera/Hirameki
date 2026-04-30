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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onAddNoteType: (String, com.ichi2.anki.notetype.AddNotetypeUiModel) -> Unit,
    onShowFields: (ManageNoteTypeUiModel) -> Unit,
    onEditCards: (ManageNoteTypeUiModel) -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    onDelete: (ManageNoteTypeUiModel) -> Unit,
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
    val sheetState = rememberModalBottomSheetState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var noteTypeToRename by remember { mutableStateOf<ManageNoteTypeUiModel?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.add_24px),
                    contentDescription = stringResource(id = R.string.cd_manage_notetypes_add),
                )
            }
        },
    ) { padding ->
        ManageNoteTypesContent(
            noteTypes = uiState.noteTypes,
            isExpanded = isExpanded,
            padding = padding,
            onNoteTypeClick = { selectedNoteType = it })

        selectedNoteType?.let { noteType ->
            NoteTypeActionBottomSheet(
                noteType = noteType,
                sheetState = sheetState,
                onDismissRequest = { selectedNoteType = null },
                onShowFields = { onShowFields(noteType) },
                onEditCards = { onEditCards(noteType) },
                onRename = { noteTypeToRename = noteType },
                onDelete = { onDelete(noteType) })
        }

        noteTypeToRename?.let { noteType ->
            RenameNoteTypeDialog(
                noteType = noteType,
                onDismissRequest = { noteTypeToRename = null },
                onRename = onRename
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
                placeholder = stringResource(R.string.search_decks), // Using search_decks as placeholder for now
                focusRequester = searchFocusRequester,
                searchAnim = searchAnim,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp, bottom = 8.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        } else {
            IconButton(onClick = { onSearchOpenChange(true) }) {
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
    onNoteTypeClick: (ManageNoteTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isExpanded) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            contentPadding = padding,
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(noteTypes, key = { it.id }) { noteType ->
                NoteTypeItem(
                    noteType = noteType, onClick = { onNoteTypeClick(noteType) })
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = padding
        ) {
            items(noteTypes, key = { it.id }) { noteType ->
                NoteTypeItem(
                    noteType = noteType, onClick = { onNoteTypeClick(noteType) })
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
                                val prefixTemplate = if (option.isStandard) {
                                    stringResource(R.string.model_browser_add_add)
                                } else {
                                    stringResource(R.string.model_browser_add_clone)
                                }
                                Text(prefixTemplate.replace($$"%1$s", option.name))
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
            onRefresh = {},
            onSearch = {},
            onAddNoteType = { _, _ -> },
            onShowFields = {},
            onEditCards = {},
            onRename = {},
            onDelete = {},
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
            onRefresh = {},
            onSearch = {},
            onAddNoteType = { _, _ -> },
            onShowFields = {},
            onEditCards = {},
            onRename = {},
            onDelete = {},
            onNavigateUp = {},
            windowWidthSizeClass = WindowWidthSizeClass.Expanded
        )
    }
}
