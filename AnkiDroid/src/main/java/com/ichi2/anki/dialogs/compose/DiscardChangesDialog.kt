// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlin.String

/**
 * A Composable dialog that asks the user whether they want to discard unsaved changes.
 *
 * @param onDismissRequest Called when the user taps outside the dialog or presses back.
 * @param onConfirm Called when the user chooses to discard changes.
 * @param onKeepEditing Called when the user chooses to keep editing.
 * @param title Optional title for the dialog.
 * @param message The message body asking for confirmation.
 * @param confirmButtonText Text for the confirm (discard) button.
 * @param dismissButtonText Text for the dismiss (keep editing) button.
 */
@Composable
fun DiscardChangesDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onKeepEditing: () -> Unit,
    title: String? = null,
    message: String = CollectionManager.TR.addingDiscardCurrentInput(),
    confirmButtonText: String = stringResource(id = R.string.discard),
    dismissButtonText: String = CollectionManager.TR.addingKeepEditing(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = message) },
        text = { Text(text = stringResource(R.string.changes_will_not_be_saved)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(text = dismissButtonText)
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DiscardChangesDialogPreview() {
    AnkiDroidTheme {
        DiscardChangesDialog(
            onDismissRequest = {},
            onConfirm = {},
            onKeepEditing = {},
        )
    }
}
