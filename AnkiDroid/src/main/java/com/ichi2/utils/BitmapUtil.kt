// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.ichi2.anki.CrashReportService
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object BitmapUtil {
    fun decodeFile(
        theFile: File,
        imageMaxSize: Int,
    ): Bitmap? {
        var bmp: Bitmap? = null
        try {
            if (!theFile.exists()) {
                Timber.i("not displaying preview - image does not exist: '%s'", theFile.path)
                return null
            }
            // Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            FileInputStream(theFile).use { fis ->
                BitmapFactory.decodeStream(fis, null, o)
            }
            var scale = 1
            if (o.outHeight > imageMaxSize || o.outWidth > imageMaxSize) {
                scale =
                    2.0
                        .pow(
                            (
                                ln(imageMaxSize / o.outHeight.coerceAtLeast(o.outWidth).toDouble()) /
                                    ln(0.5)
                            ).roundToInt().toDouble(),
                        ).toInt()
            }

            // Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            FileInputStream(theFile).use { fis ->
                bmp = BitmapFactory.decodeStream(fis, null, o2)
            }
        } catch (e: Exception) {
            // #5513 - We don't know the reason for the crash, let's find out.
            CrashReportService.sendExceptionReport(e, "BitmapUtil decodeFile")
        }
        return bmp
    }

    fun freeImageView(imageView: ImageView?) {
        // This code behaves differently on various OS builds. That is why put into try catch.
        try {
            if (imageView != null) {
                @Suppress("UNUSED_VARIABLE")
                val dr = (imageView.drawable ?: return) as? BitmapDrawable ?: return
                val bd = imageView.drawable as BitmapDrawable
                if (bd.bitmap != null) {
                    bd.bitmap.recycle()
                    imageView.setImageBitmap(null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
