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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SessionStorageTest {

    @Test
    fun verifySessionStoragePersistsAcrossCardFlipWithProductionShell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        var createdView: WebView? = null

        instrumentation.runOnMainSync {
            createdView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }

        val webView = requireNotNull(createdView)
        val baseUrl = "http://localhost/"

        val frontHtml = stdHtml(context)
        val backHtml = stdHtml(context)

        fun loadHtml(html: String) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        latch.countDown()
                    }
                }
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
            assertTrue("Page load timed out", latch.await(10, TimeUnit.SECONDS))
        }

        fun evalJs(script: String): String {
            val latch = CountDownLatch(1)
            var result: String? = null
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script) { res ->
                    result = res
                    latch.countDown()
                }
            }
            assertTrue("JS evaluation timed out", latch.await(10, TimeUnit.SECONDS))
            return result ?: "null"
        }

        try {
            // 1. Load Front card HTML
            loadHtml(frontHtml)

            // 2. Set sessionStorage data via setItem and property assignment on Front card
            evalJs("sessionStorage.setItem('ankiTestKey', 'persistentValue123');")
            evalJs("sessionStorage.ankiPropKey = 'persistentPropValue456';")

            // Verify readable on Front card
            assertEquals("\"persistentValue123\"", evalJs("sessionStorage.getItem('ankiTestKey');"))
            assertEquals("\"persistentPropValue456\"", evalJs("sessionStorage.ankiPropKey;"))

            // 3. Reload WebView with Back card HTML via loadDataWithBaseURL (simulating card flip)
            loadHtml(backHtml)

            // 4. Verify sessionStorage data survived reload and is readable on Back card
            assertEquals("\"persistentValue123\"", evalJs("sessionStorage.getItem('ankiTestKey');"))
            assertEquals("\"persistentPropValue456\"", evalJs("sessionStorage.ankiPropKey;"))

            // 5. Test removal and clearing
            evalJs("sessionStorage.removeItem('ankiTestKey');")
            assertEquals("null", evalJs("sessionStorage.getItem('ankiTestKey');"))
            assertEquals("\"persistentPropValue456\"", evalJs("sessionStorage.ankiPropKey;"))

            evalJs("sessionStorage.clear();")
            assertEquals("null", evalJs("sessionStorage.ankiPropKey;"))

        } finally {
            instrumentation.runOnMainSync {
                webView.destroy()
            }
        }
    }

    @Test
    fun verifySessionStoragePersistsWithLegacyCardTemplate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        var createdView: WebView? = null

        instrumentation.runOnMainSync {
            createdView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }

        val webView = requireNotNull(createdView)
        val baseUrl = "file:///android_asset/"
        val cardTemplateContent =
            context.assets.open("card_template.html").reader().use { it.readText() }

        val frontHtml = cardTemplateContent.replace("::content::", "<div>Front</div>")
        val backHtml = cardTemplateContent.replace("::content::", "<div>Back</div>")

        fun loadHtml(html: String) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        latch.countDown()
                    }
                }
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
            assertTrue("Page load timed out", latch.await(10, TimeUnit.SECONDS))
        }

        fun evalJs(script: String): String {
            val latch = CountDownLatch(1)
            var result: String? = null
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script) { res ->
                    result = res
                    latch.countDown()
                }
            }
            assertTrue("JS evaluation timed out", latch.await(10, TimeUnit.SECONDS))
            return result ?: "null"
        }

        try {
            loadHtml(frontHtml)
            evalJs("sessionStorage.setItem('legacyKey', 'legacyValue');")
            assertEquals("\"legacyValue\"", evalJs("sessionStorage.getItem('legacyKey');"))

            // Flip card to Back
            loadHtml(backHtml)
            assertEquals("\"legacyValue\"", evalJs("sessionStorage.getItem('legacyKey');"))
        } finally {
            instrumentation.runOnMainSync {
                webView.destroy()
            }
        }
    }
}
