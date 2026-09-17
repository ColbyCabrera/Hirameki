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
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    @VisibleForTesting
    internal val server = AnkiServer(this)

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState = _serverState.asStateFlow()

    // binding the socket and waiting for nanohttpd's listener thread is blocking io, and this view model is created
    // during composition on the main thread. the page shows its loading indicator (Stopped) until the url is known
    private val serverStart =
        viewModelScope.launch(ioDispatcher) {
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
        // a blocking start cannot be cancelled, so a screen closed while it runs would stop the server before it has
        // bound, and the socket would then stay open for the life of the process. stopping once the start is over
        // (at once if it already is, or never ran) closes whatever it opened. the state follows the stop, since a start
        // still running would otherwise report Running after it
        serverStart.invokeOnCompletion {
            server.stop()
            _serverState.value = ServerState.Stopped
            Timber.d("PageWebViewViewModel: AnkiServer stopped")
        }
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

sealed interface ServerState {
    data class Running(val serverBaseUrl: String) : ServerState
    data object Stopped : ServerState
    data class Error(val exception: Exception) : ServerState
}
