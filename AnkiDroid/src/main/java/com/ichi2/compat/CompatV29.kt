// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.compat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.ichi2.anki.common.time.TimeManager
import java.io.File
import java.io.IOException

/** Implementation of [Compat] for SDK level 29  */
open class CompatV29 : CompatV26() {
    override fun hasVideoThumbnail(path: String): Boolean? {
        return try {
            ThumbnailUtils.createVideoThumbnail(File(path), THUMBNAIL_MINI_KIND, null)
            // createVideoThumbnail throws an exception if it's null
            true
        } catch (e: IOException) {
            // The default for audio is an IOException, so don't log it
            // A log line is still produced:
            // E/MediaMetadataRetrieverJNI: getEmbeddedPicture: Call to getEmbeddedPicture failed
            if (e.message == "Failed to create thumbnail") return false
            null
        } catch (e: Exception) {
            // unexpected exception
            null
        }
    }

    override fun saveImage(
        context: Context,
        bitmap: Bitmap,
        baseFileName: String,
        extension: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Uri {
        val imagesCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val destDir = File(Environment.DIRECTORY_PICTURES, "AnkiDroid")
        val date = TimeManager.time.intTimeMS()

        val newImage =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$date.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/$extension")
                put(MediaStore.MediaColumns.DATE_ADDED, date)
                put(MediaStore.MediaColumns.DATE_MODIFIED, date)
                put(MediaStore.MediaColumns.SIZE, bitmap.byteCount)
                put(MediaStore.MediaColumns.WIDTH, bitmap.width)
                put(MediaStore.MediaColumns.HEIGHT, bitmap.height)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$destDir${File.separator}")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val newImageUri = context.contentResolver.insert(imagesCollection, newImage)
        context.contentResolver.openOutputStream(newImageUri!!).use {
            if (it != null) {
                bitmap.compress(format, quality, it)
            }
        }
        newImage.clear()
        newImage.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(newImageUri, newImage, null, null)
        return newImageUri
    }

    companion object {
        // obtained from AOSP source
        private val THUMBNAIL_MINI_KIND = Size(512, 384)
    }
}
