// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import com.ichi2.anki.R
import com.ichi2.anki.hideShowButtonCss

class AnkiPackageImporterFragment : PageFragment() {
    /** Replaced on each page load: a recreated WebView reloads the page, which may start another import */
    private var backCallback: ImportBackCallback? = null

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        backCallback?.remove()
        backCallback = ImportBackCallback().also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        return AnkiPackageImporterWebViewClient()
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        backCallback?.onPostRequest(uri)
        return super.handlePostRequest(uri, bytes)
    }

    class AnkiPackageImporterWebViewClient : PageWebViewClient() {
        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            view!!.evaluateJavascript(hideShowButtonCss) {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val title = context.getString(R.string.menu_import)
            return getIntent(context, "import-anki-package$filePath", title, AnkiPackageImporterFragment::class)
        }
    }
}
