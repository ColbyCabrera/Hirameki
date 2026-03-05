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
