/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.MainThread
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import timber.log.Timber

/**
 * Owns the one WebView of the card page, and keeps its page in step with the latest [update].
 *
 * The shell page loads once. Cards are swapped inside it, and it is reloaded only for a new
 * [WebView] after a renderer loss.
 */
@MainThread
class CardWebViewController(
    private val context: Context,
    private val onLinkClick: (String) -> Unit,
    private val onGesture: (CardTouchGesture) -> Unit,
    private val onCommandRun: (Int) -> Unit,
) : OnWebViewRecreatedListener {
    val layout = SafeWebViewLayout(context).also { it.onWebViewRecreatedListener = this }

    private var display: CardDisplay? = null
    private var style: CardPageStyle? = null
    private var isMediaAutoplayEnabled = false
    private var command: ReviewerJavascriptCommand? = null
    private var lastRunCommandId: Int? = null

    private var isPageLoaded = false
    private var pageBaseUrl: String? = null
    private var pageDisplay: CardDisplay? = null
    private var pageStyle: CardPageStyle? = null

    private val gestureListener =
        CardGestureListener(layout, currentDisplay = { display }, onGesture = { onGesture(it) })

    init {
        configureWebView()
    }

    fun update(
        display: CardDisplay,
        style: CardPageStyle,
        isMediaAutoplayEnabled: Boolean,
        command: ReviewerJavascriptCommand?,
    ) {
        val isNewCard = !display.isSameCardAs(this.display)
        this.display = display
        this.style = style
        this.command = command
        if (this.isMediaAutoplayEnabled != isMediaAutoplayEnabled) {
            this.isMediaAutoplayEnabled = isMediaAutoplayEnabled
            applyMediaAutoplay()
        }
        if (isNewCard) layout.onContentChanged()

        if (display.baseUrl.isEmpty()) return
        if (display.baseUrl != pageBaseUrl) loadPage(display.baseUrl, style) else syncPage()
    }

    override fun onWebViewRecreated() {
        configureWebView()
        val baseUrl = display?.baseUrl?.takeIf { it.isNotEmpty() }
        val style = style
        if (baseUrl != null && style != null) loadPage(baseUrl, style)
    }

    fun destroy() = layout.destroy()

    private fun loadPage(
        baseUrl: String,
        style: CardPageStyle,
    ) {
        isPageLoaded = false
        pageBaseUrl = baseUrl
        pageDisplay = null
        pageStyle = style
        layout.loadDataWithBaseURL(baseUrl, CardPage.shellHtml(context, style), "text/html", "UTF-8", null)
    }

    private fun syncPage() {
        if (!isPageLoaded) return
        style?.takeIf { it != pageStyle }?.let {
            pageStyle = it
            layout.evaluateJavascript(CardPage.styleUpdateScript(it))
        }
        display?.takeIf { it != pageDisplay }?.let {
            pageDisplay = it
            layout.evaluateJavascript(CardPage.showScript(it))
        }
        command?.takeIf { it.id != lastRunCommandId }?.let {
            lastRunCommandId = it.id
            layout.evaluateJavascript(CardPage.commandScript(it.script))
            onCommandRun(it.id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun configureWebView() {
        with(layout) {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            applyMediaAutoplay()
            setWebChromeClient(CardWebChromeClient())
            setWebViewClient(CardWebViewClient())
            setBackgroundColor(Color.TRANSPARENT)
            setOnScrollChangeListener { _, scrollX, scrollY, oldScrollX, oldScrollY ->
                gestureListener.onScrollChanged(scrollX - oldScrollX, scrollY - oldScrollY)
            }
            // Returns false so the WebView still receives the touch: links, scrolling and text
            // selection keep working.
            setOnTouchListener { _, event ->
                gestureListener.onTouchEvent(event)
                false
            }
        }
    }

    private fun applyMediaAutoplay() {
        layout.settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
    }

    private fun isLinkWithinPage(url: String): Boolean {
        val baseUrl = pageBaseUrl ?: return false
        if (!url.startsWith(baseUrl)) return false
        val path = url.removePrefix(baseUrl)
        return path.isEmpty() || path.startsWith("#") || path.startsWith("/#")
    }

    private inner class CardWebViewClient : SafeWebViewClient() {
        private val resourceHandler = ViewerResourceHandler(context)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = resourceHandler.shouldInterceptRequest(request)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            if (request.url.scheme in SCHEMES_LOADED_BY_WEBVIEW) return false
            val url = request.url.toString()
            if (isLinkWithinPage(url)) return false
            onLinkClick(url)
            return true
        }

        override fun onPageStarted(
            view: WebView,
            url: String?,
            favicon: Bitmap?,
        ) {
            isPageLoaded = false
            pageDisplay = null
        }

        override fun onPageFinished(
            view: WebView,
            url: String?,
        ) {
            isPageLoaded = true
            syncPage()
        }
    }

    private class CardWebChromeClient : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Timber
                .tag("FlashcardJS")
                .d("%s -- From line %d of %s", consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId())
            return true
        }
    }

    private companion object {
        val SCHEMES_LOADED_BY_WEBVIEW = setOf("file", "data", "javascript", "blob")
    }
}
