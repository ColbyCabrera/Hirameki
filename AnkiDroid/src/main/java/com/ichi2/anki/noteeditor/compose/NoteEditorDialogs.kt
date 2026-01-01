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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
