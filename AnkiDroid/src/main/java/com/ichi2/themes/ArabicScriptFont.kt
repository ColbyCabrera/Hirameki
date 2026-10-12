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

import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import androidx.appcompat.app.AppCompatDelegate
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.settings.Prefs
import java.util.Locale

/**
 * Where Vazirmatn replaces Android's default Arabic-script font (issue #139).
 *
 * Android draws Arabic, Persian, Urdu, and Kurdish with Noto Naskh Arabic, a print face shipped in regular and bold
 * only, and none of the app's fonts (Google Sans Flex, Google Sans Rounded, Roboto Flex) has an Arabic glyph, so
 * every Arabic-script string falls through to it.
 *
 * An Arabic-script UI language switches the whole UI font to Vazirmatn, whose Latin glyphs follow Roboto's design.
 * A per-glyph fallback (Google Sans for Latin, Vazirmatn for Arabic) cannot be written as an XML font-family or a
 * Compose FontFamily, and Roboto Flex is a downloadable font the app never holds as a file to build a
 * Typeface.CustomFallbackBuilder chain from.
 */
object ArabicScriptFont {
    /**
     * Whether the active UI locale (from per-app locale selection, or falling back to [configuration]) uses
     * Arabic script.
     */
    fun currentLocaleUsesArabicScript(configuration: Configuration): Boolean {
        val locale =
            AppCompatDelegate.getApplicationLocales().get(0)
                ?: configuration.locales.get(0)
        return locale?.usesArabicScript() == true
    }

    /**
     * Whether the active UI locale (from per-app locale selection, or falling back to [context]'s configuration) uses
     * Arabic script.
     */
    fun currentLocaleUsesArabicScript(context: Context): Boolean = currentLocaleUsesArabicScript(context.resources.configuration)

    /**
     * Whether Views and Compose draw the UI in Vazirmatn: the UI language is written in Arabic script and the switch
     * is enabled.
     *
     * The selected app locale wins over the passed configuration: on some paths (a PreferenceActivity reaching
     * Themes.setTheme before its delegate runs) the context can still carry the system locale, so an Arabic-script
     * app locale with a Latin system locale would otherwise skip the overlay. Empty when the app follows the system.
     *
     * The locale is checked first, so the other UI languages never read the preference here. False while the app is
     * not initialized: an activity started after a restore from backup still sets its theme
     * (AppLoadedFromBackupWorkaround.showedActivityFailedScreen) before it shows a toast and finishes, and [Prefs]
     * needs the app instance, so reading it there would crash that activity instead.
     */
    fun appliesToUi(configuration: Configuration): Boolean =
        currentLocaleUsesArabicScript(configuration) &&
            AnkiDroidApp.isInitialized &&
            Prefs.useArabicScriptFont

    /**
     * Whether Views and Compose draw the UI in Vazirmatn: the UI language is written in Arabic script and the switch
     * is enabled.
     */
    fun appliesToUi(context: Context): Boolean = appliesToUi(context.resources.configuration)

    /**
     * Resolves the script from CLDR likely subtags (e.g. "fa" -> fa_Arab_IR), so every Arabic-script language
     * matches without maintaining a manual list.
     */
    private fun Locale.usesArabicScript(): Boolean = ULocale.addLikelySubtags(ULocale.forLocale(this)).script == "Arab"
}
