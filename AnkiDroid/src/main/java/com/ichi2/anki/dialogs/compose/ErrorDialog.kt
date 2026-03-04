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
fun ErrorDialog(
    errorMessage: String,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.vague_error))
        },
        text = {
            Text(text = errorMessage)
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.dialog_ok))
            }
        }
    )
}

@Preview(name = "Error Dialog")
@Composable
fun ErrorDialogPreview() {
    AnkiDroidTheme {
        ErrorDialog(
            errorMessage = "A network error occurred.\n\nError details: error sending request",
            onDismissRequest = {}
        )
    }
}
