package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun BackupNoSpaceLeftDialog(
    space: Long,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {}, // Disallow dismiss on back/outside
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(text = stringResource(id = R.string.storage_almost_full_title))
        },
        text = {
            Text(text = stringResource(id = R.string.storage_warning, space / 1024 / 1024))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.dialog_ok))
            }
        }
    )
}

@Preview(name = "Backup No Space Left Dialog")
@Composable
private fun BackupNoSpaceLeftDialogPreview() {
    AnkiDroidTheme {
        BackupNoSpaceLeftDialog(
            space = 50 * 1024 * 1024,
            onConfirm = {}
        )
    }
}
