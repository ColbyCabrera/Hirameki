// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.themes

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ThemeTest {
    @ParameterizedTest
    @EnumSource(value = Theme::class)
    fun test_ofId_returns_theme(theme: Theme) {
        assertThat(Theme.ofId(theme.id), equalTo(theme))
    }

    @Test
    fun test_ofId_returns_fallback_if_id_is_invalid() {
        assertThat(Theme.ofId("999"), equalTo(Theme.fallback))
    }
}
