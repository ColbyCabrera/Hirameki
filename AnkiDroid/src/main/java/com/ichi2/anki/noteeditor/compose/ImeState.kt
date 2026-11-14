// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * Returns a [State] that tracks whether the soft keyboard is currently visible.
 *
 * Useful for reacting to keyboard open/close events, e.g. auto-scrolling a form
 * to keep fields accessible when the IME appears.
 *
 * @return `true` while the keyboard is visible, `false` otherwise
 */
@Composable
fun rememberImeState(): State<Boolean> {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    return remember {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
}
