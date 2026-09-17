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
package com.ichi2.anki.pages

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.RobolectricTest
import com.ichi2.themes.Theme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PageWebViewTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    // buffered and never replayed, like StatisticsViewModel's: a page that loads later is not sent it again
    private val commands = MutableSharedFlow<String>(extraBufferCapacity = 1)

    @Test
    fun `a js command reaches the page`() {
        showPage()

        send(DECK_SCRIPT)

        assertThat(shadowOf(page()).lastEvaluatedJavascript, containsString(DECK_SCRIPT))
    }

    @Test
    fun `the last js command is applied again to the page a dead renderer left behind`() {
        showPage()
        val first = page()
        send(DECK_SCRIPT)

        rendererGone(first)

        val rebuilt = page()
        assertThat(rebuilt, not(sameInstance(first)))
        // the rebuilt page loads "graphs" with no deck filter, so without the command again the statistics would
        // silently show the default deck, and picking the same deck again would change nothing
        pageFinished(rebuilt)
        assertThat(shadowOf(rebuilt).lastEvaluatedJavascript, containsString(DECK_SCRIPT))
    }

    @Test
    fun `a loaded page is given the number steppers, labelled in the ui language`() {
        showPage()
        val page = page()

        composeTestRule.runOnIdle { shadowOf(page).webViewClient.onPageFinished(page, "${BASE_URL}deck-options/1") }

        // the client evaluates its theme, then the motion and revert scripts, then the steppers; robolectric
        // keeps only the script evaluated last
        val script = shadowOf(page).lastEvaluatedJavascript
        // the name anki_material3_steppers.js reads its labels from, which it needs before it adds any button
        assertThat(script, containsString("window.ankiMaterial3StepperLabels = "))
        assertThat(script, containsString(JSONObject.quote(TR.actionsDecrementValue())))
        assertThat(script, containsString(JSONObject.quote(TR.actionsIncrementValue())))
        assertThat("the asset follows its labels", script, containsString("const labels = window.ankiMaterial3StepperLabels;"))
    }

    @Test
    fun `a loaded page is given the one-tap restore, worded in the ui language`() {
        // a mock of the webview, not the composed one: robolectric's remembers only the script it evaluated
        // last, and this one is not it
        val webView = mock<WebView> { on { context } doReturn ContextThemeWrapper(targetContext, Theme.LIGHT.resId) }

        PageWebViewClient().onPageFinished(webView, "${BASE_URL}deck-options/1")

        val scripts = argumentCaptor<String>()
        verify(webView, atLeastOnce()).evaluateJavascript(scripts.capture(), anyOrNull())
        // the name anki_material3_revert.js reads the restore's spoken name from, the badge's only name
        val script = scripts.allValues.single { "window.ankiMaterial3RevertLabel = " in it }
        assertThat(script, containsString(JSONObject.quote(TR.deckConfigRevertButtonTooltip())))
        assertThat("the asset follows its label", script, containsString("const label = window.ankiMaterial3RevertLabel;"))
    }

    private fun showPage() {
        composeTestRule.setContent {
            // an app theme, as every host of the page has: the page's client reads material colours off the
            // webview's context to style the page
            CompositionLocalProvider(
                LocalContext provides ContextThemeWrapper(LocalContext.current, Theme.LIGHT.resId),
            ) {
                PageWebViewInternal(path = "graphs", serverBaseUrl = BASE_URL, jsCommands = commands)
            }
        }
        composeTestRule.waitForIdle() // the page collects commands from a LaunchedEffect
    }

    /** tryEmit, not emit: emit would suspend until the page collects, and the page collects on this thread */
    private fun send(script: String) {
        check(commands.tryEmit(script)) { "the command was not buffered" }
        composeTestRule.waitForIdle()
    }

    /** the webview the page composes: an android view inside compose's host view, which the test rule hides */
    @OptIn(InternalComposeUiApi::class)
    private fun page(): WebView {
        val root = composeTestRule.onRoot().fetchSemanticsNode().root as ViewRootForTest
        return composeTestRule.runOnIdle { checkNotNull(root.view.findWebView()) { "no webview is composed" } }
    }

    private fun View.findWebView(): WebView? {
        if (this is WebView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findWebView()?.let { return it }
        }
        return null
    }

    /** what the page's client reports when the system reclaims the renderer of a backgrounded page */
    private fun rendererGone(page: WebView) {
        val detail = mock<RenderProcessGoneDetail> { on { didCrash() } doReturn false }
        composeTestRule.runOnIdle { shadowOf(page).webViewClient.onRenderProcessGone(page, detail) }
    }

    /**
     * what the page's host is told when a load finishes. its own hook, not the client's onPageFinished: the client
     * injects its theme css from there too, and robolectric's webview remembers only the script evaluated last
     */
    private fun pageFinished(page: WebView) =
        composeTestRule.runOnIdle {
            val client = shadowOf(page).webViewClient as PageWebViewClient
            client.onPageFinishedCallbacks.forEach { it.onPageFinished(page) }
        }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"

        /** what StatisticsViewModel injects to show one deck's graphs */
        const val DECK_SCRIPT = "statisticsSearchText"
    }
}
