// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.InitialActivity.StartupFailure.InitializationError
import com.ichi2.anki.R
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber

object FatalErrorDialog {
    fun build(
        activity: AnkiActivity,
        failure: InitializationError,
    ): AlertDialog {
        Timber.i("Displaying 'Fatal error'")
        return MaterialAlertDialogBuilder(activity).create {
            title(R.string.ankidroid_init_failed_webview_title)
            message(text = failure.toHumanReadableString(activity))
            positiveButton(R.string.close) {
                activity.closeCollectionAndFinish()
            }
            failure.infoLink?.let { url ->
                neutralButton(R.string.help) {
                    activity.openUrl(url)
                }
            }
            cancelable(false)
        }
    }
}
