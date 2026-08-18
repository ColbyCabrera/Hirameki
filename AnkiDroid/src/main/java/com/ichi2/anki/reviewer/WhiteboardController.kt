/*
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>
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

package com.ichi2.anki.reviewer

import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel
import com.ichi2.anki.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class WhiteboardController(
    private val activity: Reviewer,
    private val viewModel: WhiteboardViewModel,
    private val reviewerViewModel: ReviewerViewModel
) {

    var isEnabled: Boolean = false
        private set
    var isVisible: Boolean = false
        private set

    fun initialize() {
        activity.lifecycleScope.launch {
            isEnabled = withContext(ioDispatcher) {
                MetaDB.getWhiteboardState(activity, activity.parentDid)
            }
            reviewerViewModel.onEvent(ReviewerEvent.OnWhiteboardStateChanged(isEnabled))

            if (isEnabled) {
                isVisible = withContext(ioDispatcher) {
                    MetaDB.getWhiteboardVisibility(activity, activity.parentDid)
                }
                // Apply side effects without redundant DB writes (we just read these values)
                if (isVisible) {
                    activity.disableDrawerSwipe()
                } else {
                    if (!activity.hasDrawerSwipeConflicts()) {
                        activity.enableDrawerSwipe()
                    }
                }
                activity.refreshActionBar()
                // Stylus mode is managed in WhiteboardViewModel
            }
        }
    }

    fun toggle() {
        isEnabled = !isEnabled
        reviewerViewModel.onEvent(ReviewerEvent.OnWhiteboardStateChanged(isEnabled))
        Timber.i("Reviewer:: Whiteboard enabled state set to %b", isEnabled)
        // Even though the visibility is now stored in its own setting, we want it to be dependent
        // on the enabled status
        setEnabledState(isEnabled)
        setVisibility(isEnabled)
        activity.refreshActionBar()
    }

    fun clear() {
        viewModel.clearCanvas()
    }

    fun toggleEraser() {
        if (isVisible && isEnabled) {
            val isCurrentlyErasing = viewModel.isEraserActive.value
            if (isCurrentlyErasing) {
                // Switch back to the last active brush
                viewModel.setActiveBrush(viewModel.activeBrushIndex.value)
                Timber.i("Reviewer:: Whiteboard eraser mode disabled")
                activity.showSnackbar(
                    activity.getString(R.string.white_board_eraser_disabled), Snackbar.LENGTH_SHORT
                )
            } else {
                viewModel.enableEraser()
                Timber.i("Reviewer:: Whiteboard eraser mode enabled")
                activity.showSnackbar(
                    activity.getString(R.string.white_board_eraser_enabled), Snackbar.LENGTH_SHORT
                )
            }
            activity.refreshActionBar()
        }
    }

    private fun setEnabledState(state: Boolean) {
        isEnabled = state
        activity.lifecycleScope.launch(ioDispatcher) {
            MetaDB.storeWhiteboardState(activity, activity.parentDid, state)
        }
    }

    fun setVisibility(state: Boolean) {
        isVisible = state
        activity.lifecycleScope.launch(ioDispatcher) {
            MetaDB.storeWhiteboardVisibility(activity, activity.parentDid, state)
        }
        // Whiteboard visibility is now managed by Compose UI
        // The drawer swipe is still controlled here for backwards compatibility
        if (state) {
            activity.disableDrawerSwipe()
        } else {
            if (!activity.hasDrawerSwipeConflicts()) {
                activity.enableDrawerSwipe()
            }
        }
        activity.refreshActionBar() // Refresh to update icons (e.g. hide whiteboard icon)
    }

    fun saveToFile() {
        activity.lifecycleScope.launch {
            val displayMetrics = activity.resources.displayMetrics
            try {
                val savedFile = viewModel.saveToFile(
                    activity, displayMetrics.widthPixels, displayMetrics.heightPixels
                )
                if (savedFile != null) {
                    activity.showSnackbar(
                        activity.getString(R.string.white_board_image_saved, savedFile.path),
                        Snackbar.LENGTH_SHORT
                    )
                } else {
                    val errorReason = if (viewModel.paths.value.isEmpty()) {
                        activity.getString(R.string.white_board_no_content)
                    } else {
                        activity.getString(R.string.something_wrong)
                    }
                    activity.showSnackbar(
                        activity.getString(R.string.white_board_image_save_failed, errorReason),
                        Snackbar.LENGTH_SHORT
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error saving whiteboard")
                activity.showSnackbar(
                    activity.getString(
                        R.string.white_board_image_save_failed,
                        e.localizedMessage ?: activity.getString(R.string.something_wrong)
                    ), Snackbar.LENGTH_SHORT
                )
            }
        }
    }

    fun updateForNewCard() {
        viewModel.reset()
    }

    fun changePenColor() {
        // No-op in legacy Reviewer, handled by Compose/ViewModel interactions if needed
    }

    /**
     * Returns true if the whiteboard has strokes that can be undone.
     */
    fun canUndo(): Boolean = viewModel.canUndo.value

    /**
     * Undoes the last stroke on the whiteboard.
     */
    fun undo() {
        viewModel.undo()
    }
}
