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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

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
            1000L
        )
        activity.supportFragmentManager.executePendingTransactions()

        var fragment =
            activity.supportFragmentManager.findFragmentByTag(SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT)
        assertNotNull("Fragment should be added for deck info URL", fragment)
        val downloadFile =
            fragment?.arguments?.getSerializableCompat<DownloadFile>(SharedDecksActivity.DOWNLOAD_FILE)
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
        assertNull("Fragment should be removed", fragment)

        // 2. Test a search URL - should be ignored
        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/decks?search=physics",
            "userAgent",
            "contentDisposition",
            "text/html",
            0L
        )
        activity.supportFragmentManager.executePendingTransactions()

        fragment =
            activity.supportFragmentManager.findFragmentByTag(SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT)
        assertNull("Fragment should NOT be added for search URL", fragment)
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
        assertNotNull("Initial load should have occurred", initialLast)

        // Click search icon to open search bar
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.search_using_deck_name))
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

    @Test
    fun `back on the first page is left to the system`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        composeTestRule.waitForIdle()

        assertFalse(
            "nothing intercepts back on the first page, so it gets the predictive back animation",
            activity.onBackPressedDispatcher.hasEnabledCallbacks(),
        )
    }

    @Test
    fun `back after going home is not swallowed`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        val webView = activity.findViewById<WebView>(R.id.media_check_webview)
        val shadowWebView = shadowOf(webView)
        val client = shadowWebView.webViewClient
        val homeUrl = activity.getString(R.string.shared_decks_url)
        val deckUrl = "https://ankiweb.net/shared/info/12345678"

        // follow a link: the web history can go back
        shadowWebView.pushEntryToHistory(homeUrl)
        shadowWebView.pushEntryToHistory(deckUrl)
        client.doUpdateVisitedHistory(webView, deckUrl, false)
        assertTrue("back navigates the web history", activity.onBackPressedDispatcher.hasEnabledCallbacks())

        // home clears the history once the page loaded, without a doUpdateVisitedHistory call
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.home)).performClick()
        client.onPageFinished(webView, homeUrl)

        assertFalse(
            "no stale callback swallows the next back",
            activity.onBackPressedDispatcher.hasEnabledCallbacks(),
        )
    }

    @Test
    fun `a stale back callback lets back through`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        val webView = activity.findViewById<WebView>(R.id.media_check_webview)
        val shadowWebView = shadowOf(webView)
        val deckUrl = "https://ankiweb.net/shared/info/12345678"
        shadowWebView.pushEntryToHistory(activity.getString(R.string.shared_decks_url))
        shadowWebView.pushEntryToHistory(deckUrl)
        shadowWebView.webViewClient.doUpdateVisitedHistory(webView, deckUrl, false)

        // the history changes without a doUpdateVisitedHistory call
        webView.clearHistory()
        activity.onBackPressedDispatcher.onBackPressed()

        assertTrue("back leaves instead of being swallowed", activity.isFinishing)
    }

    @Test
    fun `back closes the search field first`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.search_using_deck_name))
            .performClick()
        composeTestRule.onNode(hasTestTag("search_field")).assertExists()

        composeTestRule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }

        composeTestRule.onNode(hasTestTag("search_field")).assertDoesNotExist()
        assertFalse("back closed search instead of leaving", activity.isFinishing)
    }
}
