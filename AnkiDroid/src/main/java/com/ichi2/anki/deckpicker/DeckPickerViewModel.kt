/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.os.Build
import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.card_rendering.EmptyCardsReport
import anki.i18n.GeneratedTranslations
import anki.sync.SyncStatusResponse
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.configureRenderingMode
import com.ichi2.anki.deckpicker.compose.StudyOptionsData
import com.ichi2.anki.dialogs.compose.DeckDialogType
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.QueueType.ManuallyBuried
import com.ichi2.anki.libanki.QueueType.SiblingBuried
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.libanki.utils.extend
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.notetype.ManageNoteTypesDestination
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.performBackupInBackground
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.syncAuth
import com.ichi2.anki.undoAndGetSnackbarMessage
import com.ichi2.anki.utils.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import timber.log.Timber

/**
 * ViewModel for the [DeckPicker]
 */
class DeckPickerViewModel : ViewModel(), OnErrorListener {
    val isSyncing = MutableStateFlow(false)
    val flowOfStartupResponse = MutableStateFlow<StartupResponse?>(null)

    private val flowOfDeckDueTree = MutableStateFlow<DeckNode?>(null)

    /** Decks that contain buried cards */
    private val flowOfBuriedDecks = MutableStateFlow<Set<DeckId>>(emptySet())

    private val _syncState = MutableStateFlow(SyncIconState.Normal)
    val syncState: StateFlow<SyncIconState> = _syncState.asStateFlow()

    private val _syncDialogState = MutableStateFlow<SyncDialogState?>(null)
    val syncDialogState: StateFlow<SyncDialogState?> = _syncDialogState.asStateFlow()

    private val _studyOptionsData = MutableStateFlow<StudyOptionsData?>(null)
    val studyOptionsData: StateFlow<StudyOptionsData?> = _studyOptionsData.asStateFlow()

    private val _showLoginToAnkiWebDialog = MutableStateFlow(false)
    val showLoginToAnkiWebDialog: StateFlow<Boolean> = _showLoginToAnkiWebDialog.asStateFlow()

    private val _showNetworkErrorDialog = MutableStateFlow(false)
    val showNetworkErrorDialog: StateFlow<Boolean> = _showNetworkErrorDialog.asStateFlow()

    fun setShowLoginToAnkiWebDialog(show: Boolean) {
        _showLoginToAnkiWebDialog.value = show
    }

    fun setShowNetworkErrorDialog(show: Boolean) {
        _showNetworkErrorDialog.value = show
    }

    fun showSyncDialog(title: String, message: String, onCancel: () -> Unit) {
        _syncDialogState.value = SyncDialogState(title, message, onCancel)
    }

    fun updateSyncDialog(message: String) {
        _syncDialogState.value?.let { current ->
            _syncDialogState.value = current.copy(message = message)
        }
    }

    fun hideSyncDialog() {
        _syncDialogState.value = null
    }

    /** The root of the tree displaying all decks */
    var dueTree: DeckNode?
        get() = flowOfDeckDueTree.value
        private set(value) {
            flowOfDeckDueTree.value = value
        }

    /** User filter of the deck list. Shown as a search in the UI */
    private val flowOfCurrentDeckFilter = MutableStateFlow("")

    // Declared before init because it is used by the startup collector during object construction.
    val flowOfDecksReloaded = MutableSharedFlow<Unit>()

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    val flowOfFocusedDeck = MutableStateFlow<DeckId?>(null)

    val flowOfCurrentDeckId = MutableStateFlow(1L)

    var focusedDeck: DeckId?
        get() = flowOfFocusedDeck.value
        set(value) {
            flowOfFocusedDeck.value = value
            if (value != null) flowOfCurrentDeckId.value = value
        }

    init {
        viewModelScope.launch {
            combine(
                flowOfFocusedDeck,
                flowOfDecksReloaded.onStart { emit(Unit) },
            ) { deckId, _ -> deckId }.collectLatest { deckId ->
                _studyOptionsData.value = if (deckId != null) {
                    selectAndLoadStudyOptions(deckId)
                } else {
                    null
                }
            }
        }
    }

    private suspend fun selectAndLoadStudyOptions(deckId: DeckId): StudyOptionsData? {
        return try {
            withCol {
                decks.select(deckId)
                val deck = decks.getLegacy(deckId) ?: return@withCol null
                val counts = sched.counts()
                var buriedNew = 0
                var buriedLearning = 0
                var buriedReview = 0
                val tree = sched.deckDueTree(deckId)
                if (tree != null) {
                    buriedNew = tree.newCount - counts.new
                    buriedLearning = tree.learnCount - counts.lrn
                    buriedReview = tree.reviewCount - counts.rev
                }
                StudyOptionsData(
                    deckId = deckId,
                    deckName = deck.getString("name"),
                    deckDescription = deck.description,
                    newCount = counts.new,
                    lrnCount = counts.lrn,
                    revCount = counts.rev,
                    buriedNew = buriedNew,
                    buriedLrn = buriedLearning,
                    buriedRev = buriedReview,
                    totalNewCards = sched.totalNewForCurrentDeck(),
                    totalCards = decks.cardCount(
                        deckId,
                        includeSubdecks = true,
                    ),
                    isFiltered = deck.isFiltered,
                    haveBuried = sched.haveBuried(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load study options for deck %d", deckId)
            null
        }
    }

    /**
     * Used if the Deck Due Tree is mutated
     */
    private val flowOfRefreshDeckList = MutableSharedFlow<Unit>()

    val flowOfDeckList = combine(
        flowOfDeckDueTree,
        flowOfCurrentDeckFilter,
        flowOfFocusedDeck,
        flowOfBuriedDecks,
        combine(flowOfCurrentDeckId, flowOfRefreshDeckList.onStart { emit(Unit) }, ::Pair),
    ) { tree, filter, _, buriedDecks, (currentDeckId, _) ->
        if (tree == null) return@combine FlattenedDeckList.empty

        Timber.i("currentDeckId: %d", currentDeckId)

        FlattenedDeckList(
            data = tree.filterAndFlattenDisplay(filter, currentDeckId, buriedDecks),
            hasSubDecks = tree.children.any { it.children.any() },
        )
    }

    val flowOfDestination = MutableSharedFlow<Destination>()

    /**
     * One-shot UI effects handled by [DeckPicker].
     *
     * Uses [Channel.BUFFERED] so events wait for the Activity collector instead of being dropped
     * when there is a brief gap in collection, such as during recreation.
     *
     * @see DeckPickerEffect for all possible Activity effects
     */
    private val _effects = Channel<DeckPickerEffect>(Channel.BUFFERED)
    val effects: Flow<DeckPickerEffect> = _effects.receiveAsFlow()

    /**
     * One-shot UI effects handled by Compose.
     *
     * Kept separate from [effects] so Compose and the Activity do not compete for the same event.
     *
     * @see DeckPickerComposeEffect for all possible Compose effects
     */
    private val _composeEffects = Channel<DeckPickerComposeEffect>(Channel.BUFFERED)
    val composeEffects: Flow<DeckPickerComposeEffect> = _composeEffects.receiveAsFlow()

    /** Public API for external callers to show a snackbar via the Compose effects channel */
    suspend fun showSnackbar(message: String) {
        _composeEffects.send(DeckPickerComposeEffect.ShowSnackbarMessage(message))
    }

    override val onError = MutableSharedFlow<String>()

    var loadDeckCounts: Job? = null
        private set

    /**
     * Tracks the scheduler version for which the upgrade dialog was last shown,
     * to avoid repeatedly prompting the user for the same collection version.
     */
    private var schedulerUpgradeDialogShownForVersion: Long? = null

    val flowOfPromptUserToUpdateScheduler = MutableSharedFlow<Unit>()


    val flowOfUndoUpdated = MutableSharedFlow<Unit>()

    val flowOfCollectionHasNoCards = MutableStateFlow(true)

    val flowOfDeckListInInitialState =
        combine(flowOfDeckDueTree, flowOfCollectionHasNoCards) { tree, noCards ->
            if (tree == null) return@combine null
            // Check if default deck is the only available and there are no cards
            tree.onlyHasDefaultDeck() && noCards
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    val flowOfCardsDue =
        combine(flowOfDeckDueTree, flowOfDeckListInInitialState) { tree, inInitialState ->
            if (tree == null || inInitialState != false) return@combine null
            tree.newCount + tree.revCount + tree.lrnCount
        }

    /** Studied N cards in 0 seconds today */
    val flowOfStudiedTodayStats = MutableStateFlow("")

    private val _flowOfTimeUntilNextDay = MutableStateFlow(0L)
    val flowOfTimeUntilNextDay: StateFlow<Long> = _flowOfTimeUntilNextDay.asStateFlow()

    private val _createDeckDialogState = MutableStateFlow<CreateDeckDialogState>(
        CreateDeckDialogState.Hidden
    )

    val createDeckDialogState: StateFlow<CreateDeckDialogState> =
        _createDeckDialogState.asStateFlow()

    fun showCreateDeckDialog() {
        _createDeckDialogState.value = CreateDeckDialogState.Visible(
            type = DeckDialogType.DECK, titleResId = R.string.new_deck
        )
    }

    fun showCreateSubdeckDialog(parentId: DeckId) {
        _createDeckDialogState.value = CreateDeckDialogState.Visible(
            type = DeckDialogType.SUB_DECK,
            titleResId = R.string.create_subdeck,
            parentId = parentId
        )
    }

    fun showCreateFilteredDeckDialog() {
        _createDeckDialogState.value = CreateDeckDialogState.Visible(
            type = DeckDialogType.FILTERED_DECK, titleResId = R.string.new_dynamic_deck
        )
    }

    fun showRenameDeckDialog(deckId: DeckId) = viewModelScope.launch {
        try {
            val currentName = withCol { decks.getLegacy(deckId)?.name }
            if (currentName.isNullOrBlank()) {
                Timber.w("Deck not found for rename dialog: %d", deckId)
                return@launch
            }

            _createDeckDialogState.value = CreateDeckDialogState.Visible(
                type = DeckDialogType.RENAME_DECK,
                titleResId = R.string.rename_deck,
                initialName = currentName,
                deckIdToRename = deckId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load deck %d for rename dialog", deckId)
        }
    }


    fun dismissCreateDeckDialog() {
        _createDeckDialogState.value = CreateDeckDialogState.Hidden
    }

    enum class DeckNameError {
        INVALID_NAME, ALREADY_EXISTS
    }

    suspend fun validateDeckName(
        name: String, dialogState: CreateDeckDialogState.Visible
    ): DeckNameError? {
        return when {
            name.isBlank() -> null
            !Decks.isValidDeckName(getFullDeckName(name, dialogState)) -> DeckNameError.INVALID_NAME
            deckExists(name, dialogState) -> DeckNameError.ALREADY_EXISTS
            else -> null
        }
    }

    private suspend fun deckExists(name: String, state: CreateDeckDialogState.Visible): Boolean {
        val fullName = getFullDeckName(name, state)
        val existingDeck = withCol { decks.byName(fullName) }

        // No deck with this name exists
        if (existingDeck == null) return false

        // Allow renaming a deck to itself (same deck ID)
        if (state.type == DeckDialogType.RENAME_DECK && state.deckIdToRename != null) {
            val existingDeckId = existingDeck.getLong("id")
            if (existingDeckId == state.deckIdToRename) {
                return false
            }
        }

        return true
    }

    private suspend fun getFullDeckName(
        name: String, state: CreateDeckDialogState.Visible
    ): String {
        return when (state.type) {
            DeckDialogType.SUB_DECK -> {
                val parentId = state.parentId ?: return name
                withCol { decks.getSubdeckName(parentId, name) } ?: name
            }

            else -> name
        }
    }

    fun createDeck(name: String, state: CreateDeckDialogState.Visible) {
        viewModelScope.launch {
            try {
                var operationSucceeded = true
                var newFilteredDeckId: DeckId? = null
                withCol {
                    when (state.type) {
                        DeckDialogType.DECK -> decks.id(name)
                        DeckDialogType.SUB_DECK -> {
                            val parentId = state.parentId
                            if (parentId != null) {
                                decks.getSubdeckName(parentId, name)?.let { fullName ->
                                    decks.id(fullName)
                                } ?: run {
                                    Timber.w("Failed to get subdeck name for parent %d", parentId)
                                    operationSucceeded = false
                                }
                            } else {
                                Timber.w("SUB_DECK dialog opened without parentId")
                                operationSucceeded = false
                            }
                        }

                        DeckDialogType.RENAME_DECK -> {
                            // Use lookup-only (not get-or-create) to avoid accidentally creating a deck
                            val deckId = state.deckIdToRename ?: decks.byName(state.initialName)
                                ?.getLong("id")
                            if (deckId != null) {
                                decks.getLegacy(deckId)?.let {
                                    decks.rename(it, name)
                                } ?: run {
                                    Timber.w(
                                        "Deck no longer exists for rename: %s", state.initialName
                                    )
                                    operationSucceeded = false
                                }
                            } else {
                                Timber.w("Deck not found for rename: %s", state.initialName)
                                operationSucceeded = false
                            }
                        }

                        DeckDialogType.FILTERED_DECK -> {
                            newFilteredDeckId = decks.newFiltered(name)
                        }
                    }
                }

                if (operationSucceeded) {
                    _createDeckDialogState.value = CreateDeckDialogState.Hidden
                    if (newFilteredDeckId != null) {
                        openDeckOptions(newFilteredDeckId, isFiltered = true)
                    } else {
                        updateDeckList()
                        val messageResId = when (state.type) {
                            DeckDialogType.RENAME_DECK -> R.string.deck_renamed
                            else -> R.string.deck_created
                        }
                        _composeEffects.send(DeckPickerComposeEffect.ShowSnackbar(messageResId))
                    }
                } else {
                    // Keep dialog open and show error
                    _composeEffects.send(DeckPickerComposeEffect.ShowSnackbar(R.string.something_wrong))
                }
            } catch (e: CancellationException) {
                throw e // Don't catch coroutine cancellation
            } catch (e: BackendDeckIsFilteredException) {
                _composeEffects.send(
                    DeckPickerComposeEffect.ShowSnackbarMessage(
                        e.localizedMessage ?: e.message.orEmpty()
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to create/rename deck")
                _composeEffects.send(DeckPickerComposeEffect.ShowSnackbar(R.string.something_wrong))
            }
        }
    }

    fun onDeckSelected(
        deckId: DeckId,
        selectionType: DeckSelectionType,
    ) = viewModelScope.launch {
        val result = withCol {
            decks.select(deckId)
            CardBrowser.clearLastDeckId()
            focusedDeck = deckId
            val deck = dueTree?.find(deckId)
            if (deck != null && deck.hasCardsReadyToStudy()) {
                DeckSelectionResult.HasCardsToStudy(selectionType)
            } else {
                val isEmpty = deck?.all { decks.isEmpty(it.did) } ?: true
                if (isEmpty) {
                    DeckSelectionResult.Empty(deckId)
                } else {
                    _flowOfTimeUntilNextDay.value = calculateTimeUntilNextDay(sched)
                    DeckSelectionResult.NoCardsToStudy(deckId)
                }
            }
        }
        _composeEffects.send(DeckPickerComposeEffect.HandleDeckSelection(result))
    }

    /**
     * Deletes the provided deck, child decks, and all cards inside.
     *
     * @param did ID of the deck to delete
     */
    fun deleteDeck(did: DeckId) = viewModelScope.launch {
        var followUpEffect: DeckPickerComposeEffect
        try {
            val deckName = withCol { decks.getLegacy(did)?.name }
            if (deckName == null) {
                Timber.w("Deck %d not found for deletion", did)
                followUpEffect = DeckPickerComposeEffect.ShowSnackbar(R.string.something_wrong)
            } else {
                val changes = undoableOp { decks.remove(listOf(did)) }
                // Capture the undo step so we can merge any subsequent backend
                // operations (e.g. deck selection) into this single undo entry.
                val undoStep = withCol { undoStatus().lastStep }
                // After deletion: decks.current() reverts to Default, necessitating `focusedDeck`
                // to match and avoid unnecessary scrolls in `renderPage()`.
                focusedDeck = Consts.DEFAULT_DECK_ID
                updateDeckList()
                // Merge any undo entries created by deck selection (triggered by
                // focusedDeck assignment above) so that "Undo" restores the deleted
                // deck, not an intermediate setCurrentDeck operation.
                withCol { mergeUndoEntries(undoStep) }

                val deletionResult =
                    DeckDeletionResult(deckName = deckName, cardsDeleted = changes.count)
                followUpEffect =
                    DeckPickerComposeEffect.ShowUndoSnackbar(deletionResult.toHumanReadableString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete deck %d", did)
            followUpEffect = DeckPickerComposeEffect.ShowSnackbar(R.string.something_wrong)
        }
        _composeEffects.send(followUpEffect)
    }

    /**
     * Deletes the currently selected deck
     *
     * This is a slow operation and should be inside `withProgress`
     */
    @CheckResult
    fun deleteSelectedDeck() = viewModelScope.launch {
        val targetDeckId = withCol { decks.selected() }
        deleteDeck(targetDeckId).join()
    }

    /**
     * Removes cards in [report] from the collection.
     *
     * @param report a report about the empty cards found
     * @param preserveNotes If `true`, and a note in [report] would be removed,
     * retain the first card
     */
    fun deleteEmptyCards(
        report: EmptyCardsReport,
        preserveNotes: Boolean,
    ) = viewModelScope.launch {
        // https://github.com/ankitects/anki/blob/39e293b27d36318e00131fd10144755eec8d1922/qt/aqt/emptycards.py#L98-L109
        val toDelete = mutableListOf<CardId>()

        for (note in report.notesList) {
            if (preserveNotes && note.willDeleteNote) {
                // leave first card
                toDelete.extend(note.cardIdsList.drop(1))
            } else {
                toDelete.extend(note.cardIdsList)
            }
        }
        val result = undoableOp { removeCardsAndOrphanedNotes(toDelete) }
        updateDeckList()
        val emptyResult = EmptyCardsResult(cardsDeleted = result.count)
        _composeEffects.send(DeckPickerComposeEffect.ShowUndoSnackbar(emptyResult.toHumanReadableString()))
    }

    // TODO: move withProgress to the ViewModel, so we don't return 'Job'
    fun emptyFilteredDeck(deckId: DeckId): Job = viewModelScope.launch {
        Timber.i("empty filtered deck %s", deckId)
        withCol {
            decks.select(deckId)
        }
        undoableOp { sched.emptyFilteredDeck(decks.selected()) }
        updateDeckList()
    }

    fun rebuildFilteredDeck(deckId: DeckId): Job = viewModelScope.launch {
        Timber.i("rebuild filtered deck %s", deckId)
        _effects.send(DeckPickerEffect.RebuildFilteredDeck(deckId))
    }

    fun sync() = launchCatchingIO {
        _effects.send(DeckPickerEffect.Sync)
    }

    fun undo() = launchCatchingIO {
        val message = undoAndGetSnackbarMessage()
        _composeEffects.send(DeckPickerComposeEffect.ShowSnackbarMessage(message))
    }

    fun openReviewer() = launchCatchingIO {
        _effects.send(DeckPickerEffect.NavigateToReviewer)
    }

    fun openStudyOptionsActivity() = launchCatchingIO {
        _effects.send(DeckPickerEffect.NavigateToStudyOptions)
    }

    fun exportDeck(deckId: DeckId) = launchCatchingIO {
        _effects.send(DeckPickerEffect.ShowExportDialog(deckId))
    }

    fun showCustomStudyDialog(deckId: DeckId) = launchCatchingIO {
        _effects.send(DeckPickerEffect.ShowCustomStudyDialog(deckId))
    }

    fun checkDatabase() = launchCatchingIO {
        _effects.send(DeckPickerEffect.CheckDatabase)
    }

    fun showEmptyCardsDialog() = launchCatchingIO {
        _effects.send(DeckPickerEffect.ShowEmptyCardsDialog)
    }


    fun addNote(
        deckId: DeckId?,
        setAsCurrent: Boolean,
    ) = launchCatchingIO {
        if (deckId != null && setAsCurrent) {
            withCol { decks.select(deckId) }
        }
        flowOfDestination.emit(NoteEditorLauncher.AddNote(deckId))
    }

    /**
     * Opens the Manage Note Types screen.
     */
    fun openManageNoteTypes() =
        launchCatchingIO { flowOfDestination.emit(ManageNoteTypesDestination()) }

    /**
     * Opens study options for the provided deck
     *
     * @param deckId Deck to open options for
     * @param isFiltered (optional) optimization for when we know the deck is filtered
     */
    fun openDeckOptions(
        deckId: DeckId,
        isFiltered: Boolean? = null,
    ) = launchCatchingIO {
        // open cram options if filtered deck, otherwise open regular options
        val filtered = isFiltered ?: withCol { decks.isFiltered(deckId) }
        flowOfDestination.emit(DeckOptionsDestination(deckId = deckId, isFiltered = filtered))
    }

    fun unburyDeck(deckId: DeckId) = launchCatchingIO {
        undoableOp { sched.unburyDeck(deckId) }
        updateDeckList()
    }


    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, importing, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    @RustCleanup("backup with 5 minute timer, instead of deck list refresh")
    fun updateDeckList(): Job? {
        if (!CollectionManager.isOpenUnsafe()) {
            return null
        }
        if (Build.FINGERPRINT != "robolectric") {
            // uses user's desktop settings to determine whether a backup
            // actually happens
            launchCatchingIO { performBackupInBackground() }
        }
        Timber.d("updateDeckList")
        return reloadDeckCounts()
    }

    fun reloadDeckCounts(): Job {
        loadDeckCounts?.cancel()
        val loadDeckCounts = viewModelScope.launch {
            Timber.d("Refreshing deck list")
            val (deckDueTree, collectionHasNoCards, buriedDecks) = withCol {
                val buried =
                    db.queryLongList("SELECT DISTINCT did FROM cards WHERE queue IN (${SiblingBuried.code}, ${ManuallyBuried.code})")
                        .toSet()
                Triple(sched.deckDueTree(), isEmpty, buried)
            }

            ensureActive()

            dueTree = deckDueTree
            flowOfCollectionHasNoCards.value = collectionHasNoCards
            flowOfBuriedDecks.value = buriedDecks

            launch { refreshSyncState() }

            // Backend returns studiedToday() with newlines for HTML formatting,so we replace them with spaces.
            val studiedToday = withCol { sched.studiedToday().replace("\n", " ") }

            ensureActive()
            flowOfStudiedTodayStats.value = studiedToday

            val timeUntilNextDay = withCol {
                calculateTimeUntilNextDay(sched)
            }
            ensureActive()
            _flowOfTimeUntilNextDay.value = timeUntilNextDay

            /**
             * Checks the current scheduler version and prompts the upgrade dialog if using the legacy version.
             * Ensures the dialog is only shown once per collection load, even if [updateDeckList()] is called multiple times.
             */
            val currentSchedulerVersion = withCol { config.get("schedVer") as? Long ?: 1L }

            ensureActive()

            if (currentSchedulerVersion == 1L && schedulerUpgradeDialogShownForVersion != 1L) {
                schedulerUpgradeDialogShownForVersion = 1L
                flowOfPromptUserToUpdateScheduler.emit(Unit)
            } else {
                schedulerUpgradeDialogShownForVersion = currentSchedulerVersion
            }

            // TODO: This is in the wrong place
            // current deck may have changed
            val currentDeckId = withCol { decks.current().id }
            ensureActive()
            focusedDeck = currentDeckId

            flowOfUndoUpdated.emit(Unit)

            flowOfDecksReloaded.emit(Unit)
        }
        this.loadDeckCounts = loadDeckCounts
        return loadDeckCounts
    }

    suspend fun refreshSyncState() {
        _syncState.value = withContext(Dispatchers.IO) {
            withCol { fetchSyncIconState() }
        }
    }

    private fun Collection.fetchSyncIconState(): SyncIconState {
        if (!Prefs.displaySyncStatus) return SyncIconState.Normal
        val auth = syncAuth() ?: return SyncIconState.NotLoggedIn
        return try {
            // Use CollectionManager to ensure that this doesn't block 'deck count' tasks
            // throws if a .colpkg import or similar occurs just before this call
            val output = backend.syncStatus(auth)
            if (output.hasNewEndpoint() && output.newEndpoint.isNotEmpty()) {
                Prefs.currentSyncUri = output.newEndpoint
            }
            when (output.required) {
                SyncStatusResponse.Required.NO_CHANGES -> SyncIconState.Normal
                SyncStatusResponse.Required.NORMAL_SYNC -> SyncIconState.PendingChanges
                SyncStatusResponse.Required.FULL_SYNC -> SyncIconState.OneWay
                SyncStatusResponse.Required.UNRECOGNIZED -> {
                    Timber.w("Unexpected sync status response: UNRECOGNIZED. Defaulting to Normal.")
                    SyncIconState.Normal
                }
            }
        } catch (_: BackendNetworkException) {
            SyncIconState.Normal
        } catch (e: Exception) {
            Timber.d(e, "error obtaining sync status: collection likely closed")
            SyncIconState.Normal
        }
    }

    fun updateDeckFilter(filterText: String) {
        Timber.d("filter: %s", filterText)
        flowOfCurrentDeckFilter.value = filterText
    }

    fun toggleDeckExpand(deckId: DeckId) = viewModelScope.launch {
        // update DB
        withCol { decks.collapse(deckId) }
        // update stored state
        dueTree?.find(deckId)?.run {
            collapsed = !collapsed
        }
        flowOfRefreshDeckList.emit(Unit)
    }

    sealed class CreateDeckDialogState {
        data object Hidden : CreateDeckDialogState()
        data class Visible(
            val type: DeckDialogType,
            val titleResId: Int,
            val initialName: String = "",
            val parentId: DeckId? = null,
            val deckIdToRename: DeckId? = null
        ) : CreateDeckDialogState()
    }

    sealed class StartupResponse {
        data class RequestPermissions(
            val requiredPermissions: PermissionSet,
        ) : StartupResponse()

        /**
         * The app failed to start and is probably unusable (e.g. No disk space/DB corrupt)
         *
         * @see InitialActivity.StartupFailure
         */
        data class FatalError(
            val failure: InitialActivity.StartupFailure,
        ) : StartupResponse()

        data object Success : StartupResponse()
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     *
     * @see flowOfStartupResponse
     */
    fun handleStartup(environment: AnkiDroidEnvironment) {
        if (!environment.hasRequiredPermissions()) {
            Timber.i("${this.javaClass.simpleName}: postponing startup code - permission screen shown")
            flowOfStartupResponse.value =
                StartupResponse.RequestPermissions(environment.requiredPermissions)
            return
        }

        Timber.d("handleStartup: Continuing after permission granted")
        val failure = InitialActivity.getStartupFailureType(environment::initializeAnkiDroidFolder)
        if (failure != null) {
            flowOfStartupResponse.value = StartupResponse.FatalError(failure)
            return
        }

        // successful startup

        configureRenderingMode()

        flowOfStartupResponse.value = StartupResponse.Success
    }

    /**
     * Calculates the time in milliseconds until the next Anki day rollover.
     * @param sched The scheduler to get the day cutoff from
     * @return Time in milliseconds until next day, or 0 if already past cutoff
     */
    private fun calculateTimeUntilNextDay(sched: Scheduler): Long {
        return (sched.dayCutoff * 1000 - TimeManager.time.intTimeMS()).coerceAtLeast(0L)
    }

    interface AnkiDroidEnvironment {
        fun hasRequiredPermissions(): Boolean

        val requiredPermissions: PermissionSet

        fun initializeAnkiDroidFolder(): Boolean
    }

    /** Represents [dueTree] as a list */
    data class FlattenedDeckList(
        val data: List<DisplayDeckNode>,
        val hasSubDecks: Boolean,
    ) {
        companion object {
            val empty = FlattenedDeckList(emptyList(), hasSubDecks = false)
        }
    }

    data class SyncDialogState(
        val title: String, val message: String, val onCancel: () -> Unit
    )
}

/** Result of [DeckPickerViewModel.deleteDeck] */
data class DeckDeletionResult(
    val deckName: String,
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.browsingCardsDeletedWithDeckname
     */
    // TODO: Somewhat questionable meaning: {count} cards deleted from {deck_name}.
    @CheckResult
    fun toHumanReadableString() = TR.browsingCardsDeletedWithDeckname(
        count = cardsDeleted,
        deckName = deckName,
    )
}

/** Result of [DeckPickerViewModel.deleteEmptyCards] */
data class EmptyCardsResult(
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.emptyCardsDeletedCount */
    @CheckResult
    fun toHumanReadableString() = TR.emptyCardsDeletedCount(cardsDeleted)
}

/**
 * A one-shot side effect emitted by [DeckPickerViewModel] and consumed by Compose.
 *
 * Keeping Compose-only effects separate avoids competing collectors on the Activity effect stream.
 */
sealed class DeckPickerComposeEffect {
    /** Show a snackbar with an undo action */
    data class ShowUndoSnackbar(val message: String) : DeckPickerComposeEffect()

    /** Show a simple snackbar from a string resource ID */
    data class ShowSnackbar(val messageResId: Int) : DeckPickerComposeEffect()

    /** Show a simple snackbar from a string message */
    data class ShowSnackbarMessage(val message: String) : DeckPickerComposeEffect()

    /** Handle the result of a deck selection (study, empty, congrats) */
    data class HandleDeckSelection(val result: DeckSelectionResult) : DeckPickerComposeEffect()

}

/**
 * A one-shot side effect emitted by [DeckPickerViewModel] and consumed by [DeckPicker].
 */
sealed class DeckPickerEffect {

    /** Trigger a sync operation */
    data object Sync : DeckPickerEffect()

    /** Open the reviewer for the current/selected deck */
    data object NavigateToReviewer : DeckPickerEffect()

    /** Open the study options activity */
    data object NavigateToStudyOptions : DeckPickerEffect()

    /** Show the export options dialog for the given deck */
    data class ShowExportDialog(val deckId: DeckId) : DeckPickerEffect()

    /** Show custom study dialog */
    data class ShowCustomStudyDialog(val deckId: DeckId) : DeckPickerEffect()

    /** Rebuild a filtered deck */
    data class RebuildFilteredDeck(val deckId: DeckId) : DeckPickerEffect()

    /** Check database */
    data object CheckDatabase : DeckPickerEffect()

    /** Show the empty cards dialog */
    data object ShowEmptyCardsDialog : DeckPickerEffect()
}
