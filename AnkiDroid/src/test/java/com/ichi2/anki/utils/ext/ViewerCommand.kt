// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils.ext

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding

fun ViewerCommand.addBinding(
    preferences: SharedPreferences,
    binding: MappableBinding,
) {
    val addAtStart: (MutableList<MappableBinding>, MappableBinding) -> Boolean =
        { collection, element ->
            // reorder the elements, moving the added binding to the first position
            collection.remove(element)
            collection.add(0, element)
            true
        }
    val bindings: MutableList<MappableBinding> = ReviewerBinding.fromPreference(preferences, this)
    addAtStart(bindings, binding)
    val newValue: String = bindings.toPreferenceString()
    preferences.edit { putString(preferenceKey, newValue) }
}
