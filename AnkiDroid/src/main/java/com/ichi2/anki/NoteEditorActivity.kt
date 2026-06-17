/*
 *  Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>
 *  Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.annotations.NeedsTest
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
import com.ichi2.anki.noteeditor.ImageOcclusionNotetypeMissingException
import com.ichi2.anki.noteeditor.NoteEditorCaller
import com.ichi2.anki.noteeditor.NoteEditorCaller.Companion.fromValue
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.noteeditor.NoteEditorViewModel
import com.ichi2.anki.noteeditor.ToolbarButtonModel
import com.ichi2.anki.noteeditor.compose.AddToolbarItemDialog
import com.ichi2.anki.noteeditor.compose.NoteEditorScreen
import com.ichi2.anki.noteeditor.compose.NoteEditorSimpleOverflowItem
import com.ichi2.anki.noteeditor.compose.NoteEditorToggleOverflowItem
import com.ichi2.anki.noteeditor.compose.NoteEditorTopAppBar
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.ClipboardUtil
import com.ichi2.utils.HashUtil
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.function.Consumer

class NoteEditorActivity : AnkiActivity(), BaseSnackbarBuilderProvider, DispatchKeyEventListener,
    ShortcutGroupProvider, DeckSelectionListener, TagsDialogListener {

    override val baseSnackbarBuilder: SnackbarBuilder = { }

    private var changed: Boolean
        get() = noteEditorViewModel.changed.value
        set(value) = noteEditorViewModel.setChanged(value)

    private var multimediaActionJob: Job? = null


    private var reloadRequired: Boolean
        get() = noteEditorViewModel.reloadRequired.value
        set(value) = noteEditorViewModel.setReloadRequired(value)

    private var tagsDialogFactory: TagsDialogFactory? = null

    private var editorNote: Note? = null

    private val multimediaViewModel: MultimediaViewModel by viewModels()

    private var currentEditedCard: Card? = null

    @get:VisibleForTesting
    var deckId: DeckId = 0
        private set

    private var addNote = false

    private var aedictIntent: Boolean
        get() = noteEditorViewModel.aedictIntent.value
        set(value) = noteEditorViewModel.setAedictIntent(value)

    private var caller: NoteEditorCaller
        get() = noteEditorViewModel.caller.value
        set(value) = noteEditorViewModel.setCaller(value)

    private var sourceText: Array<String?>? = null

    var clipboard: ClipboardManager? = null

    val arguments: Bundle
        get() {
            val extraArgs = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
            if (extraArgs != null) {
                return extraArgs
            }
            return intent.extras ?: Bundle()
        }

    private val inCardBrowserActivity
        get() = arguments.getBoolean(IN_CARD_BROWSER_ACTIVITY)

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
            reloadRequired = true

            Timber.d("onActivityResult() template edit return")
            lifecycleScope.launch {
                try {
                    val col = getColUnsafe
                    val currentNote = noteEditorViewModel.currentNote.value
                    if (currentNote != null) {
                        editorNote = currentNote
                        val notetype = col.notetypes.get(currentNote.noteTypeId)
                        if (notetype != null) {
                            updateCards(notetype)
                            Timber.d("Updated cards for note type: %s", notetype.name)
                        } else {
                            Timber.w("Note type not found for note")
                            noteEditorViewModel.showSnackbar(getString(R.string.something_wrong))
                        }
                    } else {
                        Timber.w("current note is null after template edit")
                        noteEditorViewModel.showSnackbar(getString(R.string.something_wrong))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error updating editor after template edit")
                    noteEditorViewModel.showSnackbar(getString(R.string.something_wrong))
                }
            }
        },
    )

    private val ioEditorLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            ImportUtils.getFileCachedCopy(this, uri)?.let { path ->
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
                } else if (caller == NoteEditorCaller.IMG_OCCLUSION) {
                    closeNoteEditor()
                }
            } else {
                if (caller == NoteEditorCaller.IMG_OCCLUSION) {
                    closeNoteEditor()
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
        noteEditorViewModel.selectDeck(deck.name)
    }

    private enum class AddClozeType {
        SAME_NUMBER, INCREMENT_NUMBER,
    }

    @VisibleForTesting
    var addNoteErrorMessage: String? = null

    private fun displayErrorSavingNote() {
        val errorMessage = snackbarErrorText
        if (errorMessage == TR.addingYouHaveAClozeDeletionNote()) {
            noClozeDialog(errorMessage)
        } else {
            noteEditorViewModel.showSnackbar(errorMessage)
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

    private fun allFieldsHaveContent() = currentFieldStrings.none { it.isEmpty() }

    @VisibleForTesting
    val noteEditorViewModel: NoteEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        tagsDialogFactory = TagsDialogFactory(this).attachToFragmentManager<TagsDialogFactory>(
            supportFragmentManager
        )

        if (savedInstanceState != null) {
            addNote = savedInstanceState.getBoolean("addNote")
            deckId = savedInstanceState.getLong("did")
        } else {
            caller = fromValue(arguments.getInt(EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value))
            if (caller == NoteEditorCaller.NO_CALLER) {
                val action = intent.action
                if (ACTION_CREATE_FLASHCARD == action || ACTION_CREATE_FLASHCARD_SEND == action || Intent.ACTION_PROCESS_TEXT == action) {
                    caller = NoteEditorCaller.NOTEEDITOR_INTENT_ADD
                }
            }
        }

        val backgroundColor = resolveThemeSurfaceColor()
        window.decorView.setBackgroundColor(backgroundColor)

        onBackPressedDispatcher.addCallback(this) {
            Timber.i("NoteEditor:: onBackPressed()")
            closeCardEditorWithCheck()
        }

        startLoadingCollection()
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        runOnUiThread {
            registerReceiver()
            setupComposeEditor(col)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.dispatchKeyEvent(event)

    override val shortcuts: ShortcutGroup
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

    private fun resolveThemeSurfaceColor(): Int {
        val typedValue = TypedValue()
        val resolved = theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurface, typedValue, true
        )

        if (!resolved) {
            return Color.DKGRAY
        }

        return when {
            typedValue.resourceId != 0 -> {
                ContextCompat.getColor(this, typedValue.resourceId)
            }

            typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT -> {
                typedValue.data
            }

            else -> Color.DKGRAY
        }
    }

    private fun setupComposeEditor(col: Collection) {
        Timber.d("NoteEditor() setupComposeEditor: caller: %s", caller)

        if (!initializeEditorLogic(col)) return

        if (addNote) {
            setTitle(R.string.cardeditor_title_add_note)
        } else {
            setTitle(R.string.cardeditor_title_edit_card)
        }

        updateToolbar()
        setupComposeContent()
    }

    private fun initializeEditorLogic(col: Collection): Boolean {
        try {
            clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }

        aedictIntent = false
        currentEditedCard = null

        when (caller) {
            NoteEditorCaller.NO_CALLER -> {
                Timber.e("no caller could be identified, closing")
                finish()
                return false
            }

            NoteEditorCaller.EDIT, NoteEditorCaller.PREVIEWER_EDIT -> {
                require(arguments.containsKey(EXTRA_CARD_ID)) {
                    "EXTRA_CARD_ID is required for $caller"
                }
                val cardId = arguments.getLong(EXTRA_CARD_ID)
                currentEditedCard = col.getCard(cardId)
                editorNote = currentEditedCard!!.note(col)
                addNote = false
            }

            NoteEditorCaller.NOTEEDITOR_INTENT_ADD,
            NoteEditorCaller.INSTANT_NOTE_EDITOR,
                -> {
                fetchIntentInformation(intent)
                if (sourceText == null) {
                    finish()
                    return false
                }
                if ("Aedict Notepad" == sourceText!![0] && addFromAedict(sourceText!![1])) {
                    finish()
                    return false
                }
                addNote = true
            }

            else -> {
                addNote = true
            }
        }

        val initialFieldText: String? = when {
            sourceText != null && sourceText!![0] != null -> sourceText!![0]
            else -> null
        }

        noteEditorViewModel.initializeEditor(
            col = col,
            cardId = currentEditedCard?.id,
            deckId = arguments.getLong(EXTRA_DID, 0L),
            isAddingNote = addNote,
            initialFieldText = initialFieldText,
        ) { success, error ->
            if (success) {
                launchCatchingTask {
                    deckId = noteEditorViewModel.currentNote.value?.let { _ ->
                        if (addNote) {
                            noteEditorViewModel.noteEditorState.value.selectedDeckName.let { deckName ->
                                withCol {
                                    decks.allNamesAndIds().find { it.name == deckName }?.id ?: 0L
                                }
                            }
                        } else {
                            currentEditedCard?.currentDeckId() ?: 0L
                        }
                    } ?: 0L

                    if (caller == NoteEditorCaller.IMG_OCCLUSION) {
                        val imageOcclusionLoadFailedMessage =
                            getString(R.string.image_occlusion_load_failed)
                        val imageUri = BundleCompat.getParcelable(
                            arguments,
                            EXTRA_IMG_OCCLUSION,
                            Uri::class.java,
                        )
                        if (imageUri == null) {
                            Timber.w("Could not load image for image occlusion: missing URI")
                            noteEditorViewModel.showSnackbar(imageOcclusionLoadFailedMessage)
                            closeNoteEditor()
                        } else {
                            val path =
                                ImportUtils.getFileCachedCopy(this@NoteEditorActivity, imageUri)
                            if (path == null) {
                                Timber.w(
                                    "Could not load image for image occlusion: failed to cache %s",
                                    imageUri,
                                )
                                noteEditorViewModel.showSnackbar(imageOcclusionLoadFailedMessage)
                                closeNoteEditor()
                            } else {
                                setupImageOcclusionEditor(path)
                            }
                        }
                    }
                }
                if (editorNote != null) {
                    updateCards(editorNote!!.notetype)
                } else {
                    val currentNotetype = col.notetypes.current()
                    updateCards(currentNotetype)
                }

                if (addNote) {
                    val copiedContents = arguments.getString(EXTRA_CONTENTS)
                    copiedContents?.let { contents ->
                        Timber.d("setupComposeEditor: Applying copied field contents")
                        setEditFieldTexts(contents)
                    }

                    val copiedTags = arguments.getStringArray(EXTRA_TAGS)
                    copiedTags?.let { tags ->
                        Timber.d(
                            "setupComposeEditor: Applying copied tags: %s",
                            tags.joinToString(),
                        )
                        noteEditorViewModel.updateTags(tags.toSet())
                    }
                }
            } else {
                Timber.e(error, "NoteEditorActivity init failed")
                val message = when (error) {
                    is ImageOcclusionNotetypeMissingException -> getString(R.string.image_occlusion_notetype_missing)
                    else -> getString(R.string.something_wrong)
                }
                noteEditorViewModel.showSnackbar(message)
                closeNoteEditor()
            }
        }
        return true
    }

    private fun setupComposeContent() {
        setContent {
            AnkiDroidTheme {
                val noteEditorState by noteEditorViewModel.noteEditorState.collectAsStateWithLifecycle()
                val availableDecks by noteEditorViewModel.availableDecks.collectAsStateWithLifecycle()
                val availableNoteTypes by noteEditorViewModel.availableNoteTypes.collectAsStateWithLifecycle()
                val toolbarButtons by noteEditorViewModel.toolbarButtons.collectAsStateWithLifecycle()
                val showToolbar by noteEditorViewModel.showToolbar.collectAsStateWithLifecycle()
                val allTags by noteEditorViewModel.tagsState.collectAsStateWithLifecycle()
                val deckTags by noteEditorViewModel.deckTags.collectAsStateWithLifecycle()
                val showDiscardChangesDialog by noteEditorViewModel.showDiscardChangesDialog.collectAsStateWithLifecycle()
                val noClozeDialogMessage by noteEditorViewModel.noClozeDialogState.collectAsStateWithLifecycle()
                val toolbarDialogState by noteEditorViewModel.toolbarDialogState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var capitalizeChecked by remember {
                    mutableStateOf(
                        sharedPrefs().getBoolean(
                            PREF_NOTE_EDITOR_CAPITALIZE,
                            true,
                        ),
                    )
                }
                var scrollToolbarChecked by remember {
                    mutableStateOf(
                        sharedPrefs().getBoolean(
                            PREF_NOTE_EDITOR_SCROLL_TOOLBAR,
                            true,
                        ),
                    )
                }

                LaunchedEffect(Unit) {
                    noteEditorViewModel.snackbarMessages.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
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
                        launchCatchingTask {
                            val deck = withCol {
                                decks.allNamesAndIds().find { it.name == deckName }
                            }
                            if (deck == null) {
                                Timber.w("onDeckSelected: Deck not found for name '%s'", deckName)
                                noteEditorViewModel.showSnackbar(getString(R.string.deck_not_found))
                                return@launchCatchingTask
                            }
                            deckId = deck.id
                            noteEditorViewModel.selectDeck(deckName)
                        }
                    },
                    onNoteTypeSelected = { noteTypeName ->
                        noteEditorViewModel.selectNoteType(noteTypeName)
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
                            onBackClick = { onBackPressedDispatcher.onBackPressed() },
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
                            noteEditorViewModel.showSnackbar(getString(R.string.activity_start_failed))
                        }
                    },
                    onImageOcclusionPasteImage = {
                        if (ClipboardUtil.hasImage(clipboard)) {
                            val uri = ClipboardUtil.getUri(clipboard)
                            val i = Intent().apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newUri(
                                    contentResolver,
                                    uri.toString(),
                                    uri,
                                )
                            }
                            ImportUtils.getFileCachedCopy(this@NoteEditorActivity, i)?.let { path ->
                                setupImageOcclusionEditor(path)
                            }
                        } else {
                            noteEditorViewModel.showSnackbar(TR.editingNoImageFoundOnClipboard())
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
                    capitalizeSentences = capitalizeChecked,
                )

                AddToolbarItemDialog(
                    state = toolbarDialogState,
                    onDismissRequest = {
                        noteEditorViewModel.dismissToolbarDialog()
                    },
                    onConfirm = { icon, prefix, suffix ->
                        val isEdit = toolbarDialogState.isEditMode
                        val index = toolbarDialogState.buttonIndex
                        noteEditorViewModel.dismissToolbarDialog()
                        if (isEdit) {
                            editToolbarButton(icon, prefix, suffix, index)
                        } else {
                            addToolbarButton(icon, prefix, suffix)
                        }
                    },
                    onDelete = if (toolbarDialogState.isEditMode) {
                        {
                            val index = toolbarDialogState.buttonIndex
                            noteEditorViewModel.dismissToolbarDialog()
                            removeToolbarButton(index)
                        }
                    } else {
                        null
                    },
                    onHelpClick = {
                        openUrl(R.string.link_manual_note_format_toolbar)
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
        savedInstanceState.putBoolean("addNote", addNote)
        savedInstanceState.putLong("did", deckId)
    }

    private fun applyFormatter(
        prefix: String,
        suffix: String,
    ) {
        noteEditorViewModel.formatSelection(prefix, suffix)
    }

    private fun displayFontSizeDialog() {
        val sizeCodes = resources.getStringArray(R.array.html_size_codes)
        MaterialAlertDialogBuilder(this).show {
            setItems(R.array.html_size_code_labels) { _, index ->
                val size = sizeCodes.getOrNull(index) ?: return@setItems
                applyFormatter("<span style=\"font-size:$size\">", "</span>")
            }
            title(R.string.menu_font_size)
        }
    }

    private fun displayInsertHeadingDialog() {
        val headingTags = arrayOf("h1", "h2", "h3", "h4", "h5")
        MaterialAlertDialogBuilder(this).show {
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

        MaterialAlertDialogBuilder(this).show {
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
            noteEditorViewModel.showSnackbar(getString(R.string.note_editor_insert_cloze_no_cloze_note_type))
            return
        }
        noteEditorViewModel.insertCloze(mode)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
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
        if (!isFinishing) {
            WidgetStatus.updateInBackground(this)
        }
    }

    private fun insertCloze(addClozeType: AddClozeType) {
        handleClozeInsertion(addClozeType.toClozeMode())
    }

    private fun fetchIntentInformation(intent: Intent) {
        val extras = arguments
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
                    noteEditorViewModel.showSnackbar(resources.getString(R.string.intent_aedict_empty))
                    return true
                }
            }
        }
        noteEditorViewModel.showSnackbar(resources.getString(R.string.intent_aedict_category))
        return true
    }

    @VisibleForTesting
    fun hasUnsavedChanges(): Boolean = noteEditorViewModel.hasUnsavedChanges()

    @VisibleForTesting
    @NeedsTest("14664: 'first field must not be empty' no longer applies after saving the note")
    suspend fun saveNote() {
        addNoteErrorMessage = null
        when (val result = noteEditorViewModel.saveNote()) {
            is NoteFieldsCheckResult.Success -> {
                changed = true
                reloadRequired = true

                if (addNote) {
                    sourceText = null
                    noteEditorViewModel.showSnackbar(TR.addingAdded())

                    val shouldClose = when (caller) {
                        NoteEditorCaller.NOTEEDITOR,
                        NoteEditorCaller.NOTEEDITOR_INTENT_ADD,
                            -> true

                        else -> aedictIntent
                    }

                    if (shouldClose) {
                        if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD || aedictIntent) {
                            showThemedToast(
                                this,
                                R.string.note_message,
                                shortLength = true,
                            )
                        }
                        val closeIntent = if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD) {
                            Intent().apply {
                                putExtra(
                                    EXTRA_ID,
                                    arguments.getString(EXTRA_ID),
                                )
                            }
                        } else {
                            null
                        }
                        closeNoteEditor(closeIntent ?: Intent())
                    } else {
                        noteEditorViewModel.currentNote.value?.notetype?.let(::updateCards)
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
        val intent = arguments.toIntent(this)
        val bundle = arguments.toBundle()
        intentEnricher.accept(bundle)
        requestAddLauncher.launch(intent)
    }

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
            val tempNote = withCol { Note.fromNotetypeId(this, notetype.id) }
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
            notetypeFile = NotetypeFile(this, notetype),
            fields = fields,
            tags = tags,
            id = noteId,
            ord = ord,
            fillEmpty = false,
        )
        val intent = TemplatePreviewerPage.getIntent(this, args)
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
            RESULT_OK
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
        if (intent != null) {
            setResult(result, intent)
        } else {
            setResult(result)
        }
        CardTemplateNotetype.clearTempNoteTypeFiles()

        Timber.i("Closing note editor")

        val animation = BundleCompat.getParcelable(
            arguments,
            FINISH_ANIMATION_EXTRA,
            ActivityTransitionAnimation.Direction::class.java,
        )
        if (animation != null) {
            finishWithAnimation(animation)
        } else {
            finish()
        }
    }

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
            dialog.show(supportFragmentManager, "deck_selection_dialog")
        }
    }

    private fun showTagsDialog() {
        val currentTags = noteEditorViewModel.noteEditorState.value.tags
        val selTags = ArrayList(currentTags)

        val dialog = tagsDialogFactory!!.newTagsDialog().withArguments(
            context = this,
            type = TagsDialog.DialogType.EDIT_TAGS,
            checkedTags = selTags,
        )
        showDialogFragment(dialog)
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        noteEditorViewModel.updateTags(selectedTags.toSet())
    }

    private fun showCardTemplateEditor() {
        val intent = Intent(this, CardTemplateEditor::class.java)
        val noteTypeName = noteEditorViewModel.noteEditorState.value.selectedNoteTypeName
        val noteTypeId = getColUnsafe.notetypes.all().find { it.name == noteTypeName }?.id

        if (noteTypeId == null) {
            Timber.w("showCardTemplateEditor(): noteTypeId is null")
            runOnUiThread {
                noteEditorViewModel.showSnackbar(getString(R.string.note_type_not_found_for_template_editor))
            }
            return
        }

        intent.putExtra("noteTypeId", noteTypeId)
        if (!addNote) {
            currentEditedCard?.nid?.let { intent.putExtra("noteId", it) }
            currentEditedCard?.ord?.let { intent.putExtra("ordId", it) }
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

        val noteTypeId = editorNote?.noteTypeId ?: notetype.id
        withCol {
            NoteService.updateMultimediaNoteFromFields(
                this,
                currentFieldStrings,
                noteTypeId,
                note,
            )
        }

        return note
    }

    @get:CheckResult
    val currentFieldStrings: Array<String>
        get() {
            val fields = noteEditorViewModel.noteEditorState.value.fields
            return Array(fields.size) { i -> fields[i].value.text }
        }

    @VisibleForTesting
    fun showMultimediaBottomSheet() {
        Timber.d("Showing MultimediaBottomSheet fragment")
        val multimediaBottomSheet = MultimediaBottomSheet()
        multimediaBottomSheet.show(supportFragmentManager, "MultimediaBottomSheet")
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
                            this@NoteEditorActivity,
                            MultimediaActivityExtra(fieldIndex, field, note),
                            AudioVideoFragment.MediaOption.AUDIO_CLIP,
                        )

                        multimediaFragmentLauncher.launch(mediaIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.OPEN_DRAWING -> {
                        val field = ImageField()
                        note.setField(fieldIndex, field)

                        val drawingIntent = MultimediaImageFragment.getIntent(
                            this@NoteEditorActivity,
                            MultimediaActivityExtra(fieldIndex, field, note),
                            MultimediaImageFragment.ImageOptions.DRAWING,
                        )

                        multimediaFragmentLauncher.launch(drawingIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING -> {
                        val field = AudioRecordingField()
                        note.setField(fieldIndex, field)
                        val audioRecordingIntent = AudioRecordingFragment.getIntent(
                            this@NoteEditorActivity,
                            MultimediaActivityExtra(fieldIndex, field, note),
                        )

                        multimediaFragmentLauncher.launch(audioRecordingIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.SELECT_VIDEO_FILE -> {
                        val field = MediaClipField()
                        note.setField(fieldIndex, field)
                        val mediaIntent = AudioVideoFragment.getIntent(
                            this@NoteEditorActivity,
                            MultimediaActivityExtra(fieldIndex, field, note),
                            AudioVideoFragment.MediaOption.VIDEO_CLIP,
                        )

                        multimediaFragmentLauncher.launch(mediaIntent)
                    }

                    MultimediaBottomSheet.MultimediaAction.OPEN_CAMERA -> {
                        val field = ImageField()
                        note.setField(fieldIndex, field)
                        val imageIntent = MultimediaImageFragment.getIntent(
                            this@NoteEditorActivity,
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
            this,
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
            putStringSet(
                PREF_NOTE_EDITOR_CUSTOM_BUTTONS,
                CustomToolbarButton.toStringSet(buttons),
            )
        }
    }

    private fun addToolbarButton(
        buttonText: String,
        prefix: String,
        suffix: String,
    ) {
        if (prefix.isEmpty() && suffix.isEmpty()) return
        val toolbarButtons = toolbarButtons
        toolbarButtons.add(
            CustomToolbarButton(
                toolbarButtons.size,
                buttonText,
                prefix,
                suffix,
            ),
        )
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

    private fun editToolbarButton(
        icon: String,
        prefix: String,
        suffix: String,
        buttonIndex: Int,
    ) {
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

    private fun updateCards(noteType: NotetypeJson) {
        Timber.d("updateCards()")
        val tmpls = noteType.templates
        var cardsList = StringBuilder()
        for ((i, tmpl) in tmpls.withIndex()) {
            var name = tmpl.jsonObject.optString("name")
            if (!addNote && tmpls.length() > 1 && noteType.jsonObject === editorNote!!.notetype.jsonObject && currentEditedCard != null && currentEditedCard!!.template(
                    getColUnsafe,
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
            id = noteEditorViewModel.currentNote.value?.noteTypeId ?: 0L
        } else {
            kind = "edit"
            id =
                requireNotNull(editorNote) { "editorNote is required when editing image occlusion" }.id
        }
        val intent = ImageOcclusion.getIntent(this, kind, id, imagePath, deckId)
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
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
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
        const val RESULT_UPDATED_IO_NOTE = 11
        const val PREF_NOTE_EDITOR_SCROLL_TOOLBAR = "noteEditorScrollToolbar"
        const val PREF_NOTE_EDITOR_SHOW_TOOLBAR = "noteEditorShowToolbar"
        const val PREF_NOTE_EDITOR_NEWLINE_REPLACE = "noteEditorNewlineReplace"

        @VisibleForTesting
        internal const val PREF_NOTE_EDITOR_CAPITALIZE = "note_editor_capitalize"
        private const val PREF_NOTE_EDITOR_CUSTOM_BUTTONS = "note_editor_custom_buttons"

        fun getIntent(
            context: Context,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent = Intent(context, NoteEditorActivity::class.java).apply {
            putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            action = intentAction
        }

        private fun shouldReplaceNewlines(): Boolean =
            AnkiDroidApp.instance.sharedPrefs().getBoolean(PREF_NOTE_EDITOR_NEWLINE_REPLACE, true)

        private fun shouldHideToolbar(): Boolean =
            !AnkiDroidApp.instance.sharedPrefs().getBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, true)

        @JvmStatic
        fun intentLaunchedWithImage(intent: Intent): Boolean {
            val type = intent.type
            val action = intent.action
            return Intent.ACTION_SEND == action && type != null && type.startsWith("image/")
        }
    }
}
