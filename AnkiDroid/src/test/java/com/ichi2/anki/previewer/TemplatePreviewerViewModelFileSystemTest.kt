// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerViewModelFileSystemTest : RobolectricTest() {
    // TODO(PERF): Needs investigation why this is necessary
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    fun `card ords are changed`() {
        runClozeTest(tempDirectory = tempDirectory, fields = mutableListOf("{{c1::one}} {{c2::bar}}")) {
            onPageFinished(false)
            val ord1 = currentCard.await().ord
            onTabSelected(1)
            val ord2 = currentCard.await().ord
            assertNotEquals(ord1, ord2)
        }
    }
}
