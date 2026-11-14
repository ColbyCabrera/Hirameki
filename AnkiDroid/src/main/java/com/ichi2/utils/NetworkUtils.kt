// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.ichi2.anki.AnkiDroidApp

object NetworkUtils {
    private val connectivityManager: ConnectivityManager?
        get() =
            AnkiDroidApp
                .instance
                .applicationContext
                .getSystemService()

    /**
     * @return whether the active network is metered
     * or false in case internet cannot be accessed
     */
    fun isActiveNetworkMetered(): Boolean =
        isOnline &&
            connectivityManager
                ?.isActiveNetworkMetered
                ?: true

    /**
     * @return whether is possible to access the internet
     */
    val isOnline: Boolean
        get() {
            val cm = connectivityManager ?: return false

            // ConnectivityManager.activeNetwork is for SDK ≥ 23
            val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            val isOnline =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            // internet capabilities can be checked if internet is temporarily disabled as well
            return isOnline && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }
}
