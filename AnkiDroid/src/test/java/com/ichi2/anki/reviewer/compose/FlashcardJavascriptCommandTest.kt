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
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** #205: a card script runs once, in the page that stays, even during the question/answer crossfade. */
@RunWith(AndroidJUnit4::class)
class FlashcardJavascriptCommandTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var isAnswerShown by mutableStateOf(false)
    private var command by mutableStateOf<ReviewerJavascriptCommand?>(null)
    private val consumed = mutableListOf<Int>()

    @Test
    fun `a command runs once`() {
        showFlashcard()
        val webView = singleWebView()
        simulatePageLoaded(webView)

        command = USER_ACTION
        composeTestRule.waitForIdle()

        assertThat(shadowOf(webView).lastEvaluatedJavascript, equalTo(USER_ACTION.script))
        assertThat(consumed, contains(USER_ACTION.id))
    }

    @Test
    fun `a command sent during the crossfade runs only in the page fading in`() {
        showFlashcard()
        simulatePageLoaded(singleWebView())
        val (question, answer) = showAnswerAndPauseMidCrossfade()
        simulatePageLoaded(answer)

        command = USER_ACTION
        recomposeMidCrossfade()

        assertThat(shadowOf(answer).lastEvaluatedJavascript, equalTo(USER_ACTION.script))
        assertThat(shadowOf(question).lastEvaluatedJavascript, not(equalTo(USER_ACTION.script)))
        assertThat("consumed once", consumed, contains(USER_ACTION.id))
    }

    @Test
    fun `a command waiting for the question page to load runs only in the answer page`() {
        showFlashcard()
        val question = singleWebView()
        command = USER_ACTION
        composeTestRule.waitForIdle()
        assertThat("waits for the page to load", consumed, hasSize(0))

        val (fadingOut, answer) = showAnswerAndPauseMidCrossfade()
        assertThat(fadingOut, equalTo(question))
        simulatePageLoaded(question)
        simulatePageLoaded(answer)

        assertThat(shadowOf(question).lastEvaluatedJavascript, not(equalTo(USER_ACTION.script)))
        assertThat(shadowOf(answer).lastEvaluatedJavascript, equalTo(USER_ACTION.script))
        assertThat("consumed once", consumed, contains(USER_ACTION.id))
    }

    private fun showFlashcard() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Flashcard(
                    baseUrl = BASE_URL,
                    questionHtml = "<p>Question</p>",
                    answerHtml = "<p>Answer</p>",
                    bodyClass = "card",
                    isMediaAutoplayEnabled = false,
                    javascriptCommand = command,
                    onJavascriptCommandConsumed = { id ->
                        consumed += id
                        if (command?.id == id) command = null
                    },
                    onGesture = {},
                    onLinkClick = {},
                    isAnswerShown = isAnswerShown,
                )
            }
        }
    }

    private data class CrossfadingWebViews(
        val question: WebView,
        val answer: WebView,
    )

    private fun showAnswerAndPauseMidCrossfade(): CrossfadingWebViews {
        val question = singleWebView()
        composeTestRule.mainClock.autoAdvance = false
        isAnswerShown = true
        repeat(3) { recomposeMidCrossfade() }
        val crossfading = webViewsWithoutWaitingForIdle()
        assertThat("the question and answer WebViews", crossfading, hasSize(2))
        return CrossfadingWebViews(question, answer = crossfading.single { it !== question })
    }

    private fun recomposeMidCrossfade() {
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun simulatePageLoaded(webView: WebView) {
        composeTestRule.runOnUiThread { webView.webViewClient.onPageFinished(webView, BASE_URL) }
    }

    private fun singleWebView(): WebView {
        composeTestRule.waitForIdle()
        val webViews = webViewsWithoutWaitingForIdle()
        assertThat("exactly one card WebView", webViews, hasSize(1))
        return webViews.single()
    }

    private fun webViewsWithoutWaitingForIdle(): List<WebView> {
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

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"
        val USER_ACTION = ReviewerJavascriptCommand(id = 1, script = "userJs1()")
    }
}
