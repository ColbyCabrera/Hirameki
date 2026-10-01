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

package com.ichi2.anki

import android.content.Intent
import android.webkit.WebView
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SharedDecksActivityTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun `test download listener filters non-deck URLs`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()

        val webView = activity.findViewById<WebView>(R.id.media_check_webview)
        val downloadListener = shadowOf(webView).downloadListener

        // 1. Test a deck info URL - should trigger fragment
        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/info/12345678",
            "userAgent",
            "attachment; filename=deck.apkg",
            "application/octet-stream",
            1000L,
        )
        activity.supportFragmentManager.executePendingTransactions()

        var fragment =
            activity.supportFragmentManager.findFragmentByTag(SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT)
        assertNotNull(fragment, "Fragment should be added for deck info URL")
        val downloadFile =
            fragment.arguments?.getSerializableCompat<DownloadFile>(SharedDecksActivity.DOWNLOAD_FILE)
        assertTrue("DOWNLOAD_FILE argument should be a DownloadFile", downloadFile is DownloadFile)
        downloadFile as DownloadFile
        assertEquals("https://ankiweb.net/shared/info/12345678", downloadFile.url)
        assertEquals("userAgent", downloadFile.userAgent)
        assertEquals("attachment; filename=deck.apkg", downloadFile.contentDisposition)
        assertEquals("application/octet-stream", downloadFile.mimeType)

        // Clear the fragment for the next test
        activity.supportFragmentManager.popBackStackImmediate()
        activity.supportFragmentManager.executePendingTransactions()
        fragment =
            activity.supportFragmentManager.findFragmentByTag(SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT)
        assertNull(fragment, "Fragment should be removed")

        // 2. Test a search URL - should be ignored
        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/decks?search=physics",
            "userAgent",
            "contentDisposition",
            "text/html",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()

        fragment =
            activity.supportFragmentManager.findFragmentByTag(SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT)
        assertNull(fragment, "Fragment should NOT be added for search URL")
    }

    @Test
    fun `test top app bar search initiates webview load`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()

        val webView = activity.findViewById<WebView>(R.id.media_check_webview)
        val shadowWebView = shadowOf(webView)

        // Capture initial state
        val initialLast = shadowWebView.lastLoadedUrl
        assertNotNull(initialLast, "Initial load should have occurred")

        // Click search icon to open search bar
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.search_using_deck_name))
            .performClick()

        // Type search query
        val query = "kanji"
        composeTestRule.onNode(hasTestTag("search_field")).performTextInput(query)

        // Perform search (IME action)
        composeTestRule.onNode(hasTestTag("search_field")).performImeAction()

        // Verify WebView loaded the correct search URL and it's different from the initial one
        val lastUrl = shadowWebView.lastLoadedUrl
        assertNotEquals("Search should have triggered a new load", initialLast, lastUrl)
        assertEquals("https://ankiweb.net/shared/decks?search=kanji", lastUrl)
    }
}
