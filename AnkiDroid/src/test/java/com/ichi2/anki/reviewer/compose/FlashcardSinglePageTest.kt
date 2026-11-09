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

package com.ichi2.anki.reviewer.compose

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.card.CardDisplay
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class FlashcardSinglePageTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var display by mutableStateOf(QUESTION)
    private var command by mutableStateOf<ReviewerJavascriptCommand?>(null)
    private val consumed = mutableListOf<Int>()

    @Test
    fun `the first card is shown once the page has loaded`() {
        showFlashcard()
        val webView = singleWebView()
        assertThat("not before the page has loaded", lastScript(webView), nullValue())

        loadPage(webView)

        assertThat(lastScript(webView), containsString("_showQuestion("))
    }

    @Test
    fun `nothing loads until the server's address is known`() {
        display = QUESTION.copy(baseUrl = "")
        showFlashcard()

        assertThat(shadowOf(singleWebView()).lastLoadDataWithBaseURL, nullValue())
    }

    @Test
    fun `sides and cards are swapped into the same WebView and page`() {
        showFlashcard()
        val webView = singleWebView()
        val pageLoad = shadowOf(webView).lastLoadDataWithBaseURL
        loadPage(webView)

        display = QUESTION.copy(isAnswerShown = true)
        assertThat(lastScript(webView), containsString("_showAnswer("))
        display = QUESTION.copy(cardDisplayIndex = 2, questionHtml = "<p>Next</p>")
        assertThat(lastScript(webView), containsString("Next"))

        assertThat(singleWebView(), sameInstance(webView))
        assertThat("no new page load", shadowOf(webView).lastLoadDataWithBaseURL, sameInstance(pageLoad))
    }

    @Test
    fun `a page that replaces the card stays open until the next card restores the card page`() {
        showFlashcard()
        val webView = singleWebView()
        val pageLoad = shadowOf(webView).lastLoadDataWithBaseURL
        loadPage(webView)

        repeat(2) { navigate(webView) }
        assertThat("the new page is left open", shadowOf(webView).lastLoadDataWithBaseURL, sameInstance(pageLoad))

        display = QUESTION.copy(cardDisplayIndex = 2, questionHtml = "<p>Next</p>")
        composeTestRule.waitForIdle()
        assertThat("the card page is loaded again", shadowOf(webView).lastLoadDataWithBaseURL, not(sameInstance(pageLoad)))
        navigate(webView)
        assertThat(lastScript(webView), containsString("Next"))
    }

    @Test
    fun `a link to an anchor reporting the page as finished does not show the card again`() {
        showFlashcard()
        val webView = singleWebView()
        loadPage(webView)
        command = USER_ACTION
        composeTestRule.waitForIdle()

        loadPage(webView)

        assertThat("the command is still the last script run", lastScript(webView), containsString("userJs1"))
        assertThat("and it ran once", consumed, contains(USER_ACTION.id))
    }

    @Test
    fun `a command waits for the page to load`() {
        command = USER_ACTION
        showFlashcard()
        val webView = singleWebView()
        assertThat(consumed, empty())

        loadPage(webView)

        assertThat(lastScript(webView), containsString("userJs1"))
        assertThat(consumed, contains(USER_ACTION.id))
    }

    private fun showFlashcard() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Flashcard(
                    display = display,
                    isMediaAutoplayEnabled = false,
                    javascriptCommand = command,
                    onJavascriptCommandConsumed = { id ->
                        consumed += id
                        if (command?.id == id) command = null
                    },
                    onGesture = {},
                    onLinkClick = {},
                )
            }
        }
    }

    private fun navigate(webView: WebView) {
        composeTestRule.runOnUiThread {
            webView.webViewClient.onPageStarted(webView, BASE_URL, null)
            webView.webViewClient.onPageFinished(webView, BASE_URL)
        }
        composeTestRule.waitForIdle()
    }

    private fun loadPage(webView: WebView) {
        composeTestRule.runOnUiThread { webView.webViewClient.onPageFinished(webView, BASE_URL) }
        composeTestRule.waitForIdle()
    }

    private fun lastScript(webView: WebView): String? {
        composeTestRule.waitForIdle()
        return shadowOf(webView).lastEvaluatedJavascript
    }

    private fun singleWebView(): WebView {
        composeTestRule.waitForIdle()
        val found = mutableListOf<WebView>()
        composeTestRule.runOnUiThread { collectWebViews(composeTestRule.activity.window.decorView, found) }
        assertThat("exactly one card WebView", found, hasSize(1))
        return found.single()
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

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"
        val QUESTION =
            CardDisplay(
                cardDisplayIndex = 1,
                isAnswerShown = false,
                questionHtml = "<p>Question</p>",
                answerHtml = "<p>Answer</p>",
                bodyClass = "card",
                baseUrl = BASE_URL,
            )
        val USER_ACTION = ReviewerJavascriptCommand(id = 1, script = "userJs1()")
    }
}
