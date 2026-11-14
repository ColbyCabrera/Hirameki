// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.pages

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.pages.PageWebViewClient.Navigation
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pages served by [AnkiServer] can call the backend, so [PageWebViewClient] must keep untrusted
 * content out of them (ported from ankidroid/Anki-Android#22156).
 */
@RunWith(AndroidJUnit4::class)
class PageWebViewClientOriginTest : RobolectricTest() {
    private val server = "http://127.0.0.1:40123/".toUri()
    private val client = PageWebViewClient().apply { serverUrl = server }

    private fun page(path: String): Uri = server.buildUpon().encodedPath(path).build()

    /** The same page as [url], on origins which merely look like ours */
    private fun otherOrigins(url: Uri): List<Uri> =
        listOf(
            url.buildUpon().scheme("https").build(),
            url.buildUpon().encodedAuthority("127.0.0.1:${url.port + 1}").build(),
            url.buildUpon().encodedAuthority("example.org:${url.port}").build(),
            url.buildUpon().encodedAuthority("user@${url.encodedAuthority}").build(),
        )

    @Test
    fun `bundled pages and nested routes load in the WebView`() {
        val urls =
            listOf(
                page("/graphs"),
                page("/deck-options/1")
                    .buildUpon()
                    .query("test=1")
                    .fragment("night")
                    .build(),
                page("/card-info/1/2"),
            )
        for (url in urls) {
            assertEquals(Navigation.LOAD, client.navigationFor(url, isForMainFrame = true), url.toString())
        }
    }

    @Test
    fun `local paths outside bundled pages are blocked`() {
        for (path in listOf("/", "/unknown", "/graphs-other", "/_anki/test", "/_app/env.js")) {
            assertEquals(Navigation.BLOCK, client.navigationFor(page(path), isForMainFrame = true), path)
        }
    }

    @Test
    fun `web links open outside the app`() {
        val urls =
            listOf(
                "http://example.org/graphs?search=hello#anchor",
                "https://example.org/graphs?search=hello#anchor",
                "https://docs.ankiweb.net/deck-options.html#daily-limits",
            ).map { it.toUri() }
        for (url in urls) {
            assertEquals(Navigation.OPEN_EXTERNALLY, client.navigationFor(url, isForMainFrame = true), url.toString())
        }
    }

    @Test
    fun `bundled page paths on other origins open outside the app`() {
        for (url in otherOrigins(page("/graphs"))) {
            assertEquals(Navigation.OPEN_EXTERNALLY, client.navigationFor(url, isForMainFrame = true), url.toString())
        }
    }

    @Test
    fun `external subframes are blocked`() {
        for (address in listOf("http://example.org/graphs", "https://example.org/graphs")) {
            assertEquals(Navigation.BLOCK, client.navigationFor(address.toUri(), isForMainFrame = false), address)
        }
    }

    @Test
    fun `non-HTTP schemes are blocked`() {
        val addresses =
            listOf(
                "intent://example.org/#Intent;scheme=https;end",
                "file:///tmp/test.html",
                "content://example.org/test.html",
                "data:text/html,test",
                "javascript:void(0)",
                "mailto:test@example.org",
            )
        for (address in addresses) {
            assertEquals(Navigation.BLOCK, client.navigationFor(address.toUri(), isForMainFrame = true), address)
        }
    }

    @Test
    fun `without a server, nothing is trusted`() {
        val untrusted = PageWebViewClient()
        assertEquals(Navigation.OPEN_EXTERNALLY, untrusted.navigationFor(page("/graphs"), isForMainFrame = true))
        assertNull(untrusted.shouldInterceptRequest(webView(), request(page("/graphs"))))
    }

    @Test
    fun `blocked navigation is handled without launching anything`() {
        val webView = webView()
        for (address in listOf(page("/unknown").toString(), "javascript:void(0)", "mailto:test@example.org")) {
            assertTrue(client.shouldOverrideUrlLoading(webView, request(address.toUri())), address)
            assertNull(shadowOf(targetContext as Application).nextStartedActivity, address)
        }
        assertTrue(client.shouldOverrideUrlLoading(webView, null as WebResourceRequest?), "a missing request is not loaded")
    }

    @Test
    fun `only the page server is served bundled pages and assets`() {
        val webView = webView()
        for (url in listOf(page("/graphs"), page("/_app/version.json"))) {
            assertNotNull(client.shouldInterceptRequest(webView, request(url)), url.toString()).data.close()
            for (other in otherOrigins(url)) {
                assertNull(client.shouldInterceptRequest(webView, request(other)), other.toString())
            }
        }
    }

    private fun webView() = WebView(targetContext)

    private fun request(
        url: Uri,
        mainFrame: Boolean = true,
    ): WebResourceRequest =
        mockk {
            every { this@mockk.url } returns url
            every { method } returns "GET"
            every { isForMainFrame } returns mainFrame
        }
}
