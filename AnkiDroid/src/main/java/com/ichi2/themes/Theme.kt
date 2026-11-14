// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.themes

import androidx.annotation.StyleRes
import com.ichi2.anki.R

enum class Theme(
    val id: String,
    @StyleRes val resId: Int,
    val isNightMode: Boolean,
) {
    // IDs correspond to @array/app_theme_values in res/values/constants.xml.
    // This enum defines LIGHT ("1") and DARK ("2") only.
    // The preference value "0" (Follow system) is not an enum constant; it is handled
    // by Themes.updateCurrentTheme() which selects LIGHT or DARK based on system mode.
    // All themes use dynamic theming via DynamicColors.applyToActivityIfAvailable().
    LIGHT("1", R.style.Theme_Dynamic_Light, false),
    DARK("2", R.style.Theme_Dynamic_Dark, true),
    ;

    companion object {
        val fallback: Theme
            get() = LIGHT

        fun ofId(id: String): Theme = entries.find { it.id == id } ?: fallback
    }
}
