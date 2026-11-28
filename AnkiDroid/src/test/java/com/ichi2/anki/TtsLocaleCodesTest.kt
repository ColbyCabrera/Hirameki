// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <48384865+criticalAY@users.noreply.github.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TtsLocaleCodesTest {
    @Test
    fun `obsolete language codes are replaced`() {
        assertThat("iw_IL".replaceObsoleteLanguageCode(), equalTo("he_IL"))
        assertThat("in_ID".replaceObsoleteLanguageCode(), equalTo("id_ID"))
        assertThat("ji".replaceObsoleteLanguageCode(), equalTo("yi"))
    }

    @Test
    fun `current language codes are unchanged`() {
        assertThat("he_IL".replaceObsoleteLanguageCode(), equalTo("he_IL"))
        assertThat("en_IN".replaceObsoleteLanguageCode(), equalTo("en_IN"))
        assertThat("ind_ID".replaceObsoleteLanguageCode(), equalTo("ind_ID"))
    }

    @Test
    fun `toAnkiTwoLetterCode uses current language codes`() {
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("he-IL")), equalTo("he_IL"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("id-ID")), equalTo("id_ID"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("yi")), equalTo("yi"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("iw-IL")), equalTo("he_IL"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("in-ID")), equalTo("id_ID"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("ji")), equalTo("yi"))
    }
}
