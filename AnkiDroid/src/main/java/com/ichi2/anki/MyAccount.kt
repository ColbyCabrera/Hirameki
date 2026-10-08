/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.dialogs.help.HelpDialog.Companion.newPrivacyPolicyInstance
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.compose.MyAccountScreen
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.Permissions
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber

/**
 * Note: [LoginActivity] extends this and should not handle account creation
 */
open class MyAccount : AnkiActivity() {
    protected val viewModel: MyAccountViewModel by viewModels()

    protected open val showSignUpButton: Boolean = true
    protected open val showNoAccountText: Boolean = true

    open fun refreshLoginStatus() {
        // Compose handles state switching via viewModel
        viewModel.updateLoginStatus()
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Timber.i("notification permission: %b", it)
        }

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("local network permission: %b", granted)
            // The ViewModel owns the deferred login; the Activity only reports the outcome.
            viewModel.onLocalNetworkPermissionResult(granted, ::onLoginSuccessful)
            if (!granted) {
                // MyAccount is Compose-only (no root_layout), so a snackbar would crash DEBUG builds.
                showThemedToast(
                    this@MyAccount,
                    getString(R.string.custom_sync_local_network_denied, Permissions.displayHost(localNetworkEndpoint())),
                    shortLength = false,
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (isUserATestClient) {
            finish()
            return
        }
        mayOpenUrl(R.string.register_url)

        val onBackPressedCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    val state = viewModel.state.value
                    if (state.screenState == MyAccountScreenState.REMOVE_ACCOUNT) {
                        closeRemoveAccountScreen()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        setContent {
            AnkiDroidTheme {
                MyAccountScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onLoginClick = { _, password ->
                        // Android 17+: LAN custom servers need ACCESS_LOCAL_NETWORK first.
                        val endpoint = localNetworkEndpoint()
                        if (Permissions.isLocalNetworkSyncBlocked(this@MyAccount, endpoint, Prefs.isCustomSyncEnabled)) {
                            MaterialAlertDialogBuilder(this@MyAccount).show {
                                message(text = getString(R.string.custom_sync_local_network_rationale, Permissions.displayHost(endpoint)))
                                positiveButton(R.string.dialog_continue) {
                                    if (Permissions.canRequestLocalNetworkPermission(this@MyAccount)) {
                                        viewModel.deferLoginForLocalNetworkPermission(password)
                                        Prefs.localNetworkPermissionRequested = true
                                        localNetworkPermissionLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK)
                                    } else {
                                        showPermanentlyDeniedLocalNetworkDialog(endpoint)
                                    }
                                }
                                negativeButton(R.string.dialog_cancel) { /* stay on screen */ }
                            }
                        } else {
                            attemptLogin(password)
                        }
                    },
                    onResetPasswordClick = { resetPassword() },
                    onSignUpClick = { openUrl(R.string.register_url) },
                    onPrivacyPolicyClick = { openAnkiDroidPrivacyPolicy() },
                    onLostEmailClick = { openUrl(R.string.link_ankiweb_lost_email_instructions) },
                    onRemoveAccountClick = { openRemoveAccountScreen() },
                    onLogoutClick = { logout() },
                    onBackPressedCallback = onBackPressedCallback,
                    showSignUp = showSignUpButton,
                    showNoAccountText = showNoAccountText,
                )
            }
        }
    }

    private fun logout() {
        viewModel.logout()
    }

    /**
     * The system will no longer show the permission prompt ("Don't ask again"):
     * deep-link to Settings instead of requesting into the void.
     */
    private fun showPermanentlyDeniedLocalNetworkDialog(endpoint: String?) {
        MaterialAlertDialogBuilder(this@MyAccount).show {
            message(text = getString(R.string.custom_sync_local_network_permanently_denied, Permissions.displayHost(endpoint)))
            positiveButton(R.string.open_settings) {
                Permissions.openAppSettingsScreen(this@MyAccount)
            }
            negativeButton(R.string.dialog_cancel) { /* stay on screen */ }
        }
    }

    /**
     * The custom sync endpoint the LAN guard should check. Login always connects to the custom
     * URI (not [getEndpoint], which prefers a public currentSyncUri), so check that one.
     */
    private fun localNetworkEndpoint(): String? = Prefs.customSyncUri.takeIf { Prefs.isCustomSyncEnabled }

    private fun attemptLogin(password: String) {
        viewModel.login(password = password, onSuccess = ::onLoginSuccessful)
    }

    private fun onLoginSuccessful() {
        setResult(RESULT_OK)
        checkNotificationPermission(
            this@MyAccount,
            notificationPermissionLauncher,
        )
        finish()
    }

    /**
     * Opens the AnkiWeb 'remove account' screen
     * @see R.string.remove_account_url
     */
    private fun openRemoveAccountScreen() {
        Timber.i("opening 'remove account'")
        viewModel.setScreenState(MyAccountScreenState.REMOVE_ACCOUNT)
    }

    private fun closeRemoveAccountScreen() {
        Timber.i("closing 'remove account'")
        viewModel.setScreenState(MyAccountScreenState.ACCOUNT_MANAGEMENT)
    }

    private fun resetPassword() {
        super.openUrl(R.string.resetpw_url)
    }

    private fun openAnkiDroidPrivacyPolicy() {
        Timber.i("Opening 'Privacy policy'")
        showDialogFragment(newPrivacyPolicyInstance())
    }

    companion object {
        /**
         * Displays a system prompt: "Allow AnkiDroid to send you notifications"
         *
         * [launcher] receives a callback result (`boolean`) unless:
         *  * Permissions were already granted
         *  * We are < API 33
         *
         * Permissions may permanently be denied, in which case [launcher] immediately
         * receives a failure result
         */
        fun checkNotificationPermission(
            context: Context,
            launcher: ActivityResultLauncher<String>,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return
            }
            val permission = Permissions.postNotification
            if (permission != null &&
                ContextCompat.checkSelfPermission(
                    context,
                    permission,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(permission)
            }
        }
    }
}
