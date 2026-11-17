// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.Event
import app.cash.turbine.test
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Links in cards open other apps, which may not exist (ported from ankidroid/Anki-Android#22503). */
@RunWith(AndroidJUnit4::class)
class ReviewerViewModelLinkTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a link no app can open shows a message instead of crashing`() =
        runTest {
            addBasicNote("Front", "Back")
            // make startActivity throw ActivityNotFoundException, as Android does
            shadowOf(app).checkActivities(true)
            val viewModel = ReviewerViewModel(app)
            advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.effect.test {
                viewModel.onEvent(ReviewerEvent.LinkClicked("unhandled-scheme://example"))
                advanceUntilIdle()
                advanceRobolectricLooper()

                val snackbars = snackbarsIn(cancelAndConsumeRemainingEvents())
                assertThat(snackbars, equalTo(listOf(app.getString(R.string.activity_start_failed))))
            }
        }

    @Test
    fun `a link an app can open starts it`() =
        runTest {
            addBasicNote("Front", "Back")
            registerBrowser()
            shadowOf(app).checkActivities(true)
            val viewModel = ReviewerViewModel(app)
            advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.effect.test {
                viewModel.onEvent(ReviewerEvent.LinkClicked("https://example.org/"))
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertThat(snackbarsIn(cancelAndConsumeRemainingEvents()), empty())
            }
            val started = shadowOf(app).nextStartedActivity
            assertThat(started.action, equalTo(Intent.ACTION_VIEW))
            assertThat(started.data, equalTo("https://example.org/".toUri()))
        }

    @Test
    fun `a missing user action link shows the missing user action dialog instead of opening an external link`() =
        runTest {
            addBasicNote("Front", "Back")
            shadowOf(app).checkActivities(true)
            val viewModel = ReviewerViewModel(app)
            advanceUntilIdle()
            advanceRobolectricLooper()

            viewModel.effect.test {
                viewModel.onEvent(ReviewerEvent.LinkClicked("missing-user-action:4"))
                advanceUntilIdle()
                advanceRobolectricLooper()

                val events = cancelAndConsumeRemainingEvents()
                val dialogs =
                    events
                        .filterIsInstance<Event.Item<ReviewerEffect>>()
                        .mapNotNull { it.value as? ReviewerEffect.ShowMissingUserActionDialog }
                assertThat(dialogs, equalTo(listOf(ReviewerEffect.ShowMissingUserActionDialog("4"))))
                assertThat(snackbarsIn(events), empty())
            }
            assertThat(shadowOf(app).nextStartedActivity, nullValue())
        }

    private fun snackbarsIn(events: List<Event<ReviewerEffect>>): List<String> =
        events
            .filterIsInstance<Event.Item<ReviewerEffect>>()
            .mapNotNull { (it.value as? ReviewerEffect.ShowSnackbar)?.message }

    private fun registerBrowser() {
        val browser = ComponentName("com.ichi2.testutils.browser", "BrowserActivity")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(browser).exported = true
            addIntentFilterForActivity(
                browser,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("https")
                },
            )
        }
    }
}
