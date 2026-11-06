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

package com.ichi2.anki.workarounds

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/** Hosts [layout], reapplying its client as hosts must when its [WebView] is replaced. */
class SafeWebViewLayoutHostFragment :
    Fragment(),
    OnWebViewRecreatedListener {
    lateinit var layout: SafeWebViewLayout
        private set

    var recreatedCount = 0
        private set

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        FrameLayout(requireContext()).apply {
            layout = SafeWebViewLayout(requireContext())
            layout.setWebViewClient(SafeWebViewClient())
            addView(layout)
        }

    override fun onWebViewRecreated() {
        recreatedCount++
        layout.setWebViewClient(SafeWebViewClient())
    }
}

/** A host which does not handle its [WebView] being replaced. */
class NotListeningHostFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())
}

class DestroyCountingSafeWebViewLayout(
    context: Context,
) : SafeWebViewLayout(context) {
    var webViewDestroyCallCount = 0
        private set

    override fun createWebView(): WebView =
        object : WebView(context) {
            override fun destroy() {
                webViewDestroyCallCount++
                super.destroy()
            }
        }
}
