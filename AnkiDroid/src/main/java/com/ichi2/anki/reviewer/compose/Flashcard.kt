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
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
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
    val isNightMode = androidx.compose.foundation.isSystemInDarkTheme()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColorHex = String.format("#%06X", (0xFFFFFF and onSurfaceColor.toArgb()))
    val typography = MaterialTheme.typography
    val displayLargeStyle = typography.displayMedium
    val bodyLargeStyle = typography.titleLarge

    Crossfade(
        targetState = Pair(isAnswerShown, if (isAnswerShown) answerHtml else questionHtml),
        animationSpec = tween(300)
    ) { (shown, currentHtml) ->
        val currentStyle = if (shown) bodyLargeStyle else displayLargeStyle
        val currentPadding = if (shown) 40 else 36
        AndroidView(
            factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    val resourceHandler = com.ichi2.anki.ViewerResourceHandler(context)

                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        return resourceHandler.shouldInterceptRequest(request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        onLinkClick(request.url.toString())
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val payload = view.tag as? FlashcardPayload ?: return
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
                setOnTouchListener { _, event ->
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
                        text-align: center;
                        font-family: 'Roboto', sans-serif;
                        font-size: ${currentStyle.fontSize.value}px;
                        font-weight: ${currentStyle.fontWeight?.weight ?: 400};
                        line-height: ${currentStyle.lineHeight.value}px;
                        letter-spacing: ${currentStyle.letterSpacing.value}px;
                        padding-top: ${currentPadding}px;
                        padding-bottom: ${toolbarHeight}px;
                    }
                    hr {
                        opacity: 0.1;
                        margin-bottom: 12px;
                    }
                    .replay-button {
                        display: inline-block;
                        height: 48px;
                        width: 48px;
                    }
                    .play-action {
                        fill: ${onSurfaceColorHex}EF;
                    }
                </style>
                """.trimIndent()

            val extraAssets = listOf("backend/js/reviewer_extras_bundle.js")
            val shell = com.ichi2.anki.previewer.stdHtml(webView.context, extraAssets, isNightMode)

            // Build the JS call to evaluate AFTER page loads via evaluateJavascript().
            // IMPORTANT: We must NOT embed _showQuestion/_showAnswer in <script> tags
            // in the HTML because IO card HTML contains </script> which prematurely
            // terminates the script tag, causing raw text to be displayed.
            val showCardScript = if (shown) {
                "_showAnswer(${kotlinx.serialization.json.Json.encodeToString(currentHtml)}, ${kotlinx.serialization.json.Json.encodeToString(bodyClass)});"
            } else {
                "_showQuestion(${kotlinx.serialization.json.Json.encodeToString(currentHtml)}, ${kotlinx.serialization.json.Json.encodeToString(answerHtml)}, ${kotlinx.serialization.json.Json.encodeToString(bodyClass)});"
            }

            val evalScript = showCardScript + "\n" + IO_POST_LOAD_SCRIPT

            val reviewerExtrasCss = """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""
            val styledHtml = shell.replace("</head>", "$reviewerExtrasCss\n$composeStyle\n</head>")

            Timber.tag("Flashcard").d("styledHtml generated")
            val payload = FlashcardPayload(currentHtml, evalScript)
            val currentPayload = webView.tag as? FlashcardPayload
            if (currentPayload?.contentKey != currentHtml) {
                webView.tag = payload
                webView.loadDataWithBaseURL(
                    baseUrl, styledHtml, "text/html", "UTF-8", null
                )
            }
        }, onRelease = { webView ->
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.setOnTouchListener(null)
            webView.destroy()
        }, modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}

/**
 * Payload stored in the WebView tag for communication between [update] and [onPageFinished].
 */
private data class FlashcardPayload(
    val contentKey: String,
    val evalScript: String,
    var scriptExecuted: Boolean = false,
)

/**
 * Post-load JavaScript for Image Occlusion layout and setup.
 *
 * IMPORTANT: _showQuestion/_showAnswer are ASYNC (queued via a Promise chain in reviewer.js).
 * When this script runs, the card HTML has NOT yet been injected into #qa.
 * We must poll for the image-occlusion-container to appear, THEN wait for the image to load,
 * THEN apply the layout and run setup.
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
        var availableWidth = Math.max(parentWidth, viewportWidth, bodyWidth);
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

        // Force layout reflow
        void container.offsetHeight;

        // Run anki.imageOcclusion.setup() if available and not already run by
        // the inline script from the card template.
        if (globalThis.anki && globalThis.anki.imageOcclusion &&
            typeof globalThis.anki.imageOcclusion.setup === 'function') {
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

@Preview(showBackground = true)
@Composable
fun FlashcardPreview() {
    Flashcard(
        baseUrl = "http://localhost",
        questionHtml = "<html><body><h1>Hello, World!</h1><a href=\"https://example.com\">A link</a></body></html>",
        answerHtml = "<html><body><p>This is the answer.</p></body></html>",
        bodyClass = "card card1",
        onTap = {},
        onLinkClick = {},
        isAnswerShown = false
    )
}

@Preview(showBackground = true)
@Composable
fun FlashcardPreviewAnswerShown() {
    Flashcard(
        baseUrl = "http://localhost",
        questionHtml = "<html><body><h1>Hello, World!</h1></body></html>",
        answerHtml = "<html><body><p>This is the answer.</p></body></html>",
        bodyClass = "card card1",
        onTap = {},
        onLinkClick = {},
        isAnswerShown = true
    )
}
