/* **************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <colbycabrera@gmail.com>                            *
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
package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

/**
 * Represents an item displayed in the overflow menu of the note editor top app bar.
 */
sealed interface NoteEditorOverflowItem {
    val id: String
    val title: String
    val enabled: Boolean
    val visible: Boolean
}

@Immutable
class NoteEditorSimpleOverflowItem(
    override val id: String,
    override val title: String,
    override val enabled: Boolean = true,
    override val visible: Boolean = true,
    val onClick: () -> Unit,
) : NoteEditorOverflowItem

@Immutable
class NoteEditorToggleOverflowItem(
    override val id: String,
    override val title: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    override val enabled: Boolean = true,
    override val visible: Boolean = true,
) : NoteEditorOverflowItem

/**
 * Top app bar for the Compose note editor screen.
 * Provides navigation, primary actions (save / preview) and an overflow menu for secondary actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteEditorTopAppBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSaveAction: Boolean = true,
    saveEnabled: Boolean = true,
    onSaveClick: (() -> Unit)? = null,
    showPreviewAction: Boolean = true,
    previewEnabled: Boolean = true,
    onPreviewClick: (() -> Unit)? = null,
    primaryActions: @Composable RowScope.() -> Unit = {},
    overflowItems: List<NoteEditorOverflowItem> = emptyList(),
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val visibleOverflowItems = remember(overflowItems) { overflowItems.filter { it.visible } }

    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        title = {
            Text(
                modifier = Modifier.padding(start = 10.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displayMediumEmphasized,
            )
        },
        navigationIcon = {
            FilledIconButton(
                onClick = onBackClick,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            primaryActions()
            if (visibleOverflowItems.isNotEmpty()) {
                FilledIconButton(
                    onClick = { overflowExpanded = true },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    shape = MaterialTheme.shapes.large,
                ) {
                    visibleOverflowItems.forEach { item ->
                        when (item) {
                            is NoteEditorSimpleOverflowItem -> {
                                DropdownMenuItem(
                                    text = { Text(item.title) },
                                    enabled = item.enabled,
                                    onClick = {
                                        overflowExpanded = false
                                        item.onClick()
                                    },
                                )
                            }

                            is NoteEditorToggleOverflowItem -> {
                                DropdownMenuItem(
                                    text = { Text(item.title) },
                                    enabled = item.enabled,
                                    onClick = {
                                        overflowExpanded = false
                                        item.onCheckedChange(!item.checked)
                                    },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = item.checked,
                                            enabled = item.enabled,
                                            onCheckedChange = null,
                                            modifier =
                                                Modifier.semantics {
                                                    contentDescription = item.title
                                                },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (showPreviewAction && onPreviewClick != null) {
                FilledIconButton(
                    onClick = onPreviewClick,
                    enabled = previewEnabled,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.visibility_24px),
                        contentDescription = stringResource(R.string.preview),
                    )
                }
            }
            if (showSaveAction && onSaveClick != null) {
                Button(
                    modifier =
                        Modifier
                            .padding(start = 6.dp, end = 12.dp)
                            .height(48.dp),
                    onClick = onSaveClick,
                    enabled = saveEnabled,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
