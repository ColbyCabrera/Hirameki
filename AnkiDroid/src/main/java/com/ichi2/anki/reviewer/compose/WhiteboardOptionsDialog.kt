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
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.whiteboard.BrushInfo
import com.ichi2.anki.ui.windows.reviewer.whiteboard.EraserMode
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardRepository
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel
import kotlin.math.roundToInt

/**
 * Dialog for adjusting brush properties like width and color.
 */
@Composable
fun BrushOptionsDialog(
    viewModel: WhiteboardViewModel,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        title = { Text(stringResource(R.string.brush_options)) },
        text = {
            BrushOptionsContent(viewModel)
        },
    )
}

/**
 * UI content for adjusting brush properties.
 */
@Composable
fun BrushOptionsContent(
    viewModel: WhiteboardViewModel,
) {
    val brushes by viewModel.brushes.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeBrushIndex.collectAsStateWithLifecycle()
    val brush = brushes.getOrNull(activeIndex) ?: return

    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            defaultColor = brush.color,
            onColorPicked = {
                viewModel.updateBrushColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brush Preview & Color Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(brush.color))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = { showColorPicker = true }) {
                Text(stringResource(R.string.select_color_title))
            }
        }

        // Width Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.whiteboard_width),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    brush.width.roundToInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = brush.width,
                onValueChange = { viewModel.setActiveStrokeWidth(it) },
                valueRange = 1f..70f,
                steps = 7,
            )
        }
    }
}

/**
 * Dialog for adjusting eraser properties like mode and width.
 */
@Composable
fun EraserOptionsDialog(
    viewModel: WhiteboardViewModel,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        title = { Text(stringResource(R.string.eraser)) },
        text = {
            EraserOptionsContent(viewModel, onDismissRequest)
        },
    )
}

/**
 * UI content for adjusting eraser properties.
 */
@Composable
fun EraserOptionsContent(
    viewModel: WhiteboardViewModel,
    onClearCanvas: () -> Unit = {},
) {
    val mode by viewModel.eraserMode.collectAsStateWithLifecycle()
    val width by viewModel.eraserDisplayWidth.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Mode Selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == EraserMode.INK,
                onClick = { viewModel.setEraserMode(EraserMode.INK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.whiteboard_ink_eraser))
            }
            SegmentedButton(
                selected = mode == EraserMode.STROKE,
                onClick = { viewModel.setEraserMode(EraserMode.STROKE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.whiteboard_stroke_eraser))
            }
        }

        // Width Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.whiteboard_width),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    width.roundToInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = width,
                onValueChange = { viewModel.setActiveStrokeWidth(it) },
                valueRange = 5f..200f,
                steps = 8,
            )
        }

        // Clear Canvas Button
        OutlinedButton(
            onClick = {
                viewModel.clearCanvas()
                onClearCanvas()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.whiteboard_clear))
        }
    }
}

/**
 * Popup for adjusting brush properties, anchored to a UI element.
 */
@Composable
fun BrushOptionsPopup(
    viewModel: WhiteboardViewModel,
    onDismissRequest: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        PopupSurface {
            BrushOptionsContent(viewModel)
        }
    }
}

/**
 * Popup for adjusting eraser properties, anchored to a UI element.
 */
@Composable
fun EraserOptionsPopup(
    viewModel: WhiteboardViewModel,
    onDismissRequest: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        PopupSurface {
            EraserOptionsContent(viewModel, onClearCanvas = onDismissRequest)
        }
    }
}

/**
 * A common surface for whiteboard popups to provide consistent styling.
 */
@Composable
private fun PopupSurface(
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .width(320.dp) // Standardized width for popups
            .padding(8.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun provideFakeWhiteboardViewModel(): WhiteboardViewModel {
    val context = LocalContext.current
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)

    return remember {
        WhiteboardViewModel(WhiteboardRepository(sharedPreferences)).apply {
            brushes.value = listOf(
                BrushInfo(color = android.graphics.Color.RED, width = 10f),
                BrushInfo(color = android.graphics.Color.BLUE, width = 20f),
            )
        }
    }
}

@Preview
@Composable
private fun BrushOptionsDialogDarkPreview() {
    BrushOptionsDialog(
        viewModel = provideFakeWhiteboardViewModel(),
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun EraserOptionsDialogPreview() {
    EraserOptionsDialog(
        viewModel = provideFakeWhiteboardViewModel(),
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun BrushOptionsPopupPreview() {
    BrushOptionsPopup(
        viewModel = provideFakeWhiteboardViewModel(),
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun EraserOptionsPopupPreview() {
    EraserOptionsPopup(
        viewModel = provideFakeWhiteboardViewModel(),
        onDismissRequest = {},
    )
}
