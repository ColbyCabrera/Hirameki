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

import android.graphics.Canvas
import android.graphics.Path
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import android.graphics.Color.WHITE
import android.graphics.Color.BLACK
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.launch

/**
 * Main composable for the drawing screen.
 * Allows users to draw images to add to their flashcards.
 *
 * @param onSave Callback when the drawing is saved successfully with the result URI
 * @param onCancel Callback when the user cancels without saving
 * @param viewModel The ViewModel managing drawing state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrawingScreen(
    onSave: (Uri) -> Unit,
    onCancel: () -> Unit,
    viewModel: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val paths by viewModel.paths.collectAsState()
    val brushColor by viewModel.brushColor.collectAsState()
    val strokeWidth by viewModel.strokeWidth.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var canvasWidth by remember { mutableIntStateOf(0) }
    var canvasHeight by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.drawing),
                        style = MaterialTheme.typography.displayMediumEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.dialog_cancel),
                        )
                    }
                },
                actions = {
                    // Undo button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = paths.isNotEmpty(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.undo_24px),
                            contentDescription = stringResource(R.string.undo),
                            tint = if (paths.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    // Color palette toggle
                    IconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Icon(
                            painter = painterResource(R.drawable.palette_24px),
                            contentDescription = stringResource(R.string.title_whiteboard_editor),
                        )
                    }
                    // Save button
                    IconButton(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.saveDrawing(context, canvasWidth, canvasHeight)
                                if (uri != null) {
                                    onSave(uri)
                                }
                            }
                        },
                        enabled = paths.isNotEmpty(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            tint = if (paths.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Color palette row (collapsible)
            if (showColorPicker) {
                ColorPaletteRow(
                    selectedColor = brushColor,
                    onColorSelected = { color ->
                        viewModel.setBrushColor(color)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Drawing canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                DrawingCanvas(
                    paths = paths,
                    brushColor = brushColor,
                    strokeWidth = strokeWidth,
                    backgroundColor = MaterialTheme.colorScheme.surface.toArgb(),
                    onPathDrawn = { path -> viewModel.addPath(path) },
                    onSizeChanged = { width, height ->
                        canvasWidth = width
                        canvasHeight = height
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Row of color buttons for selecting brush color.
 */
@Composable
fun ColorPaletteRow(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawingViewModel.PRESET_COLORS.forEach { color ->
            ColorButton(
                color = color,
                isSelected = color == selectedColor,
                onClick = { onColorSelected(color) },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Custom color button placeholder
        // TODO: Add custom color picker dialog
    }
}

/**
 * Individual color button in the palette.
 */
@Composable
fun ColorButton(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * Canvas view for drawing.
 */
@Composable
fun DrawingCanvas(
    paths: List<DrawingPath>,
    brushColor: Int,
    strokeWidth: Float,
    backgroundColor: Int,
    onPathDrawn: (Path) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            DrawingView(context).apply {
                this.onPathDrawn = onPathDrawn
                this.onSizeChanged = onSizeChanged
            }
        },
        update = { view ->
            view.setPaths(paths)
            view.setCurrentBrush(brushColor, strokeWidth)
            view.setBackgroundColor(backgroundColor)
        },
        modifier = modifier,
    )
}

/**
 * Custom View for handling touch drawing.
 */
private class DrawingView(context: android.content.Context) : View(context) {
    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var currentPath = Path()
    private var isDrawing = false
    private var currentColor = BLACK
    private var currentStrokeWidth = 8f
    private var backgroundColor = WHITE

    private var paths: List<DrawingPath> = emptyList()

    var onPathDrawn: ((Path) -> Unit)? = null
    var onSizeChanged: ((Int, Int) -> Unit)? = null

    fun setPaths(newPaths: List<DrawingPath>) {
        paths = newPaths
        invalidate()
    }

    fun setCurrentBrush(color: Int, strokeWidth: Float) {
        currentColor = color
        currentStrokeWidth = strokeWidth
    }

    override fun setBackgroundColor(color: Int) {
        this.backgroundColor = color
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(backgroundColor)

        // Draw all completed paths
        for (drawingPath in paths) {
            paint.color = drawingPath.color
            paint.strokeWidth = drawingPath.strokeWidth
        }

        // Draw current path being drawn
        if (isDrawing) {
            paint.color = currentColor
            paint.strokeWidth = currentStrokeWidth
            canvas.drawPath(currentPath, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                isDrawing = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                isDrawing = false
                onPathDrawn?.invoke(currentPath)
                currentPath = Path()
                invalidate()
                return true
            }
        }
        return false
    }
}

@Preview
@Composable
fun DrawingScreenPreview() {
    AnkiDroidTheme {
        DrawingScreen(
            onSave = {},
            onCancel = {},
        )
    }
}
