// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.preferences

import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.utils.show

/**
 * Fragment with preferences related to syncing
 */
class SyncSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_sync
    override val analyticsScreenNameConstant: String
        get() = "prefs.sync"

    override fun initSubscreen() {
        // AnkiWeb Account
        updateSyncAccountSummary()

        // Enable/disable one-way sync if the user is logged in
        updateOneWaySyncEnabledState()

        // Configure one-way sync option
        requirePreference<Preference>(R.string.one_way_sync_key).apply {
            setSummary(TR.preferencesOnNextSyncForceChangesIn())
            setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext()).show {
                    setTitle(R.string.one_way_sync_title)
                    setMessage(TR.preferencesOnNextSyncForceChangesIn())
                    setPositiveButton(R.string.dialog_ok) { _, _ ->
                        launchCatchingTask {
                            withCol { modSchemaNoCheck() }
                            showSnackbar(R.string.one_way_sync_confirmation, Snackbar.LENGTH_SHORT)
                        }
                    }
                    setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                }
                false
            }
        }
        // Custom sync server
        requirePreference<Preference>(R.string.custom_sync_server_key).setSummaryProvider {
            customSyncBase() ?: getString(R.string.custom_sync_server_summary_none_of_the_two_servers_used)
        }
    }

    private fun updateSyncAccountSummary() {
        requirePreference<Preference>(R.string.sync_account_key)
            .summary =
            Prefs.username.ifNullOrEmpty { getString(R.string.sync_account_summ_logged_out) }
    }

    private fun updateOneWaySyncEnabledState() {
        val isLoggedIn = !Prefs.username.isNullOrEmpty()
        requirePreference<Preference>(R.string.one_way_sync_key).isEnabled = isLoggedIn
    }

    // TODO trigger the summary change from MyAccount.kt once it is migrated to a fragment
    override fun onResume() {
        // Trigger a summary update in case the user logged in/out on MyAccount activity
        updateSyncAccountSummary()
        updateOneWaySyncEnabledState()
        super.onResume()
    }

    private fun customSyncBase(): String? {
        if (!Prefs.isCustomSyncEnabled) return null
        return Prefs.customSyncUri?.ifEmpty { null }
    }
}
