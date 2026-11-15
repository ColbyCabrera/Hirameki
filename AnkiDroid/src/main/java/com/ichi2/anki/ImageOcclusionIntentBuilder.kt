// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ichi2.anki.noteeditor.NoteEditorLauncher

/**
 * Builder class for creating intents related to image occlusion in the [NoteEditorFragment].
 */
class ImageOcclusionIntentBuilder(
    private val context: Context,
) {
    fun buildIntent(imageUri: Uri?): Intent = NoteEditorLauncher.ImageOcclusion(imageUri).toIntent(context)
}
