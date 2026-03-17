/****************************************************************************************
 * Copyright (c) 2024 AnkiDroid Open Source Team                                        *
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
package com.ichi2.anki.ui.compose.components

import android.graphics.Matrix
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class RoundedPolygonShape(
    private val polygon: RoundedPolygon,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val graphicsPath = polygon.toPath()
        val matrix = Matrix()
        val bounds = polygon.getBounds()
        val maxDimension = max(bounds.width, bounds.height)

        val leftOffset = (maxDimension - bounds.width) / 2f
        val topOffset = (maxDimension - bounds.height) / 2f
        matrix.postTranslate(-bounds.left + leftOffset, -bounds.top + topOffset)
        matrix.postScale(size.width / maxDimension, size.height / maxDimension)

        graphicsPath.transform(matrix)
        return Outline.Generic(graphicsPath.asComposePath())
    }
}

fun RoundedPolygon.getBounds() = calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }
