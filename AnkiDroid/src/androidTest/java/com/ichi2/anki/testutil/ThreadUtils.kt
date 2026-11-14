// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.testutil

object ThreadUtils {
    fun sleep(timeMs: Int) {
        try {
            Thread.sleep(timeMs.toLong())
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }
}
