/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.themes

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class ArabicScriptFontTest : RobolectricTest() {
    private fun configFor(languageTag: String) =
        Configuration().apply {
            setLocales(LocaleList.forLanguageTags(languageTag))
        }

    private fun contextFor(languageTag: String): Context {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        return baseContext.createConfigurationContext(configFor(languageTag))
    }

    private fun setSwitch(on: Boolean) {
        Prefs.putBoolean(R.string.arabic_script_font_key, on)
    }

    override fun tearDown() {
        super.tearDown()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun `arabic-script ui languages use vazirmatn when the switch is on`() {
        setSwitch(true)
        for (language in listOf("ar", "fa", "ur", "ckb", "ps", "ug", "sd", "az-Arab")) {
            assertThat(
                language,
                ArabicScriptFont.appliesToUi(configFor(language)),
                equalTo(true),
            )
            assertThat(
                "$language context",
                ArabicScriptFont.appliesToUi(contextFor(language)),
                equalTo(true),
            )
        }
    }

    @Test
    fun `latin ui languages keep the system font`() {
        setSwitch(true)
        for (language in listOf("en", "tr", "id", "az-Latn", "zh", "ja")) {
            assertThat(
                language,
                ArabicScriptFont.appliesToUi(configFor(language)),
                equalTo(false),
            )
            assertThat(
                "$language context",
                ArabicScriptFont.appliesToUi(contextFor(language)),
                equalTo(false),
            )
        }
    }

    @Test
    fun `non-arabic rtl languages keep the system font`() {
        setSwitch(true)
        // Hebrew is RTL but uses Hebr script, not Arab script
        assertThat(
            "he",
            ArabicScriptFont.appliesToUi(configFor("he")),
            equalTo(false),
        )
    }

    @Test
    fun `switch off keeps the system font even for arabic-script languages`() {
        setSwitch(false)
        assertThat(
            ArabicScriptFont.appliesToUi(configFor("fa")),
            equalTo(false),
        )
        assertThat(
            ArabicScriptFont.appliesToUi(contextFor("fa")),
            equalTo(false),
        )
    }

    @Test
    fun `currentLocaleUsesArabicScript detects arabic script independently of switch`() {
        setSwitch(false)
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(configFor("fa")),
            equalTo(true),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(contextFor("fa")),
            equalTo(true),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(configFor("en")),
            equalTo(false),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(contextFor("en")),
            equalTo(false),
        )
    }

    @Test
    fun `app locale overrides configuration locale`() {
        setSwitch(true)
        val controller = Robolectric.buildActivity(DeckPicker::class.java).setup()
        saveControllerForCleanup(controller)
        // System / config is English, but user selected Arabic in app settings
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        assertThat(
            ArabicScriptFont.appliesToUi(configFor("en")),
            equalTo(true),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(configFor("en")),
            equalTo(true),
        )

        // System / config is Arabic, but user selected English in app settings
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        assertThat(
            ArabicScriptFont.appliesToUi(configFor("ar")),
            equalTo(false),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(configFor("ar")),
            equalTo(false),
        )
    }

    @Test
    fun `empty configuration locales do not crash and keep system font`() {
        setSwitch(true)
        val emptyConfig =
            Configuration().apply {
                setLocales(LocaleList.getEmptyLocaleList())
            }
        assertThat(
            ArabicScriptFont.appliesToUi(emptyConfig),
            equalTo(false),
        )
        assertThat(
            ArabicScriptFont.currentLocaleUsesArabicScript(emptyConfig),
            equalTo(false),
        )
    }
}
