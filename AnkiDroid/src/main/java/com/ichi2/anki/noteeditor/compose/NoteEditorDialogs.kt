/*
 Copyright (c) 2025 Colby Cabrera <colbycabrera@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A Composable dialog that warns the user when they're saving a note with a cloze note type
 * but without any cloze deletions in the fields.
 *
 * @param message The error/warning message to display
 * @param onDismissRequest Called when the user taps outside the dialog or presses back/cancel
 * @param onSaveAnyway Called when the user chooses to save despite the warning
 */
@Composable
fun NoClozeConfirmationDialog(
    message: String,
    onDismissRequest: () -> Unit,
    onSaveAnyway: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onSaveAnyway) {
                Text(text = TR.actionsSave())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun NoClozeConfirmationDialogPreview() {
    AnkiDroidTheme {
        NoClozeConfirmationDialog(
            message = "You have a cloze deletion note type, but the text you have entered does not contain any cloze deletions.",
            onDismissRequest = {},
            onSaveAnyway = {},
        )
    }
}

/**
 * Data class representing the state for the Add/Edit Toolbar Item dialog
 */
data class ToolbarItemDialogState(
    val isVisible: Boolean = false,
    val isEditMode: Boolean = false,
    val icon: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val buttonIndex: Int = -1,
)

/**
 * A Composable dialog for adding or editing custom toolbar items.
 *
 * @param state The current dialog state
 * @param onDismissRequest Called when dialog is dismissed
 * @param onConfirm Called with (icon, prefix, suffix) when user confirms
 * @param onDelete Called when user wants to delete the item (edit mode only)
 * @param onHelpClick Called when user clicks the help button
 */
@Composable
fun AddToolbarItemDialog(
    state: ToolbarItemDialogState,
    onDismissRequest: () -> Unit,
    onConfirm: (icon: String, prefix: String, suffix: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onHelpClick: () -> Unit,
) {
    if (!state.isVisible) return

    var iconText by remember(state) { mutableStateOf(state.icon) }
    var prefixText by remember(state) { mutableStateOf(state.prefix) }
    var suffixText by remember(state) { mutableStateOf(state.suffix) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(
                    if (state.isEditMode) R.string.edit_toolbar_item else R.string.add_toolbar_item
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.toolbar_item_explain_edit_or_remove),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = iconText,
                    onValueChange = { iconText = it },
                    label = { Text(stringResource(R.string.note_editor_toolbar_icon)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = iconText.isBlank(),
                )
                OutlinedTextField(
                    value = prefixText,
                    onValueChange = { prefixText = it },
                    label = { Text(stringResource(R.string.before_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = suffixText,
                    onValueChange = { suffixText = it },
                    label = { Text(stringResource(R.string.after_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(iconText.trim(), prefixText.trim(), suffixText.trim())
                }, enabled = iconText.isNotBlank()
            ) {
                Text(
                    text = stringResource(
                        if (state.isEditMode) R.string.save else R.string.dialog_positive_create
                    )
                )
            }
        },
        dismissButton = {
            Row {
                if (state.isEditMode && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(text = TR.actionsDelete())
                    }
                }
                TextButton(onClick = onHelpClick) {
                    Text(text = stringResource(R.string.help))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.dialog_cancel))
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AddToolbarItemDialogPreview() {
    AnkiDroidTheme {
        AddToolbarItemDialog(
            state = ToolbarItemDialogState(isVisible = true),
            onDismissRequest = {},
            onConfirm = { _, _, _ -> },
            onHelpClick = {},
        )
    }
}
