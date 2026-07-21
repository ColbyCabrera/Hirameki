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

package com.ichi2.anki.cardviewer

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class SpaDomSwapTest {

    class SpaWebViewHarness(private val baseUrl: String) {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        val webView: WebView

        init {
            var createdView: WebView? = null
            instrumentation.runOnMainSync {
                createdView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                }
            }
            webView = requireNotNull(createdView)
        }

        suspend fun loadShellHtml(html: String) {
            val loaded = withTimeoutOrNull(10_000.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    instrumentation.runOnMainSync {
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    }
                }
                true
            }
            assertTrue("Shell page load timed out", loaded == true)
        }

        suspend fun evalJs(script: String): String {
            val result = withTimeoutOrNull(10_000.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    instrumentation.runOnMainSync {
                        webView.evaluateJavascript(script) { res ->
                            if (continuation.isActive) {
                                continuation.resume(res ?: "null")
                            }
                        }
                    }
                }
            }
            assertTrue("JS evaluation timed out", result != null)
            return result ?: "null"
        }

        /**
         * Polls a JS expression until it returns the expected value or times out.
         * Replaces hardcoded delay() calls to eliminate flakiness on CI runners.
         *
         * @param jsExpression The JavaScript expression to evaluate repeatedly.
         * @param expected The expected string result (as returned by evaluateJavascript).
         * @param timeoutMs Maximum time to wait before failing.
         * @param intervalMs Polling interval between evaluations.
         */
        suspend fun pollUntil(
            jsExpression: String,
            expected: String,
            timeoutMs: Long = 5_000,
            intervalMs: Long = 50
        ): String {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastResult = "null"
            while (System.currentTimeMillis() < deadline) {
                lastResult = evalJs(jsExpression)
                if (lastResult == expected) return lastResult
                delay(intervalMs)
            }
            throw AssertionError(
                "pollUntil timed out after ${timeoutMs}ms. Expression: $jsExpression, " +
                    "expected: $expected, last result: $lastResult"
            )
        }

        fun destroy() {
            instrumentation.runOnMainSync {
                WebStorage.getInstance().deleteAllData()
                webView.destroy()
            }
        }
    }

    private fun readShellHtml(context: Context): String {
        return context.assets.open("reviewer_shell.html").bufferedReader().use { it.readText() }
    }

    @Test
    fun verifySpaBridgeLoadsAndExecutesShowCard() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            val defined = harness.evalJs("typeof window.anki?.showCard;")
            assertEquals("\"function\"", defined)

            val card1Payload = """
                {
                    "html": "<div id='card1-content'>Card 1 Front</div><script>window.card1Executed = true;</script>",
                    "isAnswer": false,
                    "css": ".card1-style { color: red; }",
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($card1Payload);")

            val card1Exec = harness.evalJs("window.card1Executed;")
            assertEquals("true", card1Exec)

            val card2Payload = """
                {
                    "html": "<div id='card2-content'>Card 2 Back</div><script>window.card2Executed = true;</script>",
                    "isAnswer": true,
                    "css": ".card2-style { color: blue; }",
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($card2Payload);")

            val card2Exec = harness.evalJs("window.card2Executed;")
            assertEquals("true", card2Exec)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyCrossfadeTransitionAndRapidSwaps() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            val cardAPayload = """
                {
                    "html": "<div id='cardA'>Card A</div>",
                    "isAnswer": false,
                    "css": ".cardA { font-size: 20px; }",
                    "enableCrossfade": true
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($cardAPayload);")

            val cardBPayload = """
                {
                    "html": "<div id='cardB'>Card B Rapid</div>",
                    "isAnswer": true,
                    "css": ".cardB { font-size: 30px; }",
                    "enableCrossfade": true
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($cardBPayload);")

            // Poll until crossfade completes and the active layer contains Card B content
            harness.pollUntil(
                "document.querySelector('.card-layer:not(.layer-hidden)')?.innerHTML?.includes('Card B Rapid') ? 'true' : 'false'",
                "\"true\""
            )

            val activeContent = harness.evalJs("document.querySelector('.card-layer:not(.layer-hidden)').innerHTML;")
            assertTrue(activeContent.contains("Card B Rapid"))

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyHeadStylesAndScopePreserved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            // Card HTML containing inline <style> tag before body markup
            val cardAPayload = """
                {
                    "html": "<style>.head-styled { color: green; }</style><div class='head-styled'>Head Styled Card</div>",
                    "isAnswer": false,
                    "css": ".head-styled { font-weight: bold; }",
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($cardAPayload);")

            val hasHeadStyle = harness.evalJs("document.querySelector('style') !== null;")
            assertEquals("true", hasHeadStyle)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyEventListenerAndTimerPurgeOnSwap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            val card1Payload = """
                {
                    "html": "<div>Card 1</div><script>window.intervalCount = 0; window.testTimer = setInterval(() => { window.intervalCount++; }, 50);</script>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($card1Payload);")

            // Poll until interval has fired at least once
            harness.pollUntil(
                "(window.intervalCount || 0) > 0 ? 'true' : 'false'",
                "\"true\""
            )

            val count1 = harness.evalJs("window.intervalCount;").toIntOrNull() ?: 0
            assertTrue(count1 > 0)

            // Swap to Card 2, which triggers EventListenerTracker.purge()
            val card2Payload = """
                {
                    "html": "<div>Card 2</div>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($card2Payload);")

            val countAtSwap = harness.evalJs("window.intervalCount;").toIntOrNull() ?: 0

            // Poll briefly to confirm the interval is no longer incrementing
            harness.pollUntil(
                "window.intervalCount;",
                countAtSwap.toString(),
                timeoutMs = 500,
                intervalMs = 100
            )

            val countAfterDelay = harness.evalJs("window.intervalCount;").toIntOrNull() ?: 0

            // Verify interval timer was stopped and did not increment further
            assertEquals(countAtSwap, countAfterDelay)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyTypeInAnswerImmediateBinding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            val typePayload = """
                {
                    "html": "<div><input id='typeans' value='test' /></div>",
                    "isAnswer": false,
                    "enableCrossfade": true
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($typePayload);")

            // Verify input listener attached immediately on DOM swap (before crossfade finishes)
            val hasInput = harness.evalJs("document.querySelector('input#typeans') !== null;")
            assertEquals("true", hasInput)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyImageOcclusionSingleContainerSwap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            // First show a normal card to populate container A
            val normalPayload = """
                {
                    "html": "<div id='normal-card'>Normal Card</div>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($normalPayload);")

            // Now show an Image Occlusion card
            val ioPayload = """
                {
                    "html": "<div id='image-occlusion-container'><img src='data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7' /><svg><rect id='active-rect' /></svg></div>",
                    "isAnswer": false,
                    "enableCrossfade": true
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($ioPayload);")

            // IO card should be present in the active container
            val hasIo = harness.evalJs("document.querySelector('#image-occlusion-container') !== null;")
            assertEquals("true", hasIo)

            // Verify SVG is present inside the IO container
            val hasSvg = harness.evalJs("document.querySelector('#image-occlusion-container svg') !== null;")
            assertEquals("true", hasSvg)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyImageOcclusionSvgPurgedOnSwap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            // Show an Image Occlusion card
            val ioPayload = """
                {
                    "html": "<div id='image-occlusion-container'><img src='data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7' /><svg><rect id='io-rect' /></svg></div>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($ioPayload);")

            val hasIoBefore = harness.evalJs("document.querySelector('#image-occlusion-container') !== null;")
            assertEquals("true", hasIoBefore)

            // Swap to a new card — IO container and SVG should be purged from the previous container
            val nextPayload = """
                {
                    "html": "<div id='next-card'>Next Card</div>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($nextPayload);")

            // Verify the IO container and SVG are no longer in the document
            val hasIoAfter = harness.evalJs("document.querySelector('#image-occlusion-container') !== null;")
            assertEquals("false", hasIoAfter)

            val hasSvgAfter = harness.evalJs("document.querySelectorAll('svg').length;")
            assertEquals("0", hasSvgAfter)

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifyImageOcclusionBypassesCrossfade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = SpaWebViewHarness("http://localhost/")

        try {
            harness.loadShellHtml(readShellHtml(context))

            // Show a normal card first so currentContainer has content
            val normalPayload = """
                {
                    "html": "<div id='normal'>Normal</div>",
                    "isAnswer": false,
                    "enableCrossfade": false
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($normalPayload);")

            // Show an IO card with crossfade enabled — should bypass crossfade
            val ioPayload = """
                {
                    "html": "<div id='image-occlusion-container'><img src='data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7' /><svg><rect /></svg></div>",
                    "isAnswer": false,
                    "enableCrossfade": true
                }
            """.trimIndent()
            harness.evalJs("window.anki.showCard($ioPayload);")

            // IO cards bypass crossfade, so there should be no crossfade-active or crossfade-out classes
            val hasCrossfadeActive = harness.evalJs("document.querySelector('.crossfade-active') !== null;")
            assertEquals("false", hasCrossfadeActive)

            val hasCrossfadeOut = harness.evalJs("document.querySelector('.crossfade-out') !== null;")
            assertEquals("false", hasCrossfadeOut)

            // The IO container should be visible in the active layer
            val hasIo = harness.evalJs("document.querySelector('.card-layer:not(.layer-hidden) #image-occlusion-container') !== null;")
            assertEquals("true", hasIo)

        } finally {
            harness.destroy()
        }
    }
}
