/*
 Copyright (c) 2026 Colby Cabrera <colby.cabrera@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.ConflictResolution
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.ext.displayHost
import com.ichi2.anki.utils.ext.openAppSettingsScreen

/**
 * Which local-network permission dialog is shown, if any. Kept in ViewModel state so a
 * configuration change re-renders the dialog instead of dropping it with the Activity.
 */
sealed interface LocalNetworkPermissionDialogState {
    val endpoint: String?

    data class Rationale(
        override val endpoint: String?,
        /** Chosen conflict resolution to resume with, when the dialog defers a sync. */
        val conflict: ConflictResolution? = null,
    ) : LocalNetworkPermissionDialogState

    data class PermanentlyDenied(
        override val endpoint: String?,
    ) : LocalNetworkPermissionDialogState
}

/**
 * Explains why a LAN custom server needs Android 17's `ACCESS_LOCAL_NETWORK` before the system prompt.
 */
@Composable
fun LocalNetworkPermissionRationaleDialog(
    endpoint: String?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(stringResource(R.string.custom_sync_local_network_rationale, endpoint.displayHost()))
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.dialog_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/**
 * Shown when the system will no longer display the permission prompt; deep-links to Settings.
 */
@Composable
fun LocalNetworkPermissionPermanentlyDeniedDialog(
    endpoint: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(stringResource(R.string.custom_sync_local_network_permanently_denied, endpoint.displayHost()))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.openAppSettingsScreen()
                },
            ) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun LocalNetworkPermissionRationaleDialogPreview() {
    AnkiDroidTheme {
        LocalNetworkPermissionRationaleDialog(
            endpoint = "http://192.168.1.10:8080",
            onContinue = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun LocalNetworkPermissionPermanentlyDeniedDialogPreview() {
    AnkiDroidTheme {
        LocalNetworkPermissionPermanentlyDeniedDialog(
            endpoint = "http://192.168.1.10:8080",
            onDismiss = {},
        )
    }
}
