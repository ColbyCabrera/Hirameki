// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.BrowserOptions
import com.ichi2.anki.model.CardsOrNotes

@Composable
fun BrowserOptionsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (CardsOrNotes, Boolean, Boolean) -> Unit,
    initialCardsOrNotes: CardsOrNotes,
    initialIsTruncated: Boolean,
    initialShouldIgnoreAccents: Boolean,
    onManageColumnsClicked: () -> Unit,
    onRenameFlagClicked: () -> Unit,
) {
    var cardsOrNotes by remember { mutableStateOf(initialCardsOrNotes) }
    var isTruncated by remember { mutableStateOf(initialIsTruncated) }
    var shouldIgnoreAccents by remember { mutableStateOf(initialShouldIgnoreAccents) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.browser_options_dialog_heading)) },
        text = {
            BrowserOptions(
                onCardsModeSelected = { cardsOrNotes = CardsOrNotes.CARDS },
                onNotesModeSelected = { cardsOrNotes = CardsOrNotes.NOTES },
                initialMode = if (cardsOrNotes == CardsOrNotes.CARDS) 0 else 1,
                onTruncateChanged = { isTruncated = it },
                initialTruncate = isTruncated,
                onIgnoreAccentsChanged = { shouldIgnoreAccents = it },
                initialIgnoreAccents = shouldIgnoreAccents,
                onManageColumnsClicked = onManageColumnsClicked,
                onRenameFlagClicked = onRenameFlagClicked,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(cardsOrNotes, isTruncated, shouldIgnoreAccents)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
