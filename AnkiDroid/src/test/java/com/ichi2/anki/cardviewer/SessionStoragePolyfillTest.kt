/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionStoragePolyfillTest : RobolectricTest() {
    @Test
    fun sessionStoragePolyfillAssetExistsAndIsNonEmpty() {
        val polyfillFile = File("src/main/assets/scripts/session_storage_polyfill.js")
        assertTrue(polyfillFile.exists(), "Polyfill script file must exist")
        assertThat("Polyfill script must not be empty", polyfillFile.length(), greaterThan(0L))
    }

    @Test
    fun cardTemplateIncludesSessionStoragePolyfillBeforeScriptPlaceholderAndCardJs() {
        val templateFile = File("src/main/assets/card_template.html")
        assertTrue(templateFile.exists(), "card_template.html must exist")

        val templateContent = templateFile.readText()
        assertThat(
            "card_template.html must include session_storage_polyfill.js",
            templateContent,
            containsString("scripts/session_storage_polyfill.js"),
        )

        val polyfillIndex = templateContent.indexOf("scripts/session_storage_polyfill.js")
        val scriptPlaceholderIndex = templateContent.indexOf("::script::")
        val cardJsIndex = templateContent.indexOf("scripts/card.js")

        assertThat("Polyfill script index must be valid", polyfillIndex, greaterThan(-1))
        assertThat(
            "::script:: placeholder index must be valid",
            scriptPlaceholderIndex,
            greaterThan(-1),
        )
        assertThat("card.js index must be valid", cardJsIndex, greaterThan(-1))

        assertTrue(
            polyfillIndex < scriptPlaceholderIndex,
            "session_storage_polyfill.js must be included before ::script:: placeholder",
        )
        assertTrue(
            polyfillIndex < cardJsIndex,
            "session_storage_polyfill.js must be included before card.js",
        )
    }

    @Test
    fun polyfillContainsAllRequiredStorageMethodsProxyHandlersAndSecurityGuards() {
        val polyfillContent = File("src/main/assets/scripts/session_storage_polyfill.js").readText()

        val requiredTokens =
            listOf(
                "__anki_ss_",
                "STORAGE_METHODS",
                "getItem",
                "setItem",
                "removeItem",
                "clear",
                "key",
                "length",
                "index >>> 0",
                "Proxy",
                "Object.defineProperty",
                "sessionStorage",
                "symbol",
            )

        for (token in requiredTokens) {
            assertThat(
                "Polyfill must contain token: $token",
                polyfillContent,
                containsString(token),
            )
        }
    }
}
