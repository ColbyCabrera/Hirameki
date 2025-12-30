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
package com.ichi2.anki.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIcon(
    isSyncing: Boolean,
    syncState: SyncIconState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(1000, easing = LinearEasing)
                )
            }
        } else {
            // Finish the current rotation smoothly to the next 360 degree mark
            val current = rotation.value
            val target = (kotlin.math.ceil(current / 360f) * 360f)
            if (target > current) {
                rotation.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    )
                )
            }
            rotation.snapTo(0f)
        }
    }

    BadgedBox(
        modifier = modifier,
        badge = {
            when (syncState) {
                SyncIconState.PendingChanges -> Badge()
                SyncIconState.OneWay, SyncIconState.NotLoggedIn -> Badge {
                    Text("!")
                }
                else -> { /* No badge for Normal state */ }
            }
        },
    ) {
        val contentDescription = when (syncState) {
            SyncIconState.PendingChanges -> stringResource(R.string.sync_menu_title_pending_changes)
            SyncIconState.OneWay -> stringResource(R.string.sync_menu_title_one_way_sync)
            SyncIconState.NotLoggedIn -> stringResource(R.string.sync_menu_title_no_account)
            else -> stringResource(R.string.sync_now)
        }

        FilledIconButton(
            onClick = {
                onRefresh()
                scope.launch {
                    rotation.animateTo(
                        targetValue = rotation.value + 360f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }
            },
            enabled = !isSyncing,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync_24px),
                contentDescription = contentDescription,
                modifier = Modifier.rotate(rotation.value),
            )
        }
    }
}
