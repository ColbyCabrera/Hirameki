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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.previewer.stdHtml
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
class SessionStorageTest {

    class WebViewTestHarness(private val baseUrl: String) {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val webView: WebView

        init {
            var createdView: WebView? = null
            instrumentation.runOnMainSync {
                createdView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            }
            webView = requireNotNull(createdView)
        }

        suspend fun loadHtml(html: String) {
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
            assertTrue("Page load timed out", loaded == true)
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
                webView.destroy()
            }
        }
    }

    @Test
    fun verifySessionStoragePersistsAcrossCardFlipWithProductionShell() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = WebViewTestHarness("http://localhost/")

        val frontHtml = stdHtml(context)
        val backHtml = stdHtml(context)

        try {
            // 1. Load Front card HTML
            harness.loadHtml(frontHtml)

            // 2. Set sessionStorage data via setItem and property assignment on Front card
            harness.evalJs("sessionStorage.setItem('ankiTestKey', 'persistentValue123');")
            harness.evalJs("sessionStorage.ankiPropKey = 'persistentPropValue456';")

            // Verify readable on Front card
            assertEquals(
                "\"persistentValue123\"", harness.evalJs("sessionStorage.getItem('ankiTestKey');")
            )
            assertEquals(
                "\"persistentPropValue456\"", harness.evalJs("sessionStorage.ankiPropKey;")
            )

            // 3. Reload WebView with Back card HTML via loadDataWithBaseURL (simulating card flip)
            harness.loadHtml(backHtml)

            // 4. Verify sessionStorage data survived reload and is readable on Back card
            assertEquals(
                "\"persistentValue123\"", harness.evalJs("sessionStorage.getItem('ankiTestKey');")
            )
            assertEquals(
                "\"persistentPropValue456\"", harness.evalJs("sessionStorage.ankiPropKey;")
            )

            // 5. Test removal and clearing
            harness.evalJs("sessionStorage.removeItem('ankiTestKey');")
            assertEquals("null", harness.evalJs("sessionStorage.getItem('ankiTestKey');"))
            assertEquals(
                "\"persistentPropValue456\"", harness.evalJs("sessionStorage.ankiPropKey;")
            )

            harness.evalJs("sessionStorage.clear();")
            assertEquals("null", harness.evalJs("sessionStorage.ankiPropKey;"))

        } finally {
            harness.destroy()
        }
    }

    @Test
    fun verifySessionStoragePersistsWithLegacyCardTemplate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val harness = WebViewTestHarness("file:///android_asset/")
        val cardTemplateContent =
            context.assets.open("card_template.html").reader().use { it.readText() }

        val frontHtml = cardTemplateContent.replace("::content::", "<div>Front</div>")
        val backHtml = cardTemplateContent.replace("::content::", "<div>Back</div>")

        try {
            harness.loadHtml(frontHtml)
            harness.evalJs("sessionStorage.setItem('legacyKey', 'legacyValue');")
            assertEquals("\"legacyValue\"", harness.evalJs("sessionStorage.getItem('legacyKey');"))

            // Flip card to Back
            harness.loadHtml(backHtml)
            assertEquals("\"legacyValue\"", harness.evalJs("sessionStorage.getItem('legacyKey');"))
        } finally {
            harness.destroy()
        }
    }
}
