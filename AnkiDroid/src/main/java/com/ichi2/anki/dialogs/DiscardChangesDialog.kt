// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber

// TODO: Clean up this code
object DiscardChangesDialog {
    fun showDialog(
        context: Context,
        positiveButtonText: String = context.getString(R.string.discard),
        negativeButtonText: String = CollectionManager.TR.addingKeepEditing(),
        neutralButtonText: String? = null,
        message: String = CollectionManager.TR.addingDiscardCurrentInput(),
        negativeMethod: () -> Unit = {},
        neutralMethod: (() -> Unit)? = null,
        positiveMethod: () -> Unit,
    ) = MaterialAlertDialogBuilder(context).show {
        Timber.i("showing 'discard changes' dialog")
        message(text = message)
        positiveButton(text = positiveButtonText) { positiveMethod() }
        negativeButton(text = negativeButtonText) { negativeMethod() }
        if (neutralButtonText != null && neutralMethod != null) {
            neutralButton(text = neutralButtonText) { neutralMethod() }
        }
    }
}
