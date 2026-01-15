/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer.compose

import android.content.Context
import android.view.View
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.windows.reviewer.whiteboard.BrushInfo
import com.ichi2.anki.ui.windows.reviewer.whiteboard.ToolbarAlignment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel
import com.ichi2.anki.ui.windows.reviewer.whiteboard.compose.AddBrushButton
import com.ichi2.anki.ui.windows.reviewer.whiteboard.compose.ColorBrushButton

/**
 * Compose-based whiteboard toolbar with undo/redo, eraser, and brush selection.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhiteboardToolbar(
    viewModel: WhiteboardViewModel,
    onBrushClick: (View, Int) -> Unit,
    onBrushLongClick: (Int) -> Unit,
    onAddBrush: () -> Unit,
    onEraserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val brushes by viewModel.brushes.collectAsStateWithLifecycle()
    val activeBrushIndex by viewModel.activeBrushIndex.collectAsStateWithLifecycle()
    val isEraserActive by viewModel.isEraserActive.collectAsStateWithLifecycle()
    val alignment by viewModel.toolbarAlignment.collectAsStateWithLifecycle()
    val isStylusOnlyMode by viewModel.isStylusOnlyMode.collectAsStateWithLifecycle()

    WhiteboardToolbarContent(
        canUndo = canUndo,
        canRedo = canRedo,
        brushes = brushes,
        activeBrushIndex = activeBrushIndex,
        isEraserActive = isEraserActive,
        alignment = alignment,
        isStylusOnlyMode = isStylusOnlyMode,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onToggleEraser = onEraserClick,
        onToggleStylusMode = viewModel::toggleStylusOnlyMode,
        onSetAlignment = viewModel::setToolbarAlignment,
        onBrushClick = onBrushClick,
        onBrushLongClick = onBrushLongClick,
        onAddBrush = onAddBrush,
        modifier = modifier
    )
}

/**
 * Stateless Compose-based whiteboard toolbar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhiteboardToolbarContent(
    canUndo: Boolean,
    canRedo: Boolean,
    brushes: List<BrushInfo>,
    activeBrushIndex: Int,
    isEraserActive: Boolean,
    alignment: ToolbarAlignment,
    isStylusOnlyMode: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleEraser: () -> Unit,
    onToggleStylusMode: () -> Unit,
    onSetAlignment: (ToolbarAlignment) -> Unit,
    onBrushClick: (View, Int) -> Unit,
    onBrushLongClick: (Int) -> Unit,
    onAddBrush: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    val isVertical = alignment == ToolbarAlignment.LEFT || alignment == ToolbarAlignment.RIGHT
    val colorNormal = MaterialTheme.colorScheme.onSurface
    val colorHighlight = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraExtraLarge,
        tonalElevation = 2.dp
    ) {
        val content = @Composable {
            // Undo button
            IconButton(
                onClick = onUndo, enabled = canUndo
            ) {
                Icon(
                    painter = painterResource(R.drawable.undo_24px),
                    contentDescription = stringResource(R.string.undo),
                    tint = if (canUndo) colorNormal else colorNormal.copy(alpha = 0.38f)
                )
            }

            // Redo button
            IconButton(
                onClick = onRedo, enabled = canRedo
            ) {
                Icon(
                    painter = painterResource(R.drawable.redo_24px),
                    contentDescription = stringResource(R.string.redo),
                    tint = if (canRedo) colorNormal else colorNormal.copy(alpha = 0.38f)
                )
            }

            // Eraser button
            FilledIconToggleButton(
                onCheckedChange = { onToggleEraser() },
                checked = isEraserActive,
            ) {
                Icon(
                    painter = painterResource(R.drawable.eraser),
                    contentDescription = stringResource(R.string.eraser),
                )
            }

            // Overflow Menu Button
            Box {
                IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.whiteboard_more_options),
                        tint = colorNormal
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    // Stylus mode toggle
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stylus_mode)) },
                        onClick = {
                            showOverflowMenu = false
                            onToggleStylusMode()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(if (isStylusOnlyMode) R.drawable.check_24px else R.drawable.edit_24px),
                                contentDescription = null
                            )
                        })

                    // Toolbar position submenu
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.whiteboard_align_left)) },
                        onClick = {
                            showOverflowMenu = false
                            onSetAlignment(ToolbarAlignment.LEFT)
                        },
                        enabled = alignment != ToolbarAlignment.LEFT
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.whiteboard_align_bottom)) },
                        onClick = {
                            showOverflowMenu = false
                            onSetAlignment(ToolbarAlignment.BOTTOM)
                        },
                        enabled = alignment != ToolbarAlignment.BOTTOM
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.whiteboard_align_right)) },
                        onClick = {
                            showOverflowMenu = false
                            onSetAlignment(ToolbarAlignment.RIGHT)
                        },
                        enabled = alignment != ToolbarAlignment.RIGHT
                    )
                }
            }

            // Divider
            if (isVertical) {
                HorizontalDivider(
                    modifier = Modifier
                        .width(32.dp)
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            } else {
                VerticalDivider(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Brush palette
            val brushContent = @Composable {
                brushes.forEachIndexed { index, brush ->
                    ColorBrushButton(
                        brush = brush,
                        isSelected = (index == activeBrushIndex && !isEraserActive),
                        onClick = { view -> onBrushClick(view, index) },
                        onLongClick = { onBrushLongClick(index) },
                        colorNormal = colorNormal,
                        colorHighlight = colorHighlight
                    )
                }

                AddBrushButton(
                    onClick = onAddBrush,
                    colorNormal = colorNormal,
                    tooltip = stringResource(R.string.add_brush)
                )
            }

            if (isVertical) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    brushContent()
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    brushContent()
                }
            }
        }

        if (isVertical) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun WhiteboardToolbarPreview() {
    val context = LocalContext.current
    val viewModel: WhiteboardViewModel = viewModel(
        factory = WhiteboardViewModel.factory(
            context.getSharedPreferences("whiteboard-preview", Context.MODE_PRIVATE)
        )
    )
    AnkiDroidTheme {
        WhiteboardToolbar(
            viewModel = viewModel,
            onBrushClick = { _, _ -> },
            onBrushLongClick = { },
            onAddBrush = { },
            onEraserClick = { })
    }
}
