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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import anki.scheduler.CardAnswer
import com.google.android.material.color.utilities.ColorUtils
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingColorFactoryTest {

    @Test
    fun `light mode generates expected tonal roles`() {
        val primary = Color(0xFF6750A4) // M3 baseline Purple
        val scheme = RatingColorFactory.createRatingColorScheme(primaryColor = primary, isDark = false)

        val ratings = listOf(
            CardAnswer.Rating.AGAIN,
            CardAnswer.Rating.HARD,
            CardAnswer.Rating.GOOD,
            CardAnswer.Rating.EASY,
        )

        for (rating in ratings) {
            val role = scheme.forRating(rating)

            // In light mode:
            // Container is Tone 90 (light pastel)
            // OnContainer is Tone 10 (dark text)
            // Badge container is Tone 40 (vibrant accent)
            // OnBadge is Tone 100 (white text)
            val containerHct = Hct.fromInt(role.colorContainer.toArgb())
            val onContainerHct = Hct.fromInt(role.onColorContainer.toArgb())
            val colorHct = Hct.fromInt(role.color.toArgb())
            val onColorHct = Hct.fromInt(role.onColor.toArgb())

            assertEquals(90.0, containerHct.tone, 1.0)
            assertEquals(10.0, onContainerHct.tone, 1.0)
            assertEquals(40.0, colorHct.tone, 1.0)
            assertEquals(100.0, onColorHct.tone, 1.0)

            // Verify WCAG contrast: onColorContainer on colorContainer must be >= 4.5:1 (AAA is >= 7:1)
            val contrastText = contrastRatio(onContainerHct.tone, containerHct.tone)
            assertTrue("Text contrast ($contrastText) must be >= 4.5:1", contrastText >= 4.5)

            // Verify WCAG contrast: color (badge) on colorContainer (button) >= 3.0:1 (AA non-text)
            val contrastBadge = contrastRatio(colorHct.tone, containerHct.tone)
            assertTrue("Badge on button contrast ($contrastBadge) must be >= 3.0:1", contrastBadge >= 3.0)
        }
    }

    @Test
    fun `dark mode generates expected tonal roles`() {
        val primary = Color(0xFFD0BCFF) // M3 baseline Purple Dark
        val scheme = RatingColorFactory.createRatingColorScheme(primaryColor = primary, isDark = true)

        val ratings = listOf(
            CardAnswer.Rating.AGAIN,
            CardAnswer.Rating.HARD,
            CardAnswer.Rating.GOOD,
            CardAnswer.Rating.EASY,
        )

        for (rating in ratings) {
            val role = scheme.forRating(rating)

            // In dark mode:
            // Container is Tone 30 (dark surface)
            // OnContainer is Tone 90 (light text)
            // Badge container is Tone 80 (vibrant light accent)
            // OnBadge is Tone 20 (dark text)
            val containerHct = Hct.fromInt(role.colorContainer.toArgb())
            val onContainerHct = Hct.fromInt(role.onColorContainer.toArgb())
            val colorHct = Hct.fromInt(role.color.toArgb())
            val onColorHct = Hct.fromInt(role.onColor.toArgb())

            assertEquals(30.0, containerHct.tone, 1.0)
            assertEquals(90.0, onContainerHct.tone, 1.0)
            assertEquals(80.0, colorHct.tone, 1.0)
            assertEquals(20.0, onColorHct.tone, 1.0)

            // Verify WCAG contrast: onColorContainer on colorContainer must be >= 4.5:1
            val contrastText = contrastRatio(onContainerHct.tone, containerHct.tone)
            assertTrue("Text contrast ($contrastText) must be >= 4.5:1", contrastText >= 4.5)

            // Verify WCAG contrast: color (badge) on colorContainer (button) >= 3.0:1
            val contrastBadge = contrastRatio(colorHct.tone, containerHct.tone)
            assertTrue("Badge on button contrast ($contrastBadge) must be >= 3.0:1", contrastBadge >= 3.0)
        }
    }

    private fun contrastRatio(tone1: Double, tone2: Double): Double {
        val y1 = ColorUtils.yFromLstar(tone1)
        val y2 = ColorUtils.yFromLstar(tone2)
        val lighter = maxOf(y1, y2)
        val darker = minOf(y1, y2)
        return (lighter + 5.0) / (darker + 5.0)
    }

    @Test
    fun `harmonization subtly shifts hue without destroying semantic identity`() {
        val primaryPurple = Color(0xFF6750A4)
        val primaryGreen = Color(0xFF2E7D32)

        val purpleScheme = RatingColorFactory.createRatingColorScheme(primaryPurple, isDark = false, harmonize = true)
        val greenScheme = RatingColorFactory.createRatingColorScheme(primaryGreen, isDark = false, harmonize = true)
        val unharmonizedScheme = RatingColorFactory.createRatingColorScheme(primaryPurple, isDark = false, harmonize = false)

        val unharmonizedAgainHue = Hct.fromInt(unharmonizedScheme.again.color.toArgb()).hue
        val purpleAgainHue = Hct.fromInt(purpleScheme.again.color.toArgb()).hue
        val greenAgainHue = Hct.fromInt(greenScheme.again.color.toArgb()).hue

        // Harmonization causes a shift
        assertNotEquals(unharmonizedAgainHue, purpleAgainHue, 0.01)
        assertNotEquals(unharmonizedAgainHue, greenAgainHue, 0.01)

        // The shift is clamped <= 15 degrees, keeping it firmly in the Red hue range (~10° - 45°)
        val diffPurple = Math.abs(purpleAgainHue - unharmonizedAgainHue)
        val diffGreen = Math.abs(greenAgainHue - unharmonizedAgainHue)

        assertTrue("Hue rotation with purple ($diffPurple) must be <= 15.1 degrees", diffPurple <= 15.1)
        assertTrue("Hue rotation with green ($diffGreen) must be <= 15.1 degrees", diffGreen <= 15.1)
    }
}
