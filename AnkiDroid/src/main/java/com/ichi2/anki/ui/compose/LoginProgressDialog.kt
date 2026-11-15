// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
