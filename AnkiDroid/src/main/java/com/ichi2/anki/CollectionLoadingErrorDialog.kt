// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.showDatabaseErrorDialogInDeckPicker

/**
 * Dialog shown when the Anki collection fails to load.
 * Displays a database error dialog with options to handle the failure.
 */
class CollectionLoadingErrorDialog :
    DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
        "CollectionLoadingErrorDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Collection could not be opened
        // a stored message is replayed by whichever screen resumes first
        activity.showDatabaseErrorDialogInDeckPicker(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}
