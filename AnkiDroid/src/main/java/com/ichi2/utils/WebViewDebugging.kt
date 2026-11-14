// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.content.SharedPreferences
import android.webkit.WebView
import androidx.annotation.UiThread

object WebViewDebugging {
    private var sHasSetDataDirectory = false

    @UiThread
    fun initializeDebugging(sharedPrefs: SharedPreferences) {
        // DEFECT: We might be able to cache this value: check what happens on WebView Renderer crash
        // On your desktop use chrome://inspect to connect to emulator WebViews
        // Beware: Crash in AnkiDroidApp.onCreate() with:
        //
        //     java.lang.RuntimeException: Using WebView from more than one process at once with the same data directory
        //     is not supported. https://crbug.com/558377 : Lock owner com.ichi2.anki:acra at
        //     org.chromium.android_webview.AwDataDirLock.a(PG:26)
        //
        val enableDebugging = sharedPrefs.getBoolean("html_javascript_debugging", false)
        WebView.setWebContentsDebuggingEnabled(enableDebugging)
    }

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
