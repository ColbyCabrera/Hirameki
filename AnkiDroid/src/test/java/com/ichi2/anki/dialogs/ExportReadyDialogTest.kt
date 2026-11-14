// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ExportReadyDialog.ExportReadyDialogMessage
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportReadyDialogTest {
    @Test
    fun `fromMessage handles new KEY_EXPORT_PATH`() {
        val message =
            Message.obtain().apply {
                data =
                    Bundle().apply {
                        putString(ExportReadyDialog.KEY_EXPORT_PATH, "/new/path")
                    }
            }
        val dialogMessage = ExportReadyDialogMessage.fromMessage(message)

        // Let's check if the toMessage returns the new key, since it wraps the path
        val backToMessage = dialogMessage.toMessage()
        assertThat(backToMessage.data.getString(ExportReadyDialog.KEY_EXPORT_PATH), equalTo("/new/path"))
    }

    @Test
    fun `fromMessage handles legacy exportPath key`() {
        val message =
            Message.obtain().apply {
                data =
                    Bundle().apply {
                        putString("exportPath", "/legacy/path")
                    }
            }
        val dialogMessage = ExportReadyDialogMessage.fromMessage(message)

        val backToMessage = dialogMessage.toMessage()
        assertThat(backToMessage.data.getString(ExportReadyDialog.KEY_EXPORT_PATH), equalTo("/legacy/path"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromMessage throws on null data`() {
        val message =
            Message.obtain().apply {
                data = null
            }
        ExportReadyDialogMessage.fromMessage(message)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromMessage throws on missing export path`() {
        val message =
            Message.obtain().apply {
                data = Bundle()
            }
        ExportReadyDialogMessage.fromMessage(message)
    }
}
