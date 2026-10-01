/*
 *  Copyright (c) 2024 AnkiDroid Open Source Team
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
package com.ichi2.anki.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A progress dialog shown during login operations.
 *
 * @param onCancel Called when the user cancels the login operation
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginProgressDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.sign_in))
                LinearWavyProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                )
            }
        },
        text = { Text(text = stringResource(R.string.dialog_processing)) },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun LoginProgressDialogPreview() {
    AnkiDroidTheme {
        LoginProgressDialog(onCancel = {})
    }
}
