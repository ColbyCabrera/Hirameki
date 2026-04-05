/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
        val message = Message.obtain().apply {
            data = Bundle().apply {
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
        val message = Message.obtain().apply {
            data = Bundle().apply {
                putString("exportPath", "/legacy/path")
            }
        }
        val dialogMessage = ExportReadyDialogMessage.fromMessage(message)
        
        val backToMessage = dialogMessage.toMessage()
        assertThat(backToMessage.data.getString(ExportReadyDialog.KEY_EXPORT_PATH), equalTo("/legacy/path"))
    }
}
