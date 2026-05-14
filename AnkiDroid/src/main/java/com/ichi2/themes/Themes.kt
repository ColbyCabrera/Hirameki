/*
 Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.ui.AppCompatPreferenceActivity
import timber.log.Timber

/**
 * Helper methods to configure things related to AnkiDroid's themes
 */
object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%

    const val FOLLOW_SYSTEM_MODE = "0"
    private const val APP_THEME_KEY = "appTheme"

    var currentTheme: Theme = Theme.fallback

    fun setTheme(context: Context) {
        updateCurrentTheme(context)
        Timber.i("Setting theme to %s", currentTheme.name)
        context.setTheme(currentTheme.resId)
        // Apply dynamic colors from wallpaper on top of the base theme (Android 12+)
        // This allows all themes to use wallpaper-based colors while respecting light/dark mode
        (context as? Activity)?.let { DynamicColors.applyToActivityIfAvailable(it) }
    }

    fun setLegacyActionBar(context: Context) {
        context.setTheme(R.style.ThemeOverlay_LegacyActionBar)
    }

    /**
     * Updates [currentTheme] value based on preferences.
     * If `Follow system` is selected, it's updated to Light or Dark
     * according to system's current mode.
     * Otherwise, updates to the selected theme (Light or Dark).
     */
    fun updateCurrentTheme(context: Context) {
        // AppCompatPreferenceActivity's sharedPreferences is initialized
        // after the time when the theme should be set
        // TODO (#5019): always use the context as the parameter for getSharedPrefs
        val prefs =
            if (context is AppCompatPreferenceActivity<*>) {
                AnkiDroidApp.instance.sharedPrefs()
            } else {
                context.sharedPrefs()
            }

        val selectedTheme = prefs.getString(APP_THEME_KEY, FOLLOW_SYSTEM_MODE) ?: FOLLOW_SYSTEM_MODE

        currentTheme = when (selectedTheme) {
            Theme.LIGHT.id -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Theme.LIGHT
            }
            Theme.DARK.id -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Theme.DARK
            }
            else -> {
                // Handles FOLLOW_SYSTEM_MODE ("0") and any legacy preference values
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                if (systemIsInNightMode(context)) Theme.DARK else Theme.LIGHT
            }
        }
    }

    /**
     * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
     */
    fun disableXiaomiForceDarkMode(context: Context) {
        // Setting a theme is an additive operation, so this adds a single property.
        context.setTheme(R.style.ThemeOverlay_Xiaomi)
    }

    fun getResFromAttr(
        context: Context,
        resAttr: Int,
    ): Int {
        val attrs = intArrayOf(resAttr)
        return getResFromAttr(context, attrs)[0]
    }

    /**
     * NOTE: dangerous function, it mutates the input array and returns it!
     */
    fun getResFromAttr(
        context: Context,
        attrs: IntArray,
    ): IntArray {
        context.withStyledAttributes(attrs = attrs) {
            for (i in attrs.indices) {
                attrs[i] = getResourceId(i, 0)
            }
        }
        return attrs
    }

    @JvmStatic // tests failed when removing, maybe try later
    @ColorInt
    fun getColorFromAttr(context: Context, attr: Int): Int = MaterialColors.getColor(context, attr, 0)

    /**
     * NOTE: dangerous function, it mutates the input array and returns it!
     */
    @JvmStatic // tests failed when removing, maybe try later
    @ColorInt
    fun getColorsFromAttrs(context: Context, attrs: IntArray): IntArray {
        for (i in attrs.indices) {
            attrs[i] = getColorFromAttr(context, attrs[i])
        }
        return attrs
    }



    fun systemIsInNightMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}

@Suppress("deprecation", "API35 properly handle edge-to-edge")
fun FragmentActivity.setTransparentStatusBar() {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
        !Themes.currentTheme.isNightMode
}

fun FragmentActivity.setTransparentBackground() {
    window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
}
