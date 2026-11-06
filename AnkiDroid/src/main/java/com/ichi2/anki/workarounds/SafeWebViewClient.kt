/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Ported from AnkiDroid (https://github.com/ankidroid/Anki-Android), GPLv3.
 */

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
