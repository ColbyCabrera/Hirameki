/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com> 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
package com.ichi2.anki.reviewer.compose

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Flashcard(
    baseUrl: String,
    questionHtml: String,
    answerHtml: String,
    bodyClass: String,
    isMediaAutoplayEnabled: Boolean,
    javascriptCommand: ReviewerJavascriptCommand?,
    onJavascriptCommandConsumed: (Int) -> Unit,
    onTap: () -> Unit,
    onLinkClick: (String) -> Unit,
    isAnswerShown: Boolean,
    modifier: Modifier = Modifier,
    applyHiramekiCssMode: String = Prefs.HIRAMEKI_CSS_ALL,
    toolbarHeight: Int = 0
) {
    val context = LocalContext.current
    val currentBaseUrl by rememberUpdatedState(baseUrl)
    val currentOnJavascriptCommandConsumed by rememberUpdatedState(onJavascriptCommandConsumed)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnTap by rememberUpdatedState(onTap)
    val isNightMode = Themes.currentTheme.isNightMode
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorHex = surfaceColor.toArgb().toRGBHex()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColorHex = onSurfaceColor.toArgb().toRGBHex()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerColorHex = surfaceContainerColor.toArgb().toRGBHex()
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorHex = primaryColor.toArgb().toRGBHex()
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineColorHex = outlineColor.toArgb().toRGBHex()
    val typography = MaterialTheme.typography
    val displayLargeStyle = typography.displayMedium
    val bodyLargeStyle = typography.titleLarge

    val contentKey = remember(questionHtml, answerHtml) {
        FlashcardContentKey(questionHtml, answerHtml)
    }

    val currentStyle =
        if (isAnswerShown) bodyLargeStyle else displayLargeStyle.copy(fontWeight = FontWeight.W500)
    val currentPadding = if (isAnswerShown) 40 else 36
    val currentHtml = if (isAnswerShown) answerHtml else questionHtml

    val composeStyle = remember(
        onSurfaceColorHex,
        surfaceColorHex,
        surfaceContainerColorHex,
        primaryColorHex,
        outlineColorHex,
        currentStyle,
        currentPadding,
        toolbarHeight,
        applyHiramekiCssMode
    ) {
        if (applyHiramekiCssMode == Prefs.HIRAMEKI_CSS_DISABLED) {
            ""
        } else {
            val fontSizeStyles = if (applyHiramekiCssMode == Prefs.HIRAMEKI_CSS_NO_FONT_SIZE) {
                ""
            } else {
                """
                    font-size: ${currentStyle.fontSize.value}px;
                    line-height: ${currentStyle.lineHeight.value}px;
                    letter-spacing: ${currentStyle.letterSpacing.value}px;
                """.trimIndent()
            }

            """
                @import url('https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,100..900;1,100..900&display=swap');
                html {
                    color: ${onSurfaceColorHex}EF;
                    background-color: $surfaceColorHex;
                }
                body.card {
                    text-align: center;
                    font-family: "Roboto", sans-serif;
                    $fontSizeStyles
                    font-weight: ${currentStyle.fontWeight?.weight ?: 400};
                    text-wrap: pretty;
                    padding-top: ${currentPadding}px;
                    padding-bottom: ${toolbarHeight}px;
                    padding-left: 12px;
                    padding-right: 12px;
                    margin: 0px;
                    min-height: 100vh;
                    box-sizing: border-box;
                    background-color: $surfaceColorHex;
                    color: ${onSurfaceColorHex}EF;
                }
                body.card .back {
                    font-weight: 400;
                    line-height: 1.4;
                }
                body.card.nightMode, body.card.night_mode {
                    background-color: $surfaceColorHex;
                    color: ${onSurfaceColorHex}EF;
                }
                hr {
                    opacity: 0.1;
                    margin: 12px 0px;
                }
                img {
                    border-radius: 16px;
                }
                button {
                    font-family: inherit;
                    font-size: 14px;
                    font-weight: 500;
                    color: ${onSurfaceColorHex};
                    background-color: ${surfaceContainerColorHex};
                    border: 1px solid ${outlineColorHex}40;
                    border-radius: 12px;
                    padding: 2px 6px;
                    cursor: pointer;
                    transition: background-color 0.2s, box-shadow 0.2s, transform 0.1s;
                    align-items: center;
                    justify-content: center;
                    min-height: 48px;
                    box-shadow: 0 1px 2px rgba(0,0,0,0.05);
                }
                button:hover {
                    background-color: ${surfaceContainerColorHex}D9;
                    box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                }
                button:active {
                    background-color: ${surfaceContainerColorHex}B3;
                    transform: scale(0.97);
                }
                button:focus {
                    outline: 2px solid ${primaryColorHex};
                    outline-offset: 2px;
                }
                button:disabled {
                    opacity: 0.45;
                    cursor: not-allowed;
                    transform: none;
                }

                body.card .replay-button {
                    --replay-button-size: 42px;
                    --replay-button-icon-color: ${onSurfaceColorHex};
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    margin: 8px;
                    border-radius: 8px;
                    width: var(--replay-button-size);
                    height: var(--replay-button-size);
                    color: var(--replay-button-icon-color);
                    text-decoration: none;
                    cursor: pointer;
                    transition: transform 0.1s, opacity 0.2s;
                    -webkit-tap-highlight-color: transparent;
                }
                body.card .replay-button:hover {
                    opacity: 0.85;
                }
                body.card .replay-button:active {
                    opacity: 0.7;
                    transform: scale(0.97);
                }
                body.card .replay-button:focus-visible {
                    outline: 2px solid ${primaryColorHex};
                    outline-offset: 2px;
                }
                body.card .replay-button .play-action {
                    display: block;
                    width: 100%;
                    height: 100%;
                    color: inherit;
                    fill: currentColor;
                }
                body.card .replay-button .play-action path {
                    fill: currentColor;
                }
            """.trimIndent()
        }
    }
    var cachedShellHtml by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context, isNightMode) {
        cachedShellHtml = withContext(Dispatchers.IO) {
            buildShellHtml(context, isNightMode)
        }
    }

    // Don't render the WebView until the shell HTML is loaded off the main thread
    val shellHtml = cachedShellHtml ?: return

    AndroidView(
        factory = { context ->
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Timber.tag("FlashcardJS")
                        .d("${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                val resourceHandler = ViewerResourceHandler(context)

                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    return resourceHandler.shouldInterceptRequest(request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val scheme = uri.scheme ?: return false
                    val urlString = uri.toString()

                    // Built-in WebKit schemes that WebView loads internally
                    if (scheme in setOf("file", "data", "javascript", "blob")) {
                        return false
                    }

                    // Anki JS communication schemes: consume & return true so WebView never navigates to gesture:// or ERR_UNKNOWN_URL_SCHEME
                    if (scheme in setOf(
                            "gesture",
                            "signal",
                            "sound",
                            "playsound",
                            "ankidroid",
                            "missing-user-action"
                        )
                    ) {
                        currentOnLinkClick(urlString)
                        return true
                    }

                    val payload = view.tag as? FlashcardPayload
                    val effectiveBaseUrl = payload?.baseUrl ?: currentBaseUrl
                    if (urlString.startsWith(effectiveBaseUrl)) {
                        val path = urlString.removePrefix(effectiveBaseUrl)
                        if (path.isEmpty() || path.startsWith("#") || path.startsWith("/#")) {
                            return false
                        }
                    }

                    currentOnLinkClick(urlString)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url != null && url.startsWith("chrome-error://")) {
                        Timber.w("WebView loaded error page: %s", url)
                        return
                    }
                    val payload = view.tag as? FlashcardPayload ?: return
                    payload.shellLoaded = true

                    val pendingScript = payload.pendingShowCardScript
                    if (pendingScript != null) {
                        view.evaluateJavascript(pendingScript, null)
                        payload.pendingShowCardScript = null
                    }

                    payload.pendingJavascriptCommand?.let { command ->
                        view.evaluateJavascript(command.script, null)
                        payload.lastJavascriptCommandId = command.id
                        payload.pendingJavascriptCommand = null
                        currentOnJavascriptCommandConsumed(command.id)
                    }
                }
            }

            val gestureDetector = GestureDetector(
                context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        currentOnTap()
                        return true
                    }
                })

            @SuppressLint("ClickableViewAccessibility") setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }

            setBackgroundColor(Color.TRANSPARENT)
        }
    }, update = { webView ->
        webView.settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
        val currentPayload = webView.tag as? FlashcardPayload

        fun createShowCardScript(): String {
            val spaPayload = SpaCardPayload(
                html = currentHtml,
                isAnswer = isAnswerShown,
                composeCss = composeStyle,
                bodyClass = bodyClass,
                isNightMode = isNightMode,
                enableCrossfade = true,
                baseUrl = baseUrl
            )
            val spaJson = Json.encodeToString(spaPayload)
            return "if (window.anki && typeof window.anki.showCard === 'function') { window.anki.showCard($spaJson); }"
        }

        if (currentPayload == null) {
            val fullScript = createShowCardScript()
            webView.tag = FlashcardPayload(
                contentKey = contentKey,
                isAnswerShown = isAnswerShown,
                baseUrl = baseUrl,
                isNightMode = isNightMode,
                composeStyle = composeStyle,
                bodyClass = bodyClass,
                pendingJavascriptCommand = javascriptCommand,
                pendingShowCardScript = fullScript
            )
            webView.loadDataWithBaseURL(baseUrl, shellHtml, "text/html", "UTF-8", null)
        } else {
            val stateChanged =
                currentPayload.contentKey != contentKey || currentPayload.isAnswerShown != isAnswerShown || currentPayload.isNightMode != isNightMode || currentPayload.composeStyle != composeStyle || currentPayload.bodyClass != bodyClass || currentPayload.baseUrl != baseUrl

            if (javascriptCommand != null && currentPayload.lastJavascriptCommandId != javascriptCommand.id) {
                currentPayload.pendingJavascriptCommand = javascriptCommand
            }

            if (currentPayload.shellLoaded) {
                if (stateChanged) {
                    currentPayload.contentKey = contentKey
                    currentPayload.isAnswerShown = isAnswerShown
                    currentPayload.isNightMode = isNightMode
                    currentPayload.composeStyle = composeStyle
                    currentPayload.bodyClass = bodyClass
                    currentPayload.baseUrl = baseUrl
                    val fullScript = createShowCardScript()
                    webView.evaluateJavascript(fullScript, null)
                }

                currentPayload.pendingJavascriptCommand?.let { command ->
                    webView.evaluateJavascript(command.script, null)
                    currentPayload.lastJavascriptCommandId = command.id
                    currentPayload.pendingJavascriptCommand = null
                    currentOnJavascriptCommandConsumed(command.id)
                }
            } else {
                if (stateChanged) {
                    currentPayload.contentKey = contentKey
                    currentPayload.isAnswerShown = isAnswerShown
                    currentPayload.isNightMode = isNightMode
                    currentPayload.composeStyle = composeStyle
                    currentPayload.baseUrl = baseUrl
                    currentPayload.pendingShowCardScript = createShowCardScript()
                }
            }
        }
    }, onRelease = { webView ->
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.setOnTouchListener(null)
        webView.destroy()
    }, modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    )
}

/**
 * Payload stored in the WebView tag for communication between the update callback and onPageFinished.
 */
private data class FlashcardContentKey(
    val questionHtml: String,
    val answerHtml: String,
)

@kotlinx.serialization.Serializable
private data class SpaCardPayload(
    val html: String,
    val isAnswer: Boolean,
    val css: String = "",
    val composeCss: String = "",
    val bodyClass: String = "",
    val isNightMode: Boolean = false,
    val enableCrossfade: Boolean = true,
    val baseUrl: String = ""
)

private class FlashcardPayload(
    var contentKey: FlashcardContentKey?,
    var isAnswerShown: Boolean,
    var baseUrl: String,
    var isNightMode: Boolean,
    var composeStyle: String,
    var bodyClass: String,
    var pendingJavascriptCommand: ReviewerJavascriptCommand? = null,
    var lastJavascriptCommandId: Int = -1,
    var shellLoaded: Boolean = false,
    var pendingShowCardScript: String? = null
)

private fun buildShellHtml(context: Context, isNightMode: Boolean): String {
    return try {
        context.assets.open("reviewer_shell.html").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        stdHtml(context, listOf("scripts/reviewer_bridge.js"), isNightMode)
    }
}
