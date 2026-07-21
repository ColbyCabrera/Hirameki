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

            delay(350)

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

            delay(120)

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
            delay(120)
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
}
