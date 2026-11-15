// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import android.content.Context
import com.ichi2.anki.AnkiDroidApp

/**
 * An application which sets [com.ichi2.anki.AnkiDroidApp.INSTRUMENTATION_TESTING] to true
 * so a test collection path is used
 */
class TestingApplication : AnkiDroidApp() {
    override fun attachBaseContext(base: Context) {
        INSTRUMENTATION_TESTING = true
        super.attachBaseContext(base)
    }
}
