// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.os.Bundle
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode

/**
 * This class is an abstract base class that extends Activity and provides common initialization logic for [IntentHandler] and [IntentHandler2].
 * By centralizing common setup tasks here, it promotes code reuse.
 */
abstract class AbstractIntentHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.setTheme(this)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.progress_bar)
    }
}
