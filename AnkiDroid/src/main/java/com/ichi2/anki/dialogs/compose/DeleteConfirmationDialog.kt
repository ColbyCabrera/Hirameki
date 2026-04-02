/* **************************************************************************************
 * Copyright (c) 2025 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun DeleteConfirmationDialog(
    quantity: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    icon: (@Composable () -> Unit)? = {
        Icon(
            painter = painterResource(R.drawable.warning_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
    },
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = icon,
        title = {
            Text(text = pluralStringResource(R.plurals.card_browser_delete_notes, quantity))
        },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.delete_notes_confirmation,
                    quantity,
                    quantity
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.dialog_positive_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DeleteConfirmationDialogPreview() {
    AnkiDroidTheme {
        DeleteConfirmationDialog(
            quantity = 5,
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SingleDeleteConfirmationDialogPreview() {
    AnkiDroidTheme {
        DeleteConfirmationDialog(
            quantity = 1,
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}
