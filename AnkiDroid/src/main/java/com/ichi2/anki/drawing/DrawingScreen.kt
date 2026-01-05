/*
 * Copyright (c) 2026 Cabrera Family
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
package com.ichi2.anki.drawing

import android.graphics.Path
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.compose.ColorPickerDialog
import com.ichi2.anki.reviewer.compose.WhiteboardToolbarContent
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.windows.reviewer.whiteboard.BrushInfo
import com.ichi2.anki.ui.windows.reviewer.whiteboard.ToolbarAlignment
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Main composable for the drawing screen.
 * Allows users to draw images to add to their flashcards.
 *
 * @param onSave Callback when the drawing is saved successfully with the result URI
 * @param onFinish Callback when the user cancels without saving
 * @param viewModel The ViewModel managing drawing state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrawingScreen(
    onSave: (Uri) -> Unit,
    onFinish: () -> Unit,
    viewModel: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val paths by viewModel.paths.collectAsState()
    val brushColor by viewModel.brushColor.collectAsState()
    val strokeWidth by viewModel.strokeWidth.collectAsState()

    // Toolbar state
    val canUndo by viewModel.canUndo.collectAsState(initial = false)
    val canRedo by viewModel.canRedo.collectAsState()
    val brushes by viewModel.brushes.collectAsState()
    val activeBrushIndex by viewModel.activeBrushIndex.collectAsState()
    val isEraserActive by viewModel.isEraserActive.collectAsState()
    val isStylusOnlyMode by viewModel.isStylusOnlyMode.collectAsState()

    // Set default color to primary if not set (ViewModel defaults to Transparent)
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(Unit) {
        viewModel.initializeWithDefaultColor(primaryColor)
    }

    var canvasWidth by remember { mutableIntStateOf(0) }
    var canvasHeight by remember { mutableIntStateOf(0) }

    // Dialog state
    var showBrushOptions by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showEraserOptions by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val hasContent = paths.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(
                text = stringResource(id = R.string.title_whiteboard_editor),
                style = MaterialTheme.typography.displayMediumEmphasized,
            )
        }, navigationIcon = {
            FilledIconButton(
                modifier = Modifier.padding(end = 8.dp),
                onClick = onFinish,
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
        }, actions = {
            val nothingToSaveMessage = stringResource(R.string.nothing_to_save)
            Button(
                modifier = Modifier
                    .height(48.dp)
                    .padding(end = 8.dp),
                enabled = hasContent,
                onClick = {
                    scope.launch {
                        val uri = viewModel.saveDrawing(context, canvasWidth, canvasHeight)
                        if (uri != null) {
                            onSave(uri)
                        } else {
                            snackbarHostState.showSnackbar(nothingToSaveMessage)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 24.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.save))
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.CenterEnd
        ) {
            DrawingCanvas(
                paths = paths,
                brushColor = brushColor,
                strokeWidth = strokeWidth,
                isEraserActive = isEraserActive,
                backgroundColor = backgroundColor,
                onPathDrawn = { path -> viewModel.addPath(path) },
                onSizeChanged = { width, height ->
                    canvasWidth = width
                    canvasHeight = height
                },
                isStylusOnlyMode = isStylusOnlyMode,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .heightIn(max = 400.dp)
            ) {
                WhiteboardToolbarContent(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    brushes = brushes,
                    activeBrushIndex = activeBrushIndex,
                    isEraserActive = isEraserActive,
                    alignment = ToolbarAlignment.RIGHT,
                    isStylusOnlyMode = isStylusOnlyMode,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onToggleEraser = {
                        if (isEraserActive) {
                            showEraserOptions = true
                        } else {
                            viewModel.toggleEraser()
                        }
                    },
                    onToggleStylusMode = viewModel::toggleStylusOnlyMode,
                    onSetAlignment = viewModel::setToolbarAlignment,
                    onBrushClick = { _, index ->
                        if (activeBrushIndex == index && !isEraserActive) {
                            showBrushOptions = true
                        } else {
                            viewModel.setActiveBrush(index)
                        }
                    },
                    onBrushLongClick = { /* Handle long click if needed for color picker */ },
                    onAddBrush = viewModel::addBrush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

        }
    }

    // Brush Options Dialog
    if (showBrushOptions) {
        val activeBrush = brushes.getOrNull(activeBrushIndex)
        if (activeBrush != null) {
            DrawingBrushOptionsDialog(
                brush = activeBrush,
                onWidthChange = { viewModel.setStrokeWidth(it) },
                onColorPickerRequest = {
                    showBrushOptions = false
                    showColorPicker = true
                },
                onDismissRequest = { showBrushOptions = false })
        }
    }

    // Color Picker Dialog
    if (showColorPicker) {
        val activeBrush = brushes.getOrNull(activeBrushIndex)
        ColorPickerDialog(
            defaultColor = activeBrush?.color ?: brushColor,
            onColorPicked = { color ->
                viewModel.updateBrushColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false })
    }

    // Eraser Options Dialog
    if (showEraserOptions) {
        DrawingEraserOptionsDialog(
            eraserWidth = strokeWidth,
            onWidthChange = { viewModel.setStrokeWidth(it) },
            onClearCanvas = { viewModel.clearCanvas() },
            onDismissRequest = { showEraserOptions = false })
    }
}

/**
 * Dialog for adjusting brush properties in the drawing screen.
 */
@Composable
fun DrawingBrushOptionsDialog(
    brush: BrushInfo,
    onWidthChange: (Float) -> Unit,
    onColorPickerRequest: () -> Unit,
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
                    OutlinedButton(onClick = onColorPickerRequest) {
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
                        onValueChange = onWidthChange,
                        valueRange = 1f..70f,
                        steps = 7,
                    )
                }
            }
        },
    )
}

/**
 * Dialog for adjusting eraser properties.
 */
@Composable
fun DrawingEraserOptionsDialog(
    eraserWidth: Float,
    onWidthChange: (Float) -> Unit,
    onClearCanvas: () -> Unit,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                            eraserWidth.roundToInt().toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Slider(
                        value = eraserWidth,
                        onValueChange = onWidthChange,
                        valueRange = 5f..200f,
                        steps = 8,
                    )
                }

                // Clear Canvas Button
                OutlinedButton(
                    onClick = {
                        onClearCanvas()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.whiteboard_clear))
                }
            }
        },
    )
}

/**
 * Canvas view for drawing.
 */
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    paths: List<DrawingPath>,
    brushColor: Int,
    strokeWidth: Float,
    isEraserActive: Boolean,
    backgroundColor: Int,
    onPathDrawn: (Path) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    isStylusOnlyMode: Boolean = false,
) {
    // Current path state for live drawing
    var currentPath by remember { mutableStateOf<Path?>(null, policy = neverEqualPolicy()) }

    Canvas(modifier = modifier
        .background(Color(backgroundColor))
        .onSizeChanged {
            onSizeChanged(it.width, it.height)
        }
        .pointerInput(isStylusOnlyMode) {
            detectDragGestures(onDragStart = { offset ->
                val newPath = Path().apply {
                    moveTo(offset.x, offset.y)
                }
                currentPath = newPath
            }, onDrag = { change, _ ->
                // Filter non-stylus input when stylus-only mode is enabled
                if (isStylusOnlyMode && change.type != PointerType.Stylus) {
                    // Clear path to prevent spurious dots from being added in onDragEnd
                    currentPath = null
                    return@detectDragGestures
                }
                val path = currentPath ?: return@detectDragGestures
                val offset = change.position
                path.lineTo(offset.x, offset.y)
                // We need to recompose to show the new line - handled by neverEqualPolicy
                currentPath = path
            }, onDragEnd = {
                currentPath?.let { path ->
                    onPathDrawn(path)
                }
                currentPath = null
            }, onDragCancel = {
                currentPath = null
            })
        }) {
        // Draw all completed paths
        paths.forEach { drawingPath ->
            drawPath(
                path = drawingPath.path.asComposePath(),
                color = if (drawingPath.isEraser) Color(backgroundColor) else Color(drawingPath.color),
                style = Stroke(
                    width = drawingPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round
                )
            )
        }

        // Draw current path being drawn
        currentPath?.let { path ->
            drawPath(
                path = path.asComposePath(),
                color = if (isEraserActive) Color(backgroundColor) else Color(brushColor),
                style = Stroke(
                    width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round
                )
            )
        }
    }
}

@Preview
@Composable
fun DrawingScreenPreview() {
    AnkiDroidTheme {
        DrawingScreen(
            onFinish = {},
            onSave = {},
        )
    }
}
