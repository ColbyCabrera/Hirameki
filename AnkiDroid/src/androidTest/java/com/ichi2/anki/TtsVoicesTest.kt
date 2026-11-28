// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TtsVoicesTest : InstrumentedTest() {
    @Test
    fun normalize() {
        fun assertEqual(
            l: Locale,
            str: String,
        ) {
            val normalized = AndroidTtsVoice.normalize(l)
            assertThat(normalized.toLanguageTag(), equalTo(str))
        }

        assertEqual(Locale.forLanguageTag("en" + '-' + "GB"), "en-GB")
        assertEqual(Locale.forLanguageTag("es" + '-' + "MX"), "es-MX")
        // "spa" is an invalid language and historically was remapped from that very old ISO code
        // to "es" by the deprecated Locale constructor. It is discarded in modern times, but
        // the country still comes back as the "language" for us for display
        assertEqual(Locale.forLanguageTag("spa" + '-' + "MEX"), "mex")
        assertEqual(Locale.forLanguageTag("fil" + '-' + "PH"), "fil-PH")
        // TBC
        assertEqual(Locale.forLanguageTag("ar" + '-' + ""), "ar")
    }

    @Test
    fun toAnkiTwoLetterCodeUsesCurrentLanguageCodes() {
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("he-IL")), equalTo("he_IL"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("id-ID")), equalTo("id_ID"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("yi")), equalTo("yi"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("iw-IL")), equalTo("he_IL"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("in-ID")), equalTo("id_ID"))
        assertThat(AndroidTtsVoice.toAnkiTwoLetterCode(Locale.forLanguageTag("ji")), equalTo("yi"))
    }
}
