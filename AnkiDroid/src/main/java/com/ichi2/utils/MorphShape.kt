// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

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
 * [Morph.end] polygon as the [percentage] changes.
 *
 * @param morph The [Morph] object that defines the start and end shapes.
 * @param percentage The progress of the morph, between 0.0 and 1.0.
 */
class MorphShape(
    private val morph: Morph,
    private val percentage: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        // To draw the morph, we need to scale the path to the component size.
        val matrix = Matrix()
        matrix.setScale(size.width, size.height)

        // Create the morphed path.
        val path = morph.toPath(progress = percentage.coerceIn(0f, 1f))

        // Apply the scaling matrix to the path.
        path.transform(matrix)

        // Return the path as a generic outline.
        return Outline.Generic(path.asComposePath())
    }
}
