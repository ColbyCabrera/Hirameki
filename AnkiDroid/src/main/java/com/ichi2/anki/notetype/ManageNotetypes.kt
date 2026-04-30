/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
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
import androidx.compose.ui.platform.ComposeView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
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
            LaunchedEffect(viewModel) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
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
                onNavigateUp = { finish() })
        }
    }

    private inline fun <reified T : AnkiActivity> launchForChanges(extras: Map<String, Any>) {
        val targetIntent = Intent(this@ManageNotetypes, T::class.java).apply {
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
