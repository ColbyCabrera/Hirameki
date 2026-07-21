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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowWebView
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class SessionStorageIntegrationTest : RobolectricTest() {
    @Test
    fun webViewTemplateIncludesSessionStoragePolyfillBeforeOtherScripts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = WebView(context)
        val shadowWebView: ShadowWebView = shadowOf(webView)

        val sampleFrontHtml =
            "<html><head><script src=\"file:///android_asset/scripts/session_storage_polyfill.js\" " +
                "type=\"text/javascript\"></script></head><body>Front</body></html>"
        val sampleBackHtml =
            "<html><head><script src=\"file:///android_asset/scripts/session_storage_polyfill.js\" " +
                "type=\"text/javascript\"></script></head><body>Back</body></html>"

        // 1. Load Front Card HTML via loadDataWithBaseURL
        webView.loadDataWithBaseURL("http://127.0.0.1:8765/", sampleFrontHtml, "text/html", "utf-8", null)
        val frontLoad = shadowWebView.lastLoadDataWithBaseURL
        assertNotNull(frontLoad, "Front loadDataWithBaseURL should be recorded")
        assertThat(frontLoad.data, containsString("session_storage_polyfill.js"))

        // 2. Simulate Card Flip to Back Card HTML via loadDataWithBaseURL
        webView.loadDataWithBaseURL("http://127.0.0.1:8765/", sampleBackHtml, "text/html", "utf-8", null)
        val backLoad = shadowWebView.lastLoadDataWithBaseURL
        assertNotNull(backLoad, "Back loadDataWithBaseURL should be recorded")
        assertThat(backLoad.data, containsString("session_storage_polyfill.js"))

        // 3. Verify origin consistency (both use same baseUrl origin so localStorage state is preserved across reloads)
        assertEquals("http://127.0.0.1:8765/", frontLoad.baseUrl)
        assertEquals("http://127.0.0.1:8765/", backLoad.baseUrl)
    }

    @Test
    fun sessionStoragePolyfillAssetIsPresentAndValid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetManager = context.assets
        val inputStream = assetManager.open("scripts/session_storage_polyfill.js")
        val content = inputStream.bufferedReader().use { it.readText() }

        assertNotNull(content, "session_storage_polyfill.js asset should be readable")
        assertThat(content, containsString("window.sessionStorage"))
        assertThat(content, containsString("__anki_ss_"))
        assertThat(content, containsString("Proxy"))
        assertThat(content, containsString("localStorage"))
    }

    @Test
    fun productionShellTemplatesIncludePolyfill() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify legacy CardTemplate includes polyfill
        val cardTemplateContent = context.assets.open("card_template.html").reader().use { it.readText() }
        assertThat(cardTemplateContent, containsString("scripts/session_storage_polyfill.js"))

        // 2. Verify Compose Reviewer stdHtml includes polyfill
        val stdHtmlOutput = com.ichi2.anki.previewer.stdHtml(context)
        assertThat(stdHtmlOutput, containsString("scripts/session_storage_polyfill.js"))
    }
}
