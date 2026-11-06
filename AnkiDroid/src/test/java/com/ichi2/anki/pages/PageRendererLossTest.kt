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

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #143: pages survive losing their WebView renderer process instead of taking the app down. */
@RunWith(AndroidJUnit4::class)
class PageRendererLossTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the client always handles a lost renderer and reports it once`() {
        val client = PageWebViewClient()
        val reports = mutableListOf<Boolean>()
        client.onRendererGone = { reports += it }
        val webView = WebView(composeTestRule.activity)

        assertThat("handled: false kills the app", client.onRenderProcessGone(webView, detail(crashed = true)), equalTo(true))
        assertThat(client.onRenderProcessGone(webView, detail(crashed = false)), equalTo(true))

        assertThat("the first loss is reported, with whether it crashed", reports, equalTo(listOf(true)))
    }

    @Test
    fun `a released client still handles a lost renderer`() {
        val client = PageWebViewClient()
        var reported = false
        client.onRendererGone = { reported = true }
        client.release()

        val handled = client.onRenderProcessGone(WebView(composeTestRule.activity), detail(crashed = false))

        assertThat(handled, equalTo(true))
        assertThat("a torn-down page needs no rebuild", reported, equalTo(false))
        assertThat(client.onRendererGone, nullValue())
    }

    @Test
    fun `a page whose renderer is reclaimed is rebuilt in a new WebView`() {
        showPage()
        val original = singleWebView()

        loseRenderer(original, crashed = false)

        assertThat(singleWebView(), not(sameInstance(original)))
    }

    @Test
    fun `a page whose renderer crashed shows the page error`() {
        showPage()

        loseRenderer(singleWebView(), crashed = true)

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.page_web_view_error)).assertIsDisplayed()
    }

    @Test
    fun `a page fragment whose renderer is reclaimed is rebuilt`() {
        launchPageFragment().use { scenario ->
            lateinit var original: SingleFragmentActivity
            scenario.onActivity { activity ->
                original = activity
                activity.pageWebView().let { it.webViewClient.onRenderProcessGone(it, detail(crashed = false)) }
            }
            advanceRobolectricLooper()

            scenario.onActivity { activity ->
                assertThat("the activity was recreated", activity, not(sameInstance(original)))
                assertThat("it is still open", activity.isFinishing, equalTo(false))
            }
        }
    }

    @Test
    fun `a page fragment whose renderer crashed closes the page`() {
        launchPageFragment().use { scenario ->
            var finishing = false
            scenario.onActivity { activity ->
                activity.pageWebView().let { it.webViewClient.onRenderProcessGone(it, detail(crashed = true)) }
                finishing = activity.isFinishing
            }

            assertThat("a rebuilt page would likely crash again", finishing, equalTo(true))
        }
    }

    private fun launchPageFragment(): ActivityScenario<SingleFragmentActivity> =
        ActivityScenario.launch<SingleFragmentActivity>(PageFragment.getIntent(targetContext, "graphs")).also {
            advanceRobolectricLooper()
        }

    private fun SingleFragmentActivity.pageWebView(): WebView =
        supportFragmentManager.fragments
            .filterIsInstance<PageFragment>()
            .single()
            .webView

    private fun showPage() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                PageWebViewInternal(path = "graphs", serverBaseUrl = "http://127.0.0.1:1/")
            }
        }
    }

    private fun detail(crashed: Boolean) = mockk<RenderProcessGoneDetail> { every { didCrash() } returns crashed }

    private fun loseRenderer(
        webView: WebView,
        crashed: Boolean,
    ) {
        composeTestRule.runOnUiThread {
            webView.webViewClient.onRenderProcessGone(webView, detail(crashed))
        }
        composeTestRule.waitForIdle()
    }

    private fun singleWebView(): WebView {
        composeTestRule.waitForIdle()
        val found = mutableListOf<WebView>()
        composeTestRule.runOnUiThread { collectWebViews(composeTestRule.activity.window.decorView, found) }
        assertThat("exactly one page WebView", found, hasSize(1))
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
}
