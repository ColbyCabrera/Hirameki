/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer.spike

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import kotlin.math.abs
import kotlin.math.ceil

/** Logged under [TAG]: read it with `adb logcat -d -s CardSpike`. */
internal class SpikeReport(
    private val name: String,
) {
    fun add(line: String) {
        Log.i(TAG, "[$name] $line")
    }

    fun timings(
        label: String,
        valuesMs: List<Double>,
    ) {
        val sorted = valuesMs.sorted()
        add(
            "%s: p50=%.1f p95=%.1f max=%.1f ms (n=%d)".format(
                label,
                sorted.percentile(0.50),
                sorted.percentile(0.95),
                sorted.last(),
                sorted.size,
            ),
        )
    }

    fun write() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val webView = WebViewCompat.getCurrentWebViewPackage(context)
        val header =
            "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "webview=${webView?.packageName} ${webView?.versionName}"
        Log.i(TAG, "[$name] $header")
    }

    companion object {
        const val TAG = "CardSpike"
    }
}

private fun List<Double>.percentile(fraction: Double): Double = this[(ceil(fraction * size).toInt() - 1).coerceIn(indices)]

/** The average colour of a small patch around the given point, as fractions of the bitmap's size. */
internal fun Bitmap.sample(
    xFraction: Float,
    yFraction: Float,
    radius: Int = 3,
): Int {
    val centerX = (xFraction * width).toInt().coerceIn(radius, width - radius - 1)
    val centerY = (yFraction * height).toInt().coerceIn(radius, height - radius - 1)
    var red = 0
    var green = 0
    var blue = 0
    var count = 0
    for (x in centerX - radius..centerX + radius) {
        for (y in centerY - radius..centerY + radius) {
            val pixel = getPixel(x, y)
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
            count++
        }
    }
    return (0xFF shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
}

internal fun Int.isCloseTo(
    expected: Int,
    tolerance: Int = 24,
): Boolean =
    listOf(16, 8, 0).all { shift ->
        abs(((this shr shift) and 0xFF) - ((expected shr shift) and 0xFF)) <= tolerance
    }

internal fun Int.toHex(): String = "#%06X".format(this and 0xFFFFFF)
