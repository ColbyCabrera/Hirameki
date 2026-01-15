/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import anki.collection.OpChanges
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserActionHandler
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.MySearchesContract
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.compose.CardBrowserLayout
import com.ichi2.anki.browser.compose.FilterByTagsDialog
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.compose.FlagRenameDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import timber.log.Timber

/**
 * A Jetpack Compose-based Activity for browsing cards.
 *
 * This activity is the entry point for the card browser feature. It hosts the [CardBrowserLayout]
 * Composable, which is responsible for rendering the UI. It retains the [CardBrowserViewModel]
 * for state management and business logic.
 */
open class CardBrowser :
    AnkiActivity(),
    ChangeManager.Subscriber,
    DeckSelectionDialog.DeckSelectionListener,
    TagsDialogListener {

    val fragmented: Boolean
        get() = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_XLARGE

    private lateinit var viewModel: CardBrowserViewModel
    private lateinit var actionHandler: CardBrowserActionHandler

    private val onMySearches = registerForActivityResult(MySearchesContract()) { query ->
        if (query != null) {
            viewModel.search(query)
        }
    }

    private var onEditCardActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.i("onEditCardActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                setResult(DeckPicker.RESULT_DB_ERROR)
                finish()
                return@registerForActivityResult
            }
            if (result.resultCode == RESULT_OK) {
                viewModel.onCurrentNoteEdited()
            }
        }

    private var onAddNoteActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.i("onAddNoteActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                setResult(DeckPicker.RESULT_DB_ERROR)
                finish()
                return@registerForActivityResult
            }
            if (result.resultCode == RESULT_OK) {
                viewModel.search(viewModel.searchQuery.value)
            }
        }

    private var onPreviewCardsActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onPreviewCardsActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                setResult(DeckPicker.RESULT_DB_ERROR)
                finish()
                return@registerForActivityResult
            }
            val data = result.data
            if (data != null && (
                data.getBooleanExtra(
                    NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY,
                    false
                ) || data.getBooleanExtra(NoteEditorFragment.NOTE_CHANGED_EXTRA_KEY, false)
                )
            ) {
                viewModel.search(viewModel.searchQuery.value)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        enableEdgeToEdge()

        val launchOptions = intent?.toCardBrowserLaunchOptions()
        viewModel = createViewModel(launchOptions, fragmented)
        actionHandler = CardBrowserActionHandler(
            this,
            viewModel,
            launchEditCard = { onEditCardActivityResult.launch(it) },
            launchAddNote = { onAddNoteActivityResult.launch(it) },
            launchPreview = { onPreviewCardsActivityResult.launch(it) }
        )

        startLoadingCollection()

        setContentView(R.layout.card_browser_activity)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AnkiDroidTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    var showBrowserOptionsDialog by rememberSaveable { mutableStateOf(false) }
                    var showFilterByTagsDialog by rememberSaveable { mutableStateOf(false) }
                    var showFlagRenameDialog by rememberSaveable { mutableStateOf(false) }
                    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
                    val allTagsState by viewModel.allTags.collectAsStateWithLifecycle()
                    val deckTags by viewModel.deckTags.collectAsStateWithLifecycle()
                    val filterTagsByDeck by viewModel.filterTagsByDeck.collectAsStateWithLifecycle()

                    if (showBrowserOptionsDialog) {
                        BrowserOptionsDialog(
                            onDismissRequest = {
                                showBrowserOptionsDialog = false
                            },
                            onConfirm = { cardsOrNotes, isTruncated, shouldIgnoreAccents ->
                                viewModel.setCardsOrNotes(cardsOrNotes)
                                viewModel.setTruncated(isTruncated)
                                viewModel.setIgnoreAccents(shouldIgnoreAccents)
                            },
                            initialCardsOrNotes = viewModel.cardsOrNotes,
                            initialIsTruncated = viewModel.isTruncated,
                            initialShouldIgnoreAccents = viewModel.shouldIgnoreAccents,
                            onManageColumnsClicked = {
                                val dialog =
                                    BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
                                dialog.show(supportFragmentManager, null)
                            },
                            onRenameFlagClicked = {
                                showBrowserOptionsDialog = false
                                showFlagRenameDialog = true
                            }
                        )
                    }
                    if (showFilterByTagsDialog) {
                        FilterByTagsDialog(
                            onDismissRequest = { showFilterByTagsDialog = false },
                            onConfirm = { tags ->
                                viewModel.filterByTags(tags)
                                showFilterByTagsDialog = false
                            },
                            allTags = allTagsState,
                            initialSelection = selectedTags,
                            deckTags = deckTags,
                            initialFilterByDeck = filterTagsByDeck,
                            onFilterByDeckChanged = viewModel::setFilterTagsByDeck
                        )
                    }
                    if (showFlagRenameDialog) {
                        FlagRenameDialog(
                            onDismissRequest = {
                                showFlagRenameDialog = false
                                invalidateOptionsMenu()
                            }
                        )
                    }
                    CardBrowserLayout(
                        viewModel = viewModel,
                        fragmented = fragmented,
                        onNavigateUp = { finish() },
                        onCardClicked = { row ->
                            if (viewModel.isInMultiSelectMode) {
                                viewModel.toggleRowSelection(
                                    CardBrowserViewModel.RowSelection(
                                        rowId = CardOrNoteId(row.id),
                                        topOffset = 0
                                    )
                                )
                            }
                            else {
                                actionHandler.openNoteEditorForCard(row.id)
                            }
                        },
                        onAddNote = {
                            actionHandler.addNote()
                        },
                        onPreview = {
                            actionHandler.onPreview()
                        },
                        onFilter = viewModel::search,
                        onSelectAll = {
                            viewModel.toggleSelectAllOrNone()
                        },
                        onOptions = {
                            showBrowserOptionsDialog = true
                        },
                        onCreateFilteredDeck = {
                            actionHandler.showCreateFilteredDeckDialog()
                        },
                        onEditNote = {
                            actionHandler.openNoteEditorForCard(viewModel.currentCardId)
                        },
                        onCardInfo = {
                            val cardId = viewModel.currentCardId
                            val destination =
                                CardInfoDestination(cardId, getString(R.string.card_info_title))
                            startActivity(destination.toIntent(this@CardBrowser))
                        },
                        onChangeDeck = {
                            actionHandler.showChangeDeckDialog()
                        },
                        onReposition = {
                            actionHandler.repositionSelectedCards()
                        },
                        onSetDueDate = {
                            actionHandler.rescheduleSelectedCards()
                        },
                        onGradeNow = {
                            actionHandler.onGradeNow()
                        },
                        onResetProgress = {
                            actionHandler.onResetProgress()
                        },
                        onExportCard = {
                            actionHandler.exportSelected()
                        },
                        onFilterByTag = {
                            viewModel.loadAllTags()
                            viewModel.loadDeckTags()
                            showFilterByTagsDialog = true
                        }
                    )
                }
            }
        }
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded(): Collection loaded, ViewModel will start search.")
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) {
            return super.onKeyUp(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    actionHandler.addNote()
                    return true
                }
            }

            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed) {
                    onMySearches.launch(Unit)
                    return true
                }
            }

            KeyEvent.KEYCODE_P -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    actionHandler.onPreview()
                    return true
                }
            }

            KeyEvent.KEYCODE_Z -> {
                if (event.isCtrlPressed) {
                    viewModel.undo()
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun createViewModel(
        launchOptions: CardBrowserLaunchOptions?,
        isFragmented: Boolean
    ) = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = cacheDir,
            options = launchOptions,
            isFragmented = isFragmented
        ),
        defaultViewModelCreationExtras
    )[CardBrowserViewModel::class.java]

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        if (handler === this || handler === viewModel) {
            return
        }

        if (changes.browserTable || changes.noteText || changes.card) {
            viewModel.launchSearchForCards()
        }
    }

    override val shortcuts = null

    companion object {
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter
    ) {
        actionHandler.onSelectedTags(selectedTags, indeterminateTags, stateFilter)
    }


    override fun onDeckSelected(deck: SelectableDeck?) {
        actionHandler.onDeckSelected(deck)
    }

}
