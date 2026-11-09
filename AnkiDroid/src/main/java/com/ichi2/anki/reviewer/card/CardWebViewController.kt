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
import androidx.core.net.toUri
import androidx.webkit.WebViewFeature
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.compose.CardTouchGesture
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import kotlin.math.abs

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

    /** The latest state of the reviewer. */
    private var target: CardDisplay? = null

    /** The card to put on the page, which trails [target] while a transition covers the page. */
    private var display: CardDisplay? = null
    private lateinit var style: CardPageStyle
    private var isMediaAutoplayEnabled = false
    private var command: ReviewerJavascriptCommand? = null
    private var lastRunCommandId: Int? = null

    private var page: Page? = null
    private var pageToken = 0

    private var initialScale = 1f
    private var scale = 1f

    /** Reset with the WebView, which owns the listener. */
    private var isListeningForReports = false
    private val shownDisplay = MutableStateFlow<CardDisplay?>(null)

    /** Stays false where the page cannot report, which leaves cards to swap without a crossfade. */
    val hasShownCard: Boolean get() = shownDisplay.value != null

    private val gestureListener =
        CardGestureListener(layout, currentDisplay = { target }, onGesture = { onGesture(it) })

    init {
        configureWebView()
    }

    fun update(
        target: CardDisplay,
        style: CardPageStyle,
        isMediaAutoplayEnabled: Boolean,
        command: ReviewerJavascriptCommand?,
    ) {
        val isNewCard = !target.isSameCardAs(this.target)
        this.target = target
        this.style = style
        this.command = command
        if (this.isMediaAutoplayEnabled != isMediaAutoplayEnabled) {
            this.isMediaAutoplayEnabled = isMediaAutoplayEnabled
            applyMediaAutoplay()
        }
        if (isNewCard) layout.onContentChanged()

        if (target.baseUrl != page?.baseUrl) loadPage(target.baseUrl) else syncPage()
    }

    fun show(display: CardDisplay) {
        this.display = display
        syncPage()
    }

    suspend fun awaitShown(display: CardDisplay) {
        shownDisplay.first { it == display }
    }

    override fun onWebViewRecreated() {
        isListeningForReports = false
        configureWebView()
        target?.let { loadPage(it.baseUrl) }
    }

    fun destroy() = layout.destroy()

    private fun loadPage(baseUrl: String) {
        page = Page.Loading(baseUrl, style)
        shownDisplay.value = null
        listenForPageReports(baseUrl)
        layout.loadDataWithBaseURL(baseUrl, CardPage.shellHtml(context, style), "text/html", "UTF-8", null)
    }

    private fun syncPage() {
        when (val page = page) {
            null, is Page.Loading -> Unit
            is Page.Ready -> syncReadyPage(page)
            is Page.Replaced -> syncReplacedPage(page)
        }
    }

    private fun syncReadyPage(ready: Page.Ready) {
        if (style != ready.style) layout.evaluateJavascript(CardPage.styleUpdateScript(style))
        page = ready.copy(style = style, card = display)
        if (display != ready.card) display?.let(::showOnPage)
        takePendingCommand()?.let { layout.evaluateJavascript(CardPage.commandScript(it.script)) }
    }

    private fun syncReplacedPage(replaced: Page.Replaced) {
        if (display == replaced.leftCard) {
            takePendingCommand()
        } else if (replaced.isLoaded) {
            loadPage(replaced.baseUrl)
        }
    }

    private fun takePendingCommand(): ReviewerJavascriptCommand? =
        command?.takeIf { it.id != lastRunCommandId && display == target }?.also {
            lastRunCommandId = it.id
            onCommandRun(it.id)
        }

    private fun showOnPage(display: CardDisplay) {
        shownDisplay.value = null
        pageToken++
        resetZoom()
        layout.evaluateJavascript(CardPage.showScript(display, style.isNightMode, pageToken))
    }

    /** The page waits for the reset, because `zoomBy` finishing later would undo the scroll to the answer. */
    private fun resetZoom() {
        val factor = initialScale / scale
        if (abs(factor - 1f) < ZOOM_TOLERANCE) return
        layout.evaluateJavascript(CardPage.awaitZoomResetScript(initialScale / context.resources.displayMetrics.density))
        layout.zoomBy(factor.coerceIn(MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR))
    }

    private fun listenForPageReports(baseUrl: String) {
        if (isListeningForReports || isRobolectric) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        isListeningForReports = true
        val origin = baseUrl.toUri().let { "${it.scheme}://${it.authority}" }
        layout.addWebMessageListener(CardPage.BRIDGE_NAME, setOf(origin)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) message.data?.toIntOrNull()?.let(::onPageReportedShown)
        }
    }

    private fun onPageReportedShown(token: Int) {
        if (token != pageToken) return
        val reported = (page as? Page.Ready)?.card ?: return
        layout.postVisualStateCallback {
            if (token == pageToken) shownDisplay.value = reported
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun configureWebView() {
        initialScale = if (isRobolectric) 1f else layout.scale
        scale = initialScale
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
        val baseUrl = page?.baseUrl ?: return false
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

        override fun onScaleChanged(
            view: WebView,
            oldScale: Float,
            newScale: Float,
        ) {
            scale = newScale
        }

        override fun onPageStarted(
            view: WebView,
            url: String?,
            favicon: Bitmap?,
        ) {
            page =
                when (val current = page) {
                    is Page.Ready -> Page.Replaced(current.baseUrl, current.card, isLoaded = false)
                    is Page.Replaced -> current.copy(isLoaded = false)
                    is Page.Loading, null -> current
                }
            shownDisplay.value = null
        }

        override fun onPageFinished(
            view: WebView,
            url: String?,
        ) {
            page =
                when (val current = page) {
                    is Page.Loading -> Page.Ready(current.baseUrl, current.style, card = null)
                    is Page.Replaced -> current.copy(isLoaded = true)
                    is Page.Ready, null -> current
                }
            syncPage()
        }
    }

    private sealed interface Page {
        val baseUrl: String

        data class Loading(
            override val baseUrl: String,
            val style: CardPageStyle,
        ) : Page

        data class Ready(
            override val baseUrl: String,
            val style: CardPageStyle,
            val card: CardDisplay?,
        ) : Page

        /**
         * A link or form replaced the shell, which returns with the next card or side.
         * It waits for [isLoaded] because a callback from an interrupted load would look like the shell's.
         */
        data class Replaced(
            override val baseUrl: String,
            val leftCard: CardDisplay?,
            val isLoaded: Boolean,
        ) : Page
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
        const val ZOOM_TOLERANCE = 0.001f

        /** The range `WebView.zoomBy` accepts. */
        const val MIN_ZOOM_FACTOR = 0.01f
        const val MAX_ZOOM_FACTOR = 100f
    }
}
