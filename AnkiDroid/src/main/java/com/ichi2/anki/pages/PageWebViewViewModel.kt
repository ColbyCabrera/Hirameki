/*
 *  Copyright (c) Colby Cabrera <colbycabrera.wd@gmail.com>
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
 */
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
    application: Application
) : AndroidViewModel(application), PostRequestHandler {

    private val server = AnkiServer(this)

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState = _serverState.asStateFlow()

    /**
     * The base URL for the local server, used to load Anki pages.
     */
    val serverBaseUrl: String
        get() = server.baseUrl()

    init {
        try {
            server.start()
            _serverState.value = ServerState.Running
            Timber.d("PageWebViewViewModel: AnkiServer started at %s", serverBaseUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AnkiServer at %s", serverBaseUrl)
            _serverState.value = ServerState.Error(e)
        }
    }

    override fun onCleared() {
        server.stop()
        _serverState.value = ServerState.Stopped
        Timber.d("PageWebViewViewModel: AnkiServer stopped")
        super.onCleared()
    }

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray {
        val methodName = if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
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

sealed class ServerState {
    object Running : ServerState()
    object Stopped : ServerState()
    data class Error(val exception: Exception) : ServerState()
}
