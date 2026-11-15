// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils.ext

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.utils.showDialogFragmentImpl

/*
 ************************************************************************************************
 ****************************************** Dialogs *********************************************
 ************************************************************************************************
 */

const val DIALOG_FRAGMENT_TAG = "dialog"

/**
 * Global method to show dialog fragment including adding it to back stack
 * If you need to show a dialog from an async task, use [AnkiActivity.showAsyncDialogFragment]
 *
 * @param newFragment the [DialogFragment] you want to show
 */
fun FragmentActivity.showDialogFragment(newFragment: DialogFragment) {
    runOnUiThread {
        showDialogFragmentImpl(this.supportFragmentManager, newFragment)
    }
}

/** Dismiss whatever dialog is showing */
fun FragmentActivity.dismissAllDialogFragments() {
    // trying to pop fragment manager back state crashes if state already saved
    if (!supportFragmentManager.isStateSaved) {
        supportFragmentManager.popBackStack(
            DIALOG_FRAGMENT_TAG,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )
    }
}

/**
 * @return The last fragment added by [showDialogFragment], only  if it is the provided type.
 * `null` if the type does not match, or if a dialog has not been shown
 */
inline fun <reified T : DialogFragment> FragmentActivity.getCurrentDialogFragment(): T? =
    supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? T?

/**
 * @return The last fragment added by [showDialogFragment], only  if it is the provided type.
 * `null` if the type does not match, or if a dialog has not been shown
 */
inline fun <reified T : DialogFragment> Fragment.getCurrentDialogFragment(): T? = requireActivity().getCurrentDialogFragment()
