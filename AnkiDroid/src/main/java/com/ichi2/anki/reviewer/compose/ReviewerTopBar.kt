/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.reviewer.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.MorphingCardCount
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewerTopBar(
    newCount: Int,
    learnCount: Int,
    reviewCount: Int,
    chosenAnswer: String,
    isMarked: Boolean,
    flag: Int,
    onToggleMark: (Boolean) -> Unit,
    onSetFlag: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isAnswerShown: Boolean,
    onUnanswerCard: () -> Unit
) {
    CenterAlignedTopAppBar(
        modifier = modifier, title = { Text(chosenAnswer) }, navigationIcon = {
            Counts(
                modifier = Modifier.padding(horizontal = 8.dp),
                newCount = newCount,
                learnCount = learnCount,
                reviewCount = reviewCount
            )
        }, actions = {
            MarkIcon(
                isMarked = isMarked, onToggleMark = onToggleMark
            )
            FlagIcon(currentFlag = flag, onSetFlag = onSetFlag)
            AnimatedVisibility(visible = isAnswerShown) {
                FilledIconButton(
                    onClick = onUnanswerCard, shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        painterResource(R.drawable.undo_24px),
                        contentDescription = stringResource(id = R.string.unanswer_card),
                    )
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkIcon(isMarked: Boolean, onToggleMark: (Boolean) -> Unit) {
    FilledIconToggleButton(
        checked = isMarked,
        onCheckedChange = onToggleMark,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            checkedContentColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Icon(
            painter = if (isMarked) painterResource(R.drawable.star_shine_24px) else painterResource(
                R.drawable.star_24px
            ),
            contentDescription = stringResource(if (isMarked) R.string.menu_unmark_note else R.string.menu_mark_note)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlagIcon(currentFlag: Int, onSetFlag: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val flagColors = listOf(
        Color.Unspecified, // 0: no flag
        Color.Red,         // 1: Red
        Color(0xFFFFA500), // 2: Orange
        Color.Green,       // 3: Green
        Color.Blue,        // 4: Blue
        Color.Magenta,     // 5: Pink
        Color.Cyan,        // 6: Turquoise
        Color(0xFF9400D3)  // 7: Purple
    )
    val flagColorNames = listOf(
        stringResource(R.string.no_flag),
        stringResource(R.string.flag_red),
        stringResource(R.string.flag_orange),
        stringResource(R.string.flag_green),
        stringResource(R.string.flag_blue),
        stringResource(R.string.flag_pink),
        stringResource(R.string.flag_turquoise),
        stringResource(R.string.flag_purple)
    )

    Box {
        FilledIconButton(
            onClick = { expanded = true },
            shapes = IconButtonDefaults.shapes(),
            colors = if (currentFlag in flagColors.indices && currentFlag != 0) IconButtonDefaults.filledIconButtonColors(
                contentColor = flagColors[currentFlag],
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) else IconButtonDefaults.filledIconButtonColors(
                contentColor = IconButtonDefaults.filledIconToggleButtonColors().contentColor,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.flag_24px),
                contentDescription = stringResource(R.string.menu_flag_card),
            )
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            (0..7).forEach { flag ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.flag_24px),
                                contentDescription = null,
                                tint = flagColors[flag]
                            )
                            Text(flagColorNames[flag])
                        }
                    },
                    onClick = {
                        expanded = false
                        onSetFlag(flag)
                    }
                )
            }
        }
    }
}

@Composable
fun Counts(newCount: Int, learnCount: Int, reviewCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MorphingCardCount(
            newCount,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        MorphingCardCount(
            learnCount,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        MorphingCardCount(
            reviewCount,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewerTopBarPreview() {
    AnkiDroidTheme {
        ReviewerTopBar(
            newCount = 13,
            learnCount = 3,
            reviewCount = 7,
            chosenAnswer = "Answer",
            isMarked = true,
            flag = 1,
            onToggleMark = { _ -> },
            onSetFlag = { _ -> },
            isAnswerShown = true,
            onUnanswerCard = {})
    }
}
