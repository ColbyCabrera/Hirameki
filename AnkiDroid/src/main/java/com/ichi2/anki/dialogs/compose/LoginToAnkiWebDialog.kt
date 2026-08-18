/*
 Copyright (c) 2025 AnkiDroid Open Source Team

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
package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A Composable dialog that asks the user to login to AnkiWeb.
 *
 * @param onDismissRequest Called when the user taps outside the dialog or presses back.
 * @param onLoginClick Called when the user clicks the "Log in" button.
 */
@Composable
fun LoginToAnkiWebDialog(
    onDismissRequest: () -> Unit,
    onLoginClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.sync_problem_24px),
                contentDescription = null,
            )
        },
        title = {
            Text(text = stringResource(id = R.string.not_logged_in_title))
        },
        text = {
            Text(text = stringResource(id = R.string.login_create_account_message))
        },
        confirmButton = {
            TextButton(onClick = onLoginClick) {
                Text(text = stringResource(id = R.string.log_in))
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
private fun LoginToAnkiWebDialogPreview() {
    AnkiDroidTheme {
        LoginToAnkiWebDialog(
            onDismissRequest = {},
            onLoginClick = {},
        )
    }
}
