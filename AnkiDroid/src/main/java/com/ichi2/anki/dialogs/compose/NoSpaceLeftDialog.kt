package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun NoSpaceLeftDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.storage_full_title))
        },
        text = {
            Text(text = stringResource(id = R.string.backup_deck_no_storage_left))
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.dialog_ok))
            }
        }
    )
}

@Preview(name = "No Space Left Dialog")
@Composable
private fun NoSpaceLeftDialogPreview() {
    AnkiDroidTheme {
        NoSpaceLeftDialog(onDismissRequest = {})
    }
}
