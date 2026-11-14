// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.preferences

import androidx.preference.Preference
import com.ichi2.anki.R

/**
 * Fragment with preferences related to notifications
 */
class AccessibilitySettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_accessibility
    override val analyticsScreenNameConstant: String
        get() = "prefs.accessibility"

    override fun initSubscreen() {
        setupNewStudyScreenSettings()
    }

    private fun setupNewStudyScreenSettings() {
        // New study screen is always enabled, so always show new settings and hide legacy ones
        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.image_zoom_preference,
                R.string.show_large_answer_buttons_preference,
                R.string.pref_card_minimal_click_time,
            )
    }
}
