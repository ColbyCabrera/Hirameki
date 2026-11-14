// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.settings.enums

import androidx.annotation.StringRes

interface PrefEnum {
    @get:StringRes
    val entryResId: Int
}
