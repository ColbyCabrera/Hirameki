/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
        assertEquals("Hirameki is a customized, open-source version of AnkiDroid. If you love this app, please consider supporting the original AnkiDroid team and the creator of AnkiWeb, as this app is built on their incredible work.", donation)

        val contact = ctx.getStringByLocale(R.string.intro_fork_disclaimer_2, Locale.ENGLISH)
        assertEquals("Please direct any bug reports or feedback about this version to us directly, rather than the AnkiDroid team. Happy memorizing!", contact)

        val donate = ctx.getStringByLocale(R.string.donate, Locale.ENGLISH)
        assertEquals("Donate", donate)
    }

    @Test
    fun stringsJapanese() {
        val ctx = targetContext
        val titleJa = ctx.getStringByLocale(R.string.intro_title_before_continuing, Locale.JAPANESE)
        assertEquals("続ける前に！", titleJa)

        val donationJa = ctx.getStringByLocale(R.string.intro_fork_disclaimer_1, Locale.JAPANESE)
        assertEquals("このアプリはAnkiDroidのフォークです。AnkiDroidチームの活動を支援するために寄付をご検討ください。Ankiの作者はAnkiWeb同期の使用を快く許可してくれました。作者を支援したい場合は、AnkiのiPhone版の購入をご検討ください。", donationJa)

        val contactJa = ctx.getStringByLocale(R.string.intro_fork_disclaimer_2, Locale.JAPANESE)
        assertEquals("このバージョンに問題がある場合は、AnkiDroidチームではなく私にご連絡ください。学習をお楽しみください！", contactJa)

        val donateJa = ctx.getStringByLocale(R.string.donate, Locale.JAPANESE)
        assertEquals("AnkiDroidに寄付する", donateJa)
    }
}
