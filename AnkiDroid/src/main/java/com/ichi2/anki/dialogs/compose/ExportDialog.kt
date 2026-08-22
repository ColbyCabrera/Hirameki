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
package com.ichi2.anki.dialogs.compose

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckNameId
import kotlinx.parcelize.Parcelize

// State holder classes for checkbox groups
@Parcelize
data class CollectionExportState(
    val includeMedia: Boolean = true,
    val supportOlderVersions: Boolean = false,
) : Parcelable

@Parcelize
data class ApkgExportState(
    val includeScheduling: Boolean = true,
    val includeDeckConfigs: Boolean = false,
    val includeMedia: Boolean = true,
    val supportOlderVersions: Boolean = false,
) : Parcelable

@Parcelize
data class NotesExportState(
    val includeHtml: Boolean = true,
    val includeTags: Boolean = true,
    val includeDeckName: Boolean = false,
    val includeNotetypeName: Boolean = false,
    val includeGuid: Boolean = false,
) : Parcelable

@Parcelize
data class CardsExportState(
    val includeHtml: Boolean = true,
) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    exportFormats: List<String>,
    selectedFormat: String,
    onFormatSelected: (String) -> Unit,
    decks: List<DeckNameId>,
    selectedDeck: DeckNameId?,
    onDeckSelected: (DeckNameId) -> Unit,
    decksLoading: Boolean,
    showDeckSelector: Boolean,
    selectedItemsLabelRes: Int?,
    collectionState: CollectionExportState,
    onCollectionStateChanged: (CollectionExportState) -> Unit,
    apkgState: ApkgExportState,
    onApkgStateChanged: (ApkgExportState) -> Unit,
    notesState: NotesExportState,
    onNotesStateChanged: (NotesExportState) -> Unit,
    cardsState: CardsExportState,
    onCardsStateChanged: (CardsExportState) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        val titleRes = when (exportFormats.indexOf(selectedFormat)) {
            0 -> R.string.export_collection
            else -> R.string.export_deck
        }
        Text(text = stringResource(titleRes))
    }, confirmButton = {
        TextButton(
            onClick = onConfirm,
            enabled = !decksLoading,
        ) {
            Text(text = stringResource(R.string.dialog_ok))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(R.string.dialog_cancel))
        }
    }, text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            // I'm not using HtmlCompat.fromHtml here because it's not directly supported in Compose.
            // The strings will be plain text. If HTML is required, a more complex solution is needed.
            Text(text = stringResource(R.string.exporting_export_format))
            DropdownSelector(
                options = exportFormats,
                selectedOption = selectedFormat,
                onOptionSelected = onFormatSelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.exporting_include))

            if (showDeckSelector) {
                DropdownSelector(
                    options = decks.map { it.name },
                    selectedOption = selectedDeck?.name ?: "",
                    onOptionSelected = { name ->
                        decks.find { it.name == name }?.let { onDeckSelected(it) }
                    },
                    loading = decksLoading,
                )
            }

            selectedItemsLabelRes?.let { labelRes ->
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // This feels a bit clumsy, but it mirrors the logic of showing/hiding the layouts
            // based on the selected export format index.
            when (exportFormats.indexOf(selectedFormat)) {
                0 -> CollectionExportOptions(collectionState, onCollectionStateChanged)
                1 -> ApkgExportOptions(apkgState, onApkgStateChanged)
                2 -> NotesExportOptions(notesState, onNotesStateChanged)
                3 -> CardsExportOptions(cardsState, onCardsStateChanged)
            }
        }
    })
}

@Composable
fun CollectionExportOptions(
    state: CollectionExportState,
    onStateChanged: (CollectionExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_media),
            checked = state.includeMedia,
            onCheckedChange = { onStateChanged(state.copy(includeMedia = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_support_older_anki_versions),
            checked = state.supportOlderVersions,
            onCheckedChange = { onStateChanged(state.copy(supportOlderVersions = it)) },
        )
    }
}

@Composable
fun ApkgExportOptions(
    state: ApkgExportState,
    onStateChanged: (ApkgExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_scheduling_information),
            checked = state.includeScheduling,
            onCheckedChange = { onStateChanged(state.copy(includeScheduling = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_deck_configs),
            checked = state.includeDeckConfigs,
            onCheckedChange = { onStateChanged(state.copy(includeDeckConfigs = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_media),
            checked = state.includeMedia,
            onCheckedChange = { onStateChanged(state.copy(includeMedia = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_support_older_anki_versions),
            checked = state.supportOlderVersions,
            onCheckedChange = { onStateChanged(state.copy(supportOlderVersions = it)) },
        )
    }
}

@Composable
fun NotesExportOptions(
    state: NotesExportState,
    onStateChanged: (NotesExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_html_and_media_references),
            checked = state.includeHtml,
            onCheckedChange = { onStateChanged(state.copy(includeHtml = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_tags),
            checked = state.includeTags,
            onCheckedChange = { onStateChanged(state.copy(includeTags = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_deck),
            checked = state.includeDeckName,
            onCheckedChange = { onStateChanged(state.copy(includeDeckName = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_notetype),
            checked = state.includeNotetypeName,
            onCheckedChange = { onStateChanged(state.copy(includeNotetypeName = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_guid),
            checked = state.includeGuid,
            onCheckedChange = { onStateChanged(state.copy(includeGuid = it)) },
        )
    }
}

@Composable
fun CardsExportOptions(
    state: CardsExportState,
    onStateChanged: (CardsExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_html_and_media_references),
            checked = state.includeHtml,
            onCheckedChange = { onStateChanged(state.copy(includeHtml = it)) },
        )
    }
}

@Composable
fun CheckboxWithLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                readOnly = true,
                value = selectedOption,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = MaterialTheme.shapes.medium
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = selectionOption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onOptionSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExportDialogPreview() {
    val exportFormats =
        listOf("Collection (.colpkg)", "Deck (.apkg)", "Notes (.txt)", "Cards (.txt)")
    val decks =
        listOf(DeckNameId("All Decks", 0), DeckNameId("Default", 1), DeckNameId("French", 2))

    var selectedFormat by remember { mutableStateOf(exportFormats[1]) }
    var selectedDeck by remember { mutableStateOf(decks[0]) }
    var decksLoading by remember { mutableStateOf(false) }
    var showDeckSelector by remember { mutableStateOf(true) }
    var selectedItemsLabelRes by remember { mutableStateOf<Int?>(null) }

    var collectionState by remember { mutableStateOf(CollectionExportState()) }
    var apkgState by remember { mutableStateOf(ApkgExportState()) }
    var notesState by remember { mutableStateOf(NotesExportState()) }
    var cardsState by remember { mutableStateOf(CardsExportState()) }


    ExportDialog(
        exportFormats = exportFormats,
        selectedFormat = selectedFormat,
        onFormatSelected = { selectedFormat = it },
        decks = decks,
        selectedDeck = selectedDeck,
        onDeckSelected = { selectedDeck = it },
        decksLoading = decksLoading,
        showDeckSelector = showDeckSelector,
        selectedItemsLabelRes = selectedItemsLabelRes,
        collectionState = collectionState,
        onCollectionStateChanged = { collectionState = it },
        apkgState = apkgState,
        onApkgStateChanged = { apkgState = it },
        notesState = notesState,
        onNotesStateChanged = { notesState = it },
        cardsState = cardsState,
        onCardsStateChanged = { cardsState = it },
        onDismissRequest = {},
        onConfirm = {},
    )

}
