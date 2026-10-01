/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A Composable dialog that shows a network error.
 *
 * @param onDismissRequest Called when the user taps outside the dialog or presses back, or presses Cancel.
 * @param onRetry Called when the user clicks the "Retry" button.
 */
@Composable
fun NetworkErrorDialog(
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.sync_error))
        },
        text = {
            Text(text = stringResource(id = R.string.connection_error_message))
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(text = stringResource(id = R.string.retry))
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
private fun NetworkErrorDialogPreview() {
    AnkiDroidTheme {
        NetworkErrorDialog(
            onDismissRequest = {},
            onRetry = {},
        )
    }
}
