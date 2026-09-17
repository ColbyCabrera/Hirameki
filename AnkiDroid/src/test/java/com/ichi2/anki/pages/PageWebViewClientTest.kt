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

import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.pages.PageWebViewClient.Companion.REBUILD_LOOP_WINDOW_MS
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class PageWebViewClientTest {
    /** the canRebuild value of every rebuild the client asked its host for, in order */
    private val rebuilds = mutableListOf<Boolean>()

    /**
     * the rebuild times are kept per page path for the whole process, while robolectric restarts its clock for
     * each test: without this a path reused by a later test compares an earlier test's timestamp against a clock
     * back near zero, and that test reads as a rebuild loop
     */
    @Before
    fun clearRebuildHistory() = PageWebViewClient.clearRebuildHistory()

    @Test
    fun rebuildWaitsUntilTheScreenIsStarted() {
        val screen = TestScreen(Lifecycle.State.CREATED)
        val page = Page(screen, "/deck-options/1")

        assertThat("returning false makes chromium kill the app", page.rendererGone(), equalTo(true))
        assertThat("no rebuild while the screen is in the background", rebuilds, equalTo(emptyList()))

        screen.lifecycle.currentState = Lifecycle.State.STARTED
        assertThat(rebuilds, equalTo(listOf(true)))
    }

    @Test
    fun pageThatDiesRightAfterItsRebuildIsNotRebuiltAgain() {
        val screen = TestScreen(Lifecycle.State.RESUMED)

        Page(screen, "/graphs").rendererGone()
        Page(screen, "/graphs").rendererGone()
        assertThat(rebuilds, equalTo(listOf(true, false)))

        Page(screen, "/card-info/1").rendererGone()
        assertThat("another page is still rebuilt", rebuilds, equalTo(listOf(true, false, true)))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(REBUILD_LOOP_WINDOW_MS))
        Page(screen, "/graphs").rendererGone()
        assertThat("a later death is rebuilt", rebuilds, equalTo(listOf(true, false, true, true)))
    }

    @Test
    fun pageWithNoUrlThatDiesRightAfterItsRebuildIsNotRebuiltAgain() {
        val screen = TestScreen(Lifecycle.State.RESUMED)

        // a renderer that crashes while the first load is still uncommitted leaves the webview with no url. keyed on
        // that null the rebuild was never recorded, so such a page recreated its screen over and over
        Page(screen, path = null).rendererGone()
        Page(screen, path = null).rendererGone()

        assertThat(rebuilds, equalTo(listOf(true, false)))
    }

    @Test
    fun pageWhoseRendererIsReclaimedAgainIsStillRebuilt() {
        val screen = TestScreen(Lifecycle.State.RESUMED)

        Page(screen, "/deck-options/2").rendererGone(crashed = false)
        Page(screen, "/deck-options/2").rendererGone(crashed = false)

        // android reclaims a backgrounded renderer whenever it needs the memory, over and over and through no
        // fault of the page. counting that as a crash left the page blank on the second one, losing the edits
        // someone had half-made on it
        assertThat("a reclaimed page is always rebuilt", rebuilds, equalTo(listOf(true, true)))
    }

    @Test
    fun reclaimAfterACrashIsStillRebuilt() {
        val screen = TestScreen(Lifecycle.State.RESUMED)

        Page(screen, "/import-csv").rendererGone(crashed = true)
        Page(screen, "/import-csv").rendererGone(crashed = false)

        assertThat("a crash does not block the reclaims after it", rebuilds, equalTo(listOf(true, true)))
    }

    @Test
    fun oneDeadWebViewIsRebuiltOnce() {
        val page = Page(TestScreen(Lifecycle.State.RESUMED), "/congrats")

        assertThat(page.rendererGone(), equalTo(true))
        assertThat(page.rendererGone(), equalTo(true))
        assertThat(rebuilds, equalTo(listOf(true)))
    }

    @Test
    fun pageReleasedBeforeTheScreenStartsIsNotRebuilt() {
        val screen = TestScreen(Lifecycle.State.CREATED)
        val page = Page(screen, "/image-occlusion/1")

        page.rendererGone()
        page.client.release()
        screen.lifecycle.currentState = Lifecycle.State.STARTED

        assertThat(rebuilds, equalTo(emptyList()))
    }

    /** a webview showing [path] on [screen] (null: nothing loaded yet), with a client whose host records the rebuilds */
    private inner class Page(
        screen: LifecycleOwner,
        path: String?,
    ) {
        val client = PageWebViewClient().apply { onRendererGone = { rebuilds.add(it) } }
        private val webView =
            WebView(ApplicationProvider.getApplicationContext()).apply {
                setViewTreeLifecycleOwner(screen)
                if (path != null) loadUrl("http://127.0.0.1:1$path")
            }

        /** [crashed] is what the page died of: true a crash of its own, false the system taking the memory back */
        fun rendererGone(crashed: Boolean = true) =
            client.onRenderProcessGone(webView, mock<RenderProcessGoneDetail> { on { didCrash() } doReturn crashed })
    }

    private class TestScreen(
        state: Lifecycle.State,
    ) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = state }
    }
}
