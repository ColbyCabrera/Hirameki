// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022, 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.hideShowButtonCss

/**
 * Anki page used to import text/csv files
 */
class CsvImporter : PageFragment() {
    /** Replaced on each page load: a recreated WebView reloads the page, which may start another import */
    private var backCallback: ImportBackCallback? = null

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        backCallback?.remove()
        backCallback = ImportBackCallback().also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        super.onCreate(savedInstanceState)
        return CsvImporterWebViewClient()
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        backCallback?.onPostRequest(uri)
        return super.handlePostRequest(uri, bytes)
    }

    inner class CsvImporterWebViewClient : PageWebViewClient() {
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
        /**
         * @param filePath path of the csv file that will be imported, which should be accessible by AnkiDroid
         * @return an intent to open the [CsvImporter] page on [SingleFragmentActivity]
         */
        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val title = context.getString(R.string.menu_import)
            return getIntent(context, "import-csv$filePath", title, CsvImporter::class)
        }
    }
}
