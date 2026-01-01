/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller.Companion.fromValue
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.clozeNumbersInNote
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.multimedia.AudioRecordingFragment
import com.ichi2.anki.multimedia.AudioVideoFragment
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.anki.multimedia.MultimediaViewModel
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.EFieldType
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.noteeditor.ClozeInsertionMode
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.noteeditor.NoteEditorViewModel
import com.ichi2.anki.noteeditor.ToolbarButtonModel
import com.ichi2.anki.noteeditor.compose.NoteEditorScreen
import com.ichi2.anki.noteeditor.compose.NoteEditorSimpleOverflowItem
import com.ichi2.anki.noteeditor.compose.NoteEditorToggleOverflowItem
import com.ichi2.anki.noteeditor.compose.NoteEditorTopAppBar
import com.ichi2.anki.noteeditor.compose.AddToolbarItemDialog
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.openUrl
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.ClipboardUtil
import com.ichi2.utils.HashUtil
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.IntentUtil.resolveMimeType
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import java.util.function.Consumer

const val CALLER_KEY = "caller"

/**
 * Fragment for creating and editing notes (flashcards) in Anki.
 *
 * A note contains field data that generates one or more cards based on its note type template.
 * This editor allows users to:
 * - Add new notes or edit existing ones
 * - Select note types and target decks
 * - Edit field contents with rich text formatting
 * - Add multimedia (images, audio, video) to fields
 * - Manage tags
 * - Preview cards before saving
 *
 * The UI is built using Jetpack Compose via [NoteEditorScreen].
 *
 * @see [Anki Desktop manual](https://docs.ankiweb.net/getting-started.html.cards)
 */
class NoteEditorFragment : Fragment(R.layout.note_editor_fragment), DeckSelectionListener,
    TagsDialogListener, BaseSnackbarBuilderProvider, DispatchKeyEventListener,
    ShortcutGroupProvider {
    /** Whether any change are saved. E.g. multimedia, new card added, field changed and saved. */
    private var changed = false

    private var multimediaActionJob: Job? = null

    private val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    /**
     * Flag which forces the calling activity to rebuild it's definition of current card from scratch
     */
    private var reloadRequired = false

    private var tagsDialogFactory: TagsDialogFactory? = null

    // non-null after onCollectionLoaded
    private var editorNote: Note? = null

    private val multimediaViewModel: MultimediaViewModel by activityViewModels()

    // Null if adding a new card. Presently NonNull if editing an existing note - but this is subject to change
    private var currentEditedCard: Card? = null

    @get:VisibleForTesting
    var deckId: DeckId = 0
        private set

    // indicates if a new note is added or a card is edited
    private var addNote = false
    private var aedictIntent = false

    // indicates which activity called Note Editor
    private var caller = NoteEditorCaller.NO_CALLER

    private var sourceText: Array<String?>? = null

    var clipboard: ClipboardManager? = null

    /**
     * Whether this is displayed in a fragment view.
     * If true, this fragment is on the trailing side of the card browser.
     */
    private val inCardBrowserActivity
        get() = requireArguments().getBoolean(IN_CARD_BROWSER_ACTIVITY)

    private val requestAddLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        NoteEditorActivityResultCallback {
            if (it.resultCode != RESULT_CANCELED) {
                changed = true
            }
        },
    )

    private val multimediaFragmentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        NoteEditorActivityResultCallback { result ->
            if (result.resultCode == RESULT_CANCELED) {
                Timber.d("Multimedia result canceled")
                val index = result.data?.extras?.getInt(MULTIMEDIA_RESULT_FIELD_INDEX)
                    ?: return@NoteEditorActivityResultCallback
                showMultimediaBottomSheet()
                handleMultimediaActions(index)
                return@NoteEditorActivityResultCallback
            }

            Timber.d("Getting multimedia result")
            val extras = result.data?.extras ?: return@NoteEditorActivityResultCallback
            handleMultimediaResult(extras)
        },
    )

    private val requestTemplateEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        NoteEditorActivityResultCallback {
            // Note type can change regardless of exit type - update ourselves and CardBrowser
            reloadRequired = true

            // Update cards info after template changes
            Timber.d("onActivityResult() template edit return")
            lifecycleScope.launch {
                try {
                    val col = getColUnsafe

                    // Get the current note from ViewModel
                    val currentNote = noteEditorViewModel.currentNote.value
                    if (currentNote != null) {
                        // Sync the fragment's editorNote with the ViewModel's current note
                        editorNote = currentNote
                        // Reload the note type to ensure we have the latest version
                        val notetype = col.notetypes.get(currentNote.noteTypeId)
                        if (notetype != null) {
                            // Update cards display
                            updateCards(notetype)
                            Timber.d("Updated cards for note type: %s", notetype.name)
                        } else {
                            Timber.w("Note type not found for note")
                            showSnackbar(R.string.something_wrong)
                        }
                    } else {
                        Timber.w("Current note is null after template edit")
                        showSnackbar(R.string.something_wrong)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error updating editor after template edit")
                    showSnackbar(R.string.something_wrong)
                }
            }
        },
    )

    private val ioEditorLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            ImportUtils.getFileCachedCopy(requireContext(), uri)?.let { path ->
                setupImageOcclusionEditor(path)
            }
        }
    }

    private val requestIOEditorCloser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        NoteEditorActivityResultCallback { result ->
            if (result.resultCode != RESULT_CANCELED) {
                changed = true
                if (!addNote) {
                    reloadRequired = true
                    closeNoteEditor(RESULT_UPDATED_IO_NOTE, null)
                }
            }
        },
    )

    private inner class NoteEditorActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            Timber.d("onActivityResult() with result: %s", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeNoteEditor(DeckPicker.RESULT_DB_ERROR, null)
            }
            callback(result)
        }
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }
        require(deck is SelectableDeck.Deck)
        deckId = deck.deckId

        // Update ViewModel for Compose UI
        noteEditorViewModel.selectDeck(deck.name)
    }

    private enum class AddClozeType {
        SAME_NUMBER, INCREMENT_NUMBER,
    }

    @VisibleForTesting
    var addNoteErrorMessage: String? = null

    private fun displayErrorSavingNote() {
        val errorMessage = snackbarErrorText
        // Anki allows to proceed in case we try to add non cloze text in cloze field with warning,
        // this snackbar helps replicate similar behaviour
        if (errorMessage == TR.addingYouHaveAClozeDeletionNote()) {
            noClozeDialog(errorMessage)
        } else {
            showSnackbar(errorMessage)
        }
    }

    private fun noClozeDialog(errorMessage: String) {
        noteEditorViewModel.showNoClozeDialog(errorMessage)
    }

    @VisibleForTesting
    val snackbarErrorText: String
        get() = when {
            addNoteErrorMessage != null -> addNoteErrorMessage!!
            allFieldsHaveContent() -> resources.getString(R.string.note_editor_no_cards_created_all_fields)
            else -> resources.getString(R.string.note_editor_no_cards_created)
        }

    override val baseSnackbarBuilder: SnackbarBuilder = {}

    private fun allFieldsHaveContent() = currentFieldStrings.none { it.isNullOrEmpty() }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        tagsDialogFactory =
            TagsDialogFactory(this).attachToFragmentManager<TagsDialogFactory>(parentFragmentManager)
        super.onCreate(savedInstanceState)
        val intent = requireActivity().intent
        if (savedInstanceState != null) {
            caller = fromValue(savedInstanceState.getInt(CALLER_KEY))
            addNote = savedInstanceState.getBoolean("addNote")
            deckId = savedInstanceState.getLong("did")
            // Tags are restored via ViewModel's SavedStateHandle, not instance state
            reloadRequired = savedInstanceState.getBoolean(RELOAD_REQUIRED_EXTRA_KEY)
            changed = savedInstanceState.getBoolean(NOTE_CHANGED_EXTRA_KEY)
        } else {
            caller =
                fromValue(requireArguments().getInt(EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value))
            if (caller == NoteEditorCaller.NO_CALLER) {
                val action = intent.action
                if (ACTION_CREATE_FLASHCARD == action || ACTION_CREATE_FLASHCARD_SEND == action || Intent.ACTION_PROCESS_TEXT == action) {
                    caller = NoteEditorCaller.NOTEEDITOR_INTENT_ADD
                }
            }
        }
    }

    private val noteEditorViewModel: NoteEditorViewModel by activityViewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        try {
            setupComposeEditor(getColUnsafe)
        } catch (ex: BackendException) {
            // Specific backend exceptions (database locked, corrupt, etc.)
            Timber.w(ex, "setupComposeEditor - backend exception")
            requireAnkiActivity().onCollectionLoadError()
            return
        } catch (ex: IllegalStateException) {
            // State errors (invalid card ID, missing note, etc.)
            Timber.w(ex, "setupComposeEditor - illegal state")
            requireAnkiActivity().onCollectionLoadError()
            return
        } catch (ex: Exception) {
            // Catch-all for unexpected errors during setup
            Timber.e(ex, "setupComposeEditor - unexpected error")
            CrashReportService.sendExceptionReport(ex, "NoteEditorFragment::setupComposeEditor")
            requireAnkiActivity().onCollectionLoadError()
            return
        }

        // TODO this callback doesn't handle predictive back navigation!
        // see #14678, added to temporarily fix for a bug
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            Timber.i("NoteEditor:: onBackPressed()")
            closeCardEditorWithCheck()
        }
    }

    /**
     * Setup the Compose-based note editor
     */
    private fun setupComposeEditor(col: Collection) {
        Timber.d("NoteEditor() setupComposeEditor: caller: %s", caller)

        // Initialize editor logic (clipboard, caller determination, ViewModel setup)
        if (!initializeEditorLogic(col)) return

        // Set toolbar title
        if (addNote) {
            requireAnkiActivity().setTitle(R.string.cardeditor_title_add_note)
        } else {
            requireAnkiActivity().setTitle(R.string.cardeditor_title_edit_card)
        }

        updateToolbar()

        setupComposeContent()
    }

    /**
     * Initialize editor logic: clipboard, caller determination, and ViewModel setup.
     * @return true if initialization succeeded, false if the editor should close
     */
    private fun initializeEditorLogic(col: Collection): Boolean {
        val intent = requireActivity().intent

        requireAnkiActivity().registerReceiver()

        try {
            clipboard =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }

        aedictIntent = false
        currentEditedCard = null

        // Determine if we're adding or editing
        when (caller) {
            NoteEditorCaller.NO_CALLER -> {
                Timber.e("no caller could be identified, closing")
                requireActivity().finish()
                return false
            }

            NoteEditorCaller.EDIT, NoteEditorCaller.PREVIEWER_EDIT -> {
                val cardId =
                    requireNotNull(requireArguments().getLong(EXTRA_CARD_ID)) { "EXTRA_CARD_ID" }
                currentEditedCard = col.getCard(cardId)
                editorNote = currentEditedCard!!.note(col)
                addNote = false
            }

            NoteEditorCaller.NOTEEDITOR_INTENT_ADD,
            NoteEditorCaller.INSTANT_NOTE_EDITOR,
                -> {
                fetchIntentInformation(intent)
                if (sourceText == null) {
                    requireActivity().finish()
                    return false
                }
                if ("Aedict Notepad" == sourceText!![0] && addFromAedict(sourceText!![1])) {
                    requireActivity().finish()
                    return false
                }
                addNote = true
            }

            else -> {
                addNote = true
            }
        }

        // Extract text from intent for ACTION_PROCESS_TEXT or similar intents
        val initialFieldText: String? = when {
            sourceText != null && sourceText!![0] != null -> sourceText!![0]
            else -> null
        }

        // Initialize ViewModel
        noteEditorViewModel.initializeEditor(
            col = col,
            cardId = currentEditedCard?.id,
            deckId = requireArguments().getLong(EXTRA_DID, 0L),
            isAddingNote = addNote,
            initialFieldText = initialFieldText,
        ) { success, _ ->
            if (success) {
                // Sync Fragment's deckId with ViewModel's deckId after initialization
                // This ensures the hasUnsavedChanges() deck comparison works correctly
                launchCatchingTask {
                    deckId = noteEditorViewModel.currentNote.value?.let { _ ->
                        if (addNote) {
                            // For new notes, use the calculated deck from ViewModel
                            noteEditorViewModel.noteEditorState.value.selectedDeckName.let { deckName ->
                                withCol {
                                    decks.allNamesAndIds().find { it.name == deckName }?.id ?: 0L
                                }
                            }
                        } else {
                            // For existing cards, use the card's current deck
                            currentEditedCard?.currentDeckId() ?: 0L
                        }
                    } ?: 0L
                }
                // Update cards info for the selected note type
                if (editorNote != null) {
                    updateCards(editorNote!!.notetype)
                } else {
                    // For new notes, get the note type from the ViewModel state
                    val currentNotetype = col.notetypes.current()
                    updateCards(currentNotetype)
                }

                // Handle Copy Note: Apply copied field contents and tags after ViewModel init
                if (addNote) {
                    val copiedContents = requireArguments().getString(EXTRA_CONTENTS)
                    copiedContents?.let { contents ->
                        Timber.d("setupComposeEditor: Applying copied field contents")
                        setEditFieldTexts(contents)
                    }

                    val copiedTags = requireArguments().getStringArray(EXTRA_TAGS)
                    copiedTags?.let { tags ->
                        Timber.d(
                            "setupComposeEditor: Applying copied tags: %s", tags.joinToString()
                        )
                        noteEditorViewModel.updateTags(tags.toSet())
                    }
                }
            }
        }
        return true
    }

    /**
     * Setup the Compose content for the note editor.
     */
    private fun setupComposeContent() {
        val composeView = view?.findViewById<ComposeView>(R.id.note_editor_compose)

        composeView?.setContent {
            AnkiDroidTheme {
                val noteEditorState by noteEditorViewModel.noteEditorState.collectAsState()
                val availableDecks by noteEditorViewModel.availableDecks.collectAsState()
                val availableNoteTypes by noteEditorViewModel.availableNoteTypes.collectAsState()
                val toolbarButtons by noteEditorViewModel.toolbarButtons.collectAsState()
                val showToolbar by noteEditorViewModel.showToolbar.collectAsState()
                val allTags by noteEditorViewModel.tagsState.collectAsState()
                val deckTags by noteEditorViewModel.deckTags.collectAsState()
                val showDiscardChangesDialog by noteEditorViewModel.showDiscardChangesDialog.collectAsState()
                val noClozeDialogMessage by noteEditorViewModel.noClozeDialogState.collectAsState()
                val toolbarDialogState by noteEditorViewModel.toolbarDialogState.collectAsState()
                val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                var capitalizeChecked by remember {
                    mutableStateOf(
                        sharedPrefs().getBoolean(
                            PREF_NOTE_EDITOR_CAPITALIZE, true
                        )
                    )
                }
                var scrollToolbarChecked by remember {
                    mutableStateOf(
                        sharedPrefs().getBoolean(
                            PREF_NOTE_EDITOR_SCROLL_TOOLBAR, true
                        )
                    )
                }

                NoteEditorScreen(
                    state = noteEditorState,
                    availableDecks = availableDecks,
                    availableNoteTypes = availableNoteTypes,
                    onFieldValueChange = { index, value ->
                        noteEditorViewModel.updateFieldValue(index, value)
                    },
                    onFieldFocus = { index ->
                        noteEditorViewModel.onFieldFocus(index)
                    },
                    onCardsClick = {
                        showCardTemplateEditor()
                    },
                    onDeckSelected = { deckName ->
                        // Find the deck ID and update both fragment and ViewModel
                        launchCatchingTask {
                            val deck = withCol {
                                decks.allNamesAndIds().find { it.name == deckName }
                            }
                            if (deck == null) {
                                Timber.w("onDeckSelected: Deck not found for name '%s'", deckName)
                                showSnackbar(getString(R.string.deck_not_found))
                                return@launchCatchingTask
                            }
                            deckId = deck.id
                            noteEditorViewModel.selectDeck(deckName)
                        }
                    },
                    onNoteTypeSelected = { noteTypeName ->
                        noteEditorViewModel.selectNoteType(noteTypeName)
                        // Update cards info after note type change
                        launchCatchingTask {
                            val notetype =
                                withCol { notetypes.all().find { it.name == noteTypeName } }
                            if (notetype != null) {
                                updateCards(notetype)
                            }
                        }
                    },
                    onMultimediaClick = { index ->
                        showMultimediaBottomSheet()
                        handleMultimediaActions(index)
                    },
                    onToggleStickyClick = { index ->
                        noteEditorViewModel.toggleStickyField(index)
                    },
                    onSaveClick = {
                        launchCatchingTask { saveNote() }
                    },
                    onPreviewClick = {
                        launchCatchingTask { performPreview() }
                    },
                    onBoldClick = {
                        applyFormatter("<b>", "</b>")
                    },
                    onItalicClick = {
                        applyFormatter("<i>", "</i>")
                    },
                    onUnderlineClick = {
                        applyFormatter("<u>", "</u>")
                    },
                    onHorizontalRuleClick = {
                        applyFormatter("<hr>", "")
                    },
                    onHeadingClick = {
                        displayInsertHeadingDialog()
                    },
                    onFontSizeClick = {
                        displayFontSizeDialog()
                    },
                    onMathjaxClick = {
                        applyFormatter("\\(", "\\)")
                    },
                    onMathjaxLongClick = {
                        displayInsertMathJaxEquationsDialog()
                    },
                    onClozeClick = {
                        handleClozeInsertion(ClozeInsertionMode.SAME_NUMBER)
                    },
                    onClozeIncrementClick = {
                        handleClozeInsertion(ClozeInsertionMode.INCREMENT_NUMBER)
                    },
                    onCustomButtonClick = { button ->
                        noteEditorViewModel.applyToolbarButton(button)
                    },
                    onCustomButtonLongClick = { button ->
                        displayEditToolbarDialog(button.toCustomToolbarButton())
                    },
                    onAddCustomButtonClick = {
                        displayAddToolbarDialog()
                    },
                    customToolbarButtons = toolbarButtons,
                    isToolbarVisible = showToolbar,
                    allTags = allTags,
                    deckTags = deckTags,
                    onUpdateTags = { tags ->
                        noteEditorViewModel.updateTags(tags)
                    },
                    onAddTag = { tag ->
                        noteEditorViewModel.addTag(tag)
                    },
                    topBar = {
                        val title = stringResource(
                            if (noteEditorState.isAddingNote) {
                                R.string.cardeditor_title_add_note
                            } else {
                                R.string.cardeditor_title_edit_card
                            },
                        )
                        val allowSaveAndPreview =
                            !(noteEditorState.isAddingNote && noteEditorState.isImageOcclusion)
                        val copyEnabled = noteEditorState.fields.any { it.value.text.isNotBlank() }

                        val overflowItems = listOf(
                            NoteEditorSimpleOverflowItem(
                                id = "add_note",
                                title = stringResource(R.string.menu_add),
                                visible = !inCardBrowserActivity && !noteEditorState.isAddingNote,
                            ) {
                                addNewNote()
                            },
                            NoteEditorSimpleOverflowItem(
                                id = "copy_note",
                                title = stringResource(R.string.note_editor_copy_note),
                                visible = !noteEditorState.isAddingNote,
                                enabled = copyEnabled,
                            ) {
                                copyNote()
                            },
                            NoteEditorSimpleOverflowItem(
                                id = "font_size",
                                title = stringResource(R.string.menu_font_size),
                            ) {
                                displayFontSizeDialog()
                            },
                            NoteEditorToggleOverflowItem(
                                id = "show_toolbar",
                                title = stringResource(R.string.menu_show_toolbar),
                                checked = showToolbar,
                                onCheckedChange = { isChecked ->
                                    sharedPrefs().edit {
                                        putBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, isChecked)
                                    }
                                    updateToolbar()
                                },
                            ),
                            NoteEditorToggleOverflowItem(
                                id = "capitalize",
                                title = stringResource(R.string.note_editor_capitalize),
                                checked = capitalizeChecked,
                                onCheckedChange = { isChecked ->
                                    capitalizeChecked = isChecked
                                    toggleCapitalize(isChecked)
                                },
                            ),
                            NoteEditorToggleOverflowItem(
                                id = "scroll_toolbar",
                                title = stringResource(R.string.menu_scroll_toolbar),
                                checked = scrollToolbarChecked,
                                onCheckedChange = { isChecked ->
                                    scrollToolbarChecked = isChecked
                                    sharedPrefs().edit {
                                        putBoolean(PREF_NOTE_EDITOR_SCROLL_TOOLBAR, isChecked)
                                    }
                                    updateToolbar()
                                },
                            ),
                        )

                        NoteEditorTopAppBar(
                            title = title,
                            onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                            showSaveAction = allowSaveAndPreview,
                            saveEnabled = allowSaveAndPreview,
                            onSaveClick = {
                                launchCatchingTask { saveNote() }
                            },
                            showPreviewAction = allowSaveAndPreview,
                            previewEnabled = allowSaveAndPreview,
                            onPreviewClick = {
                                launchCatchingTask { performPreview() }
                            },
                            overflowItems = overflowItems,
                        )
                    },
                    onImageOcclusionSelectImage = {
                        try {
                            ioEditorLauncher.launch("image/*")
                        } catch (_: ActivityNotFoundException) {
                            Timber.w("No app found to handle image selection")
                            requireActivity().showSnackbar(R.string.activity_start_failed)
                        }
                    },
                    onImageOcclusionPasteImage = {
                        if (ClipboardUtil.hasImage(clipboard)) {
                            val uri = ClipboardUtil.getUri(clipboard)
                            val i = Intent().apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newUri(
                                    requireActivity().contentResolver, uri.toString(), uri
                                )
                            }
                            ImportUtils.getFileCachedCopy(requireContext(), i)?.let { path ->
                                setupImageOcclusionEditor(path)
                            }
                        } else {
                            showSnackbar(TR.editingNoImageFoundOnClipboard())
                        }
                    },
                    onImageOcclusionEdit = {
                        setupImageOcclusionEditor()
                    },
                    snackbarHostState = snackbarHostState,
                    showDiscardChangesDialog = showDiscardChangesDialog,
                    onDiscardChanges = {
                        noteEditorViewModel.setShowDiscardChangesDialog(false)
                        closeNoteEditor()
                    },
                    onKeepEditing = {
                        noteEditorViewModel.setShowDiscardChangesDialog(false)
                    },
                    noClozeDialogMessage = noClozeDialogMessage,
                    onSaveAnywayClick = {
                        noteEditorViewModel.dismissNoClozeDialog()
                        launchCatchingTask { saveNote() }
                    },
                    onDismissNoClozeDialog = {
                        noteEditorViewModel.dismissNoClozeDialog()
                    },
                )

                // Toolbar Item Dialog (Add/Edit)
                AddToolbarItemDialog(
                    state = com.ichi2.anki.noteeditor.compose.ToolbarItemDialogState(
                        isVisible = toolbarDialogState.isVisible,
                        isEditMode = toolbarDialogState.isEditMode,
                        icon = toolbarDialogState.icon,
                        prefix = toolbarDialogState.prefix,
                        suffix = toolbarDialogState.suffix,
                        buttonIndex = toolbarDialogState.buttonIndex,
                    ),
                    onDismissRequest = {
                        noteEditorViewModel.dismissToolbarDialog()
                    },
                    onConfirm = { icon, prefix, suffix ->
                        noteEditorViewModel.dismissToolbarDialog()
                        if (toolbarDialogState.isEditMode) {
                            editToolbarButton(icon, prefix, suffix, toolbarDialogState.buttonIndex)
                        } else {
                            addToolbarButton(icon, prefix, suffix)
                        }
                    },
                    onDelete = if (toolbarDialogState.isEditMode) {
                        {
                            noteEditorViewModel.dismissToolbarDialog()
                            removeToolbarButton(toolbarDialogState.buttonIndex)
                        }
                    } else null,
                    onHelpClick = {
                        requireContext().openUrl(R.string.link_manual_note_format_toolbar)
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        addInstanceStateToBundle(outState)
        super.onSaveInstanceState(outState)
    }

    private fun addInstanceStateToBundle(savedInstanceState: Bundle) {
        Timber.i("Saving instance")
        savedInstanceState.putInt(CALLER_KEY, caller.value)
        savedInstanceState.putBoolean("addNote", addNote)
        savedInstanceState.putLong("did", deckId)
        savedInstanceState.putBoolean(NOTE_CHANGED_EXTRA_KEY, changed)
        savedInstanceState.putBoolean(RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
        // Tags are persisted via ViewModel's SavedStateHandle, not instance state
    }

    private fun applyFormatter(
        prefix: String,
        suffix: String,
    ) {
        noteEditorViewModel.formatSelection(prefix, suffix)
    }

    private fun displayFontSizeDialog() {
        val sizeCodes = resources.getStringArray(R.array.html_size_codes)
        AlertDialog.Builder(requireContext()).show {
            setItems(R.array.html_size_code_labels) { _, index ->
                val size = sizeCodes.getOrNull(index) ?: return@setItems
                applyFormatter("<span style=\"font-size:$size\">", "</span>")
            }
            title(R.string.menu_font_size)
        }
    }

    private fun displayInsertHeadingDialog() {
        val headingTags = arrayOf("h1", "h2", "h3", "h4", "h5")
        AlertDialog.Builder(requireContext()).show {
            setItems(headingTags) { _, index ->
                val tag = headingTags.getOrNull(index) ?: return@setItems
                applyFormatter("<$tag>", "</$tag>")
            }
            title(R.string.insert_heading)
        }
    }

    private fun displayInsertMathJaxEquationsDialog() {
        data class MathJaxOption(
            val label: String,
            val prefix: String,
            val suffix: String,
        )

        val options = arrayOf(
            MathJaxOption(TR.editingMathjaxBlock(), prefix = "\\[\\", suffix = "\\]"),
            MathJaxOption(TR.editingMathjaxChemistry(), prefix = "\\( \\ce{", suffix = "} \\)"),
        )

        AlertDialog.Builder(requireContext()).show {
            setItems(options.map(MathJaxOption::label).toTypedArray()) { _, index ->
                val option = options.getOrNull(index) ?: return@setItems
                applyFormatter(option.prefix, option.suffix)
            }
            title(R.string.insert_mathjax)
        }
    }

    private fun handleClozeInsertion(mode: ClozeInsertionMode) {
        val isClozeType = noteEditorViewModel.noteEditorState.value.isClozeType
        if (!isClozeType) {
            showSnackbar(R.string.note_editor_insert_cloze_no_cloze_note_type)
            return
        }
        noteEditorViewModel.insertCloze(mode)
    }

    private fun handleToolbarShortcut(event: KeyEvent): Boolean {
        if (!event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) {
            return false
        }
        val digit = when (event.keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
            else -> return false
        }
        return noteEditorViewModel.applyToolbarShortcut(digit)
    }

    private fun ToolbarButtonModel.toCustomToolbarButton(): CustomToolbarButton =
        CustomToolbarButton(index = index, buttonText = text, prefix = prefix, suffix = suffix)

    private fun AddClozeType.toClozeMode(): ClozeInsertionMode = when (this) {
        AddClozeType.SAME_NUMBER -> ClozeInsertionMode.SAME_NUMBER
        AddClozeType.INCREMENT_NUMBER -> ClozeInsertionMode.INCREMENT_NUMBER
    }

    override fun onStop() {
        super.onStop()
        if (!isRemoving) {
            WidgetStatus.updateInBackground(requireContext())
        }
    }

    @KotlinCleanup("convert KeyUtils to extension functions")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (handleToolbarShortcut(event)) {
            return true
        }
        val keyCode = event.keyCode
        when (keyCode) {
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER -> if (event.isCtrlPressed) {
                if (allowSaveAndPreview()) {
                    launchCatchingTask { saveNote() }
                    return true
                }
            }

            KeyEvent.KEYCODE_D -> if (event.isCtrlPressed) {
                showDeckSelectionDialog()
                return true
            }

            KeyEvent.KEYCODE_L -> if (event.isCtrlPressed) {
                showCardTemplateEditor()
                return true
            }

            KeyEvent.KEYCODE_T -> if (event.isCtrlPressed && event.isShiftPressed) {
                showTagsDialog()
                return true
            }

            KeyEvent.KEYCODE_C -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    insertCloze(if (event.isAltPressed) AddClozeType.SAME_NUMBER else AddClozeType.INCREMENT_NUMBER)
                    return true
                }
            }

            KeyEvent.KEYCODE_P -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+P: Preview Pressed")
                    if (allowSaveAndPreview()) {
                        launchCatchingTask { performPreview() }
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun insertCloze(addClozeType: AddClozeType) {
        handleClozeInsertion(addClozeType.toClozeMode())
    }

    private fun fetchIntentInformation(intent: Intent) {
        val extras = requireArguments()
        sourceText = arrayOfNulls(2)
        if (Intent.ACTION_PROCESS_TEXT == intent.action) {
            val stringExtra = extras.getString(Intent.EXTRA_PROCESS_TEXT)
            Timber.d("Obtained %s from intent: %s", stringExtra, Intent.EXTRA_PROCESS_TEXT)
            sourceText!![0] = stringExtra ?: ""
            sourceText!![1] = ""
        } else if (ACTION_CREATE_FLASHCARD == intent.action) {
            sourceText!![0] = extras.getString(SOURCE_TEXT)
            sourceText!![1] = extras.getString(TARGET_TEXT)
        } else {
            var first: String?
            var second: String?
            first = extras.getString(Intent.EXTRA_SUBJECT) ?: ""
            second = extras.getString(Intent.EXTRA_TEXT) ?: ""
            if ("" == first) {
                first = second
                second = ""
            }
            sourceText!![0] = first
            sourceText!![1] = second
        }
    }

    private fun addFromAedict(extraText: String?): Boolean {
        var category: String
        val notepadLines = extraText!!.split("\n".toRegex()).toTypedArray()
        for (i in notepadLines.indices) {
            if (notepadLines[i].startsWith("[") && notepadLines[i].endsWith("]")) {
                category = notepadLines[i].substring(1, notepadLines[i].length - 1)
                if ("default" == category) {
                    if (notepadLines.size > i + 1) {
                        val entryLines = notepadLines[i + 1].split(":".toRegex()).toTypedArray()
                        if (entryLines.size > 1) {
                            sourceText!![0] = entryLines[1]
                            sourceText!![1] = entryLines[0]
                            aedictIntent = true
                            return false
                        }
                    }
                    showSnackbar(resources.getString(R.string.intent_aedict_empty))
                    return true
                }
            }
        }
        showSnackbar(resources.getString(R.string.intent_aedict_category))
        return true
    }

    /**
     * Checks if there are unsaved changes in the note editor.
     *
     * Delegates to ViewModel which tracks field values, tags, deck, and note type changes.
     *
     * @return true if there are unsaved changes, false otherwise
     */
    @VisibleForTesting
    fun hasUnsavedChanges(): Boolean {
        return noteEditorViewModel.hasUnsavedChanges()
    }

    // ----------------------------------------------------------------------------
    // SAVE NOTE METHODS
    // ----------------------------------------------------------------------------

    /**
     * Saves a new note with progress dialog.
     *
     * Used after user confirmation in [noClozeDialog] when adding a cloze note without cloze deletions.
     */
    private suspend fun saveNoteWithProgress() {
        requireActivity().withProgress(resources.getString(R.string.saving_facts)) {
            undoableOp {
                notetypes.save(editorNote!!.notetype)
                addNote(editorNote!!, deckId)
            }
        }
        changed = true
        sourceText = null
        showSnackbar(TR.addingAdded(), Snackbar.LENGTH_SHORT)
    }

    @VisibleForTesting
    @NeedsTest("14664: 'first field must not be empty' no longer applies after saving the note")
    suspend fun saveNote() {
        when (val result = noteEditorViewModel.saveNote()) {
            is NoteFieldsCheckResult.Success -> {
                changed = true
                reloadRequired = true

                if (addNote) {
                    sourceText = null
                    showSnackbar(TR.addingAdded(), Snackbar.LENGTH_SHORT)

                    val shouldClose = when (caller) {
                        NoteEditorCaller.NOTEEDITOR,
                        NoteEditorCaller.NOTEEDITOR_INTENT_ADD,
                            -> true

                        else -> aedictIntent
                    }

                    if (shouldClose) {
                        if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD || aedictIntent) {
                            showThemedToast(
                                requireContext(), R.string.note_message, shortLength = true
                            )
                        }
                        val closeIntent = if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD) {
                            Intent().apply {
                                putExtra(
                                    EXTRA_ID, requireArguments().getString(EXTRA_ID)
                                )
                            }
                        } else {
                            null
                        }
                        closeNoteEditor(closeIntent ?: Intent())
                    } else {
                        noteEditorViewModel.resetFieldEditedFlag()
                    }
                } else {
                    closeNoteEditor()
                }
            }

            is NoteFieldsCheckResult.Failure -> {
                addNoteErrorMessage = result.localizedMessage
                displayErrorSavingNote()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToolbar()
    }

    private fun allowSaveAndPreview(): Boolean = when {
        addNote && noteEditorViewModel.noteEditorState.value.isImageOcclusion -> false
        else -> true
    }

    private fun toggleCapitalize(value: Boolean) {
        this.sharedPrefs().edit {
            putBoolean(PREF_NOTE_EDITOR_CAPITALIZE, value)
        }
    }

    private fun addNewNote() {
        launchNoteEditor(NoteEditorLauncher.AddNote(deckId)) { }
    }

    fun copyNote() {
        val currentTags = noteEditorViewModel.noteEditorState.value.tags
        launchNoteEditor(NoteEditorLauncher.CopyNote(deckId, fieldsText, currentTags)) { }
    }

    private fun launchNoteEditor(
        arguments: NoteEditorLauncher,
        intentEnricher: Consumer<Bundle>,
    ) {
        val intent = arguments.toIntent(requireContext())
        val bundle = arguments.toBundle()
        intentEnricher.accept(bundle)
        requestAddLauncher.launch(intent)
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------
    /**
     * Opens the card previewer to show how the current note will appear as cards.
     *
     * Validates that the note has at least one non-empty field before launching the preview.
     * For existing notes, shows card ordinal selection if multiple templates exist.
     */
    @VisibleForTesting
    suspend fun performPreview() {
        val convertNewlines = shouldReplaceNewlines()

        fun String?.toFieldText(): String =
            NoteService.convertToHtmlNewline(this.orEmpty(), convertNewlines)

        val fields =
            noteEditorViewModel.noteEditorState.value.fields.map { fieldState -> fieldState.value.text.toFieldText() }
                .toMutableList()

        val tags = noteEditorViewModel.noteEditorState.value.tags.toMutableList()

        val notetype = if (editorNote != null) {
            editorNote!!.notetype
        } else {
            withCol { notetypes.current() }
        }

        val noteId = editorNote?.id ?: 0L

        val ord = if (notetype.isCloze) {
            val tempNote = withCol { Note.fromNotetypeId(this@withCol, notetype.id) }
            tempNote.fields = fields
            val clozeNumbers = withCol { clozeNumbersInNote(tempNote) }
            if (clozeNumbers.isNotEmpty()) {
                clozeNumbers.first() - 1
            } else {
                0
            }
        } else {
            currentEditedCard?.ord ?: 0
        }

        val args = TemplatePreviewerArguments(
            notetypeFile = NotetypeFile(requireContext(), notetype),
            fields = fields,
            tags = tags,
            id = noteId,
            ord = ord,
            fillEmpty = false,
        )
        val intent = TemplatePreviewerPage.getIntent(requireContext(), args)
        startActivity(intent)
    }

    private fun closeCardEditorWithCheck() {
        if (hasUnsavedChanges()) {
            showDiscardChangesDialog()
        } else {
            closeNoteEditor()
        }
    }

    private fun showDiscardChangesDialog() {
        noteEditorViewModel.setShowDiscardChangesDialog(true)
    }

    private fun closeNoteEditor(intent: Intent = Intent()) {
        val result: Int = if (changed) {
            Activity.RESULT_OK
        } else {
            RESULT_CANCELED
        }
        if (reloadRequired) {
            intent.putExtra(RELOAD_REQUIRED_EXTRA_KEY, true)
        }
        if (changed) {
            intent.putExtra(NOTE_CHANGED_EXTRA_KEY, true)
        }
        closeNoteEditor(result, intent)
    }

    private fun closeNoteEditor(
        result: Int,
        intent: Intent?,
    ) {
        requireActivity().apply {
            if (intent != null) {
                setResult(result, intent)
            } else {
                setResult(result)
            }
            CardTemplateNotetype.clearTempNoteTypeFiles()

            if (inCardBrowserActivity && requireActivity() !is NoteEditorActivity) {
                Timber.i("not closing activity: fragmented")
                return
            }

            Timber.i("Closing note editor")

            val animation = BundleCompat.getParcelable(
                requireArguments(),
                AnkiActivity.FINISH_ANIMATION_EXTRA,
                ActivityTransitionAnimation.Direction::class.java,
            )
            if (animation != null) {
                requireAnkiActivity().finishWithAnimation(animation)
            } else {
                finish()
            }
        }
    }

    /**
     * Shows the deck selection dialog for choosing where to add the note.
     */
    private fun showDeckSelectionDialog() {
        launchCatchingTask {
            val selectableDecks = withCol {
                decks.allNamesAndIds().map { SelectableDeck.Deck(it.id, it.name) }
            }

            val dialog = DeckSelectionDialog.newInstance(
                title = getString(R.string.select_deck_title),
                summaryMessage = null,
                keepRestoreDefaultButton = false,
                decks = selectableDecks,
            )
            dialog.show(parentFragmentManager, "deck_selection_dialog")
        }
    }

    /**
     * Shows the tags dialog for editing the note's tags.
     */
    private fun showTagsDialog() {
        val currentTags = noteEditorViewModel.noteEditorState.value.tags
        val selTags = ArrayList(currentTags)

        val dialog = with(requireContext()) {
            tagsDialogFactory!!.newTagsDialog().withArguments(
                context = this,
                type = TagsDialog.DialogType.EDIT_TAGS,
                checkedTags = selTags,
            )
        }
        showDialogFragment(dialog)
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        noteEditorViewModel.updateTags(selectedTags.toSet())
    }

    /**
     * Opens the card template editor for the current note type.
     *
     * Allows editing the HTML templates that define how cards are rendered.
     */
    private fun showCardTemplateEditor() {
        val intent = Intent(requireContext(), CardTemplateEditor::class.java)
        val noteTypeName = noteEditorViewModel.noteEditorState.value.selectedNoteTypeName
        val noteTypeId = getColUnsafe.notetypes.all().find { it.name == noteTypeName }?.id

        if (noteTypeId == null) {
            Timber.w("showCardTemplateEditor(): noteTypeId is null")
            requireActivity().runOnUiThread {
                showSnackbar(
                    getString(R.string.note_type_not_found_for_template_editor),
                    Snackbar.LENGTH_SHORT,
                )
            }
            return
        }

        intent.putExtra("noteTypeId", noteTypeId)
        if (!addNote) {
            val cardInfo =
                Triple(currentEditedCard?.id, currentEditedCard?.ord, currentEditedCard?.nid)

            if (cardInfo.third != null) {
                intent.putExtra("noteId", cardInfo.third)
            }
            if (cardInfo.second != null) {
                intent.putExtra("ordId", cardInfo.second)
            }
        }
        requestTemplateEditLauncher.launch(intent)
    }

    private suspend fun getCurrentMultimediaEditableNote(): MultimediaEditableNote {
        val notetype = if (editorNote != null) {
            editorNote!!.notetype
        } else {
            withCol { notetypes.current() }
        }

        val note = NoteService.createEmptyNote(notetype)
        val fields = currentFieldStrings.requireNoNulls()

        val noteTypeId = editorNote?.noteTypeId ?: notetype.id
        withCol {
            NoteService.updateMultimediaNoteFromFields(
                this@withCol, fields, noteTypeId, note
            )
        }

        return note
    }

    @get:CheckResult
    val currentFieldStrings: Array<String?>
        get() {
            val fields = noteEditorViewModel.noteEditorState.value.fields
            return Array(fields.size) { i -> fields[i].value.text }
        }

    @VisibleForTesting
    fun showMultimediaBottomSheet() {
        Timber.d("Showing MultimediaBottomSheet fragment")
        val multimediaBottomSheet = MultimediaBottomSheet()
        multimediaBottomSheet.show(parentFragmentManager, "MultimediaBottomSheet")
    }

    private fun handleMultimediaActions(fieldIndex: Int) {
        multimediaActionJob?.cancel()

        multimediaActionJob = lifecycleScope.launch {
            val note: MultimediaEditableNote = getCurrentMultimediaEditableNote()
            if (note.isEmpty) return@launch

            multimediaViewModel.multimediaAction.first { action ->
                when (action) {
                    MultimediaBottomSheet.MultimediaAction.SELECT_IMAGE_FILE -> {
                        val field = ImageField()
                        note.setField(fieldIndex, field)
                        openMultimediaImageFragment(fieldIndex = fieldIndex, field, note)
                    }

                    MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_FILE -> {
                        val field = MediaClipField()
                        note.setField(fieldIndex, field)
                        val mediaIntent = AudioVideoFragment.getIntent(
                            requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                            AudioVideoFragment.MediaOption.AUDIO_CLIP,
                        )

                        multimediaFragmentLauncher.launch(mediaIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.OPEN_DRAWING -> {
                        val field = ImageField()
                        note.setField(fieldIndex, field)

                        val drawingIntent = MultimediaImageFragment.getIntent(
                            requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                            MultimediaImageFragment.ImageOptions.DRAWING,
                        )

                        multimediaFragmentLauncher.launch(drawingIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING -> {
                        val field = AudioRecordingField()
                        note.setField(fieldIndex, field)
                        val audioRecordingIntent = AudioRecordingFragment.getIntent(
                            requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                        )

                        multimediaFragmentLauncher.launch(audioRecordingIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.SELECT_VIDEO_FILE -> {
                        val field = MediaClipField()
                        note.setField(fieldIndex, field)
                        val mediaIntent = AudioVideoFragment.getIntent(
                            requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                            AudioVideoFragment.MediaOption.VIDEO_CLIP,
                        )

                        multimediaFragmentLauncher.launch(mediaIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.OPEN_CAMERA -> {
                        val field = ImageField()
                        note.setField(fieldIndex, field)
                        val imageIntent = MultimediaImageFragment.getIntent(
                            requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                            MultimediaImageFragment.ImageOptions.CAMERA,
                        )

                        multimediaFragmentLauncher.launch(imageIntent)
                    }
                }
                true
            }
        }
    }

    private fun openMultimediaImageFragment(
        fieldIndex: Int,
        field: IField,
        multimediaNote: IMultimediaEditableNote,
        imageUri: Uri? = null,
    ) {
        val multimediaExtra =
            MultimediaActivityExtra(fieldIndex, field, multimediaNote, imageUri?.toString())

        val imageIntent = MultimediaImageFragment.getIntent(
            requireContext(),
            multimediaExtra,
            MultimediaImageFragment.ImageOptions.GALLERY,
        )

        multimediaFragmentLauncher.launch(imageIntent)
    }

    private fun handleMultimediaResult(extras: Bundle) {
        val index = extras.getInt(MULTIMEDIA_RESULT_FIELD_INDEX)
        val field = extras.getSerializableCompat<IField>(MULTIMEDIA_RESULT) ?: return

        if (field.type != EFieldType.TEXT || field.mediaFile != null) {
            addMediaFileToField(index, field)
        }
    }

    private fun addMediaFileToField(
        index: Int,
        field: IField,
    ) {
        lifecycleScope.launch {
            val note = getCurrentMultimediaEditableNote()
            note.setField(index, field)

            withCol {
                NoteService.importMediaToDirectory(this, field)
            }

            val formattedValue = field.formattedValue ?: ""

            val currentState = noteEditorViewModel.noteEditorState.value
            val fieldState = currentState.fields.find { it.index == index }

            if (fieldState != null) {
                if (field.type === EFieldType.TEXT) {
                    noteEditorViewModel.updateFieldValue(
                        index,
                        TextFieldValue(text = formattedValue),
                    )
                } else {
                    val currentValue = fieldState.value
                    val start = currentValue.selection.start
                    val end = currentValue.selection.end
                    val newText = buildString {
                        append(currentValue.text.substring(0, start))
                        append(formattedValue)
                        append(currentValue.text.substring(end))
                    }
                    val newCursor = start + formattedValue.length
                    noteEditorViewModel.updateFieldValue(
                        index,
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursor),
                        ),
                    )
                }
            }

            changed = true
        }
    }

    @VisibleForTesting
    fun clearField(index: Int) {
        setFieldValueFromUi(index, "")
    }

    val fieldsText: String
        get() {
            val fieldStates = noteEditorViewModel.noteEditorState.value.fields
            val fields = Array(fieldStates.size) { i -> fieldStates[i].value.text }
            return Utils.joinFields(fields)
        }

    private fun setEditFieldTexts(contents: String?) {
        var fields: List<String>? = null
        val len: Int
        if (contents == null) {
            len = 0
        } else {
            fields = Utils.splitFields(contents)
            len = fields.size
        }

        val currentState = noteEditorViewModel.noteEditorState.value
        for (i in currentState.fields.indices) {
            val newText = if (i < len) fields!![i] else ""
            noteEditorViewModel.updateFieldValue(
                i,
                TextFieldValue(text = newText),
            )
        }
    }

    private fun updateToolbar() {
        val shouldShow = !shouldHideToolbar()
        noteEditorViewModel.setToolbarVisibility(shouldShow)
        if (!shouldShow) {
            noteEditorViewModel.setToolbarButtons(emptyList())
            return
        }

        val buttons = toolbarButtons.map { button ->
            ToolbarButtonModel(
                index = button.index,
                text = button.buttonText,
                prefix = button.prefix,
                suffix = button.suffix,
            )
        }
        noteEditorViewModel.setToolbarButtons(buttons)
    }

    private val toolbarButtons: ArrayList<CustomToolbarButton>
        get() {
            val set = this.sharedPrefs()
                .getStringSet(PREF_NOTE_EDITOR_CUSTOM_BUTTONS, HashUtil.hashSetInit(0))
            return CustomToolbarButton.fromStringSet(set!!)
        }

    private fun saveToolbarButtons(buttons: ArrayList<CustomToolbarButton>) {
        this.sharedPrefs().edit {
            putStringSet(PREF_NOTE_EDITOR_CUSTOM_BUTTONS, CustomToolbarButton.toStringSet(buttons))
        }
    }

    private fun addToolbarButton(
        buttonText: String,
        prefix: String,
        suffix: String,
    ) {
        if (prefix.isEmpty() && suffix.isEmpty()) return
        val toolbarButtons = toolbarButtons
        toolbarButtons.add(CustomToolbarButton(toolbarButtons.size, buttonText, prefix, suffix))
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun editToolbarButton(
        buttonText: String,
        prefix: String,
        suffix: String,
        currentButton: CustomToolbarButton,
    ) {
        val toolbarButtons = toolbarButtons
        val currentButtonIndex = currentButton.index

        toolbarButtons[currentButtonIndex] = CustomToolbarButton(
            index = currentButtonIndex,
            buttonText = buttonText.ifEmpty { currentButton.buttonText },
            prefix = prefix.ifEmpty { currentButton.prefix },
            suffix = suffix.ifEmpty { currentButton.suffix },
        )

        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun suggestRemoveButton(
        button: CustomToolbarButton,
        editToolbarItemDialog: AlertDialog,
    ) {
        AlertDialog.Builder(requireContext()).show {
            title(R.string.remove_toolbar_item)
            positiveButton(R.string.dialog_positive_delete) {
                editToolbarItemDialog.dismiss()
                removeButton(button)
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun removeButton(button: CustomToolbarButton) {
        val toolbarButtons = toolbarButtons
        toolbarButtons.removeAt(button.index)
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun displayAddToolbarDialog() {
        noteEditorViewModel.showAddToolbarDialog()
    }

    private fun displayEditToolbarDialog(currentButton: CustomToolbarButton) {
        noteEditorViewModel.showEditToolbarDialog(
            icon = currentButton.buttonText,
            prefix = currentButton.prefix,
            suffix = currentButton.suffix,
            buttonIndex = currentButton.index,
        )
    }

    private fun editToolbarButton(icon: String, prefix: String, suffix: String, buttonIndex: Int) {
        val toolbarButtons = toolbarButtons
        toolbarButtons[buttonIndex] = CustomToolbarButton(buttonIndex, icon, prefix, suffix)
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun removeToolbarButton(buttonIndex: Int) {
        val toolbarButtons = toolbarButtons
        toolbarButtons.removeAt(buttonIndex)
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    override val shortcuts
        get() = ShortcutGroup(
            listOf(
                shortcut("Ctrl+ENTER") { getString(R.string.save) },
                shortcut("Ctrl+D") { getString(R.string.select_deck) },
                shortcut("Ctrl+L") { getString(R.string.card_template_editor_group) },
                shortcut("Ctrl+Shift+T") { getString(R.string.tag_editor) },
                shortcut("Ctrl+Shift+C") { getString(R.string.multimedia_editor_popup_cloze) },
                shortcut("Ctrl+P") { getString(R.string.card_editor_preview_card) },
            ),
            R.string.note_editor_group,
        )

    /** Update the list of card templates for current note type  */
    @KotlinCleanup("make non-null")
    private fun updateCards(noteType: NotetypeJson?) {
        Timber.d("updateCards()")
        val tmpls = noteType!!.templates
        var cardsList = StringBuilder()
        for ((i, tmpl) in tmpls.withIndex()) {
            var name = tmpl.jsonObject.optString("name")
            if (!addNote && tmpls.length() > 1 && noteType.jsonObject === editorNote!!.notetype.jsonObject && currentEditedCard != null && currentEditedCard!!.template(
                    getColUnsafe
                ).jsonObject.optString("name") == name
            ) {
                name = "<u>$name</u>"
            }
            cardsList.append(name)
            if (i < tmpls.length() - 1) {
                cardsList.append(", ")
            }
        }
        if (!addNote && tmpls.length() < editorNote!!.notetype.templates.length()) {
            cardsList = StringBuilder("<font color='red'>$cardsList</font>")
        }

        val cardsInfoText = resources.getString(R.string.CardEditorCards, cardsList.toString())

        noteEditorViewModel.updateCardsInfo(cardsInfoText)
    }

    private fun setupImageOcclusionEditor(imagePath: String = "") {
        val kind: String
        val id: Long
        if (addNote) {
            kind = "add"
            // For image occlusion, use the currently selected note type ID from ViewModel
            id = noteEditorViewModel.currentNote.value?.noteTypeId ?: 0L
        } else {
            kind = "edit"
            id = editorNote?.id!!
        }
        val intent = ImageOcclusion.getIntent(requireContext(), kind, id, imagePath, deckId)
        requestIOEditorCloser.launch(intent)
    }

    @VisibleForTesting
    fun setFieldValueFromUi(
        i: Int,
        newText: String?,
    ) {
        noteEditorViewModel.updateFieldValue(i, TextFieldValue(text = newText ?: ""))
    }

    companion object {
        const val SOURCE_TEXT = "SOURCE_TEXT"
        const val TARGET_TEXT = "TARGET_TEXT"
        const val EXTRA_CALLER = "CALLER"
        const val EXTRA_CARD_ID = "CARD_ID"
        const val EXTRA_CONTENTS = "CONTENTS"
        const val EXTRA_TAGS = "TAGS"
        const val EXTRA_ID = "ID"
        const val EXTRA_DID = "DECK_ID"
        const val EXTRA_TEXT_FROM_SEARCH_VIEW = "SEARCH"
        const val EXTRA_EDIT_FROM_CARD_ID = "editCid"
        const val ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD"
        const val ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND"
        const val NOTE_CHANGED_EXTRA_KEY = "noteChanged"
        const val RELOAD_REQUIRED_EXTRA_KEY = "reloadRequired"
        const val EXTRA_IMG_OCCLUSION = "image_uri"
        const val IN_CARD_BROWSER_ACTIVITY = "inCardBrowserActivity"

        enum class NoteEditorCaller(
            val value: Int,
        ) {
            NO_CALLER(0), EDIT(1), STUDYOPTIONS(2), DECKPICKER(3), REVIEWER_ADD(11), CARDBROWSER_ADD(
                7
            ),
            NOTEEDITOR(8), PREVIEWER_EDIT(9), NOTEEDITOR_INTENT_ADD(10), IMG_OCCLUSION(12), ADD_IMAGE(
                13
            ),
            INSTANT_NOTE_EDITOR(14), ;

            companion object {
                fun fromValue(value: Int) = NoteEditorCaller.entries.first { it.value == value }
            }
        }

        const val RESULT_UPDATED_IO_NOTE = 11

        const val PREF_NOTE_EDITOR_SCROLL_TOOLBAR = "noteEditorScrollToolbar"
        private const val PREF_NOTE_EDITOR_SHOW_TOOLBAR = "noteEditorShowToolbar"
        private const val PREF_NOTE_EDITOR_NEWLINE_REPLACE = "noteEditorNewlineReplace"
        private const val PREF_NOTE_EDITOR_CAPITALIZE = "note_editor_capitalize"
        private const val PREF_NOTE_EDITOR_FONT_SIZE = "note_editor_font_size"
        private const val PREF_NOTE_EDITOR_CUSTOM_BUTTONS = "note_editor_custom_buttons"

        fun newInstance(launcher: NoteEditorLauncher): NoteEditorFragment =
            NoteEditorFragment().apply {
                this.arguments = launcher.toBundle()
            }

        private fun shouldReplaceNewlines(): Boolean =
            AnkiDroidApp.instance.sharedPrefs().getBoolean(PREF_NOTE_EDITOR_NEWLINE_REPLACE, true)

        @VisibleForTesting
        @CheckResult
        fun intentLaunchedWithImage(intent: Intent): Boolean {
            if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return false
            if (ImportUtils.isInvalidViewIntent(intent)) return false
            return intent.resolveMimeType()?.startsWith("image/") == true
        }

        private fun shouldHideToolbar(): Boolean =
            !AnkiDroidApp.instance.sharedPrefs().getBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, true)
    }
}
