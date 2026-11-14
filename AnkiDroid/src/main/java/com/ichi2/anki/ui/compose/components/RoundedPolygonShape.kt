// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose.components

import android.graphics.Matrix
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

        // Center the polygon around its own logical center
        matrix.postTranslate(-polygon.centerX, -polygon.centerY)

        // Scale to fit the target size. If the target size isn't square,
        // this preserves the original behavior of potentially stretching.
        matrix.postScale(size.width / maxDimension, size.height / maxDimension)

        // Move the center of the scaled polygon to the center of the Composable
        matrix.postTranslate(size.width / 2f, size.height / 2f)

        graphicsPath.transform(matrix)
        return Outline.Generic(graphicsPath.asComposePath())
    }
}

fun RoundedPolygon.getBounds() = calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }
