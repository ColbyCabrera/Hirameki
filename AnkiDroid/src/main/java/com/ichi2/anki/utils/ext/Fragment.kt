// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils.ext

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.showDialogFragmentImpl

fun Fragment.sharedPrefs(): SharedPreferences = requireContext().sharedPrefs()

val Fragment.packageManager: PackageManager
    get() = requireContext().packageManager

/**
 * Method to show dialog fragment including adding it to back stack
 *
 * @see showDialogFragmentImpl
 */
fun Fragment.showDialogFragment(newFragment: DialogFragment) = requireActivity().showDialogFragment(newFragment)

/** @see FragmentActivity.getWindow */
val Fragment.window: Window
    get() = requireActivity().window
