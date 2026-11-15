// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
