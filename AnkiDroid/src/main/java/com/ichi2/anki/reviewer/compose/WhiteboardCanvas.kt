// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
    viewModel: WhiteboardViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paths by viewModel.paths.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val strokeWidth by viewModel.activeStrokeWidth.collectAsStateWithLifecycle()
    val isEraserActive by viewModel.isEraserActive.collectAsStateWithLifecycle()
    val eraserMode by viewModel.eraserMode.collectAsStateWithLifecycle()
    val isStylusOnlyMode by viewModel.isStylusOnlyMode.collectAsStateWithLifecycle()

    val whiteboardView =
        remember {
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
        factory = { whiteboardView },
        modifier = modifier.fillMaxSize(),
    )
}
