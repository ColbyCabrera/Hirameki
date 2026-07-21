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

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionStorageOnDeviceTest : InstrumentedTest() {
    @Test
    fun verifyPolyfillAssetIsPresentOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetManager = context.assets
        val content: String =
            assetManager.open("scripts/session_storage_polyfill.js").reader().use { reader ->
                reader.readText()
            }

        assertNotNull(content, "session_storage_polyfill.js asset should be readable on device")
        assertTrue(content.contains("window.sessionStorage"), "Must polyfill window.sessionStorage")
        assertTrue(content.contains("__anki_ss_"), "Must use prefix __anki_ss_")
    }

    @Test
    fun verifySessionStoragePersistenceAcrossReloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetManager = context.assets
        val polyfillContent = assetManager.open("scripts/session_storage_polyfill.js").reader().use { it.readText() }

        var webView: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView =
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
        }

        val baseUrl = "http://127.0.0.1:8765/"
        val frontHtml = "<html><head><script type=\"text/javascript\">\n$polyfillContent\n</script></head><body>Front</body></html>"
        val backHtml = "<html><head><script type=\"text/javascript\">\n$polyfillContent\n</script></head><body>Back</body></html>"

        // 1. Load front HTML
        var pageFinishedLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        pageFinishedLatch.countDown()
                    }
                }
            webView?.loadDataWithBaseURL(baseUrl, frontHtml, "text/html", "utf-8", null)
        }
        assertTrue(pageFinishedLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for front page to load")

        // 2. Set value in sessionStorage
        var jsResultLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.evaluateJavascript("sessionStorage.setItem('persistentKey', 'testValue');") {
                jsResultLatch.countDown()
            }
        }
        assertTrue(jsResultLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for JS evaluation to set item")

        // 3. Load back HTML
        pageFinishedLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.loadDataWithBaseURL(baseUrl, backHtml, "text/html", "utf-8", null)
        }
        assertTrue(pageFinishedLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for back page to load")

        // 4. Get value from sessionStorage
        jsResultLatch = CountDownLatch(1)
        var persistentValue: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.evaluateJavascript("sessionStorage.getItem('persistentKey');") { result ->
                persistentValue = result
                jsResultLatch.countDown()
            }
        }
        assertTrue(jsResultLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for JS evaluation to get item")

        assertEquals("\"testValue\"", persistentValue, "SessionStorage value should persist across loadDataWithBaseURL reloads")
    }
}
