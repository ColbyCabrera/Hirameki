// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class PreviewerViewModelTest {
    @Test
    fun `type answer fields are removed in questions`() {
        assertThat(
            TypeAnswer.removeTags("creu [[type:leu]]"),
            equalTo("creu "),
        )
    }
}
