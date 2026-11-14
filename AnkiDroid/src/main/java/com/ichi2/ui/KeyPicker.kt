// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.ui

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

typealias KeyCode = Int

/**
 * Square dialog which allows a user to select a [Binding] for a key press
 * This does not yet support bluetooth headsets.
 */
class KeyPicker(
    val rootLayout: View,
) : WarningDisplay {
    private val textView: TextView = rootLayout.findViewById(R.id.key_picker_selected_key)

    override val warningTextView: FixedTextView = rootLayout.findViewById(R.id.warning)

    private val context: Context get() = rootLayout.context

    private var text: String
        set(value) {
            textView.text = value
        }
        get() = textView.text.toString()

    private var onBindingChangedListener: ((Binding) -> Unit)? = null
    private var isValidKeyCode: ((KeyCode) -> Boolean)? = null

    /** Currently bound key */
    private var binding: Binding? = null

    fun getBinding(): Binding? = binding

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true

        // When accepting a keypress, we only want to find the keycode, not the unicode character.
        val newBinding =
            Binding
                .possibleKeyBindings(event)
                .filterIsInstance<Binding.KeyCode>()
                .firstOrNull { binding -> isValidKeyCode?.invoke(binding.keycode) != false } ?: return true
        Timber.d("Changed key to '%s'", newBinding)
        binding = newBinding
        text = newBinding.toDisplayString(context)
        onBindingChangedListener?.invoke(newBinding)
        return true
    }

    fun setBindingChangedListener(listener: (Binding) -> Unit) {
        onBindingChangedListener = listener
    }

    fun setKeycodeValidation(validation: (KeyCode) -> Boolean) {
        isValidKeyCode = validation
    }

    init {
        textView.requestFocus()
        textView.setOnKeyListener { _, _, event -> dispatchKeyEvent(event) }
    }

    companion object {
        fun inflate(context: Context): KeyPicker {
            val layout = LayoutInflater.from(context).inflate(R.layout.dialog_key_picker, null)
            return KeyPicker(layout)
        }
    }
}
