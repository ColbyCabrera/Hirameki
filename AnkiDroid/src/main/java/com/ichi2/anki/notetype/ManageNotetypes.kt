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

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.notetype.compose.ManageNoteTypesScreen
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.userAcceptsSchemaChange
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.withProgress
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class ManageNotetypes : AnkiActivity() {
    private lateinit var actionBar: ActionBar

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
        actionBar = enableToolbar()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.noteTypes.size) {
                actionBar.subtitle = resources.getQuantityString(
                    R.plurals.model_browser_types_available,
                    uiState.noteTypes.size,
                    uiState.noteTypes.size,
                )
            }

            ManageNoteTypesScreen(
                uiState = uiState,
                onRefresh = { viewModel.refresh() },
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
                onDelete = ::deleteNotetype,
                onNavigateUp = { finish() })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu.findItem(R.id.search_item)
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchView = searchItem?.actionView as? AccessibleSearchView
        searchView?.maxWidth = Integer.MAX_VALUE
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.updateSearchQuery(newText.orEmpty())
                    return true
                }
            },
        )
        return true
    }

    private fun deleteNotetype(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
        launchCatchingTask {
            @StringRes val messageResourceId: Int? = if (userAcceptsSchemaChange()) {
                withProgress {
                    withCol {
                        if (getNotetypeNames().size <= 1) {
                            return@withCol null
                        }
                        R.string.model_delete_warning
                    }
                }
            } else {
                return@launchCatchingTask
            }
            if (messageResourceId == null) {
                showSnackbar(getString(R.string.toast_last_model))
                return@launchCatchingTask
            }
            AlertDialog.Builder(this@ManageNotetypes).show {
                title(R.string.model_browser_delete)
                message(messageResourceId)
                positiveButton(R.string.dialog_positive_delete) {
                    viewModel.deleteNoteType(manageNoteTypeUiModel.id)
                }
                negativeButton(R.string.dialog_cancel)
            }
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
