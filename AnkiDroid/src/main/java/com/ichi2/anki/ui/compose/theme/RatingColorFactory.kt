/* **************************************************************************************
 * Copyright (c) 2026 Antigravity contributors                                          *
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
package com.ichi2.anki.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import anki.scheduler.CardAnswer
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette

/**
 * Represents the Material 3 tonal roles for a semantic review button.
 *
 * Adheres strictly to Material 3 luminance/tonal contrast pairings:
 * - [colorContainer]: Low-to-medium emphasis container (Tone 90 in light, Tone 30 in dark)
 * - [onColorContainer]: Text and icons on container (Tone 10 in light, Tone 90 in dark) -> WCAG AAA (~11.5:1)
 * - [color]: High-emphasis accent for badge container (Tone 40 in light, Tone 80 in dark) -> WCAG AA non-text (~4.8:1 on button)
 * - [onColor]: High-contrast text on badge (Tone 100 in light, Tone 20 in dark) -> WCAG AA (~5.6:1)
 */
@Immutable
data class TonalRole(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

/**
 * Complete set of Material 3 rating tonal color roles for Again, Hard, Good, and Easy.
 */
@Immutable
data class RatingColorScheme(
    val again: TonalRole,
    val hard: TonalRole,
    val good: TonalRole,
    val easy: TonalRole,
) {
    fun forRating(rating: CardAnswer.Rating): TonalRole = when (rating) {
        CardAnswer.Rating.AGAIN -> again
        CardAnswer.Rating.HARD -> hard
        CardAnswer.Rating.GOOD -> good
        CardAnswer.Rating.EASY -> easy
        else -> good
    }
}

/**
 * Factory that derives Material 3 harmonized tonal palettes for flashcard rating buttons.
 *
 * Uses CAM16/HCT perceptual color space and Material Color Utilities (`Blend.harmonize`).
 * The algorithm clamps hue rotation to a maximum of 15 degrees toward the theme's primary color,
 * ensuring that Again (Red), Hard (Orange), Good (Green), and Easy (Blue) maintain their distinctive
 * semantic identities across any dynamic wallpaper or custom theme palette.
 */
object RatingColorFactory {
    // Canonical Anki Semantic Seed Colors
    const val SEED_AGAIN: Int = 0xFFE53935.toInt() // Material Red 600
    const val SEED_HARD: Int = 0xFFFB8C00.toInt()  // Material Orange 600 / Amber
    const val SEED_GOOD: Int = 0xFF43A047.toInt()  // Material Green 600
    const val SEED_EASY: Int = 0xFF1E88E5.toInt()  // Material Blue 600

    fun createRatingColorScheme(
        primaryColor: Color,
        isDark: Boolean,
        harmonize: Boolean = true,
    ): RatingColorScheme {
        val primaryArgb = primaryColor.toArgb()

        fun buildRole(seedColor: Int): TonalRole {
            val finalArgb = if (harmonize) {
                Blend.harmonize(seedColor, primaryArgb)
            } else {
                seedColor
            }
            val hct = Hct.fromInt(finalArgb)
            val palette = TonalPalette.fromHueAndChroma(hct.hue, hct.chroma)

            return if (isDark) {
                TonalRole(
                    color = Color(palette.tone(80)),
                    onColor = Color(palette.tone(20)),
                    colorContainer = Color(palette.tone(30)),
                    onColorContainer = Color(palette.tone(90)),
                )
            } else {
                TonalRole(
                    color = Color(palette.tone(40)),
                    onColor = Color(palette.tone(100)),
                    colorContainer = Color(palette.tone(90)),
                    onColorContainer = Color(palette.tone(10)),
                )
            }
        }

        return RatingColorScheme(
            again = buildRole(SEED_AGAIN),
            hard = buildRole(SEED_HARD),
            good = buildRole(SEED_GOOD),
            easy = buildRole(SEED_EASY),
        )
    }
}
