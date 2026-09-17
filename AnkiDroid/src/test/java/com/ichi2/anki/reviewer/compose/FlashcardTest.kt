/*
 * Copyright (c) 2026 Hirameki contributors
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

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.ArabicScriptFont
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class FlashcardTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** the card's webviews that are composed now */
    private val pages = mutableListOf<WebView>()

    private var answerShown by mutableStateOf(false)
    private var showing by mutableStateOf(true)
    private var paintKey by mutableLongStateOf(0L)
    private var toolbarHeight by mutableIntStateOf(0)
    private var replayFinished = 0
    private val longPresses = mutableListOf<Boolean>()

    @Test
    fun `a dead renderer is replaced by a fresh webview that shows the same card and side`() {
        showCard()
        answerShown = true
        val first = page()

        assertThat("returning false makes chromium kill the app", rendererGone(first, crashed = false), equalTo(true))

        val second = page()
        assertThat(second, not(sameInstance(first)))
        assertThat("the dead webview is destroyed", shadowOf(first).wasDestroyCalled(), equalTo(true))
        assertThat("the fresh webview loads the shell", shadowOf(second).lastLoadDataWithBaseURL.baseUrl, equalTo(BASE_URL))
        shellLoaded(second)
        assertThat(shadowOf(second).lastEvaluatedJavascript, containsString("_showAnswer(\"answer\""))
    }

    @Test
    fun `a renderer the system kills again is rebuilt again`() {
        showCard()
        rendererGone(page(), crashed = false)
        rendererGone(page(), crashed = false)

        composeTestRule.runOnIdle { assertThat(pages.size, equalTo(1)) }
    }

    @Test
    fun `a show that crashes the fresh renderer too gets no webview until the page shows something else`() {
        showCard()
        rendererGone(page(), crashed = true)
        val rebuilt = page()
        rendererGone(rebuilt, crashed = true)

        composeTestRule.runOnIdle {
            assertThat("rebuilding would crash every renderer again", pages, empty())
            assertThat(shadowOf(rebuilt).wasDestroyCalled(), equalTo(true))
        }

        answerShown = true
        assertThat(shadowOf(page()).lastLoadDataWithBaseURL.baseUrl, equalTo(BASE_URL))
    }

    @Test
    fun `a show drawn after a renderer crash is rebuilt, not left blank, when a later crash comes`() =
        withPaintReports { webViewCompat ->
            showCard()
            rendererGone(page(), crashed = true)
            val rebuilt = page()
            shellLoaded(rebuilt)
            drawn(rebuilt, webViewCompat)

            rendererGone(rebuilt, crashed = true)

            composeTestRule.runOnIdle { assertThat("a show that drew is no crash loop", pages.size, equalTo(1)) }
        }

    @Test
    fun `a question left blank by a crash loop gets a fresh webview when it is shown again`() {
        showCard()
        rendererGone(page(), crashed = true)
        rendererGone(page(), crashed = true)
        answerShown = true
        page()

        answerShown = false

        val question = page()
        shellLoaded(question)
        assertThat(shadowOf(question).lastEvaluatedJavascript, containsString("_showQuestion(\"question\""))
    }

    @Test
    fun `a card face left blank by a crash loop stays blank while turned away and gets a fresh webview once turned back`() {
        showCard()
        rendererGone(page(), crashed = true)
        rendererGone(page(), crashed = true)

        showing = false
        composeTestRule.runOnIdle { assertThat("rebuilt while hidden, it would crash the face on show", pages, empty()) }

        showing = true
        assertThat(shadowOf(page()).lastLoadDataWithBaseURL.baseUrl, equalTo(BASE_URL))
    }

    @Test
    fun `a card left blank by a crash loop gets a fresh webview when the same card is shown again`() {
        showCard()
        rendererGone(page(), crashed = true)
        rendererGone(page(), crashed = true)

        // the same html under a new paint key: the card came back, after an undo or a relearning step
        paintKey = 1L

        assertThat(shadowOf(page()).lastLoadDataWithBaseURL.baseUrl, equalTo(BASE_URL))
    }

    @Test
    fun `turning the arabic script font off restyles the loaded page without reloading it`() {
        showCard()
        val page = page()
        val shell = shadowOf(page).lastLoadDataWithBaseURL
        assertThat(shell.data, containsString(ArabicScriptFont.CARD_FONT_FACE))
        assertThat(shell.data, containsString("font-family: \"Roboto\", \"${ArabicScriptFont.CARD_FONT_FAMILY}\", sans-serif;"))
        shellLoaded(page)

        composeTestRule.runOnIdle { Prefs.putBoolean(R.string.arabic_script_font_key, false) }

        composeTestRule.runOnIdle {
            assertThat("no reload", shadowOf(page).lastLoadDataWithBaseURL, sameInstance(shell))
            val update = shadowOf(page).lastEvaluatedJavascript
            // the style block travels as a json string, so its quotes arrive escaped
            assertThat(update, containsString("font-family: \\\"Roboto\\\", sans-serif;"))
            assertThat(update, not(containsString(ArabicScriptFont.CARD_FONT_FAMILY)))
        }
    }

    // the listener and the message are mocks; no real webview is asked for the web message features
    @SuppressLint("RequiresFeature")
    @Test
    fun `a card script posting something that is not a show report cannot crash the app`() =
        withPaintReports { webViewCompat ->
            showCard()
            val page = page()
            shellLoaded(page)
            val listeners = argumentCaptor<WebViewCompat.WebMessageListener>()
            webViewCompat.verify({ WebViewCompat.addWebMessageListener(eq(page), any(), any(), listeners.capture()) }, atLeastOnce())

            // any script on the card can reach every bridge, and postMessage takes an arraybuffer as readily
            // as a string; read as a string it throws out of a webview callback, which kills the app
            assertDoesNotThrow {
                composeTestRule.runOnIdle {
                    listeners.allValues.forEach {
                        it.onPostMessage(page, WebMessageCompat(byteArrayOf(1)), BASE_URL.toUri(), true, mock())
                    }
                }
            }
        }

    // the listener and the messages are mocks; no real webview is asked for the web message features
    @SuppressLint("RequiresFeature")
    @Test
    fun `what the page says of a long press reaches the app, from the page itself only`() =
        withPaintReports { webViewCompat ->
            showCard()
            val page = page()
            val listener = argumentCaptor<WebViewCompat.WebMessageListener>()
            webViewCompat.verify { WebViewCompat.addWebMessageListener(eq(page), eq(LONG_PRESS_BRIDGE), any(), listener.capture()) }

            composeTestRule.runOnIdle {
                listener.firstValue.onPostMessage(page, WebMessageCompat(LONG_PRESS_SELECTED), BASE_URL.toUri(), true, mock())
                listener.firstValue.onPostMessage(page, WebMessageCompat("none"), BASE_URL.toUri(), true, mock())
                // a card's own iframe can share the page's origin, and its long presses are its own
                listener.firstValue.onPostMessage(page, WebMessageCompat(LONG_PRESS_SELECTED), BASE_URL.toUri(), false, mock())
            }

            assertThat(longPresses, equalTo(listOf(true, false)))
        }

    @Test
    fun `a card page that replaces the shell has the shell loaded again`() {
        showCard()
        val page = page()
        val shell = shadowOf(page).lastLoadDataWithBaseURL
        shellLoaded(page)
        // the card's own html navigated the page: a second finish, for a document that is not the shell
        shellLoaded(page)
        pageReportsShell(page, isShell = false)

        composeTestRule.runOnIdle {
            val reloaded = shadowOf(page).lastLoadDataWithBaseURL
            assertThat("without this the face stays blank for the rest of the session", reloaded, not(sameInstance(shell)))
            assertThat(reloaded.baseUrl, equalTo(BASE_URL))
        }
    }

    @Test
    fun `an in-page anchor leaves the loaded shell alone`() {
        showCard()
        val page = page()
        val shell = shadowOf(page).lastLoadDataWithBaseURL
        shellLoaded(page)
        shellLoaded(page)
        pageReportsShell(page, isShell = true)

        composeTestRule.runOnIdle {
            assertThat("the card is still in the page it was swapped into", shadowOf(page).lastLoadDataWithBaseURL, sameInstance(shell))
        }
    }

    @Test
    fun `a card that replaces the page every time stops being reloaded, and the next card gets the page back`() {
        showCard()
        val page = page()
        shellLoaded(page)
        repeat(MAX_SHELL_RELOADS) {
            // the card navigates away again, and the recovery load that follows finishes
            shellLoaded(page)
            pageReportsShell(page, isShell = false)
            shellLoaded(page)
        }
        val lastLoad = composeTestRule.runOnIdle { shadowOf(page).lastLoadDataWithBaseURL }

        shellLoaded(page)
        pageReportsShell(page, isShell = false)

        composeTestRule.runOnIdle {
            assertThat(
                "reloading a page the card replaces on every show only spins the renderer",
                shadowOf(page).lastLoadDataWithBaseURL,
                sameInstance(lastLoad),
            )
        }

        // the same html under a new paint key: another card, which must not inherit the dead page
        paintKey = 1L

        composeTestRule.runOnIdle {
            assertThat("the next card loads the page again", shadowOf(page).lastLoadDataWithBaseURL, not(sameInstance(lastLoad)))
        }
    }

    @Test
    fun `a card face turned away pauses the media on its own page`() {
        showCard()
        val page = page()
        shellLoaded(page)

        composeTestRule.runOnIdle {
            assertThat("nothing is paused while it shows", shadowOf(page).lastEvaluatedJavascript, not(containsString("m.pause()")))
        }

        showing = false

        // without this the face turned away played its audio under the face turned to
        composeTestRule.runOnIdle { assertThat(shadowOf(page).lastEvaluatedJavascript, containsString("m.pause()")) }
    }

    @Test
    fun `a show fits the card's text once it is swapped in, before its paint is reported`() {
        showCard()
        val page = page()
        shellLoaded(page)

        composeTestRule.runOnIdle {
            val show = shadowOf(page).lastEvaluatedJavascript
            val swap = show.indexOf("_showQuestion(")
            val fit = show.indexOf("hiramekiFitText()")
            val report = show.indexOf("postMessage(")
            assertThat("the show swaps the card in", swap, greaterThanOrEqualTo(0))
            // queued behind the show, it measures the typeset card in the task that makes it visible; the paint
            // report after it then covers the fitted card
            assertThat("the fit is queued after the swap", fit, greaterThan(swap))
            assertThat("and before the paint report", report, greaterThan(fit))
            assertThat("queued, not run as the show is sent", show.substring(swap, fit), containsString("_queueAction("))
        }
    }

    @Test
    fun `a new answer bar height fits the card's text to its new room`() {
        showCard()
        val page = page()
        shellLoaded(page)

        toolbarHeight = 120

        composeTestRule.runOnIdle {
            val update = shadowOf(page).lastEvaluatedJavascript
            assertThat("the style block with the new room", update, containsString("padding-bottom: 120px;"))
            assertThat("fitted again after it", update.indexOf("hiramekiFitText()"), greaterThan(update.indexOf("padding-bottom: 120px;")))
        }
    }

    @Test
    fun `the page script can fit a card's text`() {
        showCard()
        val shell = shadowOf(page()).lastLoadDataWithBaseURL.data
        // the fit itself is plain page javascript; its rules are exercised against a layout model outside robolectric,
        // which has no layout engine (tools/asset-tests). here: the shell carries it, under the name the shows call,
        // and the style block allows it
        assertThat(shell, containsString("window.hiramekiFitText = fitText;"))
        assertThat(shell, containsString(FIT_ALLOWED))
    }

    @Test
    fun `with font size changes off the page does not fit a card's text`() {
        Prefs.putString(R.string.apply_hirameki_css_preference, Prefs.HIRAMEKI_CSS_NO_FONT_SIZE)
        showCard()
        val shell = shadowOf(page()).lastLoadDataWithBaseURL.data

        assertThat("the rest of hirameki css still applies", shell, containsString("--hirameki-primary:"))
        // a user who chose to keep a deck's own font sizes had them changed on every card taller than its page
        assertThat(shell, not(containsString(FIT_ALLOWED)))
    }

    @Test
    fun `a page made for a face turned away, after replays have finished, sends that page nothing before it loads`() {
        showing = false
        replayFinished = 3
        showCard()

        composeTestRule.runOnIdle {
            // unseeded, a new page read a replay restore and a media pause from its defaults and sent both at once
            assertThat(shadowOf(page()).lastEvaluatedJavascript, nullValue())
        }
    }

    private fun showCard() {
        composeTestRule.setContent {
            Flashcard(
                baseUrl = BASE_URL,
                questionHtml = "question",
                answerHtml = "answer",
                bodyClass = "card",
                isMediaAutoplayEnabled = false,
                javascriptCommand = null,
                onJavascriptCommandConsumed = {},
                onTap = {},
                onLinkClick = {},
                isAnswerShown = answerShown,
                toolbarHeight = toolbarHeight,
                replayFinished = replayFinished,
                isShowing = showing,
                onWebView = { page, alive -> if (alive) pages += page else pages -= page },
                paintKey = paintKey,
                onLongPress = { longPresses += it },
            )
        }
    }

    private fun page(): WebView = composeTestRule.runOnIdle { pages.single() }

    /** what webview does once the shell document has loaded: the page then gets its show */
    private fun shellLoaded(page: WebView) = composeTestRule.runOnIdle { shadowOf(page).webViewClient.onPageFinished(page, BASE_URL) }

    /** what the page answers when the app asks whether the document that just finished is still the shell */
    private fun pageReportsShell(
        page: WebView,
        isShell: Boolean,
    ) = composeTestRule.runOnIdle {
        val probe = requireNotNull(shadowOf(page).lastEvaluatedJavascriptCallback) { "the page was never asked" }
        probe.onReceiveValue(if (isShell) "true" else "false")
    }

    private fun rendererGone(
        page: WebView,
        crashed: Boolean,
    ): Boolean {
        val detail = mock<RenderProcessGoneDetail> { on { didCrash() } doReturn crashed }
        return composeTestRule.runOnIdle { shadowOf(page).webViewClient.onRenderProcessGone(page, detail) }
    }

    /** runs [block] with a webview that can message the app, so a page can report what it drew and its long presses */
    private fun withPaintReports(block: (MockedStatic<WebViewCompat>) -> Unit) {
        mockStatic(WebViewFeature::class.java).use { feature ->
            feature.`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }.thenReturn(true)
            mockStatic(WebViewCompat::class.java).use(block)
        }
    }

    /**
     * what the page and webview do once [page] has drawn its latest show: the page reports the show, then the frame
     * holding it is committed. robolectric's webview has no visual-state callbacks, so a stand-in view delivers it
     */
    // verifies the mocked static call; no real webview is ever asked for the web message listener feature
    @SuppressLint("RequiresFeature")
    private fun drawn(
        page: WebView,
        webViewCompat: MockedStatic<WebViewCompat>,
    ) {
        val listener = argumentCaptor<WebViewCompat.WebMessageListener>()
        webViewCompat.verify { WebViewCompat.addWebMessageListener(eq(page), eq(PAINT_BRIDGE), any(), listener.capture()) }
        val report = requireNotNull(PAINT_REPORT.find(shadowOf(page).lastEvaluatedJavascript)) { "the page was sent no show" }
        val view = mock<WebView> { on { tag } doReturn page.tag }
        doAnswer { it.getArgument<WebView.VisualStateCallback>(1).onComplete(it.getArgument(0)) }
            .whenever(view)
            .postVisualStateCallback(any(), any())
        composeTestRule.runOnIdle {
            listener.firstValue.onPostMessage(view, WebMessageCompat(report.groupValues[1]), BASE_URL.toUri(), true, mock())
        }
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"

        /** the style block's word to the page script that it may fit a card's text */
        const val FIT_ALLOWED = "--hirameki-fit: 1;"

        /** the paint report a show asks the page to send, carrying the show's sequence number */
        val PAINT_REPORT = Regex("""postMessage\('(\d+)'\)""")
    }
}
