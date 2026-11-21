// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.compose

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.net.toUri
import androidx.core.view.allViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.card.CardDisplay
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
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
    private var isWebViewFocusableInTouchMode by mutableStateOf(false)
    private val consumed = mutableListOf<Int>()
    private val clickedLinks = mutableListOf<String>()

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

    @Test
    fun `a command sent while a link has replaced the card is dropped`() {
        showFlashcard()
        val webView = singleWebView()
        loadPage(webView)
        navigate(webView)

        command = USER_ACTION
        composeTestRule.waitForIdle()
        assertThat(consumed, contains(USER_ACTION.id))
        assertThat(lastScript(webView), not(containsString("userJs1")))
    }

    @Test
    fun `missing user action signal is forwarded to onLinkClick without navigating away`() {
        showFlashcard()
        val webView = singleWebView()
        loadPage(webView)

        val request = mockk<WebResourceRequest> { every { url } returns "missing-user-action:4".toUri() }
        var handled = false
        composeTestRule.runOnUiThread {
            handled = webView.webViewClient.shouldOverrideUrlLoading(webView, request)
        }

        assertThat("custom scheme navigation is intercepted", handled, equalTo(true))
        assertThat(clickedLinks, contains("missing-user-action:4"))
    }

    @Test
    fun `card WebView is not focusable in touch mode by default and follows the parameter`() {
        showFlashcard()
        val webView = singleWebView()
        assertThat("touch focus disabled by default", webView.isFocusableInTouchMode, equalTo(false))

        isWebViewFocusableInTouchMode = true
        composeTestRule.waitForIdle()
        assertThat("touch focus enabled when requested", webView.isFocusableInTouchMode, equalTo(true))
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
                    onLinkClick = { clickedLinks += it },
                    isWebViewFocusableInTouchMode = isWebViewFocusableInTouchMode,
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
        var found = emptyList<WebView>()
        composeTestRule.runOnUiThread {
            found =
                composeTestRule.activity.window.decorView.allViews
                    .filterIsInstance<WebView>()
                    .toList()
        }
        assertThat("exactly one card WebView", found, hasSize(1))
        return found.single()
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
