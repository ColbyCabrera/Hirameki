/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.DeckPickerViewModel.DeckNameError

enum class DeckDialogType {
    DECK, SUB_DECK, RENAME_DECK, FILTERED_DECK
}

fun String.containsNumberLargerThanNine(): Boolean =
    Regex("""(?:[^:]|^)[1-9]\d+(?:[^:]|$)""").find(this) != null

@Composable
fun CreateDeckDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (deckName: String) -> Unit,
    dialogType: DeckDialogType,
    title: String,
    initialDeckName: String = "",
    validateDeckName: (String) -> DeckNameError? // Returns error message or null if valid
) {
    var deckName by remember { mutableStateOf(initialDeckName) }
    val focusRequester = remember { FocusRequester() }
    val error = validateDeckName(deckName)

    val errorMessage = when (error) {
        DeckNameError.INVALID_NAME -> stringResource(R.string.invalid_deck_name)
        DeckNameError.ALREADY_EXISTS -> stringResource(R.string.deck_already_exists)
        null -> null
    }
    // Autofocus the text field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(onDismissRequest = onDismissRequest, title = { Text(text = title) }, text = {
        Column {
            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.deck_name)) },
                isError = errorMessage != null,
                supportingText = {
                    when {
                        errorMessage != null -> Text(errorMessage)
                        deckName.containsNumberLargerThanNine() -> Text(stringResource(R.string.create_deck_numeric_hint))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (error == null && deckName.isNotBlank()) onConfirm(deckName) })
            )
        }
    }, confirmButton = {
        TextButton(
            onClick = { onConfirm(deckName) }, enabled = error == null && deckName.isNotBlank()
        ) {
            Text(stringResource(R.string.dialog_ok))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(R.string.dialog_cancel))
        }
    })
}

@Preview
@Composable
private fun CreateDeckDialogPreview() {
    CreateDeckDialog(
        onDismissRequest = {},
        onConfirm = {},
        dialogType = DeckDialogType.DECK,
        title = "Create Deck",
        validateDeckName = { null }
    )
}

@Preview
@Composable
private fun CreateDeckDialogErrorPreview() {
    CreateDeckDialog(
        onDismissRequest = {},
        onConfirm = {},
        dialogType = DeckDialogType.DECK,
        title = "Create Deck",
        initialDeckName = "Existing Deck",
        validateDeckName = { DeckNameError.ALREADY_EXISTS }
    )
}

@Preview
@Composable
private fun CreateDeckDialogRenamePreview() {
    CreateDeckDialog(
        onDismissRequest = {},
        onConfirm = {},
        dialogType = DeckDialogType.RENAME_DECK,
        title = "Rename Deck",
        initialDeckName = "My Study Deck",
        validateDeckName = { null }
    )
}

@Preview
@Composable
private fun CreateDeckDialogNumericHintPreview() {
    CreateDeckDialog(
        onDismissRequest = {},
        onConfirm = {},
        dialogType = DeckDialogType.DECK,
        title = "Create Deck",
        initialDeckName = "10. Chemistry",
        validateDeckName = { null }
    )
}
