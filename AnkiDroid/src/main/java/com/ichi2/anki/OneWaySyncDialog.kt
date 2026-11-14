// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ankitects Pty Ltd <https://apps.ankiweb.net>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.os.Bundle
import android.os.Message
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.utils.ext.showDialogFragment
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Dialog that prompts the user to confirm a one-way sync.
 *
 * A one-way sync (also known as a full sync) occurs when the local database schema is modified
 * in a way that cannot be merged with the server's version. When this happens, the user must
 * choose to either overwrite the server with their local collection or overwrite their local
 * collection with the server's version.
 *
 * This dialog is typically triggered when an operation throws a [com.ichi2.anki.libanki.exception.ConfirmModSchemaException],
 * indicating that a schema modification requires a full sync.
 *
 * If the user confirms, [com.ichi2.anki.libanki.Collection.modSchemaNoCheck] is called to bypass
 * the safety check and force the schema modification, effectively marking the collection for a one-way sync.
 */
class OneWaySyncDialog(
    val message: String?,
) : DialogHandlerMessage(
        which = WhichDialogHandler.MSG_SHOW_ONE_WAY_SYNC_DIALOG,
        analyticName = "OneWaySyncDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Confirmation dialog for one-way sync
        val dialog = ConfirmationDialog()
        val confirm =
            Runnable {
                // Bypass the check once the user confirms
                activity.launchCatchingTask {
                    try {
                        withCol { modSchemaNoCheck() }
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            throw e
                        }
                        Timber.e(e, "Failed to modify schema")
                        activity.showSimpleMessageDialog("Failed to modify schema")
                    }
                }
            }
        dialog.setConfirm(confirm)
        dialog.setArgs(message)
        activity.showDialogFragment(dialog)
    }

    override fun toMessage(): Message =
        Message.obtain().apply {
            what = this@OneWaySyncDialog.what
            data =
                Bundle().apply {
                    putString("message", message)
                }
        }

    companion object {
        fun fromMessage(message: Message): DialogHandlerMessage = OneWaySyncDialog(message.data.getString("message"))
    }
}
