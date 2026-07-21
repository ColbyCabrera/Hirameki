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
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionStorageTest : InstrumentedTest() {

    @Test
    fun verifySessionStoragePersistsAcrossCardFlipWithProductionShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val productionShellHtml = stdHtml(context)

        var webView: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }

        val baseUrl = "http://127.0.0.1:8765/"

        // 1. Load Front Card HTML using the real production shell stdHtml()
        var pageFinishedLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageFinishedLatch.countDown()
                }
            }
            webView?.loadDataWithBaseURL(baseUrl, productionShellHtml, "text/html", "utf-8", null)
        }
        assertTrue(
            pageFinishedLatch.await(5, TimeUnit.SECONDS),
            "Timeout waiting for front card page load"
        )

        // 2. Execute real JS to set item in sessionStorage on Front Card
        var jsLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.evaluateJavascript("sessionStorage.setItem('realUserData', 'hello_from_front');") {
                jsLatch.countDown()
            }
        }
        assertTrue(jsLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for JS setItem execution")

        // 3. Simulate Card Flip to Back Card (loadDataWithBaseURL reload using production shell)
        pageFinishedLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.loadDataWithBaseURL(baseUrl, productionShellHtml, "text/html", "utf-8", null)
        }
        assertTrue(
            pageFinishedLatch.await(5, TimeUnit.SECONDS),
            "Timeout waiting for back card page reload"
        )

        // 4. Execute real JS to retrieve item from sessionStorage on Back Card
        jsLatch = CountDownLatch(1)
        var retrievedValue: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.evaluateJavascript("sessionStorage.getItem('realUserData');") { result ->
                retrievedValue = result
                jsLatch.countDown()
            }
        }
        assertTrue(jsLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for JS getItem execution")

        // 5. Assert value persisted across the document reload
        assertEquals(
            "\"hello_from_front\"",
            retrievedValue,
            "sessionStorage data must persist across loadDataWithBaseURL reloads in production shell"
        )
    }
}
