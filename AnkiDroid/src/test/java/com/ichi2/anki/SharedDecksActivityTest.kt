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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
