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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.notetype.ManageNoteTypeUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageNoteTypesScreen(
    noteTypes: List<ManageNoteTypeUiModel>,
    onAddNoteType: () -> Unit,
    onShowFields: (ManageNoteTypeUiModel) -> Unit,
    onEditCards: (ManageNoteTypeUiModel) -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    onDelete: (ManageNoteTypeUiModel) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNoteType) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_manage_notetypes_add),
                )
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(noteTypes) { noteType ->
                NoteTypeItem(
                    noteType = noteType,
                    onShowFields = { onShowFields(noteType) },
                    onEditCards = { onEditCards(noteType) },
                    onRename = { onRename(noteType) },
                    onDelete = { onDelete(noteType) },
                )
            }
        }
    }
}

@Composable
fun NoteTypeItem(
    noteType: ManageNoteTypeUiModel,
    onShowFields: () -> Unit,
    onEditCards: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(noteType.name) },
        supportingContent = {
            Text(
                stringResource(
                    R.plurals.manage_notetypes_note_count,
                    noteType.useCount,
                    noteType.useCount
                )
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.fields)) },
                        onClick = {
                            showMenu = false
                            onShowFields()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.cards)) },
                        onClick = {
                            showMenu = false
                            onEditCards()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.rename)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.dialog_positive_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Preview
@Composable
fun PreviewManageNoteTypesScreen() {
    val noteTypes = listOf(
        ManageNoteTypeUiModel(0, "Basic", 1),
        ManageNoteTypeUiModel(1, "Basic (and reversed card)", 2),
        ManageNoteTypeUiModel(2, "Cloze", 3),
    )
    ManageNoteTypesScreen(
        noteTypes = noteTypes,
        onAddNoteType = {},
        onShowFields = {},
        onEditCards = {},
        onRename = {},
        onDelete = {},
    )
}
