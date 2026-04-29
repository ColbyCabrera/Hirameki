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
package com.ichi2.anki.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.ichi2.utils.MorphShape
import kotlinx.coroutines.launch
import kotlin.random.Random

// A list of interesting shapes to cycle through for the morph animation.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val MORPHING_SHAPES = listOf(
    MaterialShapes.Circle,
    MaterialShapes.Pill,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Sunny,
    MaterialShapes.Oval,
    MaterialShapes.Square,
    MaterialShapes.Slanted,
    MaterialShapes.Arch,
    MaterialShapes.Arrow,
    MaterialShapes.Fan,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.SoftBoom,
    MaterialShapes.Ghostish,
    MaterialShapes.Puffy,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.Bun,
    MaterialShapes.Flower
)

// The spring animation gives it a lively, physical feel.
private val MORPH_ANIMATION_SPEC: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 0.6f, stiffness = 200f)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingCardCount(
    cardCount: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    shapes: List<RoundedPolygon> = MORPHING_SHAPES,
) {
    // State for managing the morph animation.
    var currentShapeIndex by remember(shapes) { mutableIntStateOf(Random.nextInt(shapes.size)) }
    var startShape by remember(shapes) { mutableStateOf(shapes[currentShapeIndex]) }
    var endShape by remember(shapes) { mutableStateOf(shapes[currentShapeIndex]) }
    val morphProgress = remember { Animatable(0f) }

    // State for the rotation animation.
    val rotation = remember { Animatable(0f) }

    // Store the previous card count to determine the direction of change.
    var previousCardCount by remember { mutableIntStateOf(cardCount) }

    // Trigger the animation whenever the cardCount changes.
    LaunchedEffect(cardCount) {
        if (cardCount == previousCardCount) return@LaunchedEffect

        // Determine rotation direction based on count change.
        val rotationDirection = if (cardCount > previousCardCount) 1f else -1f

        // Set up the shapes for the upcoming morph.
        startShape = shapes[currentShapeIndex]
        currentShapeIndex = (currentShapeIndex + 1) % shapes.size
        endShape = shapes[currentShapeIndex]

        // Reset progress and rotation before starting new animations.
        morphProgress.snapTo(0f)
        rotation.snapTo(0f)

        // Run morph and rotation animations in parallel.
        launch {
            morphProgress.animateTo(targetValue = 1f, animationSpec = MORPH_ANIMATION_SPEC)
        }
        launch {
            rotation.animateTo(
                targetValue = 360f * rotationDirection, animationSpec = MORPH_ANIMATION_SPEC
            )
        }

        // Update the state for the next change.
        previousCardCount = cardCount
    }

    // Create the Morph object, normalizing shapes to ensure smooth transitions.
    val morph = remember(startShape, endShape) {
        Morph(startShape.normalized(), endShape.normalized())
    }

    // Create the dynamic MorphShape using the current animation progress.
    val morphingShape = MorphShape(morph, morphProgress.value)

    Box(modifier = modifier
        .size(36.dp)
        .graphicsLayer {
            // Apply the rotation from the animation.
            rotationZ = rotation.value
        }
        .clip(morphingShape)
        .background(containerColor),

        contentAlignment = Alignment.Center) {
        // AnimatedContent provides a nice transition for the text itself.
        AnimatedContent(
            targetState = cardCount, transitionSpec = {
                val enter = if (targetState > initialState) {
                    slideInVertically { height -> height } + fadeIn()
                } else {
                    slideInVertically { height -> -height } + fadeIn()
                }
                val exit = if (targetState > initialState) {
                    slideOutVertically { height -> -height } + fadeOut()
                } else {
                    slideOutVertically { height -> height } + fadeOut()
                }
                enter togetherWith exit using SizeTransform(clip = false)
            }, label = "CardCountAnimation"
        ) { count ->
            Text(
                modifier = Modifier.basicMarquee(),
                text = count.toString(),
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
