// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.preferences

import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class CustomButtonsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_custom_buttons
    override val analyticsScreenNameConstant: String
        get() = "prefs.custom_buttons"

    override fun initSubscreen() {
        // Reset toolbar button customizations
        val resetCustomButtons = requirePreference<Preference>("reset_custom_buttons")
        resetCustomButtons.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext()).show {
                    title(R.string.reset_settings_to_default)
                    positiveButton(R.string.reset) {
                        // Reset the settings to default
                        requireContext().sharedPrefs().edit {
                            allKeys().forEach {
                                remove(it)
                            }
                        }
                        // #9263: refresh the screen to display the changes
                        preferenceScreen.removeAll()
                        addPreferencesFromResource(preferenceResource)
                        initSubscreen()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
                true
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun allKeys(): HashSet<String> = preferenceScreen.allPreferences().mapTo(hashSetOf()) { it.key }
}
