// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * An activity which **only** involves logging in to an account:
 *
 * * Logging in
 * * Resetting the password
 *
 * This was created for a 'load from AnkiWeb' flow, in which we only wanted to encourage an existing
 * user to sync from AnkiWeb, to ensure that they don't have two collections, causing a sync conflict.
 *
 * Use [MyAccount] if you want to handle the 'logged in' state, and creating a new account
 *
 * Activity Results:
 * * [RESULT_OK] - login was successful OR login had already occurred
 * * [RESULT_CANCELED] - login did not occur
 *
 * TODO: Move this to a fragment
 */
@NeedsTest("14650: collection permissions are required for this screen to be usable")
class LoginActivity :
    MyAccount(),
    CollectionPermissionScreenLauncher {
    override val permissionScreenLauncher = recreateActivityResultLauncher()
    override val showSignUpButton: Boolean = false
    override val showNoAccountText: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isFinishing) {
            return
        }

        // LoginActivity can be opened from IntroductionActivity, so we need permissions
        if (collectionPermissionScreenWasOpened()) {
            return
        }

        refreshLoginStatus()
    }

    /**
     * Handles closing the activity and setting the result when the user is logged in
     */
    override fun refreshLoginStatus() {
        if (!Prefs.hkey.isNullOrEmpty()) {
            // This was intended to be shown from the 'app intro' where a user should not be logged in
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showThemedToast(this, R.string.already_logged_in, true)
                Timber.w("LoginActivity shown when user was logged in")
            }
            setResult(RESULT_OK)
            finish()
            return
        }
        super.refreshLoginStatus()
    }
}
