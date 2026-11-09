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
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.card.CardDisplay
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
import org.robolectric.Shadows.shadowOf

/** #143: the reviewer's card survives losing its WebView renderer process. */
@RunWith(AndroidJUnit4::class)
class FlashcardRendererLossTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var questionHtml by mutableStateOf("<p>Question 1</p>")
    private var cardDisplayIndex by mutableLongStateOf(1L)

    private val rendererFailedText: String
        get() = composeTestRule.activity.getString(R.string.webview_crash_loop_dialog_title)

    @Test
    fun `a reclaimed renderer is handled and its WebView replaced`() {
        showFlashcard()
        val original = singleWebView()

        assertThat("handled: false kills the app", loseRenderer(original, crashed = false), equalTo(true))

        val replacement = singleWebView()
        assertThat("a new WebView replaces the dead one", replacement, not(sameInstance(original)))
        assertThat("it loads the card page", shadowOf(replacement).lastLoadDataWithBaseURL?.baseUrl, equalTo(BASE_URL))
    }

    @Test
    fun `a reclaimed renderer never counts as a crash loop`() {
        showFlashcard()

        repeat(3) { loseRenderer(singleWebView(), crashed = false) }

        singleWebView()
        assertThat(isRendererFailureShown(), equalTo(false))
    }

    @Test
    fun `a renderer crashing twice in a row on one card shows an error instead of rebuilding`() {
        showFlashcard()

        loseRenderer(singleWebView(), crashed = true)
        val rebuilt = singleWebView()
        loseRenderer(rebuilt, crashed = true)

        assertThat("no third WebView is built for the card", webViews(), empty())
        assertThat(isRendererFailureShown(), equalTo(true))
    }

    @Test
    fun `the same HTML shown again gets a new WebView after a crash loop`() {
        showFlashcard()
        loseRenderer(singleWebView(), crashed = true)
        loseRenderer(singleWebView(), crashed = true)

        // another card with identical HTML, or this card again after a rating
        cardDisplayIndex++

        singleWebView()
        assertThat(isRendererFailureShown(), equalTo(false))
    }

    @Test
    fun `crashes on different cards are not a crash loop`() {
        showFlashcard()
        loseRenderer(singleWebView(), crashed = true)

        questionHtml = "<p>Question 2</p>"
        composeTestRule.waitForIdle()
        loseRenderer(singleWebView(), crashed = true)

        singleWebView()
        assertThat(isRendererFailureShown(), equalTo(false))
    }

    private fun showFlashcard() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Flashcard(
                    display =
                        CardDisplay(
                            cardDisplayIndex = cardDisplayIndex,
                            isAnswerShown = false,
                            questionHtml = questionHtml,
                            answerHtml = "<p>Answer</p>",
                            bodyClass = "card",
                            baseUrl = BASE_URL,
                        ),
                    isMediaAutoplayEnabled = false,
                    javascriptCommand = null,
                    onJavascriptCommandConsumed = {},
                    onGesture = {},
                    onLinkClick = {},
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

    private fun webViews(): List<WebView> = viewsOfType()

    private fun isRendererFailureShown(): Boolean = viewsOfType<TextView>().any { it.text == rendererFailedText }

    private inline fun <reified T : View> viewsOfType(): List<T> {
        composeTestRule.waitForIdle()
        val found = mutableListOf<T>()
        composeTestRule.runOnUiThread { collectViews(composeTestRule.activity.window.decorView) { (it as? T)?.let(found::add) } }
        return found
    }

    private fun collectViews(
        view: View,
        visit: (View) -> Unit,
    ) {
        visit(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectViews(view.getChildAt(i), visit)
        }
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"
    }
}
