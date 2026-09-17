/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.ui.compose

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RemoveAccountContentTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `leaving account removal destroys its webview`() {
        var showRemoveAccount by mutableStateOf(true)
        composeTestRule.setContent {
            if (showRemoveAccount) RemoveAccountContent(onBack = {})
        }
        val webView = page()
        assertThat("the page is live while shown", shadowOf(webView).wasDestroyCalled(), equalTo(false))

        showRemoveAccount = false
        composeTestRule.waitForIdle()

        assertThat("the page is destroyed once the screen is left", shadowOf(webView).wasDestroyCalled(), equalTo(true))
    }

    /** the webview the screen composes: an android view inside compose's host view, which the test rule hides */
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
}
