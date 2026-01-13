/* **************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <colbycabrera@gmail.com>                            *
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
package com.ichi2.anki.noteeditor

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.config.ConfigKey
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.checkNoteFieldsResponse
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.Note.ClozeUtils
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.noteeditor.compose.NoteEditorState
import com.ichi2.anki.noteeditor.compose.NoteFieldState
import com.ichi2.anki.noteeditor.compose.ToolbarItemDialogState
import com.ichi2.anki.servicelayer.NoteService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

enum class ClozeInsertionMode {
    SAME_NUMBER, INCREMENT_NUMBER,
}

/**
 * Identifies the source that launched the Note Editor.
 * This is used to determine the editor's behavior (add vs edit mode)
 * and how to handle the result when closing.
 *
 * Moved from NoteEditorFragment.Companion for shared access during migration.
 */
enum class NoteEditorCaller(val value: Int) {
    NO_CALLER(0), EDIT(1), STUDYOPTIONS(2), DECKPICKER(3),

    // Values 4, 5, 6 intentionally skipped - deprecated callers removed during migration
    // from NoteEditorFragment.Companion. Do not reuse these values.
    CARDBROWSER_ADD(7), NOTEEDITOR(8), PREVIEWER_EDIT(9), NOTEEDITOR_INTENT_ADD(10), REVIEWER_ADD(11), IMG_OCCLUSION(
        12
    ),
    ADD_IMAGE(13), INSTANT_NOTE_EDITOR(14), ;

    companion object {
        /**
         * Converts an integer value to the corresponding [NoteEditorCaller].
         * Returns [NO_CALLER] for unknown values to prevent crashes from corrupted SavedStateHandle data.
         */
        fun fromValue(value: Int): NoteEditorCaller =
            entries.firstOrNull { it.value == value } ?: NO_CALLER
    }
}

/**
 * Represents the result of a note save operation with type-safe data.
 * Eliminates the need for unchecked casts by providing a sealed class hierarchy.
 */
sealed class SaveResult {
    /**
     * Success result when adding a new note.
     * @param note The freshly created note with sticky field values applied
     * @param stickyInfo Map of field index to (isSticky flag, field value) pairs
     */
    data class NewNote(
        val note: Note,
        val stickyInfo: Map<Int, Pair<Boolean, String>>,
    ) : SaveResult()

    /**
     * Success result when updating an existing note.
     * @param card The updated card if deck was changed, null otherwise
     */
    data class UpdatedNote(
        val card: Card?,
    ) : SaveResult()

    /**
     * Failure result from validation or save operation.
     * @param validationResult The validation failure details
     */
    data class ValidationFailure(
        val validationResult: NoteFieldsCheckResult.Failure,
    ) : SaveResult()
}

/**
 * ViewModel for the Note Editor screen
 * Manages note editing state and business logic
 *
 * @param savedStateHandle Handles state persistence across process death (optional for backward compatibility).
 *                         When null, draft state persistence is disabled but the ViewModel remains functional.
 *                         To enable persistence, provide SavedStateHandle via a ViewModelFactory.
 * @param collectionProvider Provides access to the Anki collection, defaults to unsafe global access for compatibility.
 *                           In production or tests, inject a proper Collection provider for better testability.
 */
class NoteEditorViewModel(
    private val savedStateHandle: SavedStateHandle? = null,
    private val collectionProvider: suspend () -> Collection = { CollectionManager.getColUnsafe() },
) : ViewModel() {
    @VisibleForTesting
    var ioDispatcher: CoroutineDispatcher = com.ichi2.anki.ioDispatcher

    companion object {
        // Keys for SavedStateHandle persistence
        private const val KEY_FIELD_VALUES = "note_editor_field_values"
        private const val KEY_TAGS = "note_editor_tags"
        private const val KEY_SELECTED_DECK_NAME = "note_editor_selected_deck_name"
        private const val KEY_FOCUSED_FIELD_INDEX = "note_editor_focused_field_index"

        // Keys for caller/result state (migrated from Fragment)
        private const val KEY_CALLER = "note_editor_caller"
        private const val KEY_CHANGED = "note_editor_changed"
        private const val KEY_RELOAD_REQUIRED = "note_editor_reload_required"
        private const val KEY_AEDICT_INTENT = "note_editor_aedict_intent"

        /**
         * Note: SavedStateHandle is nullable to maintain backward compatibility with existing code
         * that creates the ViewModel without providing dependencies. When SavedStateHandle is null,
         * draft state persistence is disabled, but all other functionality works normally.
         *
         * To enable full production features including process death recovery:
         * - Provide SavedStateHandle through a ViewModelFactory
         * - Inject a Collection provider for testability
         */
    }

    private val _noteEditorState = MutableStateFlow(
        NoteEditorState(
            fields = emptyList(),
            tags = emptyList(),
            selectedDeckName = "",
            selectedNoteTypeName = "",
            isAddingNote = true,
            isClozeType = false,
            isImageOcclusion = false,
            cardsInfo = "",
            focusedFieldIndex = null,
            isTagsButtonEnabled = true,
            isCardsButtonEnabled = true,
        ),
    )

    /**
     * Immutable stream of the current editor UI state consumed by the Compose UI.
     * Includes fields, tags, selected deck/type, flags (cloze/image occlusion),
     * cards info, focus, and button enabled state.
     */
    val noteEditorState: StateFlow<NoteEditorState> = _noteEditorState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)

    /**
     * One-shot or sticky error messages surfaced to the UI layer for snackbars/dialogs.
     * Null indicates no current error to display.
     */
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _availableDecks = MutableStateFlow<List<String>>(emptyList())

    /** List of deck names available for selection in the editor. */
    val availableDecks: StateFlow<List<String>> = _availableDecks.asStateFlow()

    private val _availableNoteTypes = MutableStateFlow<List<String>>(emptyList())

    /** List of note type names available for selection in the editor. */
    val availableNoteTypes: StateFlow<List<String>> = _availableNoteTypes.asStateFlow()

    private val _toolbarButtons = MutableStateFlow<List<ToolbarButtonModel>>(emptyList())

    /** Custom toolbar buttons available in the formatting toolbar. */
    val toolbarButtons: StateFlow<List<ToolbarButtonModel>> = _toolbarButtons.asStateFlow()

    private val _showToolbar = MutableStateFlow(true)

    /** Whether the formatting toolbar should be visible. */
    val showToolbar: StateFlow<Boolean> = _showToolbar.asStateFlow()

    private val _isFieldEdited = MutableStateFlow(false)

    /** True if any field content has been modified and not yet saved. */
    val isFieldEdited: StateFlow<Boolean> = _isFieldEdited.asStateFlow()

    private val _showDiscardChangesDialog = MutableStateFlow(false)

    /** Controls visibility of the discard changes confirmation dialog */
    val showDiscardChangesDialog: StateFlow<Boolean> = _showDiscardChangesDialog.asStateFlow()

    private val _noClozeDialogState = MutableStateFlow<String?>(null)

    /**
     * Controls visibility of the no-cloze confirmation dialog.
     * When non-null, shows dialog with the error message.
     * Set to null to dismiss the dialog.
     */
    val noClozeDialogState: StateFlow<String?> = _noClozeDialogState.asStateFlow()

    private val _toolbarDialogState = MutableStateFlow(ToolbarItemDialogState())

    /**
     * Controls visibility and state of the add/edit toolbar item dialog.
     */
    val toolbarDialogState: StateFlow<ToolbarItemDialogState> = _toolbarDialogState.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)

    /** The underlying Note being edited (null when creating a new, not yet initialized). */
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _currentCard = MutableStateFlow<Card?>(null)
    private val _deckId = MutableStateFlow<DeckId>(0L)

    private val _tagsState = MutableStateFlow<TagsState>(TagsState.Loading)
    val tagsState: StateFlow<TagsState> = _tagsState.asStateFlow()

    private val _deckTags = MutableStateFlow<Set<String>>(emptySet())
    val deckTags: StateFlow<Set<String>> = _deckTags.asStateFlow()

    // Store initial field values to detect actual changes
    private var initialFieldValues: List<String> = emptyList()

    // Store initial tags to detect changes
    private var initialTags: List<String> = emptyList()

    // Store initial deck ID to detect deck changes (for editing existing cards)
    private var initialDeckId: DeckId = 0L

    // Store initial note type ID to detect note type changes
    private var initialNoteTypeId: Long = 0L

    // ============================================================================
    // Caller/Result State (migrated from NoteEditorFragment)
    // ============================================================================

    private val _caller = MutableStateFlow(NoteEditorCaller.NO_CALLER)

    /** Identifies which component launched the editor (determines add/edit mode and result handling). */
    val caller: StateFlow<NoteEditorCaller> = _caller.asStateFlow()

    private val _changed = MutableStateFlow(false)

    /** True if any changes have been saved (e.g., multimedia added, note saved). */
    val changed: StateFlow<Boolean> = _changed.asStateFlow()

    private val _reloadRequired = MutableStateFlow(false)

    /** Signals that the calling activity should rebuild its card definition from scratch. */
    val reloadRequired: StateFlow<Boolean> = _reloadRequired.asStateFlow()

    private val _aedictIntent = MutableStateFlow(false)

    /** True if the editor was opened from Aedict Notepad with special handling. */
    val aedictIntent: StateFlow<Boolean> = _aedictIntent.asStateFlow()

    init {
        // Restore caller state from SavedStateHandle on process death recovery
        savedStateHandle?.let { handle ->
            handle.get<Int>(KEY_CALLER)?.let { callerValue ->
                _caller.value = NoteEditorCaller.fromValue(callerValue)
            }
            handle.get<Boolean>(KEY_CHANGED)?.let { _changed.value = it }
            handle.get<Boolean>(KEY_RELOAD_REQUIRED)?.let { _reloadRequired.value = it }
            handle.get<Boolean>(KEY_AEDICT_INTENT)?.let { _aedictIntent.value = it }
        }
    }

    /**
     * Sets the caller and persists to SavedStateHandle for process death recovery.
     */
    fun setCaller(newCaller: NoteEditorCaller) {
        _caller.value = newCaller
        savedStateHandle?.set(KEY_CALLER, newCaller.value)
    }

    /**
     * Marks that changes have been made and persists to SavedStateHandle.
     */
    fun setChanged(value: Boolean) {
        _changed.value = value
        savedStateHandle?.set(KEY_CHANGED, value)
    }

    /**
     * Marks that reload is required and persists to SavedStateHandle.
     */
    fun setReloadRequired(value: Boolean) {
        _reloadRequired.value = value
        savedStateHandle?.set(KEY_RELOAD_REQUIRED, value)
    }

    /**
     * Sets the Aedict intent flag and persists to SavedStateHandle.
     */
    fun setAedictIntent(value: Boolean) {
        _aedictIntent.value = value
        savedStateHandle?.set(KEY_AEDICT_INTENT, value)
    }

    /**
     * Initialize the editor with a new or existing note
     */
    fun initializeEditor(
        col: Collection,
        cardId: Long? = null,
        deckId: Long? = null,
        isAddingNote: Boolean = true,
        initialFieldText: String? = null,
        onComplete: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                // Attempt to restore draft state from SavedStateHandle
                val restoredFieldValues = savedStateHandle?.get<Array<String>>(KEY_FIELD_VALUES)
                val restoredTags = savedStateHandle?.get<Array<String>>(KEY_TAGS)
                val restoredDeckName = savedStateHandle?.get<String>(KEY_SELECTED_DECK_NAME)
                val restoredFocusedIndex = savedStateHandle?.get<Int>(KEY_FOCUSED_FIELD_INDEX)

                // Check cancellation after reading saved state
                ensureActive()

                // Perform all DB operations on IO dispatcher
                withContext(ioDispatcher) {
                    // Load note and determine deck
                    if (cardId != null && !isAddingNote) {
                        // Editing an existing card - use the card's deck
                        val card = col.getCard(cardId)
                        ensureActive() // Check cancellation after DB access
                        _currentCard.value = card
                        _currentNote.value = card.note(col)
                        _deckId.value = card.currentDeckId()
                    } else {
                        // Adding a new note - use the provided deckId or calculate it
                        val notetype = col.notetypes.current()
                        ensureActive() // Check cancellation after DB access
                        val newNote = Note.fromNotetypeId(col, notetype.id)

                        // Restore field values if available
                        if (restoredFieldValues != null && restoredFieldValues.size == newNote.fields.size) {
                            restoredFieldValues.forEachIndexed { index, value ->
                                newNote.fields[index] = value
                            }
                            ensureActive() // Check cancellation after field restoration
                        } else if (initialFieldText != null && newNote.fields.isNotEmpty()) {
                            // If no restored values but initial text is provided (e.g., from ACTION_PROCESS_TEXT),
                            // set it as the first field's content
                            newNote.fields[0] = initialFieldText
                            Timber.d("Set initial field text from intent: %s", initialFieldText)
                        }

                        _currentNote.value = newNote
                        _deckId.value = calculateDeckIdForNewNote(col, deckId, notetype)

                        // Restore deck if available
                        if (restoredDeckName != null) {
                            ensureActive() // Check cancellation before deck lookup
                            val restoredDeck =
                                col.decks.allNamesAndIds().find { it.name == restoredDeckName }
                            if (restoredDeck != null) {
                                _deckId.value = restoredDeck.id
                            }
                        }
                    }

                    // Load available decks and note types
                    ensureActive()
                    _availableDecks.value =
                        col.decks.allNamesAndIds(skipEmptyDefault = true).map { it.name }
                    _availableNoteTypes.value = col.notetypes.all().map { it.name }

                    // Check cancellation after loading deck/notetype lists
                    ensureActive()

                    // Update UI state
                    updateStateFromNote(col, isAddingNote)

                    // Check cancellation after state update
                    ensureActive()
                }

                // Back on Main dispatcher - update UI state
                // Restore tags if available
                if (restoredTags != null) {
                    _noteEditorState.update { it.copy(tags = restoredTags.toList()) }
                }

                // Restore focused field if available and valid
                if (restoredFocusedIndex != null) {
                    _noteEditorState.update { currentState ->
                        if (currentState.fields.any { it.index == restoredFocusedIndex }) {
                            currentState.copy(focusedFieldIndex = restoredFocusedIndex)
                        } else {
                            currentState
                        }
                    }
                }

                // Capture initial field values for change detection
                initialFieldValues = _noteEditorState.value.fields.map { it.value.text }
                initialTags = _noteEditorState.value.tags

                // Capture initial deck and note type for change detection
                initialDeckId = _deckId.value
                initialNoteTypeId = _currentNote.value?.notetype?.id ?: 0L

                // Reset the field edited flag after initialization to prevent false positives
                _isFieldEdited.value = false

                // Load tags
                loadTags(col)

                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val errorMessage = "Failed to initialize editor: ${e.message ?: "Unknown error"}"
                Timber.e(e, "Error initializing note editor")
                _errorState.value = errorMessage
                onComplete?.invoke(false, errorMessage)
            }
        }
    }

    /**
     * Escapes a deck name for use in a deck search query.
     * Must escape backslashes first, then quotes, to prevent breaking the search query.
     */
    private fun escapeForDeckQuery(deckName: String): String {
        return deckName.replace("\\", "\\\\") // Escape backslashes first
            .replace("\"", "\\\"") // Then escape quotes
    }

    private fun loadTags(col: Collection) {
        viewModelScope.launch {
            _tagsState.value = TagsState.Loading

            // Perform all DB operations on IO dispatcher
            val (allTags, deckSpecificTags) = withContext(ioDispatcher) {
                val all = col.tags.all().sorted()

                val deckSpecific = if (_deckId.value != 0L) {
                    // Query all notes in the selected deck using the deck search operator
                    val noteIds = try {
                        val deckName = col.decks.name(_deckId.value)
                        val escapedDeckName = escapeForDeckQuery(deckName)
                        col.findNotes("deck:\"$escapedDeckName\"")
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading deck tags")
                        emptyList()
                    }

                    // Collect all tags from the notes
                    noteIds.asSequence().mapNotNull { noteId ->
                            try {
                                col.getNote(noteId).tags
                            } catch (e: Exception) {
                                Timber.w(e, "Error getting note tags for note $noteId")
                                null
                            }
                        }.flatten().toSet()
                } else {
                    emptySet()
                }

                all to deckSpecific
            }

            // Back on Main dispatcher - update UI state
            _tagsState.value = TagsState.Loaded(allTags)
            _deckTags.value = deckSpecificTags
        }
    }

    /**
     * Calculate the deck ID for a new note based on preferences and context
     */
    private fun calculateDeckIdForNewNote(
        col: Collection,
        providedDeckId: Long?,
        notetype: NotetypeJson,
    ): Long {
        // If a specific deck was provided and it's valid, use it
        if (providedDeckId != null && providedDeckId != 0L) {
            return providedDeckId
        }

        // Check if we should use the current deck or the note type's deck
        val useCurrentDeck = try {
            col.config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)
        } catch (e: Exception) {
            Timber.w(e, "Error reading config, defaulting to current deck")
            true
        }

        if (!useCurrentDeck) {
            // Use the note type's default deck
            return notetype.did
        }

        // Use the current deck
        val currentDeckId = try {
            col.config.get(com.ichi2.anki.libanki.Decks.CURRENT_DECK) ?: 1L
        } catch (e: Exception) {
            Timber.w(e, "Error getting current deck, using default")
            1L
        }

        // If current deck is filtered, use default deck instead
        return if (col.decks.isFiltered(currentDeckId)) {
            1L
        } else {
            currentDeckId
        }
    }

    /**
     * Update field value
     */
    fun updateFieldValue(
        index: Int,
        value: TextFieldValue,
    ) {
        _noteEditorState.update { currentState ->
            val position = currentState.fields.indexOfFirst { it.index == index }
            if (position == -1) {
                return@update currentState
            }
            val updatedFields = currentState.fields.toMutableList()
            updatedFields[position] = updatedFields[position].copy(value = value)
            val focusedIndex = currentState.focusedFieldIndex ?: index
            currentState.copy(fields = updatedFields, focusedFieldIndex = focusedIndex)
        }

        // Check if field values have actually changed from initial values
        updateFieldEditedFlag()

        // Persist field values to SavedStateHandle
        persistDraftState()
    }

    /**
     * Toggle sticky state for a field
     */
    fun toggleStickyField(index: Int) {
        _noteEditorState.update { currentState ->
            val position = currentState.fields.indexOfFirst { it.index == index }
            if (position == -1) {
                return@update currentState
            }
            val updatedFields = currentState.fields.toMutableList()
            val currentField = updatedFields[position]
            updatedFields[position] = currentField.copy(isSticky = !currentField.isSticky)
            currentState.copy(fields = updatedFields)
        }
    }

    /**
     * Update tags
     */
    fun updateTags(tags: Set<String>) {
        _noteEditorState.update { it.copy(tags = tags.toList()) }
        updateFieldEditedFlag()
        persistDraftState()
    }

    fun addTag(tag: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val col = collectionProvider()
                // Register the tag by setting its collapse state (registers if missing)
                col.tags.setCollapsed(tag, collapsed = false)
            }
            // loadTags uses IO dispatcher internally, safe to call here
            loadTags(collectionProvider())
        }
    }

    fun onFieldFocus(index: Int) {
        _noteEditorState.update { currentState ->
            if (currentState.fields.any { it.index == index }) {
                currentState.copy(focusedFieldIndex = index)
            } else {
                currentState
            }
        }
        persistDraftState()
    }

    /**
     * Select a deck
     */
    fun selectDeck(deckName: String) {
        viewModelScope.launch {
            try {
                val col = collectionProvider()
                ensureActive() // Check cancellation after getting collection

                // Perform DB operations on IO dispatcher
                val deckId = withContext(ioDispatcher) {
                    val deck = col.decks.allNamesAndIds().find { it.name == deckName }
                    deck?.id
                }

                // Back on Main dispatcher - update UI state
                if (deckId != null) {
                    _deckId.value = deckId
                    _noteEditorState.update { it.copy(selectedDeckName = deckName) }
                    persistDraftState()
                    loadTags(col) // Reload deck tags when deck changes
                }
            } catch (e: Exception) {
                val errorMessage = "Failed to select deck: ${e.message ?: "Unknown error"}"
                Timber.e(e, "Error selecting deck")
                _errorState.value = errorMessage
            }
        }
    }

    /**
     * Select a note type
     */
    fun selectNoteType(noteTypeName: String) {
        viewModelScope.launch {
            try {
                val col = collectionProvider()
                ensureActive() // Check cancellation after getting collection

                // Perform all DB operations on IO dispatcher
                val result = withContext(ioDispatcher) {
                    val notetype = col.notetypes.all().find { it.name == noteTypeName }
                    if (notetype == null) {
                        Timber.w("Note type '%s' not found", noteTypeName)
                        return@withContext null
                    }

                    // Check if we're already using this note type
                    val currentNote = _currentNote.value
                    if (currentNote != null) {
                        Timber.d(
                            "Current note type: '%s' (id: %d)",
                            currentNote.notetype.name,
                            currentNote.notetype.id
                        )
                        if (currentNote.notetype.id == notetype.id) {
                            Timber.d(
                                "Note type '%s' is already selected, skipping change",
                                noteTypeName
                            )
                            return@withContext null
                        }
                    }

                    ensureActive() // Check cancellation after notetype lookup

                    Timber.i(
                        "Changing note type from '%s' to '%s' (id: %d)",
                        currentNote?.notetype?.name ?: "null",
                        noteTypeName,
                        notetype.id,
                    )

                    // Set the current note type in the collection
                    col.notetypes.setCurrent(notetype)

                    // Clear notetype cache to ensure we get the fresh notetype
                    col.notetypes.clearCache()

                    // Re-fetch the notetype to ensure we have the latest version
                    val freshNotetype = col.notetypes.get(notetype.id)
                    if (freshNotetype == null) {
                        Timber.e("Failed to fetch fresh notetype after cache clear")
                        return@withContext null
                    }

                    Timber.d(
                        "Fresh notetype: name='%s', id=%d, fields=%d",
                        freshNotetype.name,
                        freshNotetype.id,
                        freshNotetype.fields.length(),
                    )

                    // Update the current deck to use this note type
                    val currentDeck = col.decks.current()
                    currentDeck.put("mid", freshNotetype.id)
                    col.decks.save(currentDeck)

                    // Calculate new deck ID if configuration says to use note type's default deck
                    val newDeckId =
                        if (!col.config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
                            Timber.d(
                                "Updated deck ID to note type's default deck: %d",
                                freshNotetype.did
                            )
                            freshNotetype.did
                        } else {
                            _deckId.value
                        }

                    // Capture existing note to preserve matching field values
                    val oldNote = _currentNote.value
                    val newNote = Note.fromNotetypeId(col, freshNotetype.id)

                    Timber.d(
                        "After Note.fromNotetypeId: newNote.notetype.name='%s', newNote.notetype.id=%d",
                        newNote.notetype.name,
                        newNote.notetype.id,
                    )
                    Timber.d(
                        "Expected notetype.name='%s', notetype.id=%d",
                        freshNotetype.name,
                        freshNotetype.id
                    )

                    // Copy field values from old note to new note where field names match
                    if (oldNote != null) {
                        ensureActive()

                        // Preserve original note ID so updateNote targets the correct record
                        // Note: guId, mod, usn have private setters and will be updated by col.updateNote()
                        newNote.id = oldNote.id

                        val oldNotetype = oldNote.notetype
                        oldNotetype.fields.forEachIndexed { oldIndex, oldField ->
                            if (oldIndex < oldNote.fields.size) {
                                val oldValue = oldNote.fields[oldIndex]
                                // Find matching field in new notetype
                                val newIndex =
                                    freshNotetype.fields.indexOfFirst { it.name == oldField.name }
                                if (newIndex >= 0 && newIndex < newNote.fields.size) {
                                    newNote.fields[newIndex] = oldValue
                                    Timber.v(
                                        "Copied field '%s' -> '%s': %s",
                                        oldField.name,
                                        freshNotetype.fields[newIndex].name,
                                        oldValue,
                                    )
                                }
                            }
                        }

                        // Reapply current draft tags from the UI state
                        val currentTags = _noteEditorState.value.tags
                        if (currentTags.isNotEmpty()) {
                            newNote.setTagsFromStr(col, currentTags.joinToString(" "))
                            Timber.d(
                                "Reapplied tags to new note: %s",
                                currentTags.joinToString(", ")
                            )
                        }

                        ensureActive() // Check cancellation after field copying and tag restoration
                    }

                    Timber.d(
                        "About to return result: newNote object id=%s, notetype.name='%s', notetype.id=%d, fields=%d",
                        System.identityHashCode(newNote),
                        newNote.notetype.name,
                        newNote.notetype.id,
                        newNote.fields.size,
                    )

                    // Return result data
                    Triple(newNote, freshNotetype, newDeckId)
                }

                // Back on Main dispatcher - update UI state
                if (result != null) {
                    val (newNote, freshNotetype, newDeckId) = result

                    // Update deck ID if needed
                    if (newDeckId != _deckId.value) {
                        _deckId.value = newDeckId
                    }

                    Timber.d(
                        "Setting _currentNote.value: newNote object id=%s, notetype.name='%s', notetype.id=%d",
                        System.identityHashCode(newNote),
                        newNote.notetype.name,
                        newNote.notetype.id,
                    )

                    // Force StateFlow update by creating a new reference
                    // Note: StateFlow uses equals() which only compares IDs, so we need to force the update
                    _currentNote.value = null
                    _currentNote.value = newNote

                    Timber.d(
                        "After setting _currentNote.value: _currentNote.value object id=%s, notetype.name='%s', notetype.id=%d",
                        System.identityHashCode(_currentNote.value),
                        _currentNote.value?.notetype?.name,
                        _currentNote.value?.notetype?.id,
                    )

                    // Pass the fresh notetype directly to avoid cache issues
                    updateStateFromNoteWithNotetype(
                        col,
                        _noteEditorState.value.isAddingNote,
                        freshNotetype
                    )

                    // Update initial field values after note type change
                    initialFieldValues = _noteEditorState.value.fields.map { it.value.text }
                    _isFieldEdited.value = false

                    persistDraftState()

                    Timber.d("Successfully changed note type to '%s'", noteTypeName)
                }
            } catch (e: Exception) {
                val errorMessage = "Failed to change note type: ${e.message ?: "Unknown error"}"
                Timber.e(e, "Error changing note type")
                _errorState.value = errorMessage
            }
        }
    }

    fun setToolbarButtons(buttons: List<ToolbarButtonModel>) {
        _toolbarButtons.value = buttons
    }

    fun setToolbarVisibility(isVisible: Boolean) {
        _showToolbar.value = isVisible
    }

    /**
     * Save the note
     * @return NoteFieldsCheckResult indicating success or failure with error details
     */
    suspend fun saveNote(): NoteFieldsCheckResult {
        return try {
            val col = collectionProvider()
            val note = _currentNote.value ?: return NoteFieldsCheckResult.Failure(null)
            val currentCard = _currentCard.value

            // Perform all DB operations on IO dispatcher
            val saveResult: SaveResult = withContext(ioDispatcher) {
                // Update note fields from state
                val fields = _noteEditorState.value.fields
                fields.forEach { fieldState ->
                    val fieldIndex = fieldState.index
                    if (fieldIndex in note.fields.indices) {
                        // Convert newlines to HTML <br> tags when saving
                        // This ensures newlines are properly displayed when viewing cards
                        note.fields[fieldIndex] = NoteService.convertToHtmlNewline(
                            fieldState.value.text,
                            replaceNewlines = true,
                        )
                    }
                }

                // Update tags
                note.setTagsFromStr(col, _noteEditorState.value.tags.joinToString(" "))

                // For new notes, validate fields before saving
                if (_noteEditorState.value.isAddingNote) {
                    val validationResult = checkNoteFieldsResponse(note)
                    if (validationResult is NoteFieldsCheckResult.Failure) {
                        return@withContext SaveResult.ValidationFailure(validationResult)
                    }
                    col.addNote(note, _deckId.value)

                    // Update Note Type's default deck if configured not to use current deck
                    // This mirrors legacy behavior where selecting a deck for a note type updates its preference
                    if (!col.config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
                        val notetype = note.notetype
                        if (notetype.did != _deckId.value) {
                            Timber.d(
                                "Updating note type '%s' default deck to %d",
                                notetype.name,
                                _deckId.value
                            )
                            notetype.did = _deckId.value
                            col.notetypes.save(notetype)
                        }
                    }

                    // Reset to a fresh blank note for the next add, preserving sticky field values and state
                    val currentState = _noteEditorState.value

                    // Capture both sticky values and sticky state flags from the current UI
                    val stickyInfo = currentState.fields.associate { field ->
                        field.index to (field.isSticky to field.value.text)
                    }

                    val freshNote = Note.fromNotetypeId(col, note.notetype.id)
                    // Apply sticky field values to the new note
                    stickyInfo.forEach { (index, stickyData) ->
                        val (isSticky, value) = stickyData
                        if (isSticky && index < freshNote.fields.size) {
                            freshNote.fields[index] = value
                        }
                    }

                    // Return success with fresh note and sticky info
                    SaveResult.NewNote(freshNote, stickyInfo)
                } else {
                    // When editing an existing card, check if deck changed
                    val updatedCard =
                        if (currentCard != null && currentCard.currentDeckId() != _deckId.value) {
                            // Move card to new deck
                            col.setDeck(listOf(currentCard.id), _deckId.value)
                            // Refresh the card object to reflect database changes
                            currentCard.load(col)
                            Timber.d("Card deck updated to %d", _deckId.value)
                            currentCard
                        } else {
                            null
                        }

                    // Explicitly ignore OpChanges - UI updates happen through reactive state
                    col.updateNote(note).let { }

                    // Return success with updated card if applicable
                    SaveResult.UpdatedNote(updatedCard)
                }
            }

            // Back on Main dispatcher - update UI state based on save result type
            when (saveResult) {
                is SaveResult.ValidationFailure -> {
                    _errorState.value = "Note validation failed: Please check required fields"
                    return saveResult.validationResult
                }

                is SaveResult.NewNote -> {
                    // Update the current note reference
                    _currentNote.value = saveResult.note

                    // Update state with the fresh note, then restore sticky flags from UI
                    updateStateFromNote(col, isAddingNote = true)

                    // Restore the sticky state flags that were set in the UI
                    _noteEditorState.update { state ->
                        val updatedFields = state.fields.map { field ->
                            val uiStickyState = saveResult.stickyInfo[field.index]?.first
                            if (uiStickyState != null) {
                                field.copy(isSticky = uiStickyState)
                            } else {
                                field
                            }
                        }
                        state.copy(fields = updatedFields)
                    }

                    // Update initial field values after resetting for next note
                    initialFieldValues = _noteEditorState.value.fields.map { it.value.text }
                    initialTags = _noteEditorState.value.tags
                    _isFieldEdited.value = false
                }

                is SaveResult.UpdatedNote -> {
                    if (saveResult.card != null) {
                        // Update the cached card
                        _currentCard.value = saveResult.card
                    }
                }
            }

            // Clear draft state after successful save
            clearDraftState()
            NoteFieldsCheckResult.Success
        } catch (e: Exception) {
            val errorMessage = "Failed to save note: ${e.message ?: "Unknown error"}"
            Timber.e(e, "Error saving note")
            _errorState.value = errorMessage
            NoteFieldsCheckResult.Failure(null)
        }
    }

    fun formatSelection(
        prefix: String,
        suffix: String,
    ): Boolean {
        val targetIndex = determineFocusIndex() ?: return false
        val result = updateFieldValueInternal(targetIndex) { value ->
            val text = value.text
            val selection = value.selection
            val start = selection.start.coerceIn(0, text.length)
            val end = selection.end.coerceIn(0, text.length)
            val rangeStart = min(start, end)
            val rangeEnd = max(start, end)
            val before = text.take(rangeStart)
            val selected = text.substring(rangeStart, rangeEnd)
            val after = text.substring(rangeEnd)

            if (selected.isEmpty()) {
                val newText = buildString {
                    append(before)
                    append(prefix)
                    append(suffix)
                    append(after)
                }
                val cursor = rangeStart + prefix.length
                value.copy(text = newText, selection = TextRange(cursor, cursor))
            } else {
                val newText = buildString {
                    append(before)
                    append(prefix)
                    append(selected)
                    append(suffix)
                    append(after)
                }
                val newStart = rangeStart + prefix.length
                val newEnd = newStart + selected.length
                value.copy(text = newText, selection = TextRange(newStart, newEnd))
            }
        }
        if (result) {
            _isFieldEdited.value = true
        }
        return result
    }

    fun insertCloze(mode: ClozeInsertionMode): Boolean {
        val baseIndex = calculateNextClozeIndex()
        val clozeIndex = when (mode) {
            ClozeInsertionMode.SAME_NUMBER -> max(1, baseIndex - 1)
            ClozeInsertionMode.INCREMENT_NUMBER -> baseIndex
        }
        return formatSelection("{{c$clozeIndex::", "}}")
    }

    fun applyToolbarButton(button: ToolbarButtonModel): Boolean =
        formatSelection(button.prefix, button.suffix)

    fun applyToolbarShortcut(shortcutDigit: Int): Boolean {
        val buttons = _toolbarButtons.value
        if (buttons.isEmpty()) {
            return false
        }
        val target = buttons.firstOrNull { button ->
            val visualIndex = button.index + 1
            val mod = visualIndex % 10
            if (shortcutDigit == 0) {
                mod == 0
            } else {
                mod == shortcutDigit
            }
        } ?: return false
        return applyToolbarButton(target)
    }

    /**
     * Helper to map note fields into NoteFieldState instances, converting HTML <br> tags
     * to newlines for editing. Centralizes the replacement logic used in multiple places.
     * This allows users to see and edit newlines naturally.
     * When saving, these newlines are converted back to <br> tags.
     * TODO: Respect PREF_NOTE_EDITOR_NEWLINE_REPLACE preference (currently always converts, defaults to true)
     */
    private fun mapFieldsFromNote(
        note: Note,
        notetype: NotetypeJson,
    ): List<NoteFieldState> = (0 until notetype.fields.length()).map { index ->
        val field = notetype.fields[index]
        val value = if (index < note.fields.size) note.fields[index] else ""
        val editableValue =
            value.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
                .replace("<BR>", "\n").replace("<BR/>", "\n").replace("<BR />", "\n")
        NoteFieldState(
            name = field.name,
            value = TextFieldValue(editableValue),
            isSticky = field.sticky,
            hint = "",
            index = index,
        )
    }

    /**
     * Update state from the current note
     */
    private fun updateStateFromNote(
        col: Collection,
        isAddingNote: Boolean,
    ) {
        val note = _currentNote.value ?: return
        val notetype = note.notetype

        Timber.d(
            "updateStateFromNote: note object id=%s, notetype.name='%s', notetype.id=%d",
            System.identityHashCode(note),
            notetype.name,
            notetype.id,
        )

        val fields = mapFieldsFromNote(note, notetype)

        val deckName = getDeckNameSafely(col, _deckId.value)

        Timber.d(
            "updateStateFromNote: Updating state with note type '%s', %d fields",
            notetype.name,
            fields.size
        )

        _noteEditorState.update { currentState ->
            val newFocus = currentState.focusedFieldIndex?.takeIf { focus ->
                fields.any { it.index == focus }
            } ?: fields.firstOrNull()?.index

            Timber.d(
                "updateStateFromNote: Old state note type='%s', New state note type='%s'",
                currentState.selectedNoteTypeName,
                notetype.name,
            )

            currentState.copy(
                fields = fields,
                tags = note.tags,
                selectedDeckName = deckName,
                selectedNoteTypeName = notetype.name,
                isAddingNote = isAddingNote,
                isClozeType = notetype.isCloze,
                isImageOcclusion = notetype.isImageOcclusion,
                cardsInfo = if (isAddingNote) {
                    ""
                } else {
                    "Cards: ${note.numberOfCards(col)}"
                },
                focusedFieldIndex = newFocus,
            )
        }

        Timber.d(
            "updateStateFromNote: State updated, current selectedNoteTypeName='%s'",
            _noteEditorState.value.selectedNoteTypeName,
        )
    }

    /**
     * Update state from the current note using an explicitly provided notetype
     * This avoids cache issues when the note's notetype property might be stale
     */
    private fun updateStateFromNoteWithNotetype(
        col: Collection,
        isAddingNote: Boolean,
        notetype: NotetypeJson,
    ) {
        val note = _currentNote.value ?: return

        Timber.d(
            "updateStateFromNoteWithNotetype: note object id=%s, provided notetype.name='%s', notetype.id=%d",
            System.identityHashCode(note),
            notetype.name,
            notetype.id,
        )
        Timber.d(
            "updateStateFromNoteWithNotetype: note has %d fields, notetype defines %d fields",
            note.fields.size,
            notetype.fields.length(),
        )

        // Map based on the notetype's field definitions (not the note's current fields)
        // This ensures we get the correct number of fields
        val fields = mapFieldsFromNote(note, notetype)

        val deckName = try {
            if (_deckId.value == 0L) {
                // If deckId is not set, use the default deck
                col.decks.name(1L)
            } else {
                col.decks.name(_deckId.value)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error getting deck name for deck ID ${_deckId.value}, using default deck")
            try {
                // Fall back to the default deck (ID 1)
                col.decks.name(1L)
            } catch (e2: Exception) {
                Timber.e(e2, "Error getting default deck name")
                "Default"
            }
        }

        Timber.d(
            "updateStateFromNoteWithNotetype: Updating state with note type '%s', %d fields",
            notetype.name,
            fields.size
        )

        _noteEditorState.update { currentState ->
            val newFocus = currentState.focusedFieldIndex?.takeIf { focus ->
                fields.any { it.index == focus }
            } ?: fields.firstOrNull()?.index

            Timber.d(
                "updateStateFromNoteWithNotetype: Old state note type='%s', New state note type='%s'",
                currentState.selectedNoteTypeName,
                notetype.name,
            )

            currentState.copy(
                fields = fields,
                tags = note.tags,
                selectedDeckName = deckName,
                selectedNoteTypeName = notetype.name,
                isAddingNote = isAddingNote,
                isClozeType = notetype.isCloze,
                isImageOcclusion = notetype.isImageOcclusion,
                // Don't update cardsInfo here - let the Fragment's updateCards() handle it
                // cardsInfo = currentState.cardsInfo,
                focusedFieldIndex = newFocus,
            )
        }

        Timber.d(
            "updateStateFromNoteWithNotetype: State updated, current selectedNoteTypeName='%s'",
            _noteEditorState.value.selectedNoteTypeName,
        )
    }

    /**
     * Toggle toolbar visibility
     */
    fun toggleToolbarVisibility() {
        _showToolbar.update { !it }
    }

    /**
     * Check if current field values differ from initial values
     */
    private fun updateFieldEditedFlag() {
        val currentValues = _noteEditorState.value.fields.map { it.value.text }
        // Also check if tags changed
        val tagsChanged = _noteEditorState.value.tags != initialTags
        _isFieldEdited.value = currentValues != initialFieldValues || tagsChanged
    }

    /**
     * Reset the field edited flag (e.g., after successful save)
     */
    fun resetFieldEditedFlag() {
        _isFieldEdited.value = false
        // Update initial values to current values after save
        initialFieldValues = _noteEditorState.value.fields.map { it.value.text }
        initialTags = _noteEditorState.value.tags
    }

    /**
     * Show or hide the discard changes confirmation dialog
     */
    fun setShowDiscardChangesDialog(show: Boolean) {
        _showDiscardChangesDialog.value = show
    }

    /**
     * Check if there are unsaved changes.
     *
     * Checks field content, tags, deck selection, and note type changes.
     *
     * **Design Decision**: Deck and note type changes are intentionally counted as "unsaved changes"
     * for BOTH new notes and existing notes. While some may argue that selecting a deck/note type
     * for a new note is "initial setup" rather than a change, I prefer to show the discard dialog
     * to prevent accidental loss of user selections. This is a deliberate UX choice.
     *
     * Note: The tracking variables (initialDeckId, initialNoteTypeId) are set once during
     * initialization and not updated after intentional changes. This is intentional - any deviation
     * from the initial state should trigger the warning, regardless of intermediate changes.
     */
    fun hasUnsavedChanges(): Boolean {
        // If we have specific field edit flag
        if (_isFieldEdited.value) return true

        // Manual check of field values against initial values
        val currentValues = _noteEditorState.value.fields.map { it.value.text }
        if (currentValues != initialFieldValues) return true

        // Check tags
        if (_noteEditorState.value.tags != initialTags) return true

        // Check deck change (applies to both new and existing notes - see docstring)
        if (initialDeckId != 0L && _deckId.value != initialDeckId) return true

        // Check note type change (applies to both new and existing notes - see docstring)
        val currentNoteTypeId = _currentNote.value?.notetype?.id ?: 0L
        return initialNoteTypeId != 0L && currentNoteTypeId != initialNoteTypeId
    }

    private fun determineFocusIndex(): Int? {
        val state = _noteEditorState.value
        val focus = state.focusedFieldIndex
        if (focus != null && state.fields.any { it.index == focus }) {
            return focus
        }
        return state.fields.firstOrNull()?.index
    }

    private fun updateFieldValueInternal(
        fieldIndex: Int,
        transform: (TextFieldValue) -> TextFieldValue?,
    ): Boolean {
        var applied = false
        _noteEditorState.update { currentState ->
            val position = currentState.fields.indexOfFirst { it.index == fieldIndex }
            if (position == -1) {
                return@update currentState
            }
            val field = currentState.fields[position]
            val newValue = transform(field.value)
            if (newValue == null || newValue == field.value) {
                return@update currentState
            }
            val updatedFields = currentState.fields.toMutableList()
            updatedFields[position] = field.copy(value = newValue)
            applied = true
            currentState.copy(fields = updatedFields, focusedFieldIndex = fieldIndex)
        }
        return applied
    }

    private fun calculateNextClozeIndex(): Int {
        return try {
            val values = _noteEditorState.value.fields.map { it.value.text }
            // Check for cancellation before potentially expensive operation
            if (!viewModelScope.coroutineContext.isActive) {
                Timber.d("Coroutine cancelled during cloze index calculation")
                return 1
            }
            ClozeUtils.getNextClozeIndex(values)
        } catch (e: Exception) {
            Timber.w(e, "Error calculating next cloze index")
            1
        }
    }

    /**
     * Persist draft state to SavedStateHandle for process death recovery
     */
    private fun persistDraftState() {
        if (savedStateHandle == null) {
            Timber.d("SavedStateHandle not available, skipping draft state persistence")
            return
        }
        try {
            val state = _noteEditorState.value
            savedStateHandle[KEY_FIELD_VALUES] = state.fields.map { it.value.text }.toTypedArray()
            savedStateHandle[KEY_TAGS] = state.tags.toTypedArray()
            savedStateHandle[KEY_SELECTED_DECK_NAME] = state.selectedDeckName
            state.focusedFieldIndex?.let { savedStateHandle[KEY_FOCUSED_FIELD_INDEX] = it }
        } catch (e: Exception) {
            Timber.w(e, "Error persisting draft state")
        }
    }

    /**
     * Clear draft state from SavedStateHandle after successful save
     */
    private fun clearDraftState() {
        if (savedStateHandle == null) {
            return
        }
        try {
            savedStateHandle.remove<Array<String>>(KEY_FIELD_VALUES)
            savedStateHandle.remove<Array<String>>(KEY_TAGS)
            savedStateHandle.remove<String>(KEY_SELECTED_DECK_NAME)
            savedStateHandle.remove<Int>(KEY_FOCUSED_FIELD_INDEX)
        } catch (e: Exception) {
            Timber.w(e, "Error clearing draft state")
        }
    }

    /**
     * Clear the current error state (e.g., after user acknowledges the error)
     */
    fun clearError() {
        _errorState.value = null
    }

    /**
     * Update the cards info display after template changes
     */
    fun updateCardsInfo(cardsInfo: String) {
        _noteEditorState.update { currentState ->
            currentState.copy(cardsInfo = cardsInfo)
        }
    }

    /**
     * Enable or disable the Tags button
     */
    fun setTagsButtonEnabled(enabled: Boolean) {
        _noteEditorState.update { currentState ->
            currentState.copy(isTagsButtonEnabled = enabled)
        }
    }

    /**
     * Enable or disable the Cards button
     */
    fun setCardsButtonEnabled(enabled: Boolean) {
        _noteEditorState.update { currentState ->
            currentState.copy(isCardsButtonEnabled = enabled)
        }
    }

    /**
     * Show the no-cloze confirmation dialog with the given error message
     */
    fun showNoClozeDialog(errorMessage: String) {
        _noClozeDialogState.value = errorMessage
    }

    /**
     * Dismiss the no-cloze confirmation dialog
     */
    fun dismissNoClozeDialog() {
        _noClozeDialogState.value = null
    }

    /**
     * Show the add toolbar item dialog
     */
    fun showAddToolbarDialog() {
        _toolbarDialogState.value = ToolbarItemDialogState(isVisible = true, isEditMode = false)
    }

    /**
     * Show the edit toolbar item dialog with existing values
     */
    fun showEditToolbarDialog(icon: String, prefix: String, suffix: String, buttonIndex: Int) {
        _toolbarDialogState.value = ToolbarItemDialogState(
            isVisible = true,
            isEditMode = true,
            icon = icon,
            prefix = prefix,
            suffix = suffix,
            buttonIndex = buttonIndex,
        )
    }

    /**
     * Dismiss the toolbar item dialog
     */
    fun dismissToolbarDialog() {
        _toolbarDialogState.value = ToolbarItemDialogState()
    }

    /**
     * Safely retrieve the deck name for a given deck ID, with fallback to default deck
     */
    private fun getDeckNameSafely(
        col: Collection,
        deckId: Long,
    ): String = try {
        if (deckId == 0L) {
            // If deckId is not set, use the default deck
            col.decks.name(1L)
        } else {
            col.decks.name(deckId)
        }
    } catch (e: Exception) {
        Timber.w(e, "Error getting deck name for deck ID $deckId, using default deck")
        try {
            // Fall back to the default deck (ID 1)
            col.decks.name(1L)
        } catch (e2: Exception) {
            Timber.e(e2, "Error getting default deck name")
            "Default"
        }
    }
}
