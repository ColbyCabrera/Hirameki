// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.model

import androidx.annotation.CheckResult
import com.ichi2.themes.Themes.currentTheme

class WhiteboardPenColor(
    val lightPenColor: Int?,
    val darkPenColor: Int?,
) {
    fun fromPreferences(): Int? =
        if (currentTheme.isNightMode) {
            darkPenColor
        } else {
            lightPenColor
        }

    companion object {
        @get:CheckResult
        val default: WhiteboardPenColor
            get() = WhiteboardPenColor(null, null)
    }
}
