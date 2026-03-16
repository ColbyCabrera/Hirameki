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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SoftBurstShape = RoundedPolygonShape(MaterialShapes.SoftBurst)

/** Non-obvious layout and animation constants used throughout the introduction screen. */
private object IntroConstants {
    const val ROTATION_DURATION_MS = 9000
    val BurstSize = 300.dp
    val BurstOffsetX = 20.dp
    val BurstOffsetY = 26.dp
    val HeroAreaHeight = 350.dp
    val TitleFontSize = 64.sp
    const val TITLE_ROTATION = 6f
    val WelcomeBannerHeight = 110.dp
    val WelcomeBannerWidth = 280.dp
    val WelcomeBannerOffsetX = (-40).dp
    val WelcomeBannerOffsetY = (-66).dp
    const val WELCOME_BANNER_ROTATION = -10f
    val GetStartedButtonHeight = 88.dp
    val SyncButtonHeight = 72.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun IntroductionScreen(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onGetStarted: () -> Unit,
    onSync: () -> Unit,
) {
    LocalUriHandler.current

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
            animation = tween(IntroConstants.ROTATION_DURATION_MS, easing = LinearEasing),
        ),
        label = "IntroIconRotationAngle",
    )

    AnkiDroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.systemBars.exclude(WindowInsets.navigationBars),
        ) { padding ->
            AnimatedContent(
                targetState = acknowledged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    (slideInVertically { it * direction } + fadeIn()).togetherWith(
                        slideOutVertically { -it * direction } + fadeOut(),
                    ).using(SizeTransform(clip = false))
                },
                label = "IntroTransition",
            ) { isAcknowledged ->
                if (!isAcknowledged) {
                    DisclaimerContent(
                        rotation = rotation,
                        onContinue = { onAcknowledgedChange(true) },
                    )
                } else {
                    ActionContent(
                        onGetStarted = onGetStarted,
                        onSync = onSync,
                    )
                }
            }
        }
    }
}

@Composable
fun IntroductionScreen(
    onGetStarted: () -> Unit,
    onSync: () -> Unit,
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

// region Extracted Composables

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DisclaimerContent(
    rotation: Float,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntroConstants.HeroAreaHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(IntroConstants.BurstSize)
                    .offset(x = IntroConstants.BurstOffsetX, y = IntroConstants.BurstOffsetY)
                    .graphicsLayer { rotationZ = rotation }
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        shape = SoftBurstShape,
                    ),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLargeEmphasized,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = IntroConstants.TitleFontSize,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .offset(
                        x = IntroConstants.BurstOffsetX, y = IntroConstants.BurstOffsetY
                    )
                    .rotate(IntroConstants.TITLE_ROTATION)
                    .testTag("app_name"),
            )
            Box(
                modifier = Modifier
                    .height(IntroConstants.WelcomeBannerHeight)
                    .width(IntroConstants.WelcomeBannerWidth)
                    .offset(
                        x = IntroConstants.WelcomeBannerOffsetX,
                        y = IntroConstants.WelcomeBannerOffsetY,
                    )
                    .rotate(IntroConstants.WELCOME_BANNER_ROTATION)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraExtraLarge,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Welcome to",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Disclaimer 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(-2f)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(
                        topStart = 40.dp,
                        bottomEnd = 40.dp,
                        topEnd = 12.dp,
                        bottomStart = 12.dp,
                    ),
                )
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.intro_fork_disclaimer_1),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Disclaimer 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .rotate(3f)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        bottomEnd = 12.dp,
                        topEnd = 40.dp,
                        bottomStart = 40.dp,
                    ),
                )
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.intro_fork_disclaimer_2),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val largeButtonHeight = ButtonDefaults.LargeContainerHeight

        Button(
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 8.dp)
                .rotate(-4f)
                .testTag("continue_button"),
            shapes = ButtonDefaults.shapesFor(largeButtonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(largeButtonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                stringResource(R.string.intro_continue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun ActionContent(
    onGetStarted: () -> Unit,
    onSync: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(IntroConstants.GetStartedButtonHeight)
                .rotate(-2f)
                .testTag("get_started"),
            shape = RoundedCornerShape(
                topStart = 48.dp,
                bottomEnd = 48.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                stringResource(R.string.intro_get_started),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSync,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(IntroConstants.SyncButtonHeight)
                .rotate(3f)
                .testTag("sync_button"),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                bottomEnd = 16.dp,
                topEnd = 48.dp,
                bottomStart = 48.dp,
            ),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                stringResource(R.string.intro_sync_from_ankiweb),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
