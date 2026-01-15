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
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.compose.BrushOptionsPopup
import com.ichi2.anki.reviewer.compose.ColorPickerDialog
import com.ichi2.anki.reviewer.compose.EraserOptionsPopup
import com.ichi2.anki.reviewer.compose.WhiteboardToolbar
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/**
 * Fragment that displays a whiteboard and its controls.
 */
class WhiteboardFragment : Fragment() {
    private val viewModel: WhiteboardViewModel by viewModels {
        WhiteboardViewModel.factory(AnkiDroidApp.sharedPrefs())
    }
    private val showEraserOptions = MutableStateFlow(false)
    private val showBrushOptionsIndex = MutableStateFlow<Int?>(null)
    private val showAddBrushDialog = MutableStateFlow(false)
    private val showRemoveBrushDialogIndex = MutableStateFlow<Int?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AnkiDroidTheme {
                WhiteboardScreen(viewModel)
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // Collect snackbar events with lifecycle-aware scope
        viewModel.snackbarEvent.onEach { messageResId ->
            showSnackbar(messageResId)
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    @Composable
    private fun WhiteboardScreen(viewModel: WhiteboardViewModel) {
        val alignment by viewModel.toolbarAlignment.collectAsStateWithLifecycle()
        val isStylusOnlyMode by viewModel.isStylusOnlyMode.collectAsStateWithLifecycle()
        val brushes by viewModel.brushes.collectAsStateWithLifecycle()
        val activeBrushIndex by viewModel.activeBrushIndex.collectAsStateWithLifecycle()
        val isEraserActive by viewModel.isEraserActive.collectAsStateWithLifecycle()
        val paths by viewModel.paths.collectAsStateWithLifecycle()
        val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
        val activeStrokeWidth by viewModel.activeStrokeWidth.collectAsStateWithLifecycle()
        val eraserMode by viewModel.eraserMode.collectAsStateWithLifecycle()
        // Note: WhiteboardView needs to know eraser display width too if it draws a preview
        // but looking at WhiteboardView.kt, it uses currentPaint.strokeWidth for eraserPreviewPaint

        Box(modifier = Modifier.fillMaxSize()) {
            // Canvas
            AndroidView(
                factory = { context ->
                    WhiteboardView(context).apply {
                        onNewPath = viewModel::addPath
                        onEraseGestureStart = viewModel::startPathEraseGesture
                        onEraseGestureMove = viewModel::erasePathsAtPoint
                        onEraseGestureEnd = viewModel::endPathEraseGesture
                    }
                },
                update = { view ->
                    view.setHistory(paths)
                    view.setCurrentBrush(brushColor, activeStrokeWidth)
                    view.isEraserActive = isEraserActive
                    view.eraserMode = eraserMode
                    view.isStylusOnlyMode = isStylusOnlyMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Toolbar Positioning logic
            val toolbarAlignment = when (alignment) {
                ToolbarAlignment.BOTTOM -> Alignment.BottomCenter
                ToolbarAlignment.LEFT -> Alignment.CenterStart
                ToolbarAlignment.RIGHT -> Alignment.CenterEnd
            }

            val toolbarPadding = when (alignment) {
                ToolbarAlignment.BOTTOM -> Modifier.padding(
                    bottom = 8.dp,
                    start = 24.dp,
                    end = 24.dp,
                )

                ToolbarAlignment.LEFT -> Modifier.padding(start = 8.dp)
                ToolbarAlignment.RIGHT -> Modifier.padding(end = 8.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(toolbarPadding),
                contentAlignment = toolbarAlignment,
            ) {
                // Popups are placed relative to the toolbar in the composition
                PopupsContent()

                WhiteboardToolbar(viewModel = viewModel, onBrushClick = { _, index ->
                    if (activeBrushIndex == index && !isEraserActive) {
                        showBrushOptionsIndex.value = index
                    } else {
                        viewModel.setActiveBrush(index)
                    }

                }, onBrushLongClick = { index ->
                    if (brushes.size > 1) {
                        showRemoveBrushDialogIndex.value = index
                    } else {
                        viewModel.emitSnackbar(R.string.cannot_remove_last_brush_message)
                    }
                }, onAddBrush = { showAddBrushDialog.value = true }, onEraserClick = {
                    if (isEraserActive) {
                        showEraserOptions.value = !showEraserOptions.value
                    } else {
                        viewModel.enableEraser()
                    }
                })
            }
        }
    }

    @Composable
    private fun PopupsContent() {
        val eraserVisible by showEraserOptions.collectAsStateWithLifecycle()
        val brushIndex by showBrushOptionsIndex.collectAsStateWithLifecycle()
        val addBrushVisible by showAddBrushDialog.collectAsStateWithLifecycle()
        val removeBrushIndex by showRemoveBrushDialogIndex.collectAsStateWithLifecycle()
        val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()

        if (eraserVisible) {
            EraserOptionsPopup(
                viewModel = viewModel,
                onDismissRequest = { showEraserOptions.value = false },
            )
        }

        brushIndex?.let {
            BrushOptionsPopup(
                viewModel = viewModel,
                onDismissRequest = { showBrushOptionsIndex.value = null },
            )
        }

        if (addBrushVisible) {
            ColorPickerDialog(defaultColor = brushColor, onColorPicked = {
                viewModel.addBrush(it)
                showAddBrushDialog.value = false
            }, onDismiss = { showAddBrushDialog.value = false })
        }

        removeBrushIndex?.let { index ->
            AlertDialog(
                onDismissRequest = { showRemoveBrushDialogIndex.value = null },
                confirmButton = {
                    TextButton(onClick = {
                        Timber.i("Removed brush of index %d", index)
                        viewModel.removeBrush(index)
                        showRemoveBrushDialogIndex.value = null
                    }) {
                        Text(stringResource(R.string.dialog_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveBrushDialogIndex.value = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
                text = { Text(stringResource(R.string.whiteboard_remove_brush_message)) },
            )
        }
    }
}
