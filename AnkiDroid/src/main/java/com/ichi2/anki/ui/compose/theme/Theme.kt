/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.themes.Themes

@ColorInt
private fun ankiColor(context: Context, @AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // Default M3
    small = RoundedCornerShape(8.dp), // Expressive: Slightly more rounded
    medium = RoundedCornerShape(16.dp), // Expressive: More pronounced rounding for cards/buttons
    large = RoundedCornerShape(24.dp), // Expressive: Very rounded for larger elements like dialogs
    extraLarge = RoundedCornerShape(32.dp), // Expressive: For prominent elements like FABs or hero containers
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnkiDroidTheme(
    harmonizeRatings: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentAnkiTheme = Themes.currentTheme
    val isNightMode = currentAnkiTheme.isNightMode
    val colorScheme = if (isNightMode) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    val ratingColors = remember(colorScheme.primary, isNightMode, harmonizeRatings) {
        RatingColorFactory.createRatingColorScheme(
            primaryColor = colorScheme.primary,
            isDark = isNightMode,
            harmonize = harmonizeRatings
        )
    }

    val ankiColors = remember(ratingColors, colorScheme, context) {
        AnkiColors(
            ratings = ratingColors,
            againButton = ratingColors.again.color,
            hardButton = ratingColors.hard.color,
            goodButton = ratingColors.good.color,
            easyButton = ratingColors.easy.color,
            newCount = Color(ankiColor(context, R.attr.newCountColor)),
            learnCount = Color(ankiColor(context, R.attr.learnCountColor)),
            reviewCount = Color(ankiColor(context, R.attr.reviewCountColor)),
            topBar = Color(ankiColor(context, R.attr.topBarColor))
        )
    }

    CompositionLocalProvider(LocalAnkiColors provides ankiColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}