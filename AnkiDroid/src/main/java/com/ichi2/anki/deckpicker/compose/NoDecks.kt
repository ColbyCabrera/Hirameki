/* **************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <gdthyispro@gmail.com>                              *
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

package com.ichi2.anki.deckpicker.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ichi2.anki.R
import com.ichi2.utils.MorphShape

/**
 * Empty-collection state shown when the user has no decks to study yet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoDecks(
    onCreateDeck: () -> Unit,
    onGetSharedDecks: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val animatableShift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        visible = true
        animatableShift.animateTo(
            targetValue = 1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -360f, animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Remember the morph because generating the shape path is relatively expensive.
    val morph = remember {
        Morph(
            start = MaterialShapes.Pentagon,
            end = MaterialShapes.Cookie12Sided,
        )
    }

    val morphShape = MorphShape(morph = morph, percentage = animatableShift.value)

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier
            .size(300.dp)
            .scale(scaleX = 1.2f, scaleY = 1.2f)
            .graphicsLayer {
                this.alpha = 0.1f
                this.shadowElevation = 0f
                this.shape = morphShape
                this.clip = true
                this.rotationZ = rotation
            }
            .background(MaterialTheme.colorScheme.tertiaryContainer))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = visible, enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn()
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape) // Consistent expressive shape
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.folder_open_24px),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible, enter = slideInVertically(
                    initialOffsetY = { 50 }, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.no_cards_placeholder_title),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.no_cards_placeholder_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            lineBreak = LineBreak.Paragraph
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 48.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = visible, enter = slideInVertically(
                    initialOffsetY = { 100 }, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(), modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onCreateDeck,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.new_deck),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = onGetSharedDecks,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.get_shared),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun NoDecksPreview() {
    NoDecks(onCreateDeck = {}, onGetSharedDecks = {})
}
