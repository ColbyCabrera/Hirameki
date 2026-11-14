// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.utils.ext

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResultListener

/**
 * Utility method that simplifies setting a fragment result listener in a [FragmentActivity].
 * Similar to [Fragment.setFragmentResultListener] library method.
 *
 * @param requestKey identifier for the request
 * @param listener a callback triggered when the result associated to [requestKey] is set.
 */
fun FragmentActivity.setFragmentResultListener(
    requestKey: String,
    listener: ((requestKey: String, bundle: Bundle) -> Unit),
) {
    supportFragmentManager.setFragmentResultListener(requestKey, this, listener)
}
