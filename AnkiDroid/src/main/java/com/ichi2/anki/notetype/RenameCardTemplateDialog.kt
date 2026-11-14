// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.notetype

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class RenameCardTemplateDialog {
    companion object {
        fun showInstance(
            context: Context,
            prefill: String,
            block: (result: String) -> Unit,
        ) {
            AlertDialog
                .Builder(context)
                .show {
                    title(R.string.rename_card_type)
                    positiveButton(R.string.dialog_ok) { }
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
                }.input(
                    hint = CollectionManager.TR.actionsNewName(),
                    displayKeyboard = true,
                    allowEmpty = false,
                    prefill = prefill,
                    waitForPositiveButton = true,
                    callback = { dialog, result ->
                        block(result.toString())
                        dialog.dismiss()
                    },
                )
        }
    }
}
