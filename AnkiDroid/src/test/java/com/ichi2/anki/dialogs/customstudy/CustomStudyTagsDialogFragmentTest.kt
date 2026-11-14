// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.customstudy

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyTagsDialogFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.testutils.AnkiFragmentScenario
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomStudyTagsDialogFragmentTest : RobolectricTest() {
    /**
     * Regression test.
     *
     * `CustomStudyTagsViewModel` has a second constructor parameter with a default value, which
     * means the compiler does not generate the `SavedStateHandle`-only constructor that
     * `by viewModels()` looks for. Without `@JvmOverloads` this threw
     * "Cannot create an instance of class ...CustomStudyTagsViewModel" the moment the dialog
     * composed, so opening "Custom study -> Study by card state or tag" crashed.
     *
     * The assertions are deliberately thin: the point is that launching the dialog does not throw.
     */
    @Test
    fun `dialog launches and builds its view model`() {
        AnkiFragmentScenario
            .launch(CustomStudyTagsDialogFragment::class.java, arguments())
            .use { scenario ->
                scenario.onFragment { fragment ->
                    assertThat(fragment.dialog, notNullValue())
                    assertThat(fragment.requireView(), notNullValue())
                }
            }
    }

    @Test
    fun `tag limit notice keeps only the first sentence of the backend explanation`() {
        val message = maxTagsMessage()

        // Whitespace runs are collapsed so the sentence does not read as broken layout.
        assertThat("no double spaces", message.contains("  "), equalTo(false))
        // Nothing follows the first full stop.
        val firstStop = message.indexOf('.')
        assertThat("message has a sentence", firstStop, not(equalTo(-1)))
        assertThat(
            "text after the first sentence was dropped",
            message.indexOf('.', firstStop + 1),
            equalTo(-1),
        )
    }

    private fun arguments() =
        Bundle().apply {
            putLong(ARG_DECK_ID, DEFAULT_DECK_ID)
        }
}
