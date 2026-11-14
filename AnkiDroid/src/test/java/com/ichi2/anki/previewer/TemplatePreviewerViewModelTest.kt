// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerViewModelTest : JvmTest() {
    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    fun `getCurrentTabIndex returns the correct tab if the first cloze isn't 1 and ord isn't 0`() =
        runClozeTest(ord = 6, fields = mutableListOf("{{c7::foo}} {{c4::bar}} {{c9::ha}}")) {
            val expectedTab = 1 // 0 will be c4 (ord 3), 1: c7 (ord 6), 2: c9 (ord 8)
            assertThat(getCurrentTabIndex(), equalTo(expectedTab))
        }

    @Test
    fun `correct cloze ord is shown for tab`() =
        runClozeTest(ord = 8, fields = mutableListOf("{{c7::foo}} {{c4::bar}} {{c9::ha}}")) {
            onTabSelected(0) // 0 will be c4 (ord 3), 1: c7 (ord 6), 2: c9 (ord 8)
            assertThat(ordFlow.value, equalTo(3))
        }

    @Test
    fun `empty front field detected correctly for tab badge`() =
        runOptionalReversedTest(
            fields =
                mutableListOf(
                    "we have two normal fields",
                    "and purposefully leave the third blank",
                    "",
                ),
        ) {
            onPageFinished(false)
            assertThat(this.cardsWithEmptyFronts!!.await()[0], equalTo(false))
            assertThat(this.cardsWithEmptyFronts.await()[1], equalTo(true))
        }

    private fun runClozeTest(
        ord: Int = 0,
        fields: MutableList<String>? = null,
        block: suspend TemplatePreviewerViewModel.() -> Unit,
    ) = runClozeTest(ord, tempDirectory, fields, block)

    private fun runOptionalReversedTest(
        ord: Int = 0,
        fields: MutableList<String>? = null,
        block: suspend TemplatePreviewerViewModel.() -> Unit,
    ) = runTest {
        val notetype = col.notetypes.byName("Basic (optional reversed card)")!!
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = NotetypeFile(tempDirectory.root, notetype),
                fields = fields ?: mutableListOf("question text", "answer text", "y"),
                tags = mutableListOf(),
                ord = ord,
            )
        val viewModel = TemplatePreviewerViewModel(arguments)
        block(viewModel)
    }
}

fun AnkiTest.runClozeTest(
    ord: Int = 0,
    tempDirectory: TemporaryFolder,
    fields: MutableList<String>? = null,
    block: suspend TemplatePreviewerViewModel.() -> Unit,
) = runTest {
    val notetype = col.notetypes.byName("Cloze")!!
    val arguments =
        TemplatePreviewerArguments(
            notetypeFile = NotetypeFile(tempDirectory.root, notetype),
            fields = fields ?: mutableListOf("{{c1::foo}} {{c2::bar}}", "anki"),
            tags = mutableListOf(),
            ord = ord,
        )
    val viewModel = TemplatePreviewerViewModel(arguments)
    block(viewModel)
}
