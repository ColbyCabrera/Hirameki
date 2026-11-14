// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

// Partly ported from AnkiDroid (https://github.com/ankidroid/Anki-Android), GPLv3.

package com.ichi2.anki.workarounds

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.time.toJavaDuration

/** #143: a [SafeWebViewLayout] survives losing its renderer process. */
@RunWith(AndroidJUnit4::class)
class SafeWebViewLayoutTest : RobolectricTest() {
    @Test
    fun `a lost renderer is handled and its WebView replaced`() {
        withHost { layout, host ->
            val original = layout.requireWebView()

            assertThat("handled: false kills the app", loseRenderer(original, crashed = false), equalTo(true))

            assertThat("the dead WebView is destroyed", shadowOf(original).wasDestroyCalled(), equalTo(true))
            val replacement = layout.requireWebView()
            assertThat(replacement, not(sameInstance(original)))
            assertThat("the only child", layout.getChildAt(0), sameInstance(replacement))
            assertThat(layout.childCount, equalTo(1))
            assertThat("the host reconfigures the replacement", host.recreatedCount, equalTo(1))
            assertThat(shadowOf(replacement).webViewClient, instanceOf(SafeWebViewClient::class.java))
        }
    }

    @Test
    fun `reclaimed renderers are always replaced`() {
        withHost { layout, host ->
            repeat(3) { loseRenderer(layout.requireWebView(), crashed = false) }

            assertThat(host.recreatedCount, equalTo(3))
            assertThat(layout.webView, notNullValue())
        }
    }

    @Test
    fun `a renderer crashing twice in quick succession shows an error instead of a WebView`() {
        withHost { layout, host ->
            loseRenderer(layout.requireWebView(), crashed = true)
            val second = layout.requireWebView()
            loseRenderer(second, crashed = true)

            assertThat("no third WebView", host.recreatedCount, equalTo(1))
            assertThat(layout.webView, nullValue())
            assertThat(shadowOf(second).wasDestroyCalled(), equalTo(true))
            assertThat(layout.childCount, equalTo(1))
            assertThat(
                layout.findText(R.string.webview_crash_loop_dialog_title),
                notNullValue(),
            )

            layout.loadUrl("https://example.com/")
            assertThat("calls are ignored", shadowOf(second).lastLoadedUrl, nullValue())

            // reattaching does not retry
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            parent.addView(layout)
            assertThat(layout.webView, nullValue())
        }
    }

    @Test
    fun `retrying after a crash loop gives the content a new WebView`() {
        withHost { layout, host ->
            repeat(2) { loseRenderer(layout.requireWebView(), crashed = true) }
            // the user reads the error, picks other content and retries
            ShadowSystemClock.advanceBy(SafeWebViewLayout.CRASH_LOOP_WINDOW.toJavaDuration() + Duration.ofSeconds(1))

            checkNotNull(layout.findText(R.string.retry)).performClick()

            assertThat(host.recreatedCount, equalTo(2))
            assertThat(layout.webView, notNullValue())
            assertThat("the error is removed", layout.childCount, equalTo(1))

            // a crash soon after retrying is a crash loop again
            loseRenderer(layout.requireWebView(), crashed = true)
            assertThat(host.recreatedCount, equalTo(2))
            assertThat(layout.webView, nullValue())
            assertThat(layout.findText(R.string.retry), notNullValue())
        }
    }

    @Test
    fun `renderer crashes far apart are each recovered`() {
        withHost { layout, host ->
            loseRenderer(layout.requireWebView(), crashed = true)
            ShadowSystemClock.advanceBy(SafeWebViewLayout.CRASH_LOOP_WINDOW.toJavaDuration() + Duration.ofSeconds(1))
            loseRenderer(layout.requireWebView(), crashed = true)

            assertThat(host.recreatedCount, equalTo(2))
            assertThat(layout.webView, notNullValue())
        }
    }

    @Test
    fun `a reclaimed renderer between crashes does not reset the crash loop`() {
        withHost { layout, host ->
            loseRenderer(layout.requireWebView(), crashed = true)
            loseRenderer(layout.requireWebView(), crashed = false)
            loseRenderer(layout.requireWebView(), crashed = true)

            assertThat(host.recreatedCount, equalTo(2))
            assertThat(layout.webView, nullValue())
        }
    }

    @Test
    fun `an explicit listener is called instead of the host`() {
        withHost { layout, host ->
            var explicitCalls = 0
            layout.onWebViewRecreatedListener =
                OnWebViewRecreatedListener {
                    explicitCalls++
                    layout.setWebViewClient(SafeWebViewClient())
                }

            loseRenderer(layout.requireWebView(), crashed = false)

            assertThat(explicitCalls, equalTo(1))
            assertThat(host.recreatedCount, equalTo(0))
        }
    }

    @Test
    fun `a host which does not handle recreation fails in debug builds`() {
        launchFragmentInContainer<NotListeningHostFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val exception =
                    assertFailsWith<IllegalStateException> {
                        (fragment.requireView() as ViewGroup).addView(SafeWebViewLayout(fragment.requireContext()))
                    }
                assertThat(exception.message, equalTo("'NotListeningHostFragment' must implement OnWebViewRecreatedListener"))
            }
        }
    }

    @Test
    fun `a SafeWebViewClient outside a SafeWebViewLayout fails loudly`() {
        val webView = WebView(targetContext)
        val client = SafeWebViewClient()

        assertFailsWith<IllegalStateException> { client.onRenderProcessGone(webView, detail(crashed = false)) }
    }

    @Test
    fun `onRenderProcessGone with stale WebView destroys stale instance only`() {
        withHost { layout, host ->
            val currentWebView = layout.requireWebView()
            val staleWebView = WebView(host.requireContext())

            layout.onRenderProcessGone(staleWebView, didCrash = false)

            assertThat("stale WebView is destroyed", shadowOf(staleWebView).wasDestroyCalled(), equalTo(true))
            assertThat("current WebView is still attached", layout.getChildAt(0), sameInstance(currentWebView))
            assertThat("current WebView is not destroyed", shadowOf(currentWebView).wasDestroyCalled(), equalTo(false))
            assertThat(host.recreatedCount, equalTo(0))
        }
    }

    @Test
    fun `destroy after early-return onRenderProcessGone does not destroy WebView twice`() {
        val layout = DestroyCountingSafeWebViewLayout(targetContext)
        val webView = layout.requireWebView()

        layout.onRenderProcessGone(webView, didCrash = false)
        assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
        assertThat(layout.webViewDestroyCallCount, equalTo(1))

        layout.destroy()
        layout.destroy()

        assertThat("destroy does not invoke WebView.destroy again", layout.webViewDestroyCallCount, equalTo(1))
    }

    @Test
    fun `onRenderProcessGone skips recreation when layout is not attached to window`() {
        withHost { layout, host ->
            val webView = layout.requireWebView()
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            assertThat(host.view, notNullValue())
            assertThat(layout.isAttachedToWindow, equalTo(false))

            layout.onRenderProcessGone(webView, didCrash = false)

            assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
            assertThat(webView.parent, nullValue())
            assertThat(layout.childCount, equalTo(0))
            assertThat(host.recreatedCount, equalTo(0))

            layout.loadUrl("https://blocked.example/")
            assertThat("loadUrl no-ops while WebView is destroyed", shadowOf(webView).lastLoadedUrl, nullValue())
        }
    }

    @Test
    fun `reattaching layout after skipped recreation recovers inner WebView`() {
        withHost { layout, host ->
            val webView = layout.requireWebView()
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)
            layout.onRenderProcessGone(webView, didCrash = false)
            assertThat(layout.childCount, equalTo(0))

            parent.addView(layout)

            assertThat(layout.childCount, equalTo(1))
            assertThat(host.recreatedCount, equalTo(1))
            val recoveryUrl = "https://recovery.example/"
            layout.loadUrl(recoveryUrl)
            assertThat(
                "loadUrl works again after reattachment recovery",
                shadowOf(layout.requireWebView()).lastLoadedUrl,
                equalTo(recoveryUrl),
            )
        }
    }

    @Test
    fun `reattaching after intentional destroy does not recover inner WebView`() {
        withHost { layout, _ ->
            val parent = layout.parent as ViewGroup
            val deadWebView = layout.requireWebView()
            layout.destroy()
            assertThat("destroy removes the dead WebView", layout.childCount, equalTo(0))

            parent.removeView(layout)
            parent.addView(layout)
            assertThat("intentional destroy is terminal", layout.childCount, equalTo(0))

            layout.loadUrl("https://blocked-after-intentional-destroy.example/")
            assertThat(shadowOf(deadWebView).lastLoadedUrl, equalTo("about:blank"))
        }
    }

    @Test
    fun `destroy after crash cleanup prevents recovery on reattach`() {
        withHost { layout, _ ->
            val webView = layout.requireWebView()
            val parent = layout.parent as ViewGroup
            parent.removeView(layout)

            layout.onRenderProcessGone(webView, didCrash = false)
            assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
            assertThat(layout.childCount, equalTo(0))

            layout.destroy()
            parent.addView(layout)

            assertThat(layout.isAttachedToWindow, equalTo(true))
            assertThat("owner teardown must prevent crash recovery", layout.childCount, equalTo(0))
        }
    }

    @Test
    fun `recovery is skipped when orphaned layout reattaches outside fragment view`() {
        launchFragmentInContainer<SafeWebViewLayoutHostFragment>().use { scenario ->
            lateinit var layout: SafeWebViewLayout
            lateinit var staleFragmentRoot: ViewGroup
            lateinit var deadWebView: WebView

            scenario.onFragment { fragment ->
                layout = fragment.layout
                staleFragmentRoot = fragment.requireView() as ViewGroup
                deadWebView = layout.requireWebView()
                staleFragmentRoot.removeView(layout)
                layout.onRenderProcessGone(deadWebView, didCrash = false)
                assertThat(layout.childCount, equalTo(0))
            }

            scenario.recreate()

            scenario.onFragment { fragment ->
                val currentFragmentRoot = fragment.requireView()
                staleFragmentRoot.addView(
                    layout,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
                (currentFragmentRoot.parent as ViewGroup).addView(staleFragmentRoot)

                assertThat(layout.childCount, equalTo(0))
                layout.loadUrl("https://blocked.example/")
                assertThat(
                    "loadUrl no-ops when recovery is skipped for orphaned layout",
                    shadowOf(deadWebView).lastLoadedUrl,
                    nullValue(),
                )
                assertThat(layout.childCount, equalTo(0))
            }
        }
    }

    private fun withHost(block: (SafeWebViewLayout, SafeWebViewLayoutHostFragment) -> Unit) {
        launchFragmentInContainer<SafeWebViewLayoutHostFragment>().use { scenario ->
            scenario.onFragment { fragment -> block(fragment.layout, fragment) }
        }
    }

    /** Delivers the loss as Android does: to the client of the [webView] */
    private fun loseRenderer(
        webView: WebView,
        crashed: Boolean,
    ): Boolean = shadowOf(webView).webViewClient.onRenderProcessGone(webView, detail(crashed))

    private fun detail(crashed: Boolean) = mockk<RenderProcessGoneDetail> { every { didCrash() } returns crashed }

    private fun SafeWebViewLayout.requireWebView(): WebView = checkNotNull(webView) { "the WebView is destroyed" }

    /** The view showing the string [resId], if any */
    private fun View.findText(
        @StringRes resId: Int,
    ): TextView? {
        val text = context.getString(resId)
        return when (this) {
            is TextView -> takeIf { it.text.toString() == text }
            is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findText(resId) }
            else -> null
        }
    }
}
