/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber

sealed class LoginError {
    /**
     * Indicates whether this error type should prompt the user to reset their password.
     * This encapsulates authentication error detection logic, making it easier to add
     * new authentication-related error types (e.g., account locked, password expired)
     * without updating all consumers.
     */
    abstract val requiresPasswordReset: Boolean

    data class StringResource(
        @StringRes val resId: Int,
        override val requiresPasswordReset: Boolean = false,
    ) : LoginError()

    data class DynamicString(
        val text: String,
        override val requiresPasswordReset: Boolean = false,
    ) : LoginError()
}

data class MyAccountState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val email: String = "",
    val isLoginLoading: Boolean = false,
    val loginError: LoginError? = null,
    val screenState: MyAccountScreenState = MyAccountScreenState.ACCOUNT_MANAGEMENT,
)

enum class MyAccountScreenState {
    ACCOUNT_MANAGEMENT,
    REMOVE_ACCOUNT,
}

class MyAccountViewModel : ViewModel() {
    private val _state = MutableStateFlow(MyAccountState())
    val state: StateFlow<MyAccountState> = _state.asStateFlow()

    private var loginJob: Job? = null

    init {
        val loggedIn = Prefs.hkey != null
        _state.update {
            it.copy(
                isLoggedIn = loggedIn,
                username = Prefs.username,
                email = Prefs.username ?: "",
            )
        }
    }

    fun onEmailChanged(email: String) {
        _state.update { it.copy(email = email, loginError = null) }
    }

    fun login(
        password: String,
        onSuccess: () -> Unit,
    ) {
        // Prevent multiple concurrent login attempts
        if (loginJob?.isActive == true) {
            return
        }

        val email = _state.value.email.trim()
        if (email.isEmpty() || password.isEmpty()) {
            _state.update { it.copy(loginError = LoginError.StringResource(R.string.login_error_email_password_required)) }
            return
        }

        _state.update { it.copy(isLoginLoading = true, loginError = null) }

        loginJob =
            viewModelScope.launch {
                try {
                    val endpoint = if (Prefs.isCustomSyncEnabled) Prefs.customSyncUri else null
                    val auth =
                        withCol {
                            syncLogin(email, password, endpoint)
                        }
                    Prefs.username = email
                    Prefs.hkey = auth.hkey
                    _state.update {
                        it.copy(
                            isLoginLoading = false,
                            isLoggedIn = true,
                            username = email,
                        )
                    }
                    onSuccess()
                } catch (e: BackendInterruptedException) {
                    // User cancelled - just clear loading state, don't show error
                    Timber.i("Login cancelled by user")
                    _state.update { it.copy(isLoginLoading = false) }
                } catch (e: BackendSyncException.BackendSyncAuthFailedException) {
                    _state.update {
                        it.copy(
                            isLoginLoading = false,
                            loginError =
                                LoginError.StringResource(
                                    resId = R.string.login_error_authentication_failed,
                                    requiresPasswordReset = true,
                                ),
                        )
                    }
                } catch (e: BackendNetworkException) {
                    Timber.w(e, "Network error during login")
                    _state.update {
                        it.copy(
                            isLoginLoading = false,
                            loginError = LoginError.StringResource(R.string.login_error_network),
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Login failed")
                    _state.update {
                        it.copy(
                            isLoginLoading = false,
                            loginError =
                                e.message?.let { message -> LoginError.DynamicString(message) }
                                    ?: LoginError.StringResource(R.string.login_error_unknown),
                        )
                    }
                } finally {
                    loginJob = null
                }
            }
    }

    /**
     * Cancels an in-progress login operation.
     * Sets the backend abort flag which will cause the network operation to throw BackendInterruptedException.
     */
    fun cancelLogin() {
        val jobToCancel = loginJob ?: return

        viewModelScope.launch {
            try {
                CollectionManager.getBackend().setWantsAbort()
            } catch (e: Exception) {
                Timber.w(e, "Failed to set abort flag")
            }
        }
        jobToCancel.cancel()
        if (loginJob == jobToCancel) {
            loginJob = null
        }
        _state.update { it.copy(isLoginLoading = false) }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                withCol {
                    media.forceResync()
                }
            } catch (e: Exception) {
                Timber.e(e, "logout: media.forceResync() failed")
            } finally {
                Prefs.hkey = null
                Prefs.username = null
                Prefs.currentSyncUri = null
                _state.update { it.copy(isLoggedIn = false, username = null, email = "") }
            }
        }
    }

    fun setScreenState(screenState: MyAccountScreenState) {
        _state.update { it.copy(screenState = screenState) }
    }

    fun updateLoginStatus() {
        val loggedIn = Prefs.hkey != null
        _state.update {
            it.copy(
                isLoggedIn = loggedIn,
                username = Prefs.username,
            )
        }
    }
}
