// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.webkit.WebView
import timber.log.Timber

object WebViewDebugging {
    private var sHasSetDataDirectory = false

    /** Throws IllegalStateException if a WebView has been initialized  */
    fun setDataDirectorySuffix(suffix: String) {
        WebView.setDataDirectorySuffix(suffix)
        sHasSetDataDirectory = true
    }

    fun hasSetDataDirectory(): Boolean {
        // Implicitly truth requires API >= P
        return sHasSetDataDirectory
    }
}

fun setWebContentsDebuggingEnabled(enabled: Boolean) {
    try {
        WebView.setWebContentsDebuggingEnabled(enabled)
    } catch (e: Exception) {
        // throws when the device has no WebView, which must not stop the app from starting
        Timber.w(e, "setWebContentsDebuggingEnabled")
    }
}
