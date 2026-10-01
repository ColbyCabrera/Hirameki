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

package com.ichi2.anki.multimedia.audio

object WaveformUtils {
    private const val MAX_RAW_AMPLITUDE = 32767f

    /**
     * Normalizes a raw amplitude value from the audio recorder (0-32767) to a 0.0-1.0 range.
     */
    fun normalize(rawAmplitude: Int): Float = (rawAmplitude / MAX_RAW_AMPLITUDE).coerceIn(0f, 1f)

    /**
     * Legacy normalization for the Android View, maintaining original scaling/sensitivity.
     * Formerly: (amp.toInt() / 7).coerceAtMost(300).coerceAtLeast(6)
     */
    fun legacyNormalize(rawAmplitude: Float): Float = (rawAmplitude / 7f).coerceIn(6f, 300f)
}
