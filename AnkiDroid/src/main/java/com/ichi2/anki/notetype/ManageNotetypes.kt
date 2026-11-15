// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.notetype

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.notetype.compose.DeleteSelectedNoteTypesDialog
import com.ichi2.anki.notetype.compose.ManageNoteTypesScreen
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.userAcceptsSchemaChange
import com.ichi2.anki.utils.Destination

class ManageNotetypes : AnkiActivity() {
    private val viewModel: ManageNoteTypesViewModel by viewModels()

    private val outsideChangesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                viewModel.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        setTitle(R.string.model_browser_label)
        setContentView(R.layout.manage_notetypes)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showBatchDeleteConfirmation by remember { mutableStateOf(false) }
            LaunchedEffect(viewModel) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is ManageNoteTypesUiEvent.ShowErrorMessage -> {
                            showSnackbar(event.message)
                        }

                        is ManageNoteTypesUiEvent.ShowSnackbar -> {
                            showSnackbar(getString(event.messageId))
                        }

                        is ManageNoteTypesUiEvent.PromptSchemaChangeWarning -> {
                            launchCatchingTask {
                                if (userAcceptsSchemaChange()) {
                                    viewModel.showDeleteConfirmation(event.noteType)
                                }
                            }
                        }

                        is ManageNoteTypesUiEvent.PromptDeleteSelectedConfirmation -> {
                            launchCatchingTask {
                                if (userAcceptsSchemaChange()) {
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
                    launchForChanges<NoteTypeFieldEditor>(
                        mapOf(
                            "title" to it.name,
                            "noteTypeID" to it.id,
                        ),
                    )
                },
                onEditCards = { launchForChanges<CardTemplateEditor>(mapOf("noteTypeId" to it.id)) },
                onRename = { viewModel.renameNoteType(it.id, it.name) },
                onDeleteRequest = { viewModel.requestDeleteNoteType(it) },
                onDeleteConfirm = { viewModel.confirmDeleteNoteType(it.id) },
                onDeleteDismiss = { viewModel.dismissDeleteConfirmation() },
                onToggleSelection = { viewModel.toggleNoteTypeSelection(it) },
                onSelectAll = { viewModel.selectAllNoteTypes() },
                onDeselectAll = { viewModel.deselectAllNoteTypes() },
                onDeleteSelected = { viewModel.deleteSelectedNoteTypes() },
                onNavigateUp = { finish() },
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
    }

    private inline fun <reified T : AnkiActivity> launchForChanges(extras: Map<String, Any>) {
        val targetIntent =
            Intent(this@ManageNotetypes, T::class.java).apply {
                extras.forEach { toExtra(it) }
            }
        outsideChangesLauncher.launch(targetIntent)
    }

    private fun Intent.toExtra(newExtra: Map.Entry<String, Any>) {
        when (newExtra.value) {
            is String -> putExtra(newExtra.key, newExtra.value as String)
            is Long -> putExtra(newExtra.key, newExtra.value as Long)
            else -> throw IllegalArgumentException("Unexpected value type: ${newExtra.value}")
        }
    }
}

class ManageNoteTypesDestination : Destination {
    override fun toIntent(context: Context) = Intent(context, ManageNotetypes::class.java)
}
