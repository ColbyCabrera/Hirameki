// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.workarounds.SafeWebViewLayout
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
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class SharedDecksActivityTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun `test download listener filters non-deck URLs`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()

        val webView = requireNotNull(activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview).webView)
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

        val webView = requireNotNull(activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview).webView)
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

    @Test
    fun `rapid download callbacks start only one download`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()
        val webView = requireNotNull(activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview).webView)
        val downloadListener = shadowOf(webView).downloadListener

        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/info/12345678",
            "AnkiDroid",
            "attachment; filename=\"first.apkg\"",
            "application/octet-stream",
            0L,
        )
        downloadListener.onDownloadStart(
            "https://ankiweb.net/svc/shared/download-deck/5678?t=key",
            "AnkiDroid",
            "attachment; filename=\"second.apkg\"",
            "application/octet-stream",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.fragments.size)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun `download callback keeps the existing download screen`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()
        val webView = requireNotNull(activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview).webView)
        val downloadListener = shadowOf(webView).downloadListener

        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/info/12345678",
            "AnkiDroid",
            "attachment; filename=\"first.apkg\"",
            "application/octet-stream",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()
        val existing = activity.supportFragmentManager.fragments.single()

        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/info/12345678",
            "AnkiDroid",
            "attachment; filename=\"first.apkg\"",
            "application/octet-stream",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertSame(existing, activity.supportFragmentManager.fragments.single())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun `download can start while closing the previous download screen is pending`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent())
        controller.setup()
        val activity = controller.get()
        val webView = requireNotNull(activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview).webView)
        val downloadListener = shadowOf(webView).downloadListener

        downloadListener.onDownloadStart(
            "https://ankiweb.net/shared/info/12345678",
            "AnkiDroid",
            "attachment; filename=\"first.apkg\"",
            "application/octet-stream",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()
        val first = activity.supportFragmentManager.fragments.single()

        activity.supportFragmentManager.popBackStack()
        downloadListener.onDownloadStart(
            "https://ankiweb.net/svc/shared/download-deck/5678?t=key",
            "AnkiDroid",
            "attachment; filename=\"second.apkg\"",
            "application/octet-stream",
            0L,
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertNotSame(first, activity.supportFragmentManager.fragments.single())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }
}
