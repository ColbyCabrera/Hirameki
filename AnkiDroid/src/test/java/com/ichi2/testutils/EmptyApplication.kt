// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import android.app.Application
import com.ichi2.anki.AnkiDroidApp

class EmptyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // reset the static state of the app
        AnkiDroidApp.simulateRestoreFromBackup()
    }
}
