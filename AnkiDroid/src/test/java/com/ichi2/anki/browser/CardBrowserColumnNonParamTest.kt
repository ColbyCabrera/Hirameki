// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals

/** @see CardBrowserColumn */
@RunWith(AndroidJUnit4::class)
class CardBrowserColumnNonParamTest : JvmTest() {
    @Test
    fun `all keys are documented`() {
        // meta test - the column keys aren't documented well
        // this ensures the columns are greppable in the code
        val ankiColumnKeys =
            col.backend
                .allBrowserColumns()
                .map { it.key }
                .sorted()
        val ourKeys = CardBrowserColumn.entries.map { it.ankiColumnKey }.sorted()

        assertContentEquals(ankiColumnKeys, ourKeys)
    }
}
