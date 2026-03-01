/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.CheckboxPrompt
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun BackupPromptDialogCompose(
    isLoggedIn: Boolean,
    allowUserToPermanentlyDismissDialog: Boolean,
    onBackup: () -> Unit,
    onDismissRequest: () -> Unit,
    onDoNotShowAgainChanged: (Boolean) -> Unit,
    isDoNotShowAgainChecked: Boolean
) {
    AlertDialog(onDismissRequest = onDismissRequest, icon = {
        Icon(
            painter = painterResource(
                id = if (isLoggedIn) R.drawable.ic_baseline_backup_24 else R.drawable.ic_backup_restore
            ), contentDescription = null
        )
    }, title = {
        Text(stringResource(R.string.backup_your_collection))
    }, text = {
        Column {
            Text(stringResource(R.string.backup_collection_message))

            if (allowUserToPermanentlyDismissDialog) {
                Spacer(modifier = Modifier.height(16.dp))
                CheckboxPrompt(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.button_do_not_show_again),
                    isChecked = isDoNotShowAgainChecked,
                    onCheckedChange = onDoNotShowAgainChanged,
                    horizontalPadding = 0.dp
                )
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = onBackup,
            enabled = !(allowUserToPermanentlyDismissDialog && isDoNotShowAgainChecked)
        ) {
            Text(stringResource(if (isLoggedIn) R.string.button_sync else R.string.button_backup))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(R.string.button_backup_later))
        }
    })
}

@Preview(name = "Logged In")
@Composable
fun BackupPromptDialogComposeLoggedInPreview() {
    AnkiDroidTheme {
        BackupPromptDialogCompose(
            isLoggedIn = true,
            allowUserToPermanentlyDismissDialog = true,
            onBackup = {},
            onDismissRequest = {},
            onDoNotShowAgainChanged = {},
            isDoNotShowAgainChecked = false
        )
    }
}

@Preview(name = "Not Logged In")
@Composable
fun BackupPromptDialogComposeNotLoggedInPreview() {
    AnkiDroidTheme {
        BackupPromptDialogCompose(
            isLoggedIn = false,
            allowUserToPermanentlyDismissDialog = true,
            onBackup = {},
            onDismissRequest = {},
            onDoNotShowAgainChanged = {},
            isDoNotShowAgainChecked = false
        )
    }
}

@Preview(name = "Do Not Show Again Checked")
@Composable
fun BackupPromptDialogComposeDoNotShowAgainPreview() {
    AnkiDroidTheme {
        BackupPromptDialogCompose(
            isLoggedIn = true,
            allowUserToPermanentlyDismissDialog = true,
            onBackup = {},
            onDismissRequest = {},
            onDoNotShowAgainChanged = {},
            isDoNotShowAgainChecked = true
        )
    }
}
