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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionStorageIntegrationTest : RobolectricTest() {

    @Test
    fun productionShellTemplatesIncludePolyfill() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cardTemplateContent =
            context.assets.open("card_template.html").reader().use { it.readText() }
        assertTrue(
            cardTemplateContent.contains("scripts/session_storage_polyfill.js"),
            "card_template.html must include polyfill"
        )

        val stdHtmlOutput = stdHtml(context)
        assertTrue(
            stdHtmlOutput.contains("scripts/session_storage_polyfill.js"),
            "stdHtml must include polyfill"
        )
    }

    @Test
    fun polyfillScriptAssetIsPresentAndValid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scriptContent = context.assets.open("scripts/session_storage_polyfill.js").reader()
            .use { it.readText() }

        assertTrue(
            scriptContent.contains("__anki_ss_"),
            "Polyfill must define namespace prefix __anki_ss_"
        )
        assertTrue(scriptContent.contains("Proxy"), "Polyfill must use JS Proxy object")
        assertTrue(
            scriptContent.contains("localStorage"),
            "Polyfill must use localStorage storage backend"
        )
        assertTrue(
            scriptContent.contains("window.sessionStorage"),
            "Polyfill must define window.sessionStorage"
        )
        assertTrue(scriptContent.contains("getItem"), "Polyfill must implement getItem")
        assertTrue(scriptContent.contains("setItem"), "Polyfill must implement setItem")
        assertTrue(scriptContent.contains("removeItem"), "Polyfill must implement removeItem")
        assertTrue(scriptContent.contains("clear"), "Polyfill must implement clear")
    }

    @Test
    fun simulateSessionStoragePersistenceLogicAcrossDocumentReload() {
        // Simulated localStorage backing store per origin
        val originLocalStorage = mutableMapOf<String, String>()
        val prefix = "__anki_ss_"

        // Front Card: script execution sets item
        fun setItemOnFront(key: String, value: String) {
            originLocalStorage[prefix + key] = value
        }

        // Back Card: document reloads (loadDataWithBaseURL), JS scope re-initializes,
        // polyfill queries same originLocalStorage
        fun getItemOnBack(key: String): String? {
            return originLocalStorage[prefix + key]
        }

        setItemOnFront("cardState", "flipped")
        assertEquals("flipped", getItemOnBack("cardState"))

        // Clear only session storage keys
        originLocalStorage["unrelatedKey"] = "keepMe"
        val keysToRemove = originLocalStorage.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { originLocalStorage.remove(it) }

        assertNull(getItemOnBack("cardState"))
        assertEquals("keepMe", originLocalStorage["unrelatedKey"])
    }
}
