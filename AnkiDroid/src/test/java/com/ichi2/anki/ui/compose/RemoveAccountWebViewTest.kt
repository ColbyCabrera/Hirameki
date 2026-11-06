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

package com.ichi2.anki.ui.compose

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** #143: the 'remove account' page survives losing its WebView renderer process. */
@RunWith(AndroidJUnit4::class)
class RemoveAccountWebViewTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the page is loaded again in a new WebView`() {
        // a composable host is neither a Fragment nor an Activity listener: this fails in debug if not handled
        composeTestRule.setContent { RemoveAccountWebView(REMOVE_ACCOUNT_URL, urlsToRedirect = emptyList()) }
        composeTestRule.waitForIdle()
        lateinit var layout: SafeWebViewLayout
        composeTestRule.runOnUiThread { layout = checkNotNull(findLayout(composeTestRule.activity.window.decorView)) }
        val original = checkNotNull(layout.webView)
        val detail = mockk<RenderProcessGoneDetail> { every { didCrash() } returns false }

        var handled = false
        composeTestRule.runOnUiThread { handled = shadowOf(original).webViewClient.onRenderProcessGone(original, detail) }

        assertThat("handled: false kills the app", handled, equalTo(true))
        val replacement = checkNotNull(layout.webView)
        assertThat(replacement, not(sameInstance(original)))
        assertThat(shadowOf(replacement).lastLoadedUrl, equalTo(REMOVE_ACCOUNT_URL))
        assertThat(shadowOf(replacement).webViewClient, instanceOf(SafeWebViewClient::class.java))
        assertThat(replacement.settings.allowFileAccess, equalTo(false))
    }

    private fun findLayout(view: View): SafeWebViewLayout? =
        when (view) {
            is SafeWebViewLayout -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findLayout(view.getChildAt(it)) }
            else -> null
        }

    private companion object {
        const val REMOVE_ACCOUNT_URL = "https://ankiweb.net/account/remove-account"
    }
}
