package com.ichi2.anki.pages

import android.webkit.WebResourceError

fun interface OnErrorCallback {
    fun onError(error: WebResourceError)
}
