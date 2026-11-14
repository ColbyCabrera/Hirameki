// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import android.view.KeyEvent
import androidx.annotation.VisibleForTesting

object KeyUtils {
    @VisibleForTesting
    fun isDigit(event: KeyEvent): Boolean {
        val unicodeChar = event.getUnicodeChar(0)
        return unicodeChar >= '0'.code && unicodeChar <= '9'.code
    }

    fun getDigit(event: KeyEvent): Int? {
        val unicodeChar = event.getUnicodeChar(0)
        return if (isDigit(event)) unicodeChar - '0'.code else null
    }
}
