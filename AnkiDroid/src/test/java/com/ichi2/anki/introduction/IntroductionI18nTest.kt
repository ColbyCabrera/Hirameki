// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.introduction

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.LanguageUtil.getStringByLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class IntroductionI18nTest : RobolectricTest() {
    @Test
    fun stringsEnglish() {
        val ctx = targetContext
        val title = ctx.getStringByLocale(R.string.intro_title_before_continuing, Locale.ENGLISH)
        assertEquals("Welcome to Hirameki!", title)

        val donation = ctx.getStringByLocale(R.string.intro_fork_disclaimer_1, Locale.ENGLISH)
        assertEquals(
            "Hirameki is a customized, open-source version of AnkiDroid. If you love this app, please consider supporting the original AnkiDroid team and the creator of AnkiWeb, as this app is built on their incredible work.",
            donation,
        )

        val contact = ctx.getStringByLocale(R.string.intro_fork_disclaimer_2, Locale.ENGLISH)
        assertEquals(
            "Please direct any bug reports or feedback about this version to us directly, rather than the AnkiDroid team. Happy memorizing!",
            contact,
        )

        val donate = ctx.getStringByLocale(R.string.donate, Locale.ENGLISH)
        assertEquals("Donate", donate)
    }

    @Test
    fun stringsJapanese() {
        val ctx = targetContext
        val titleJa = ctx.getStringByLocale(R.string.intro_title_before_continuing, Locale.JAPANESE)
        assertEquals("続ける前に！", titleJa)

        val donationJa = ctx.getStringByLocale(R.string.intro_fork_disclaimer_1, Locale.JAPANESE)
        assertEquals(
            "このアプリはAnkiDroidのフォークです。AnkiDroidチームの活動を支援するために寄付をご検討ください。Ankiの作者はAnkiWeb同期の使用を快く許可してくれました。作者を支援したい場合は、AnkiのiPhone版の購入をご検討ください。",
            donationJa,
        )

        val contactJa = ctx.getStringByLocale(R.string.intro_fork_disclaimer_2, Locale.JAPANESE)
        assertEquals("このバージョンに問題がある場合は、AnkiDroidチームではなく私にご連絡ください。学習をお楽しみください！", contactJa)

        val donateJa = ctx.getStringByLocale(R.string.donate, Locale.JAPANESE)
        assertEquals("AnkiDroidに寄付する", donateJa)
    }
}
