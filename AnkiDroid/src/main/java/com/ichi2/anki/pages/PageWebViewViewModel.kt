// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * ViewModel for managing the [AnkiServer] lifecycle in Compose-based PageWebView screens.
 *
 * The server starts when the ViewModel is created and stops when cleared.
 * This ensures the server survives configuration changes.
 */
class PageWebViewViewModel(
    application: Application,
) : AndroidViewModel(application),
    PostRequestHandler {
    private val server = AnkiServer(this)

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState = _serverState.asStateFlow()

    init {
        try {
            server.start()
            val url = server.baseUrl()
            _serverState.value = ServerState.Running(url)
            Timber.d("PageWebViewViewModel: AnkiServer started at %s", url)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AnkiServer")
            _serverState.value = ServerState.Error(e)
        }
    }

    override fun onCleared() {
        server.stop()
        _serverState.value = ServerState.Stopped
        Timber.d("PageWebViewViewModel: AnkiServer stopped")
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray {
        val methodName =
            if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
                uri.substring(AnkiServer.ANKI_PREFIX.length)
            } else {
                throw IllegalArgumentException("unhandled request: $uri")
            }
        // Try UI methods first, then collection methods
        // Note: UI methods require FragmentActivity context which we don't have here
        // So we only use collection methods for now
        return handleCollectionPostRequest(methodName, bytes)
            ?: throw IllegalArgumentException("unhandled method: $methodName")
    }
}

sealed interface ServerState {
    data class Running(
        val serverBaseUrl: String,
    ) : ServerState

    data object Stopped : ServerState

    data class Error(
        val exception: Exception,
    ) : ServerState
}
