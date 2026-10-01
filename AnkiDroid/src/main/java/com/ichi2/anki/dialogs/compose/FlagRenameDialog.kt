/*
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>
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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun FlagRenameDialog(onDismissRequest: () -> Unit) {
    var flags by remember { mutableStateOf<List<Pair<Flag, String>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Initial load
    LaunchedEffect(Unit) {
        try {
            flags = loadFlags()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load flags")
        }
    }

    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(R.string.dialog_ok))
        }
    }, title = { Text(stringResource(R.string.rename_flag)) }, text = {
        FlagRenameScreen(
            flags = flags,
            onRename = { flag, newName ->
                val trimmedName = newName.trim()
                if (trimmedName.isNotEmpty()) {
                    scope.launch {
                        try {
                            // Optimistic update
                            val updatedFlags =
                                flags.map {
                                    if (it.first == flag) flag to trimmedName else it
                                }
                            flags = updatedFlags

                            flag.rename(trimmedName)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to rename flag")
                            // Revert optimistic update on failure
                            try {
                                flags = loadFlags()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to reload flags after rename failure")
                            }
                        }
                    }
                }
            },
        )
    })
}

private suspend fun loadFlags(): List<Pair<Flag, String>> =
    Flag
        .queryDisplayNames()
        .filter { it.key != Flag.NONE }
        .map { (flag, displayName) -> flag to displayName }
