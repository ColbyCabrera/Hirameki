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

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.createTransientDirectory
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * #143: screens which replace their WebView when its renderer process is lost, and set the
 * replacement up again. A client which does not handle the loss (including no client) makes Android
 * kill the app.
 */
@RunWith(AndroidJUnit4::class)
class WebViewRendererLossHostsTest : RobolectricTest() {
    @Test
    fun `shared decks reloads the shared decks page`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        val layout = activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview)
        val original = layout.requireWebView()

        val replacement = layout.replaceWebView()

        assertThat(shadowOf(replacement).lastLoadedUrl, equalTo(activity.getString(R.string.shared_decks_url)))
        assertThat("decks can still be downloaded", shadowOf(replacement).downloadListener, notNullValue())
        assertThat(replacement.settings.javaScriptEnabled, equalTo(original.settings.javaScriptEnabled))
    }

    @Test
    fun `changelog is shown again`() {
        val intent = Intent(targetContext, Info::class.java).putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
        val activity = Robolectric.buildActivity(Info::class.java, intent).setup().get()
        val layout = activity.findViewById<SafeWebViewLayout>(R.id.info)

        val replacement = layout.replaceWebView()

        assertThat(shadowOf(replacement).lastLoadedUrl, equalTo("/android_asset/changelog.html"))
        assertThat(replacement.settings.javaScriptEnabled, equalTo(true))
    }

    @Test
    fun `shared decks back leaves the screen once the WebView keeps crashing`() {
        val activity = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).setup().get()
        val layout = activity.findViewById<SafeWebViewLayout>(R.id.media_check_webview)

        assertBackLeavesAfterCrashLoop(activity, layout)
    }

    @Test
    fun `changelog back leaves the screen once the WebView keeps crashing`() {
        val intent = Intent(targetContext, Info::class.java).putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
        val activity = Robolectric.buildActivity(Info::class.java, intent).setup().get()
        val layout = activity.findViewById<SafeWebViewLayout>(R.id.info)

        assertBackLeavesAfterCrashLoop(activity, layout)
    }

    /** Back handled by the page's history must not be swallowed once that history is gone */
    private fun assertBackLeavesAfterCrashLoop(
        activity: AnkiActivity,
        layout: SafeWebViewLayout,
    ) {
        layout.loseRenderer(crashed = true)
        // the user navigates in the replacement: Back goes back in the page
        val webView = layout.requireWebView()
        shadowOf(webView).pushEntryToHistory(FIRST_PAGE)
        shadowOf(webView).pushEntryToHistory(NEXT_PAGE)
        shadowOf(webView).webViewClient.doUpdateVisitedHistory(webView, NEXT_PAGE, false)

        // then the renderer crashes again
        layout.loseRenderer(crashed = true)
        assertThat("the WebView keeps crashing", layout.webView, nullValue())
        activity.onBackPressedDispatcher.onBackPressed()

        assertThat("Back leaves the screen", activity.isFinishing, equalTo(true))
    }

    @Test
    fun `media check shows its report again`() {
        ActivityScenario.launch<SingleFragmentActivity>(MediaCheckFragment.getIntent(targetContext)).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            lateinit var layout: SafeWebViewLayout
            scenario.onActivity { layout = it.findViewById(R.id.media_check_webview) }
            // the media check itself fails here: test collections have no media folder
            val report = shadowOf(layout.requireWebView()).lastLoadDataWithBaseURL?.data
            assertThat("the report is shown", report, notNullValue())

            scenario.onActivity {
                val replacement = layout.replaceWebView()

                assertThat(shadowOf(replacement).lastLoadDataWithBaseURL?.data, equalTo(report))
            }
        }
    }

    @Test
    fun `image preview is shown again`() {
        val image = File(targetContext.cacheDir, "renderer-loss.png")
        image.outputStream().use { createBitmap(4, 4).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val extra = MultimediaActivityExtra(0, ImageField(), MultimediaEditableNote(), Uri.fromFile(image).toString())
        val intent = MultimediaImageFragment.getIntent(targetContext, extra, MultimediaImageFragment.ImageOptions.GALLERY)

        ActivityScenario.launch<Activity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val layout = activity.findViewById<SafeWebViewLayout>(R.id.multimedia_webview)
                val preview = shadowOf(layout.requireWebView()).lastLoadDataWithBaseURL?.data
                assertThat("the image is previewed", preview, notNullValue())

                val replacement = layout.replaceWebView()

                assertThat(shadowOf(replacement).lastLoadDataWithBaseURL?.data, equalTo(preview))
            }
        }
    }

    @Test
    fun `card previewer shows the card again`() {
        withPreviewer { layout ->
            val original = layout.requireWebView()
            // the ViewModel starts showing cards once the first page finished
            shadowOf(original).webViewClient.onPageFinished(original, null)

            val replacement = layout.replaceWebView()

            assertThat(
                "the card page is loaded",
                shadowOf(replacement).lastLoadDataWithBaseURL?.baseUrl,
                equalTo(shadowOf(original).lastLoadDataWithBaseURL?.baseUrl),
            )
            // otherwise the ViewModel starts showing cards a second time
            assertThat("only the card is shown again", replacement.cardViewerClient().isAfterRecreation, equalTo(true))
        }
    }

    @Test
    fun `card previewer replaced before its first page finished still starts showing cards`() {
        withPreviewer { layout ->
            val replacement = layout.replaceWebView()

            assertThat(replacement.cardViewerClient().isAfterRecreation, equalTo(false))
        }
    }

    private fun withPreviewer(block: (SafeWebViewLayout) -> Unit) {
        val note = addBasicNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col)),
                currentIndex = 0,
            )
        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { block(it.findViewById(R.id.webview)) }
        }
    }

    private fun WebView.cardViewerClient() = shadowOf(this).webViewClient as CardViewerFragment.CardViewerWebViewClient

    private fun SafeWebViewLayout.requireWebView(): WebView = checkNotNull(webView) { "the WebView is destroyed" }

    private fun SafeWebViewLayout.loseRenderer(crashed: Boolean): Boolean {
        val webView = requireWebView()
        val detail = mockk<RenderProcessGoneDetail> { every { didCrash() } returns crashed }
        return shadowOf(webView).webViewClient.onRenderProcessGone(webView, detail)
    }

    /**
     * Loses the renderer of the WebView, as Android reports it, and returns its replacement after
     * checking that it can survive another loss.
     */
    private fun SafeWebViewLayout.replaceWebView(): WebView {
        val original = requireWebView()

        val handled = loseRenderer(crashed = false)
        ShadowLooper.idleMainLooper()

        assertThat("handled: false kills the app", handled, equalTo(true))
        val replacement = requireWebView()
        assertThat(replacement, not(sameInstance(original)))
        assertThat("the replacement is shown", replacement.parent, sameInstance<Any>(this))
        assertThat("the replacement survives the next loss", shadowOf(replacement).webViewClient, instanceOf(SafeWebViewClient::class.java))
        return replacement
    }

    private companion object {
        const val FIRST_PAGE = "https://ankiweb.net/shared/decks"
        const val NEXT_PAGE = "https://ankiweb.net/shared/info/1"
    }
}
