/*
 *  Copyright (c) 2025 AnkiDroid Open Source Team
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.delay
import timber.log.Timber

private data class HelpLink(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val icon: Int,
    val url: String
)

private val helpLinks = listOf(
    HelpLink(
        R.string.help_item_anki_manual,
        R.string.help_manual_subtitle,
        R.drawable.help_24px,
        "https://docs.ankidroid.org"
    ), HelpLink(
        R.string.help_forum_title,
        R.string.help_forum_subtitle,
        R.drawable.forum_24px,
        "https://forums.ankiweb.net"
    ), HelpLink(
        R.string.help_issue_tracker_title,
        R.string.help_issue_tracker_subtitle,
        R.drawable.bug_report_24px,
        "https://github.com/ColbyCabrera/Hirameki/issues"
    ), HelpLink(
        R.string.help_donate_title,
        R.string.help_donate_subtitle,
        R.drawable.volunteer_activism_24px,
        "https://ankidroid.org/#donations"
    )
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val HeroShape = RoundedPolygonShape(MaterialShapes.Cookie4Sided)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val iconShapes = listOf(
    RoundedPolygonShape(MaterialShapes.Clover4Leaf),
    RoundedPolygonShape(MaterialShapes.SoftBoom),
    RoundedPolygonShape(MaterialShapes.Sunny),
    RoundedPolygonShape(MaterialShapes.VerySunny),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HelpScreen(onNavigateUp: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            LargeTopAppBar(
                navigationIcon = {
                FilledIconButton(
                    onClick = onNavigateUp,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_24px),
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }, title = {
                Text(
                    text = stringResource(id = R.string.help_screen_title),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                )
            }, scrollBehavior = scrollBehavior
            )
        }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hero Section
            item {
                HelpHeroSection()
            }

            // Help Cards with staggered animation
            itemsIndexed(helpLinks) { index, helpLink ->
                val iconShape = iconShapes[index % iconShapes.size]

                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 100L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                        animationSpec = spring(dampingRatio = 0.8f), initialOffsetY = { it / 2 })
                ) {
                    HelpItem(
                        titleRes = helpLink.titleRes,
                        subtitleRes = helpLink.subtitleRes,
                        icon = helpLink.icon,
                        iconShape = iconShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(helpLink.url))
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Timber.w("No application found to open link: %s", helpLink.url)
                                Toast.makeText(
                                    context,
                                    R.string.no_application_to_open_link,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HelpHeroSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "HeroRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ), label = "HeroRotationAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center
        ) {
            // Animated background shape
            Box(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .background(
                    MaterialTheme.colorScheme.primaryContainer, shape = HeroShape
                ))
            // Help icon
            Icon(
                painter = painterResource(R.drawable.help_filled_24px),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.help_hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HelpItem(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    @DrawableRes icon: Int,
    iconShape: RoundedPolygonShape,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor, contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp, pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon with shaped background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer, shape = iconShape
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = titleRes),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(id = subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            Icon(
                painter = painterResource(R.drawable.arrow_outward_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        }
    }
}

@Preview
@Composable
fun HelpScreenPreview() {
    AnkiDroidTheme {
        HelpScreen(onNavigateUp = {})
    }
}
