// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Nishant <nishantbhandari0019@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

/** Retains the sync request, not a continuation belonging to the destroyed activity. */
class MeteredSyncDialog : DialogFragment() {
    private var alwaysAllowChecked: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        alwaysAllowChecked = savedInstanceState?.getBoolean(STATE_ALWAYS_ALLOW) ?: false
        return MaterialAlertDialogBuilder(requireContext()).create {
            message(R.string.metered_sync_data_warning)
            positiveButton(R.string.dialog_continue) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_METERED_SYNC,
                    Bundle().apply { putBoolean(RESULT_SYNC_MEDIA, requireArguments().getBoolean(ARG_SYNC_MEDIA)) },
                )
            }
            negativeButton(R.string.dialog_cancel)
            checkBoxPrompt(
                R.string.button_do_not_show_again,
                isCheckedDefault = alwaysAllowChecked,
            ) { isChecked ->
                alwaysAllowChecked = isChecked
                Prefs.allowSyncOnMeteredConnections = isChecked
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ALWAYS_ALLOW, alwaysAllowChecked)
    }

    companion object {
        const val TAG = "meteredSyncConfirmation"
        const val REQUEST_METERED_SYNC = "request_metered_sync"
        const val RESULT_SYNC_MEDIA = "sync_media"
        private const val ARG_SYNC_MEDIA = "sync_media"
        private const val STATE_ALWAYS_ALLOW = "always_allow"

        fun newInstance(syncMedia: Boolean) =
            MeteredSyncDialog().apply {
                arguments = Bundle().apply { putBoolean(ARG_SYNC_MEDIA, syncMedia) }
            }
    }
}
