// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
class EnvironmentTest {
    @Test
    fun testStorageState() {
        val originalState = Environment.getExternalStorageState()
        try {
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_REMOVED)
            assertEquals(false, AnkiDroidApp.isSdCardMounted)
        } finally {
            ShadowEnvironment.setExternalStorageState(originalState)
        }
    }
}
