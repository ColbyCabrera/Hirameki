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
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the arabic-script font overlay actually reaches. A theme-level font never wins against a text appearance a
 * widget applies to its own TextView, so the overlay has to swap the typescale attributes those appearances come
 * from ([ThemeOverlay.Hirameki.ArabicScriptFont] in arabic-script-font-theme-overlay.xml).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class ArabicScriptFontOverlayTest {
    @Test
    fun `toolbar and action bar titles are drawn in vazirmatn`() {
        // material's Widget.Material3.Toolbar takes its titleTextAppearance from textAppearanceTitleLarge
        assertFontFamily(com.google.android.material.R.attr.textAppearanceTitleLarge)
    }

    @Test
    fun `the expanded settings title is drawn in vazirmatn`() {
        // settings_fragment.xml's collapsing toolbar takes its expanded title from textAppearanceHeadlineSmall
        assertFontFamily(com.google.android.material.R.attr.textAppearanceHeadlineSmall)
    }

    @Test
    fun `list rows and buttons are drawn in vazirmatn`() {
        assertFontFamily(com.google.android.material.R.attr.textAppearanceBodyLarge)
        assertFontFamily(com.google.android.material.R.attr.textAppearanceLabelLarge)
        assertFontFamily(com.google.android.material.R.attr.textAppearanceTitleMedium)
    }

    @Test
    fun `dialog messages and list row second lines are drawn in vazirmatn`() {
        // a material dialog's message resolves to textAppearanceBodyMedium (m3_comp_dialog_supporting_text_type),
        // and so does android:textAppearanceListItemSecondary
        assertFontFamily(com.google.android.material.R.attr.textAppearanceBodyMedium)
    }

    @Test
    fun `bottom navigation labels are drawn in vazirmatn`() {
        // material 1.14 takes itemTextAppearanceActive/Inactive from textAppearanceLabelMedium
        assertFontFamily(com.google.android.material.R.attr.textAppearanceLabelMedium)
    }

    @Test
    fun `card template editor tab labels are drawn in vazirmatn`() {
        // the tab label is the template name, which an arabic-script user writes in arabic script
        val arabic = themedContext(withOverlay = true)
        val appearance = arabic.appearanceOf(R.style.TabLayoutStyle, com.google.android.material.R.attr.tabTextAppearance)

        assertThat("android:fontFamily", arabic.fontFamilyOf(appearance, android.R.attr.fontFamily), equalTo<Any>(R.font.vazirmatn_family))
        assertThat("fontFamily", arabic.fontFamilyOf(appearance, appCompatFontFamily()), equalTo<Any>(R.font.vazirmatn_family))
    }

    @Test
    fun `card template editor tab labels keep their font in every other ui language`() {
        // naming the attribute instead of the style must not change the appearance outside the overlay
        val plain = themedContext(withOverlay = false)
        val appearance = plain.appearanceOf(R.style.TabLayoutStyle, com.google.android.material.R.attr.tabTextAppearance)

        assertThat(
            "the material label large appearance",
            appearance,
            equalTo(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge),
        )
    }

    @Test
    fun `a settings row is drawn in one font, title and summary`() {
        // the title takes android:textAppearanceListItem, which material3 maps to textAppearanceTitleMedium and the
        // overlay swaps. the summary takes android:textAppearanceSmall (PreferenceSummaryTextStyle in androidx
        // preference) -> TextAppearance.AppCompat.Small, which names no font at all, so the overlay's theme-level
        // family is what reaches it. the two must not drift apart, or a settings row is drawn in two fonts
        val arabic = themedContext(withOverlay = true)
        val plain = themedContext(withOverlay = false)

        assertThat(
            "the summary font matches the title font",
            arabic.preferenceRowFont(android.R.id.summary),
            equalTo(arabic.preferenceRowFont(android.R.id.title)),
        )
        assertThat(
            "the overlay reaches the summary",
            arabic.preferenceRowFont(android.R.id.summary),
            not(equalTo(plain.preferenceRowFont(android.R.id.summary))),
        )
    }

    @Test
    fun `the deck options toolbar title keeps its font in every other ui language`() {
        // pages/DeckOptions.kt hands this appearance to the toolbar in code. sans-serif is what it has always been
        // drawn in: appcompat reads the app-namespace fontFamily first, and the material parent sets it
        val plain = themedContext(withOverlay = false)
        val toolbar = MaterialToolbar(plain)
        toolbar.title = "deck options"
        toolbar.setTitleTextAppearance(plain, R.style.TextAppearance_Anki_PageToolbar_Expressive)

        assertThat(
            "family the title is drawn in",
            shadowOf(toolbar.titleView().typeface).fontDescription.familyName,
            equalTo("sans-serif"),
        )
    }

    @Test
    fun `the deck options toolbar title is drawn in vazirmatn`() {
        val arabic = themedContext(withOverlay = true)
        val style = R.style.TextAppearance_Anki_PageToolbar_Expressive

        assertThat("android:fontFamily", arabic.fontFamilyOf(style, android.R.attr.fontFamily), equalTo<Any>(R.font.vazirmatn_family))
        assertThat("fontFamily", arabic.fontFamilyOf(style, appCompatFontFamily()), equalTo<Any>(R.font.vazirmatn_family))
    }

    private fun MaterialToolbar.titleView(): TextView {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.text == title) return child
        }
        throw AssertionError("the toolbar has no title view")
    }

    /** both spellings of the attribute: appcompat's TextView prefers the app-namespace one over android's */
    private fun assertFontFamily(
        @AttrRes typescale: Int,
    ) {
        val arabic = themedContext(withOverlay = true)
        val style = arabic.styleOf(typescale)
        assertThat(
            "android:fontFamily of the text appearance",
            arabic.fontFamilyOf(style, android.R.attr.fontFamily),
            equalTo<Any>(R.font.vazirmatn_family),
        )
        assertThat(
            "fontFamily of the text appearance",
            arabic.fontFamilyOf(style, appCompatFontFamily()),
            equalTo<Any>(R.font.vazirmatn_family),
        )
    }

    /** a context themed the way [Themes.setTheme] themes an activity: the overlay layered over the app theme */
    private fun themedContext(withOverlay: Boolean): Context {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), Theme.LIGHT.resId)
        if (withOverlay) {
            context.theme // the base theme has to exist before the overlay is applied over it
            context.setTheme(R.style.ThemeOverlay_Hirameki_ArabicScriptFont)
        }
        return context
    }

    /** the style a theme attribute such as textAppearanceTitleLarge points at */
    @StyleRes
    private fun Context.styleOf(
        @AttrRes attr: Int,
    ): Int {
        val values = theme.obtainStyledAttributes(intArrayOf(attr))
        try {
            return values.getResourceId(0, 0)
        } finally {
            values.recycle()
        }
    }

    /** the font a settings row's [viewId] is drawn in, from androidx preference's own row layout */
    private fun Context.preferenceRowFont(
        @IdRes viewId: Int,
    ): String {
        val row = LayoutInflater.from(this).inflate(androidx.preference.R.layout.preference_material, null)
        return shadowOf(row.findViewById<TextView>(viewId).typeface).fontDescription.familyName
    }

    /** the text appearance a widget style names, such as TabLayoutStyle's tabTextAppearance */
    @StyleRes
    private fun Context.appearanceOf(
        @StyleRes style: Int,
        @AttrRes attr: Int,
    ): Int {
        val values = obtainStyledAttributes(style, intArrayOf(attr))
        try {
            return values.getResourceId(0, 0)
        } finally {
            values.recycle()
        }
    }

    /** the font a text appearance names: a font resource id, or the family name when it names one */
    private fun Context.fontFamilyOf(
        @StyleRes style: Int,
        @AttrRes attr: Int,
    ): Any? {
        val values = obtainStyledAttributes(style, intArrayOf(attr))
        try {
            val font = values.getResourceId(0, 0)
            return if (font != 0 && resources.getResourceTypeName(font) == "font") font else values.getString(0)
        } finally {
            values.recycle()
        }
    }

    private fun appCompatFontFamily(): Int = androidx.appcompat.R.attr.fontFamily
}
