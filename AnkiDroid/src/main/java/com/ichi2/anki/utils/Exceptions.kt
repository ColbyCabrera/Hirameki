// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Context
import com.ichi2.anki.R

fun interface TranslatableException {
    fun getTranslatedMessage(context: Context): String
}

/**
 * Get an user-friendly error message out of an exception.
 * If the exception is a [TranslatableException], a localized error message is returned.
 *
 * TODO Special-case some of the most common exceptions thrown by the system or the library.
 */
fun Context.getUserFriendlyErrorText(e: Exception): String =
    if (e is TranslatableException) {
        e.getTranslatedMessage(this)
    } else {
        e.localizedMessage?.ifBlank { null }
            ?: e.message?.ifBlank { null }
            ?: e::class.simpleName?.ifBlank { null }
            ?: getString(R.string.error__etc__unknown_error)
    }
