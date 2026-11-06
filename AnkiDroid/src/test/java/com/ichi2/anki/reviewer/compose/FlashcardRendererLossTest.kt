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

package com.ichi2.anki.reviewer.compose

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #143: the reviewer's card survives losing its WebView renderer process. */
@RunWith(AndroidJUnit4::class)
class FlashcardRendererLossTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var questionHtml by mutableStateOf("<p>Question 1</p>")

    private val rendererFailedText: String
        get() = composeTestRule.activity.getString(R.string.webview_crash_loop_dialog_title)

    @Test
    fun `a reclaimed renderer is handled and its WebView replaced`() {
        showFlashcard()
        val original = singleWebView()

        assertThat("handled: false kills the app", loseRenderer(original, crashed = false), equalTo(true))

        assertThat("a new WebView replaces the dead one", singleWebView(), not(sameInstance(original)))
    }

    @Test
    fun `a reclaimed renderer never counts as a crash loop`() {
        showFlashcard()

        repeat(3) { loseRenderer(singleWebView(), crashed = false) }

        singleWebView()
        composeTestRule.onNodeWithText(rendererFailedText).assertDoesNotExist()
    }

    @Test
    fun `one crash delivered to several WebViews counts once`() {
        showFlashcard()
        val webView = singleWebView()
        val crash = detail(crashed = true)

        // every WebView on the renderer gets the callback for the same loss before the next frame,
        // two during the question/answer crossfade
        composeTestRule.runOnUiThread {
            repeat(2) { webView.webViewClient.onRenderProcessGone(webView, crash) }
        }
        composeTestRule.waitForIdle()

        singleWebView()
        composeTestRule.onNodeWithText(rendererFailedText).assertDoesNotExist()
        // a second crash really is the second one
        loseRenderer(singleWebView(), crashed = true)
        composeTestRule.onNodeWithText(rendererFailedText).assertIsDisplayed()
    }

    @Test
    fun `a renderer crashing twice in a row on one card shows an error instead of rebuilding`() {
        showFlashcard()

        loseRenderer(singleWebView(), crashed = true)
        val rebuilt = singleWebView()
        loseRenderer(rebuilt, crashed = true)

        assertThat("no third WebView is built for the card", webViews(), empty())
        composeTestRule.onNodeWithText(rendererFailedText).assertIsDisplayed()
    }

    @Test
    fun `the next card gets a new WebView after a crash loop`() {
        showFlashcard()
        loseRenderer(singleWebView(), crashed = true)
        loseRenderer(singleWebView(), crashed = true)

        questionHtml = "<p>Question 2</p>"

        singleWebView()
        composeTestRule.onNodeWithText(rendererFailedText).assertDoesNotExist()
    }

    @Test
    fun `crashes on different cards are not a crash loop`() {
        showFlashcard()
        loseRenderer(singleWebView(), crashed = true)

        questionHtml = "<p>Question 2</p>"
        composeTestRule.waitForIdle()
        loseRenderer(singleWebView(), crashed = true)

        singleWebView()
        composeTestRule.onNodeWithText(rendererFailedText).assertDoesNotExist()
    }

    private fun showFlashcard() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Flashcard(
                    baseUrl = "http://127.0.0.1:1/",
                    questionHtml = questionHtml,
                    answerHtml = "<p>Answer</p>",
                    bodyClass = "card",
                    isMediaAutoplayEnabled = false,
                    javascriptCommand = null,
                    onJavascriptCommandConsumed = {},
                    onTap = {},
                    onLinkClick = {},
                    isAnswerShown = false,
                )
            }
        }
    }

    private fun loseRenderer(
        webView: WebView,
        crashed: Boolean,
    ): Boolean {
        val detail = detail(crashed)
        var handled = false
        composeTestRule.runOnUiThread {
            handled = webView.webViewClient.onRenderProcessGone(webView, detail)
        }
        composeTestRule.waitForIdle()
        return handled
    }

    private fun detail(crashed: Boolean) = mockk<RenderProcessGoneDetail> { every { didCrash() } returns crashed }

    private fun singleWebView(): WebView {
        val webViews = webViews()
        assertThat("exactly one card WebView", webViews, hasSize(1))
        return webViews.single()
    }

    private fun webViews(): List<WebView> {
        composeTestRule.waitForIdle()
        val found = mutableListOf<WebView>()
        composeTestRule.runOnUiThread { collectWebViews(composeTestRule.activity.window.decorView, found) }
        return found
    }

    private fun collectWebViews(
        view: View,
        into: MutableList<WebView>,
    ) {
        if (view is WebView) into += view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectWebViews(view.getChildAt(i), into)
        }
    }
}
