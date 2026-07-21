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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.previewer.stdHtml
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStorageIntegrationTest : RobolectricTest() {

    @Test
    fun sessionStoragePolyfillAssetIsPresentAndValid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = context.assets.open("scripts/session_storage_polyfill.js").reader().use { it.readText() }

        assertNotNull(content, "session_storage_polyfill.js asset should be readable")
        assertTrue(content.contains("window.sessionStorage"), "Must polyfill window.sessionStorage")
        assertTrue(content.contains("__anki_ss_"), "Must use prefix __anki_ss_")
        assertTrue(content.contains("Proxy"), "Must use Proxy trap")
        assertTrue(content.contains("localStorage"), "Must back onto localStorage")
    }

    @Test
    fun productionShellTemplatesIncludePolyfill() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Legacy reviewer shell (card_template.html)
        val cardTemplateContent = context.assets.open("card_template.html").reader().use { it.readText() }
        assertTrue(cardTemplateContent.contains("scripts/session_storage_polyfill.js"), "card_template.html must include polyfill")

        // 2. Compose reviewer shell (stdHtml)
        val stdHtmlOutput = stdHtml(context)
        assertTrue(stdHtmlOutput.contains("scripts/session_storage_polyfill.js"), "stdHtml must include polyfill")
    }
}

