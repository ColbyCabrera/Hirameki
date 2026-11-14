// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.deckpicker.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
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
import com.ichi2.anki.R

/**
 * Progress dialog used for longer running deck picker operations such as sync.
 */
@Composable
fun DeckPickerProgressDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = title)
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        },
        text = { Text(text = message) },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        },
    )
}

@Preview
@Composable
fun DeckPickerProgressDialogPreview() {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        DeckPickerProgressDialog(
            title = "Syncing",
            message = "Syncing in progress...",
            onCancel = { showDialog = false },
        )
    }
}
