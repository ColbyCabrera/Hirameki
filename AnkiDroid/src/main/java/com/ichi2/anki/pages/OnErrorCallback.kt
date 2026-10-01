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
 */

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
