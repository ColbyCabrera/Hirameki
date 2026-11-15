// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.utils.StringUtils.toTitleCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test

/** Test of [toTitleCase] */
class StringUtilsTest {
    @Test
    fun toTitleCase_null_is_null() {
        assertThat(toTitleCase(null), nullValue())
    }

    @Test
    fun toTitleCase_single_letter() {
        assertThat(toTitleCase("a"), equalTo("A"))
    }

    @Test
    fun toTitleCase_single_upper_letter() {
        assertThat(toTitleCase("A"), equalTo("A"))
    }

    @Test
    fun toTitleCase_string() {
        assertThat(toTitleCase("aB"), equalTo("Ab"))
    }
}
