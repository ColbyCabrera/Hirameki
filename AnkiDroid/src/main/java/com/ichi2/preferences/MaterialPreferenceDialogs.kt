// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Mirrors [PreferenceDialogFragmentCompat.onCreateDialog], but builds with
 * [MaterialAlertDialogBuilder] so the dialog gets the Material 3 container background
 * instead of AppCompat's `?android:colorBackgroundFloating`.
 */
internal fun PreferenceDialogFragmentCompat.createMaterialPreferenceDialog(
    createDialogView: (Context) -> View?,
    bindDialogView: (View) -> Unit,
    prepareDialogBuilder: (AlertDialog.Builder) -> Unit,
    showSoftInput: Boolean = false,
): Dialog {
    val dialogPreference = preference
    val builder =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogPreference.dialogTitle)
            .setIcon(dialogPreference.dialogIcon)
            .setPositiveButton(dialogPreference.positiveButtonText, this)
            .setNegativeButton(dialogPreference.negativeButtonText, this)

    val contentView = createDialogView(requireContext())
    if (contentView != null) {
        bindDialogView(contentView)
        builder.setView(contentView)
    } else {
        builder.setMessage(dialogPreference.dialogMessage)
    }

    prepareDialogBuilder(builder)

    return builder.create().apply {
        if (showSoftInput) {
            window?.decorView?.windowInsetsController?.show(WindowInsets.Type.ime())
        }
    }
}

class MaterialListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        createMaterialPreferenceDialog(::onCreateDialogView, ::onBindDialogView, ::onPrepareDialogBuilder)
}

class MaterialMultiSelectListPreferenceDialogFragment : MultiSelectListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        createMaterialPreferenceDialog(::onCreateDialogView, ::onBindDialogView, ::onPrepareDialogBuilder)
}

open class MaterialEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        createMaterialPreferenceDialog(
            ::onCreateDialogView,
            ::onBindDialogView,
            ::onPrepareDialogBuilder,
            showSoftInput = true,
        )
}
