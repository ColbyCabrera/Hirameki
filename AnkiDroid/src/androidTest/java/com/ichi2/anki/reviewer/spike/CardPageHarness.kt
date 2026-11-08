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

package com.ichi2.anki.reviewer.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.previewer.stdHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal typealias ComponentActivityRule =
    AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

internal enum class CaptureMethod { SoftwareDraw, GraphicsLayer, PixelCopy }

internal class Capture(
    val bitmap: Bitmap,
    val ms: Double,
    val succeeded: Boolean = true,
)

internal data class ShowTiming(
    val readyMs: Double,
    val drawnMs: Double,
)

/**
 * One persistent card WebView, loaded once with the production shell, with a Compose element
 * floating over it like the reviewer's answer buttons.
 */
internal class CardPageHarness(
    private val composeRule: ComponentActivityRule,
    private val mediaDir: File? = null,
) {
    lateinit var webView: WebView
        private set
    private lateinit var cardLayer: GraphicsLayer
    private var floatingUiCenterInWindow = Offset.Zero
    private val readyTokens = Channel<String>(Channel.UNLIMITED)
    private val pageFinished = CompletableDeferred<Unit>()
    private var softwareBitmap: Bitmap? = null
    private var pixelCopyBitmap: Bitmap? = null
    val consoleMessages = CopyOnWriteArrayList<String>()

    val isWebMessageListenerSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    suspend fun start() {
        composeRule.setContent {
            val layer = rememberGraphicsLayer()
            SideEffect { cardLayer = layer }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context -> createWebView(context).also { webView = it } },
                    modifier =
                        Modifier.fillMaxSize().drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .size(240.dp, 72.dp)
                        .background(Color(FLOATING_UI_COLOR))
                        .onGloballyPositioned { floatingUiCenterInWindow = it.boundsInWindow().center },
                )
            }
        }
        composeRule.waitForIdle()
        loadShell()
    }

    private suspend fun loadShell() {
        onMain {
            val shell =
                stdHtml(webView.context, listOf("backend/js/reviewer_extras_bundle.js"))
                    .replace("</head>", "$REVIEWER_EXTRAS_CSS_LINK\n</head>")
            webView.loadDataWithBaseURL(SPIKE_BASE_URL, shell, "text/html", "UTF-8", null)
        }
        withTimeout(10.seconds) { pageFinished.await() }
        onMain { webView.evaluateJavascript(PAGE_SCRIPT, null) }
    }

    suspend fun show(
        token: Int,
        html: String,
        isAnswer: Boolean = false,
        answerHtml: String = "",
        bodyClass: String = "card card1",
    ): ShowTiming {
        val script =
            "spike.show($token, $isAnswer, ${JSONObject.quote(html)}, ${JSONObject.quote(answerHtml)}, " +
                "${JSONObject.quote(bodyClass)});"
        val sentAt = System.nanoTime()
        onMain { webView.evaluateJavascript(script, null) }
        withTimeout(10.seconds) {
            do {
                val received = readyTokens.receive()
            } while (received != token.toString())
        }
        val readyAt = System.nanoTime()
        awaitVisualState(token.toLong())
        val drawnAt = System.nanoTime()
        return ShowTiming(readyMs = (readyAt - sentAt).nanosToMs(), drawnMs = (drawnAt - sentAt).nanosToMs())
    }

    suspend fun evaluate(script: String): String =
        onMain {
            suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(script) { continuation.resume(it ?: "null") }
            }
        }

    suspend fun capture(method: CaptureMethod): Capture =
        when (method) {
            CaptureMethod.SoftwareDraw -> captureWithSoftwareDraw()
            CaptureMethod.GraphicsLayer -> captureWithGraphicsLayer()
            CaptureMethod.PixelCopy -> captureWithPixelCopy()
        }

    /** The floating Compose element's centre, as a fraction of the WebView's size. */
    suspend fun floatingUiCenterFraction(): Offset =
        onMain {
            val location = IntArray(2).also { webView.getLocationInWindow(it) }
            Offset(
                (floatingUiCenterInWindow.x - location[0]) / webView.width,
                (floatingUiCenterInWindow.y - location[1]) / webView.height,
            )
        }

    private suspend fun captureWithSoftwareDraw(): Capture =
        onMain {
            val bitmap = reusableBitmap(softwareBitmap).also { softwareBitmap = it }
            val start = System.nanoTime()
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            webView.draw(Canvas(bitmap))
            Capture(bitmap, (System.nanoTime() - start).nanosToMs())
        }

    private suspend fun captureWithGraphicsLayer(): Capture =
        onMain {
            val start = System.nanoTime()
            val image = cardLayer.toImageBitmap()
            val ms = (System.nanoTime() - start).nanosToMs()
            Capture(image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), ms)
        }

    private suspend fun captureWithPixelCopy(): Capture =
        onMain {
            val bitmap = reusableBitmap(pixelCopyBitmap).also { pixelCopyBitmap = it }
            val location = IntArray(2).also { webView.getLocationInWindow(it) }
            val area = Rect(location[0], location[1], location[0] + webView.width, location[1] + webView.height)
            val start = System.nanoTime()
            val result =
                suspendCancellableCoroutine { continuation ->
                    PixelCopy.request(
                        composeRule.activity.window,
                        area,
                        bitmap,
                        { continuation.resume(it) },
                        Handler(Looper.getMainLooper()),
                    )
                }
            Capture(bitmap, (System.nanoTime() - start).nanosToMs(), succeeded = result == PixelCopy.SUCCESS)
        }

    private fun reusableBitmap(existing: Bitmap?): Bitmap =
        existing?.takeIf { it.width == webView.width && it.height == webView.height }
            ?: Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)

    private suspend fun awaitVisualState(requestId: Long) =
        withTimeout(5.seconds) {
            onMain {
                suspendCancellableCoroutine { continuation ->
                    webView.postVisualStateCallback(
                        requestId,
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        },
                    )
                }
            }
        }

    private fun createWebView(context: Context): WebView {
        val resourceHandler = ViewerResourceHandler(context)
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            if (isWebMessageListenerSupported) {
                WebViewCompat.addWebMessageListener(this, BRIDGE_NAME, setOf(SPIKE_ORIGIN)) { _, message, _, _, _ ->
                    message.data?.let { readyTokens.trySend(it) }
                }
            }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        consoleMessages += consoleMessage.message()
                        return true
                    }
                }
            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = mediaResponse(request) ?: resourceHandler.shouldInterceptRequest(request)

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        pageFinished.complete(Unit)
                    }
                }
        }
    }

    private fun mediaResponse(request: WebResourceRequest): WebResourceResponse? {
        val dir = mediaDir ?: return null
        if (request.url.host != SPIKE_HOST) return null
        val name =
            request.url.path
                ?.removePrefix("/")
                ?.takeIf { it.isNotEmpty() } ?: return null
        val file = File(dir, name).takeIf { it.isFile } ?: return null
        return WebResourceResponse(URLConnection.guessContentTypeFromName(file.name), null, FileInputStream(file))
    }

    private suspend fun <T> onMain(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }

    companion object {
        const val FLOATING_UI_COLOR = 0xFFFF00FF
        private const val SPIKE_HOST = "127.0.0.1"
        private const val SPIKE_ORIGIN = "http://$SPIKE_HOST:1"
        private const val SPIKE_BASE_URL = "$SPIKE_ORIGIN/"
        private const val BRIDGE_NAME = "spikeBridge"
        private const val REVIEWER_EXTRAS_CSS_LINK =
            """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""

        private val PAGE_SCRIPT =
            """
            globalThis.spike = {
                show(token, isAnswer, html, answerHtml, bodyClass) {
                    if (isAnswer) _showAnswer(html, bodyClass); else _showQuestion(html, answerHtml, bodyClass);
                    _queueAction(() =>
                        new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
                            .then(() => $BRIDGE_NAME.postMessage(String(token))),
                    );
                },
            };
            """.trimIndent()
    }
}

internal fun Long.nanosToMs(): Double = this / 1_000_000.0
