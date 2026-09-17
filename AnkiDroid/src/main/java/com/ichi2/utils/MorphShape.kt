/*
 * Copyright (c) 2024 Colby Cabrera <colbycabrera.wd@gmail.com>
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
package com.ichi2.utils

import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * A [Shape] that morphs between two [androidx.graphics.shapes.RoundedPolygon]s.
 *
 * This class allows you to create a shape that smoothly transitions between a [Morph.start] and
 * [Morph.end] polygon as the [progress] changes.
 *
 * @param morph The [Morph] object that defines the start and end shapes.
 * @param progress The progress of the morph, between 0.0 and 1.0. it is read each time the outline is built, and
 * compose observes state read there, so a shape reading animated state follows it without being recreated.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    /** a shape fixed at [percentage], between 0.0 and 1.0 */
    constructor(morph: Morph, percentage: Float) : this(morph, { percentage })

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // To draw the morph, we need to scale the path to the component size.
        val matrix = Matrix()
        matrix.setScale(size.width, size.height)

        // Create the morphed path.
        val path = morph.toPath(progress = progress().coerceIn(0f, 1f))

        // Apply the scaling matrix to the path.
        path.transform(matrix)

        // Return the path as a generic outline.
        return Outline.Generic(path.asComposePath())
    }
}