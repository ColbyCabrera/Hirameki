// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerRefresh
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Tests [AbstractFlashcardViewer.refreshIfRequired]. Command routing is tested in `ReviewerCommandRoutingTest`. */
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    companion object {
        val updateCard: ViewerRefresh = ViewerRefresh(queues = true, note = true, card = true)
    }

    @Test
    fun testRefreshIfRequired() {
        // Create an ActivityController instance for AbstractFlashcardViewer
        Robolectric.buildActivity(AbstractFlashcardViewerTest.NonAbstractFlashcardViewer::class.java).use { controller ->
            // Case 1: Activity is resuming, refreshRequired is not null
            with(controller.create().start().get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = true)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 2: Activity is not resuming, lifecycle is at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 3: Activity is not resuming, lifecycle is not at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.pause() // Ensure the activity is paused before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is not set to null
                assertThat(refreshRequired, notNullValue())
            }
        }
    }
}
