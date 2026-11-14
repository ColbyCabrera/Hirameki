// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.preferences

import android.content.SharedPreferences

/** Provides a reference to [SharedPreferences] without a required Android dependency */
// SharedPreferences is an interface, so this remains Android-free
fun interface SharedPreferencesProvider {
    fun sharedPrefs(): SharedPreferences
}
