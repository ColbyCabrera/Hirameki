package com.ichi2.anki.pages

import android.webkit.WebResourceError

/**
 * Callback invoked when a WebView encounters a loading error for the main frame.
 *
 * This is called by [PageWebViewClient.onReceivedError] when the main page fails to load.
 * Use this to update UI state (e.g., show an error overlay) when page loading fails.
 *
 * @see PageWebViewClient.onErrorCallbacks
 */
fun interface OnErrorCallback {
    fun onError(error: WebResourceError)
}