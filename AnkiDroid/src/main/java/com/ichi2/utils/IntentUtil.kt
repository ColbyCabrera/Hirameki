// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import java.lang.Exception

object IntentUtil {
    @JvmStatic // (fixable) required due to structure of unit tests
    fun canOpenIntent(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            val packageManager = context.packageManager
            intent.resolveActivity(packageManager) != null
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    fun tryOpenIntent(
        activity: AnkiActivity,
        intent: Intent,
    ) {
        try {
            if (canOpenIntent(activity, intent)) {
                activity.startActivity(intent)
            } else {
                val errorMsg = activity.getString(R.string.feedback_no_suitable_app_found)
                activity.showSnackbar(errorMsg, Snackbar.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Timber.w(e)
            val errorMsg = activity.getString(R.string.feedback_no_suitable_app_found)
            activity.showSnackbar(errorMsg, Snackbar.LENGTH_SHORT)
        }
    }

    fun Intent.resolveMimeType(): String? =
        if (type == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(data.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            type
        }
}
