/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.themes

import android.content.res.Configuration
import android.icu.util.ULocale
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.settings.Prefs
import java.util.Locale

/**
 * where vazirmatn replaces android's default arabic-script font (issue #139).
 *
 * android draws arabic, persian, urdu and kurdish with noto naskh arabic, a print face shipped in regular and bold
 * only, and none of the app's fonts (google sans flex, google sans rounded, roboto flex, the cards' roboto) has an
 * arabic glyph, so every arabic-script string fell through to it.
 *
 * ui: an arabic-script ui language switches the whole ui font to vazirmatn, whose latin is roboto's design. a
 * per-glyph fallback (google sans for latin, vazirmatn for arabic) cannot be written as an xml font-family or a
 * compose FontFamily, and roboto flex is a downloadable font the app never holds as a file to build a
 * Typeface.CustomFallbackBuilder chain from.
 *
 * cards: vazirmatn joins the hirameki card font stack after roboto, limited to arabic-script code points, in any
 * ui language.
 */
object ArabicScriptFont {
    /** same-origin path the card page loads the font from; com.ichi2.anki.ViewerResourceHandler answers it */
    const val CARD_FONT_PATH = "/_hirameki/fonts/vazirmatn.ttf"

    /** private family name, so a deck's own "Vazirmatn" @font-face is never merged into this one */
    const val CARD_FONT_FAMILY = "Hirameki Arabic"

    /**
     * must sit in an inline <style> of the card page: the root-relative url then resolves against the page's
     * http://127.0.0.1 origin. from a file:///android_asset stylesheet it would resolve to file://, and webview
     * refuses cors requests (every @font-face load is one) to file:// from an http page.
     * unicode-range is google fonts' arabic subset (bmp part, small gaps merged): the font is fetched only for a card
     * that has one of these characters, and latin never reaches it.
     * font-display block: swap would draw naskh first and switch fonts on every card; the file is local and fast.
     */
    val CARD_FONT_FACE =
        """
        @font-face {
            font-family: "$CARD_FONT_FAMILY";
            src: url("$CARD_FONT_PATH") format("truetype");
            font-weight: 100 900;
            font-style: normal;
            font-display: block;
            unicode-range: U+0600-06FF, U+0750-077F, U+0870-08FF, U+200C-200E, U+2010-2011, U+204F, U+2E41, U+FB50-FDFF, U+FE70-FEFC;
        }
        """.trimIndent()

    /**
     * whether views and compose draw the ui in vazirmatn: the ui language is written in arabic script and the switch
     * is on.
     *
     * the locale is checked first, so the other ui languages never read the preference here. false while the app is
     * not initialized: an activity started after a restore from backup still sets its theme
     * (AppLoadedFromBackupWorkaround.showedActivityFailedScreen) before it shows a toast and finishes, and [Prefs]
     * needs the app instance, so reading it there would crash that activity instead.
     */
    fun appliesToUi(configuration: Configuration): Boolean =
        configuration.locales.get(0)?.usesArabicScript() == true &&
            AnkiDroidApp.isInitialized &&
            Prefs.useArabicScriptFont

    /**
     * the script from cldr likely subtags ("fa" -> fa_Arab_IR), so every arabic-script language counts without a
     * hand-kept list
     */
    private fun Locale.usesArabicScript(): Boolean = ULocale.addLikelySubtags(ULocale.forLocale(this)).script == "Arab"
}
