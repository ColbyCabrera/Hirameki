// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.addTextChangedListener
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import kotlin.jvm.Throws

/**
 * A drop-in alternative to [EditTextPreference] with some extra functionality.
 *
 *   * It supports changing input type via `android:inputType` XML attribute,
 *     which can help the keyboard app with choosing a more suitable keyboard layout.
 *     For the list of available input types, see [TextView.getInputType].
 *
 *   * If [continuousValidator] is set, the dialog uses it to prevent user
 *     from entering invalid data. On each text change, the validator is run;
 *     if it throws, the Ok button gets disabled.
 */
open class VersatileTextPreference(
    context: Context,
    attrs: AttributeSet?,
) : EditTextPreference(context, attrs),
    DialogFragmentProvider {
    fun interface Validator {
        @Throws(Exception::class)
        fun validate(value: String)
    }

    val referenceEditText = AppCompatEditText(context, attrs)

    var continuousValidator: Validator? = null

    override fun makeDialogFragment() = VersatileTextPreferenceDialogFragment()
}

open class VersatileTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    private val versatileTextPreference get() = preference as VersatileTextPreference

    protected lateinit var editText: EditText

    // This changes input type first, as it resets the cursor,
    // And only then calls super, which sets up text and moves the cursor to end.
    //
    // Positive button isn't present in a dialog until it is shown, which happens around onStart;
    // for simplicity, obtain it in the listener itself.
    override fun onBindDialogView(contentView: View) {
        editText = contentView.findViewById(android.R.id.edit)!!
        editText.inputType = versatileTextPreference.referenceEditText.inputType

        super.onBindDialogView(contentView)

        versatileTextPreference.continuousValidator?.let { validator ->
            editText.addTextChangedListener(afterTextChanged = {
                val alertDialog = dialog as AlertDialog
                val positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)

                positiveButton?.isEnabled =
                    try {
                        validator.validate(editText.text.toString())
                        true
                    } catch (e: Exception) {
                        false
                    }
            })
        }
    }
}
