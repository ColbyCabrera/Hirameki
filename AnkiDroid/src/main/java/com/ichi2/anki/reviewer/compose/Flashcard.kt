/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.previewer.stdHtml
import kotlinx.serialization.json.Json
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Flashcard(
    baseUrl: String,
    questionHtml: String,
    answerHtml: String,
    bodyClass: String,
    onTap: () -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAnswerShown: Boolean,
    toolbarHeight: Int = 0
) {
    val isNightMode = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorHex = String.format("#%06X", (0xFFFFFF and surfaceColor.toArgb()))
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColorHex = String.format("#%06X", (0xFFFFFF and onSurfaceColor.toArgb()))
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerColorHex =
        String.format("#%06X", (0xFFFFFF and surfaceContainerColor.toArgb()))
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorHex = String.format("#%06X", (0xFFFFFF and primaryColor.toArgb()))
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineColorHex = String.format("#%06X", (0xFFFFFF and outlineColor.toArgb()))
    val typography = MaterialTheme.typography
    val displayLargeStyle = typography.displayMedium
    val bodyLargeStyle = typography.titleLarge

    Crossfade(
        targetState = Pair(isAnswerShown, if (isAnswerShown) answerHtml else questionHtml),
        animationSpec = tween(300),
        label = "FlashcardCrossfade"
    ) { (shown, currentHtml) ->
        val currentStyle = if (shown) bodyLargeStyle else displayLargeStyle
        val currentPadding = if (shown) 40 else 36

        AndroidView(
            factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true

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
                        val url = request.url.toString()
                        if (url.startsWith("file:") || url.startsWith("data:") || url.startsWith(
                                "javascript:"
                            ) || url.startsWith("blob:")
                        ) {
                            return false
                        }
                        if (url.startsWith(baseUrl)) {
                            val path = url.substringAfter(baseUrl)
                            if (path.isEmpty() || path.startsWith("#") || path.startsWith("/#")) {
                                return false
                            }
                        }
                        onLinkClick(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val payload = view.tag as? FlashcardPayload ?: return
                        payload.shellLoaded = true
                        if (payload.scriptExecuted) return
                        payload.scriptExecuted = true
                        view.evaluateJavascript(payload.evalScript, null)
                    }
                }

                val gestureDetector = GestureDetector(
                    context, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            onTap()
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
            val composeStyle = """
                <style id="compose-styles">
                    @import url('https://fonts.googleapis.com/css2?family=Roboto&display=swap');
                    html {
                        color: ${onSurfaceColorHex}EF;
                        background-color: $surfaceColorHex;
                    }
                    body.card {
                        text-align: center;
                        font-family: 'Roboto', sans-serif;
                        font-size: ${currentStyle.fontSize.value}px;
                        font-weight: ${currentStyle.fontWeight?.weight ?: 400};
                        line-height: ${currentStyle.lineHeight.value}px;
                        letter-spacing: ${currentStyle.letterSpacing.value}px;
                        text-wrap: pretty;
                        padding-top: ${currentPadding}px;
                        padding-bottom: ${toolbarHeight}px;
                        background-color: $surfaceColorHex;
                        color: ${onSurfaceColorHex}EF;
                    }
                    body.card.nightMode, body.card.night_mode {
                        background-color: $surfaceColorHex;
                        color: ${onSurfaceColorHex}EF;
                    }
                    hr {
                        opacity: 0.1;
                        margin-bottom: 12px;
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
                        padding: 2px;
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

                    .replay-button svg {
                        color: ${onSurfaceColorHex}EF;
                        display: inline-block;
                        height: 64px;
                        width: 64px;
                    }
                    .replay-button svg path {
                        fill: ${onSurfaceColorHex}EF;
                    }
                    .replay-button .playImage {
                        display: block;
                        width: 100%;
                        height: 100%;
                        fill: currentColor;
                        color: inherit;
                    }
                </style>
            """.trimIndent()

            val extraAssets = listOf("backend/js/reviewer_extras_bundle.js")
            val shell = stdHtml(webView.context, extraAssets, isNightMode)

            // Build the JS call to evaluate AFTER page loads via evaluateJavascript().
            // IMPORTANT: We must NOT embed _showQuestion/_showAnswer in <script> tags
            // in the HTML because IO card HTML contains </script> which prematurely
            // terminates the script tag, causing raw text to be displayed.
            val showCardScript = if (shown) {
                "_showAnswer(${Json.encodeToString(currentHtml)}, ${
                    Json.encodeToString(
                        bodyClass
                    )
                });"
            } else {
                "_showQuestion(${Json.encodeToString(currentHtml)}, ${
                    Json.encodeToString(
                        answerHtml
                    )
                }, ${Json.encodeToString(bodyClass)});"
            }

            val evalScript = IO_SETUP_INTERCEPT + "\n" + showCardScript + "\n" + IO_POST_LOAD_SCRIPT

            val reviewerExtrasCss =
                """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""
            val styledHtml = shell.replace("</head>", "$reviewerExtrasCss\n$composeStyle\n</head>")

            Timber.tag("Flashcard").d("styledHtml generated")

            val contentKey = "${questionHtml.hashCode()}_${answerHtml.hashCode()}"
            val currentPayload = webView.tag as? FlashcardPayload

            if (currentPayload?.contentKey != contentKey || !currentPayload.shellLoaded) {
                val payload = FlashcardPayload(contentKey, evalScript)
                webView.tag = payload
                webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
            } else {
                currentPayload.evalScript = evalScript
                currentPayload.scriptExecuted = true
                webView.evaluateJavascript(evalScript, null)
            }
        }, onRelease = { webView ->
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
}

/**
 * Payload stored in the WebView tag for communication between the update callback and onPageFinished.
 */
private data class FlashcardPayload(
    val contentKey: String,
    var evalScript: String,
    var scriptExecuted: Boolean = false,
    var shellLoaded: Boolean = false,
)

/**
 * Intercepts anki.imageOcclusion.setup() with a no-op BEFORE _showQuestion runs.
 *
 * Why: _showQuestion is async (queued via a Promise chain). When the queued work
 * resolves, it sets innerHTML and then executes the card's inline scripts, including
 * `<script>anki.imageOcclusion.setup()</script>`. setup() waits for the image to load,
 * then uses requestAnimationFrame to size the canvas and draw masks. But if the image
 * is cached, setup() completes (~16ms) before our layout poll (50ms) fires, resulting
 * in a 0x0 canvas — masks invisible 95% of the time.
 *
 * By intercepting setup() here (before _showQuestion queues), the card's inline script
 * becomes a no-op. Our [IO_POST_LOAD_SCRIPT] then applies layout dimensions and calls
 * the original setup() exactly once, guaranteeing correct canvas sizing.
 */
private const val IO_SETUP_INTERCEPT: String = """
if (globalThis.anki && globalThis.anki.imageOcclusion && typeof globalThis.anki.imageOcclusion.setup === 'function') {
    globalThis.__ioOriginalSetup = globalThis.anki.imageOcclusion.setup;
    globalThis.anki.imageOcclusion.setup = function() { return Promise.resolve(); };
}
"""

/**
 * Post-load JavaScript for Image Occlusion layout and setup.
 *
 * IMPORTANT: _showQuestion/_showAnswer are ASYNC (queued via a Promise chain in reviewer.js).
 * When this script runs, the card HTML has NOT yet been injected into #qa.
 * We must poll for the image-occlusion-container to appear, THEN wait for the image to load,
 * THEN apply layout dimensions, THEN call the original setup() exactly once.
 */
private val IO_POST_LOAD_SCRIPT: String = """
(function() {
    var maxWaitAttempts = 80;
    var waitAttempts = 0;

    function waitForContainer() {
        var container = document.getElementById('image-occlusion-container');
        if (!container) {
            if (waitAttempts < maxWaitAttempts) {
                waitAttempts++;
                setTimeout(waitForContainer, 50);
            }
            return;
        }
        var image = container.querySelector('img');
        if (!image) return;

        if (image.complete && image.naturalWidth > 0) {
            applyLayout(container, image);
        } else {
            image.addEventListener('load', function() {
                applyLayout(container, image);
            });
            image.addEventListener('error', function() {
                applyLayout(container, image);
            });
        }
    }

    function applyLayout(container, image) {
        if (image.naturalWidth <= 0 || image.naturalHeight <= 0) return;

        var parentWidth = container.parentElement ? container.parentElement.clientWidth : 0;
        var viewportWidth = document.documentElement.clientWidth;
        var bodyWidth = document.body ? document.body.clientWidth : 0;
        var availableWidth = parentWidth > 0 ? parentWidth : 0;
        if (availableWidth <= 0 && bodyWidth > 0) {
            availableWidth = bodyWidth;
        }
        if (availableWidth <= 0 && viewportWidth > 0) {
            availableWidth = viewportWidth;
        }
        var width = Math.max(1, availableWidth);
        var height = Math.max(1, Math.round(width * image.naturalHeight / image.naturalWidth));

        container.style.display = 'block';
        container.style.width = width + 'px';
        container.style.height = height + 'px';
        container.style.minHeight = height + 'px';
        container.style.maxWidth = '100%';
        container.style.aspectRatio = image.naturalWidth + ' / ' + image.naturalHeight;

        image.style.width = width + 'px';
        image.style.height = height + 'px';

        var canvas = document.getElementById('image-occlusion-canvas');
        if (canvas) {
            canvas.style.width = width + 'px';
            canvas.style.height = height + 'px';
        }

        // Force layout reflow so canvas gets correct clientWidth/clientHeight
        void container.offsetHeight;

        // Call the original setup() exactly once. The card's inline script was
        // intercepted (no-op) by IO_SETUP_INTERCEPT, so this is the only real call.
        // setup() uses requestAnimationFrame internally, and now the container/canvas
        // have correct CSS dimensions for it to read.
        if (globalThis.__ioOriginalSetup) {
            globalThis.anki.imageOcclusion.setup = globalThis.__ioOriginalSetup;
            delete globalThis.__ioOriginalSetup;
            try {
                globalThis.anki.imageOcclusion.setup();
            } catch(e) {
                var err = document.getElementById('err');
                if (err && !err.innerText) err.innerText = String(e);
            }
        }
    }

    waitForContainer();
})();
""".trimIndent()

