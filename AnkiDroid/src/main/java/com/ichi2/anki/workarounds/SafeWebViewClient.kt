// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

// Ported from AnkiDroid (https://github.com/ankidroid/Anki-Android), GPLv3.

package com.ichi2.anki.workarounds

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

/** Receives [WebViewClient.onRenderProcessGone] from a [SafeWebViewClient]. */
fun interface OnRenderProcessGoneListener {
    /**
     * @param webView the [WebView] whose renderer is gone. It must not be used again.
     * @param didCrash `true` if the renderer crashed, `false` if the system killed it to reclaim memory
     */
    fun onRenderProcessGone(
        webView: WebView,
        didCrash: Boolean,
    )
}

/**
 * A [WebViewClient] that survives losing its renderer process, which Android otherwise answers by
 * killing the app (#143).
 *
 * Set it with [SafeWebViewLayout.setWebViewClient], which replaces the dead [WebView].
 */
open class SafeWebViewClient : WebViewClient() {
    private var onRenderProcessGoneListener: OnRenderProcessGoneListener? = null

    final override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        val didCrash = detail.didCrash()
        Timber.w("onRenderProcessGone (didCrash %b)", didCrash)
        val listener =
            checkNotNull(onRenderProcessGoneListener) {
                "SafeWebViewClient must be set through SafeWebViewLayout.setWebViewClient"
            }
        listener.onRenderProcessGone(view, didCrash)
        return true
    }

    fun setOnRenderProcessGoneListener(listener: OnRenderProcessGoneListener) {
        onRenderProcessGoneListener = listener
    }
}
