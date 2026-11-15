// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.preferences

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.ui.preferences.screens.BackupLimitsPresenter

/**
 * Fragment with preferences related to backup.
 */
class BackupLimitsSettingsFragment : SettingsFragment() {
    init {
        BackupLimitsPresenter(this).also { it.observeLifecycle() }
    }

    override val preferenceResource: Int
        get() = R.xml.preferences_backup_limits

    override val analyticsScreenNameConstant: String
        get() = "prefs.backup_limits"

    override fun initSubscreen() {
        // initialization handled by BackupLimitsPresenter
    }

    override fun onStart() {
        super.onStart()
        requireActivity().title = CollectionManager.TR.preferencesBackups()
    }
}
