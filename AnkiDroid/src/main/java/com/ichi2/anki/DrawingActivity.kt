/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ichi2.anki.drawing.DrawingScreen
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import timber.log.Timber

/**
 * Activity allowing the user to draw an image to be added the collection
 *
 * User can use all basic whiteboard functionality and can save image from this activity.
 *
 * To access this screen: Add/Edit Note - Attachment - Add Image - Drawing
 */
class DrawingActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnkiDroidTheme {
                DrawingScreen(
                    onSave = { uri -> finishWithSuccess(uri) },
                    onFinish = { finish() },
                )
            }
        }
    }

    private fun finishWithSuccess(uri: Uri) {
        Timber.i("Drawing:: Save successful, returning URI: %s", uri)
        val resultData = Intent()
        resultData.putExtra(EXTRA_RESULT_WHITEBOARD, uri)
        setResult(RESULT_OK, resultData)
        finish()
    }

    companion object {
        const val EXTRA_RESULT_WHITEBOARD = "drawing.editedImage"
    }
}
