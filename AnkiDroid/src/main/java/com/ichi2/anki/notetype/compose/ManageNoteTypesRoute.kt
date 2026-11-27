// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.notetype.compose

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.notetype.ManageNoteTypesUiEvent
import com.ichi2.anki.notetype.ManageNoteTypesViewModel
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.userAcceptsSchemaChange

/**
 * Route composable wiring [ManageNoteTypesViewModel], activity result launchers, and schema-change
 * confirmations into [ManageNoteTypesScreen] for the Navigation 3 graph.
 */
@Composable
fun ManageNoteTypesRoute(
    onNavigateUp: () -> Unit,
    viewModel: ManageNoteTypesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current as? AnkiActivity
    var showBatchDeleteConfirmation by remember { mutableStateOf(false) }

    // NoteTypeFieldEditor and CardTemplateEditor do not call setResult(RESULT_OK), so refresh on
    // any return from those activities.
    val outsideChangesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    LaunchedEffect(viewModel, lifecycle, activity) {
        viewModel.uiEvents.flowWithLifecycle(lifecycle).collect { event ->
            when (event) {
                is ManageNoteTypesUiEvent.ShowErrorMessage -> {
                    activity?.showSnackbar(event.message)
                }

                is ManageNoteTypesUiEvent.ShowSnackbar -> {
                    activity?.showSnackbar(activity.getString(event.messageId))
                }

                is ManageNoteTypesUiEvent.PromptSchemaChangeWarning -> {
                    activity?.launchCatchingTask {
                        if (activity.userAcceptsSchemaChange()) {
                            viewModel.showDeleteConfirmation(event.noteType)
                        }
                    }
                }

                is ManageNoteTypesUiEvent.PromptDeleteSelectedConfirmation -> {
                    activity?.launchCatchingTask {
                        if (activity.userAcceptsSchemaChange()) {
                            showBatchDeleteConfirmation = true
                        }
                    }
                }
            }
        }
    }

    ManageNoteTypesScreen(
        uiState = uiState,
        onSearch = { viewModel.updateSearchQuery(it) },
        onAddNoteType = { name, option -> viewModel.addNoteType(name, option) },
        onShowFields = {
            outsideChangesLauncher.launch(
                Intent(context, NoteTypeFieldEditor::class.java).apply {
                    putExtra("title", it.name)
                    putExtra("noteTypeID", it.id)
                },
            )
        },
        onEditCards = {
            outsideChangesLauncher.launch(
                Intent(context, CardTemplateEditor::class.java).apply {
                    putExtra("noteTypeId", it.id)
                },
            )
        },
        onRename = { viewModel.renameNoteType(it.id, it.name) },
        onDeleteRequest = { viewModel.requestDeleteNoteType(it) },
        onDeleteConfirm = { viewModel.confirmDeleteNoteType(it.id) },
        onDeleteDismiss = { viewModel.dismissDeleteConfirmation() },
        onToggleSelection = { viewModel.toggleNoteTypeSelection(it) },
        onSelectAll = { viewModel.selectAllNoteTypes() },
        onDeselectAll = { viewModel.deselectAllNoteTypes() },
        onDeleteSelected = { viewModel.deleteSelectedNoteTypes() },
        onNavigateUp = onNavigateUp,
    )

    if (showBatchDeleteConfirmation) {
        DeleteSelectedNoteTypesDialog(
            count = uiState.selectedNoteTypeIds.size,
            onDismissRequest = { showBatchDeleteConfirmation = false },
            onConfirm = {
                viewModel.confirmDeleteSelectedNoteTypes()
                showBatchDeleteConfirmation = false
            },
        )
    }
}
