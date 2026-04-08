/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.pages

import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.Repeat
import com.ichi2.anki.testutil.RepeatRule
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.utils.toRGBHex
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeckOptionsCssTest : InstrumentedTest() {

    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    @get:Rule
    val repeatRule = RepeatRule()

    @Before
    fun before() {
        disableIntroductionSlide()
    }

    @Test
    fun testCssInjection() {
        // Use a random deck if possible, or just the first one
        val deckId = col.decks.allNamesAndIds().random().id
        val intent = DeckOptions.getIntent(testContext, deckId)

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var cssContent: String? = null

            scenario.onActivity { activity ->
                val fragment = activity.requireDeckOptionsFragment()
                val webView = fragment.webView
                
                // The PageWebViewClient manages the injection logic.
                val webViewClient = webView.webViewClient as PageWebViewClient
                
                // WAIT for the WebView to become visible "naturally" 
                // (i.e. via the deckOptionsReady call from the JS side)
                // In the test, we'll poll for fragment.webView.isVisible
                // This replaces the "cheat" of manually calling activity.deckOptionsReady(byteArrayOf())
                
                // Since onActivity runs on the UI thread, we can't poll here easily.
                // We'll move the visibility check into a polling loop outside.

                webViewClient.runWhenPageStyled(webView) {
                    webView.evaluateJavascript(
                        "(function() { " +
                        "  var style = document.getElementById('material3-theme'); " +
                        "  return style ? style.textContent : null; " +
                        "})();"
                    ) { result ->
                        // result will be like "\"some-css-content\"" or "null"
                        if (result != "null") {
                            cssContent = result
                            latch.countDown()
                        }
                    }
                }
            }

            // Polling loop for visibility to verify "real" readiness
            var isVisible = false
            for (i in 0 until 40) { // 20 seconds total (0.5s intervals)
                scenario.onActivity { activity ->
                    val fragment = activity.requireDeckOptionsFragment()
                    isVisible = fragment.webView.isVisible
                }
                if (isVisible) break
                Thread.sleep(500)
            }

            assertThat("WebView should become visible naturally (deckOptionsReady)", isVisible, `is`(true))

            // Wait for the style to be injected and verified
            val success = latch.await(10, TimeUnit.SECONDS)
            assertThat("Timed out waiting for CSS injection", success, `is`(true))
            assertThat("CSS content should not be null", cssContent, notNullValue())
            
            // evaluateJavascript returns "null" as a string if the JS returned null
            assertThat("CSS content should not be \"null\"", cssContent != "null", `is`(true))
            
            // Check for a few expected CSS variables from anki_material3_theme.css
            assertThat("CSS content should contain --canvas variable", cssContent!!.contains("--canvas"), `is`(true))
            assertThat("CSS content should contain --fg variable", cssContent.contains("--fg"), `is`(true))
            assertThat("CSS content should contain .deck-options-page selector", cssContent.contains(".deck-options-page"), `is`(true))
        }
    }

    @Test
    @Repeat(100)
    fun testCssApplication() {
        val deckId = col.decks.allNamesAndIds().random().id
        val intent = DeckOptions.getIntent(testContext, deckId)

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            var expectedBgColor: String? = null
            var actualBgColor: String? = null
            val latch = CountDownLatch(1)

            scenario.onActivity { activity ->
                val fragment = activity.requireDeckOptionsFragment()
                val webView = fragment.webView
                val colorInt = MaterialColors.getColor(webView, android.R.attr.colorBackground)
                expectedBgColor = colorInt.toRGBHex().lowercase()
                val webViewClient = webView.webViewClient as PageWebViewClient

                webViewClient.runWhenPageStyled(webView) {
                    // Check computed style of the body
                    webView.evaluateJavascript(
                        "(function() { " +
                        "  var style = getComputedStyle(document.documentElement); " +
                        "  return style.getPropertyValue('--canvas').trim(); " +
                        "})();"
                    ) { result ->
                        // result is like "\"#ffffff\"" or null
                        try {
                            if (result != null) {
                                actualBgColor = result.replace("\"", "").lowercase()
                            }
                        } finally {
                            latch.countDown()
                        }
                    }
                }
            }

            // Polling loop for visibility to verify "real" readiness
            var isVisible = false
            for (i in 0 until 40) {
                scenario.onActivity { activity -> isVisible = activity.requireDeckOptionsFragment().webView.isVisible }
                if (isVisible) break
                Thread.sleep(500)
            }

            assertThat("WebView should become visible naturally", isVisible, `is`(true))

            val success = latch.await(10, TimeUnit.SECONDS)
            assertThat("Timed out waiting for computed style check", success, `is`(true))
            
            // NOTE: This is where we expect failure in those 10/100 cases
            // We will now poll if it failed, to see if it fixes itself or what the value actually was!
            if (actualBgColor != expectedBgColor) {
                var latestColor: String? = actualBgColor
                println("DeckOptionsCssTest: initial mismatch! expected=$expectedBgColor, actual=$actualBgColor. Polling for a fix...")
                var fixed = false
                for (i in 0 until 20) {
                    Thread.sleep(200)
                    scenario.onActivity { activity ->
                        activity.requireDeckOptionsFragment().webView.evaluateJavascript(
                            "(function() { " +
                            "  var style = getComputedStyle(document.documentElement); " +
                            "  return style.getPropertyValue('--canvas').trim(); " +
                            "})();"
                        ) { result ->
                            latestColor = result.replace("\"", "").lowercase()
                        }
                    }
                    if (latestColor == expectedBgColor) {
                        fixed = true
                        println("DeckOptionsCssTest: matched after ${i * 200}ms!")
                        break
                    }
                }
                println("DeckOptionsCssTest: final latestColor=$latestColor, expected=$expectedBgColor")
                assertThat("Applied CSS variable --canvas should match theme background", latestColor, `is`(expectedBgColor))
            } else {
                assertThat("Applied CSS variable --canvas should match theme background", actualBgColor, `is`(expectedBgColor))
            }
        }
    }
}
