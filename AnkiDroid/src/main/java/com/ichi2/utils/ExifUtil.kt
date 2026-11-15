// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.lang.Exception

object ExifUtil {
    fun rotateFromCamera(
        theFile: File,
        bitmap: Bitmap,
    ): Bitmap {
        var bmp = bitmap
        return try {
            val exif = ExifInterface(theFile.path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            var angle = 0
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    angle = 90
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    angle = 180
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    angle = 270
                }
            }
            val mat = Matrix()
            mat.postRotate(angle.toFloat())
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
            bmp
        } catch (e: Exception) {
            Timber.w(e)
            bmp
        }
    }
}
