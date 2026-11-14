// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.utils.ext

import androidx.lifecycle.SavedStateHandle

fun <T> SavedStateHandle.require(key: String): T =
    checkNotNull(get(key)) {
        "Wrong type or key '$key' not found"
    }
