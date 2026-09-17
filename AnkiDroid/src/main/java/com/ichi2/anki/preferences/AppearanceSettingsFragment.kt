/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.updateCurrentTheme

class AppearanceSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_appearance
    override val analyticsScreenNameConstant: String
        get() = "prefs.appearance"

    override fun initSubscreen() {
        val appThemePref = requirePreference<ListPreference>(R.string.app_theme_key)

        appThemePref.setOnPreferenceChangeListener { newValue ->
            // Only restart if theme has changed
            if (newValue != appThemePref.value) {
                val previousThemeId = Themes.currentTheme.id
                appThemePref.value = newValue
                updateCurrentTheme(requireContext())

                if (previousThemeId != Themes.currentTheme.id) {
                    ActivityCompat.recreate(requireActivity())
                }
            }
        }

        // the ui font is picked when an activity is created (theme overlay, compose typography), so the switch shows
        // after a recreate: settings recreates itself here, and every activity behind it recreates when it starts
        // again (AnkiActivity.onStart)
        requirePreference<SwitchPreferenceCompat>(R.string.arabic_script_font_key).apply {
            setOnPreferenceChangeListener { newValue ->
                isChecked = newValue
                ActivityCompat.recreate(requireActivity())
            }
        }

        // Show estimate time
        // Represents the collection pref "estTime": i.e.
        // whether the buttons should indicate the duration of the interval if we click on them.
        requirePreference<SwitchPreferenceCompat>(R.string.show_estimates_preference).apply {
            launchCatchingTask { isChecked = CollectionPreferences.getShowIntervalOnButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowIntervalsOnButtons(newValue) }
            }
        }
        // Show progress
        // Represents the collection pref "dueCounts": i.e.
        // whether the remaining number of cards should be shown.
        requirePreference<SwitchPreferenceCompat>(R.string.show_progress_preference).apply {
            launchCatchingTask { isChecked = CollectionPreferences.getShowRemainingDueCounts() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowRemainingDueCounts(newValue) }
            }
        }

        // Show play buttons on cards with audio
        // Note: Stored inverted in the collection as HIDE_AUDIO_PLAY_BUTTONS
        requirePreference<SwitchPreferenceCompat>(R.string.show_audio_play_buttons_key).apply {
            title = CollectionManager.TR.preferencesShowPlayButtonsOnCardsWith()
            launchCatchingTask { isChecked = !CollectionPreferences.getHidePlayAudioButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setHideAudioPlayButtons(!newValue) }
            }
        }

        setupNewStudyScreenSettings()
    }

    private fun setupNewStudyScreenSettings() {
        // New study screen is always enabled, so always hide legacy settings
        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.study_screen_category_key,
                R.string.custom_buttons_link_preference,
                R.string.fullscreen_mode_preference,
                R.string.center_vertically_preference,
                R.string.show_estimates_preference,
                R.string.answer_buttons_position_preference,
                R.string.show_topbar_preference,
                R.string.show_eta_preference,
                R.string.show_audio_play_buttons_key,
                R.string.show_deck_title_key,
            )
    }
}
