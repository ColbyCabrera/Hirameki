// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.pages

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.pages.PostRequestBridge.Companion.HEADER_SIZE
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Import cancellation must follow the newly loaded page after renderer recovery. */
@RunWith(AndroidJUnit4::class)
class ImporterRendererRecoveryTest : RobolectricTest() {
    @Test
    fun `package import can be cancelled after completed page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"), cancel = false)
        }

    @Test
    fun `CSV import can be cancelled after completed page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(CsvImporter.getIntent(targetContext, "/unused.csv"), cancel = false)
        }

    @Test
    fun `package import can be cancelled after cancelled page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"), cancel = true)
        }

    @Test
    fun `CSV import can be cancelled after cancelled page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(CsvImporter.getIntent(targetContext, "/unused.csv"), cancel = true)
        }

    private suspend fun TestScope.assertCancellationAfterRecovery(
        intent: Intent,
        cancel: Boolean,
    ) {
        Robolectric.buildActivity(SingleFragmentActivity::class.java, intent).use { controller ->
            controller.setup().visible()
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            val page = activity.fragment as PageFragment
            val bridge = PostRequestBridge(page, this)
            val dispatcher = activity.onBackPressedDispatcher

            assertSuccessful(bridge.handleMessage(request("latestProgress")))
            assertTrue(dispatcher.hasEnabledCallbacks())
            if (cancel) {
                dispatcher.onBackPressed()
                assertFalse(activity.isFinishing)
            } else {
                assertSuccessful(bridge.handleMessage(request("importDone")))
            }
            assertFalse(dispatcher.hasEnabledCallbacks())

            // Simulate renderer loss by recreating the Activity as PageFragment does on renderer exit.
            val previousWebView = page.webView
            controller.recreate()
            advanceUntilIdle()
            advanceRobolectricLooper()
            val recoveredActivity = controller.get()
            val recoveredPage = recoveredActivity.fragment as PageFragment
            assertNotSame(previousWebView, recoveredPage.webView, "Recovery must create and configure a new WebView")

            // Recovery reloads the initial importer route. Its next import must be cancellable.
            val recoveredDispatcher = recoveredActivity.onBackPressedDispatcher
            val recoveredBridge = PostRequestBridge(recoveredPage, this)
            assertSuccessful(recoveredBridge.handleMessage(request("latestProgress")))
            assertTrue(recoveredDispatcher.hasEnabledCallbacks(), "The new import page must have a fresh, registered cancellation callback")
            recoveredDispatcher.onBackPressed()
            assertFalse(recoveredActivity.isFinishing, "Back must cancel the new import rather than leave")
        }
    }

    private fun assertSuccessful(reply: ByteArray?) {
        assertEquals(200, ByteBuffer.wrap(assertNotNull(reply)).getShort(Int.SIZE_BYTES).toInt())
    }

    private fun request(method: String): ByteArray {
        val path = "/_anki/$method".encodeToByteArray()
        return ByteBuffer
            .allocate(HEADER_SIZE + path.size)
            .putInt(1)
            .putShort(path.size.toShort())
            .put(path)
            .array()
    }
}
