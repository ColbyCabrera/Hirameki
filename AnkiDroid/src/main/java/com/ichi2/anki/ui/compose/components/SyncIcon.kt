// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.flow.first

@Composable
fun SyncIcon(
    isSyncing: Boolean,
    syncState: SyncIconState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    // Capture the latest syncing state so our coroutine can read it
    // safely without being canceled and restarted.
    val currentIsSyncing by rememberUpdatedState(isSyncing)

    LaunchedEffect(Unit) {
        while (true) {
            // Wait until syncing starts
            snapshotFlow { currentIsSyncing }.first { it }

            // Loop full 360-degree rotations as long as syncing is active.
            // Guarantee the animation gracefully completes its current rotation before stopping.
            while (currentIsSyncing) {
                rotation.animateTo(
                    targetValue = rotation.targetValue + 360f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                )
                rotation.snapTo(0f)
            }
        }
    }

    val syncIconDescription =
        if (isSyncing) {
            stringResource(R.string.syncing)
        } else {
            when (syncState) {
                SyncIconState.PendingChanges -> stringResource(R.string.sync_menu_title_pending_changes)
                SyncIconState.OneWay -> stringResource(R.string.sync_menu_title_one_way_sync)
                SyncIconState.NotLoggedIn -> stringResource(R.string.sync_menu_title_no_account)
                else -> stringResource(R.string.sync_now)
            }
        }

    BadgedBox(
        modifier = modifier,
        badge = {
            when (syncState) {
                SyncIconState.PendingChanges -> Badge()
                SyncIconState.OneWay, SyncIconState.NotLoggedIn ->
                    Badge {
                        val badgeDescription =
                            when (syncState) {
                                SyncIconState.OneWay -> stringResource(R.string.sync_menu_title_one_way_sync)
                                else -> stringResource(R.string.sync_menu_title_no_account)
                            }
                        Text(
                            text = "!",
                            modifier =
                                Modifier.semantics {
                                    contentDescription = badgeDescription
                                },
                        )
                    }

                else -> { // No badge for Normal state
                }
            }
        },
    ) {
        FilledIconButton(
            onClick = onRefresh,
            enabled = !isSyncing,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync_24px),
                contentDescription = syncIconDescription,
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
            )
        }
    }
}

@Preview(name = "SyncIcon States", showBackground = true)
@Composable
private fun SyncIconPreview() {
    AnkiDroidTheme {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SyncIcon(
                isSyncing = false,
                syncState = SyncIconState.Normal,
                onRefresh = { },
            )
            SyncIcon(
                isSyncing = true,
                syncState = SyncIconState.Normal,
                onRefresh = { },
            )
            SyncIcon(
                isSyncing = false,
                syncState = SyncIconState.PendingChanges,
                onRefresh = { },
            )
            SyncIcon(
                isSyncing = false,
                syncState = SyncIconState.OneWay,
                onRefresh = { },
            )
            SyncIcon(
                isSyncing = false,
                syncState = SyncIconState.NotLoggedIn,
                onRefresh = { },
            )
        }
    }
}
