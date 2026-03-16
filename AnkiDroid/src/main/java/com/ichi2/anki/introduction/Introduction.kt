/*
* Copyright (c) 2022 David Allison <davidallisongithub@gmail.com> 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

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

package com.ichi2.anki.introduction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SoftBurstShape = RoundedPolygonShape(MaterialShapes.SoftBurst)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun IntroductionScreen(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onGetStarted: () -> Unit,
    onSync: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    // Reset acknowledged state if back is pressed
    if (acknowledged) {
        BackHandler {
            onAcknowledgedChange(false)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "IntroIconRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
        ),
        label = "IntroIconRotationAngle",
    )

    AnkiDroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 64.dp, bottom = 24.dp)
                            .size(124.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = rotation
                            }
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = SoftBurstShape,
                            ))
                        Image(
                            modifier = Modifier.size(60.dp),
                            painter = painterResource(R.drawable.cards_stack_24px),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val buttonModifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)

                    AnimatedContent(
                        targetState = acknowledged, transitionSpec = {
                            val direction = if (targetState) 1 else -1
                            (slideInVertically { it * direction } + fadeIn()).togetherWith(
                                    slideOutVertically { -it * direction } + fadeOut())
                                .using(SizeTransform(clip = false))
                        }, label = "IntroTransition"
                    ) { isAcknowledged ->
                        if (!isAcknowledged) {
                            // Disclaimer / Intro State
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.intro_title_before_continuing),
                                    style = MaterialTheme.typography.displayMediumEmphasized,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("intro_title")
                                )
                                Spacer(modifier = Modifier.height(32.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(32.dp)
                                        )
                                        .padding(24.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.intro_fork_disclaimer_1),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = stringResource(R.string.intro_fork_disclaimer_2),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Button(
                                    onClick = { onAcknowledgedChange(true) },
                                    modifier = buttonModifier.testTag("continue_button"),
                                    shapes = ButtonDefaults.shapes()

                                ) {
                                    Text(
                                        stringResource(R.string.intro_continue),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { uriHandler.openUri("https://opencollective.com/ankidroid") },
                                    modifier = buttonModifier,
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(
                                        stringResource(R.string.donate),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        } else {
                            // Action State (Get Started / Sync)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = onGetStarted,
                                    modifier = buttonModifier.testTag("get_started"),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(
                                        stringResource(R.string.intro_get_started),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onSync,
                                    modifier = buttonModifier.testTag("sync_button"),
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(
                                        stringResource(R.string.intro_sync_from_ankiweb),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntroductionScreen(
    onGetStarted: () -> Unit, onSync: () -> Unit
) {
    val (acknowledged, onAcknowledgedChange) = remember { mutableStateOf(false) }
    IntroductionScreen(
        acknowledged = acknowledged,
        onAcknowledgedChange = onAcknowledgedChange,
        onGetStarted = onGetStarted,
        onSync = onSync,
    )
}

@Preview
@Composable
fun IntroductionScreenPreview() {
    IntroductionScreen(onGetStarted = { }, onSync = { })
}
