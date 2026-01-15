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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardView
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel

/**
 * Compose wrapper for WhiteboardView that connects it to WhiteboardViewModel.
 */
@Composable
fun WhiteboardCanvas(
    viewModel: WhiteboardViewModel, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val paths by viewModel.paths.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val strokeWidth by viewModel.activeStrokeWidth.collectAsStateWithLifecycle()
    val isEraserActive by viewModel.isEraserActive.collectAsStateWithLifecycle()
    val eraserMode by viewModel.eraserMode.collectAsStateWithLifecycle()
    val isStylusOnlyMode by viewModel.isStylusOnlyMode.collectAsStateWithLifecycle()

    val whiteboardView = remember {
        WhiteboardView(context).apply {
            onNewPath = viewModel::addPath
            onEraseGestureStart = viewModel::startPathEraseGesture
            onEraseGestureMove = viewModel::erasePathsAtPoint
            onEraseGestureEnd = viewModel::endPathEraseGesture
        }
    }

    // Update the view when paths change
    LaunchedEffect(paths) {
        whiteboardView.setHistory(paths)
    }

    // Update brush/eraser settings
    LaunchedEffect(brushColor, strokeWidth) {
        whiteboardView.setCurrentBrush(brushColor, strokeWidth)
    }

    LaunchedEffect(isEraserActive) {
        whiteboardView.isEraserActive = isEraserActive
    }

    LaunchedEffect(eraserMode) {
        whiteboardView.eraserMode = eraserMode
    }

    LaunchedEffect(isStylusOnlyMode) {
        whiteboardView.isStylusOnlyMode = isStylusOnlyMode
    }

    AndroidView(
        factory = { whiteboardView }, modifier = modifier.fillMaxSize()
    )
}
