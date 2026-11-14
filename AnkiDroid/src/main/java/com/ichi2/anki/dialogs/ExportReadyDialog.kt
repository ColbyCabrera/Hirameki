// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Message
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

class ExportReadyDialog : AsyncDialogFragment() {
    private val exportPath
        get() =
            requireArguments().getString(KEY_EXPORT_PATH)
                ?: error("Missing required argument: exportPath!")

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(requireActivity())

        dialog
            .setTitle(notificationTitle)
            .positiveButton(R.string.export_choice_save_to) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SAVE,
                    Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) },
                )
            }.negativeButton(R.string.export_choice_share) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SHARE,
                    Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) },
                )
            }

        return dialog.create()
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_ready_title)

    override val notificationMessage: String? = null

    override val dialogHandlerMessage: DialogHandlerMessage
        get() = ExportReadyDialogMessage(exportPath)

    /** Export ready dialog message*/
    class ExportReadyDialogMessage(
        private val exportPath: String,
    ) : DialogHandlerMessage(
            which = WhichDialogHandler.MSG_EXPORT_READY,
            analyticName = "ExportReadyDialog",
        ) {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // we may be called via any AnkiActivity but export is a DeckPicker thing
            activity.requireDeckPickerOrShowError()?.showDialogFragment(newInstance(exportPath))
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@ExportReadyDialogMessage.what
                data = Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) }
            }

        companion object {
            fun fromMessage(message: Message): ExportReadyDialogMessage {
                val data = requireNotNull(message.data) { "Missing message data bundle" }
                val exportPath =
                    data.getString(KEY_EXPORT_PATH)
                        ?: data.getString("exportPath")
                        ?: throw IllegalArgumentException("Missing export path")
                return ExportReadyDialogMessage(exportPath)
            }
        }
    }

    companion object {
        const val REQUEST_EXPORT_SAVE = "request_export_save"
        const val REQUEST_EXPORT_SHARE = "request_export_share"
        const val KEY_EXPORT_PATH = "key_export_path"

        fun newInstance(exportPath: String) =
            ExportReadyDialog().apply {
                arguments = Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) }
            }
    }
}
