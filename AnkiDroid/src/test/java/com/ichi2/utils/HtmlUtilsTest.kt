// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.HtmlUtils.escape
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class HtmlUtilsTest {
    @Test
    fun japaneseIsNotEscaped() {
        assertThat(escape("飲む"), equalTo("飲む"))
    }

    @Test
    fun angleBraceIsEscaped() {
        assertThat(escape("<"), equalTo("&lt;"))
    }

    @Test
    fun ampersandIsEscaped() {
        assertThat(escape("&"), equalTo("&amp;"))
    }

    @Test
    fun newLineIsNotEscaped() {
        assertThat(escape("\n"), equalTo("\n"))
    }

    @Test
    fun returnIsNotEscaped() {
        assertThat(escape("\r"), equalTo("\r"))
    }
}
