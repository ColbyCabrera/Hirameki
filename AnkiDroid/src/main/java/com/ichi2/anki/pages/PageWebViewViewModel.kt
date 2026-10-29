// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * ViewModel for handling POST requests in Compose-based PageWebView screens via [PostRequestBridge].
 */
class PageWebViewViewModel(
    application: Application,
) : AndroidViewModel(application),
    PostRequestHandler {
    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        val methodName =
            uri.backendMethodName ?: throw IllegalArgumentException("unhandled request: $uri")
        // Try UI methods first, then collection methods
        // Note: UI methods require FragmentActivity context which we don't have here
        // So we only use collection methods for now
        return handleCollectionPostRequest(methodName, bytes)
            ?: throw IllegalArgumentException("unhandled method: $methodName")
    }
}
