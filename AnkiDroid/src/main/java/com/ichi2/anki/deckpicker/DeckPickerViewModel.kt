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
import com.ichi2.anki.dialogs.compose.DeckDialogType
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
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
import com.ichi2.anki.utils.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
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

    private val _syncState = MutableStateFlow(SyncIconState.Normal)
    val syncState: StateFlow<SyncIconState> = _syncState.asStateFlow()

    private val _syncDialogState = MutableStateFlow<SyncDialogState?>(null)
    val syncDialogState: StateFlow<SyncDialogState?> = _syncDialogState.asStateFlow()

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

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    val flowOfFocusedDeck = MutableStateFlow<DeckId?>(null)

    var focusedDeck: DeckId?
        get() = flowOfFocusedDeck.value
        set(value) {
            flowOfFocusedDeck.value = value
        }

    /**
     * Used if the Deck Due Tree is mutated
     */
    private val flowOfRefreshDeckList = MutableSharedFlow<Unit>()

    val flowOfDeckList = combine(
        flowOfDeckDueTree,
        flowOfCurrentDeckFilter,
        flowOfFocusedDeck,
        flowOfRefreshDeckList.onStart { emit(Unit) },
    ) { tree, filter, _, _ ->
        if (tree == null) return@combine FlattenedDeckList.empty

        // TODO: use flowOfFocusedDeck once it's set on all instances
        val currentDeckId = withCol { decks.current().getLong("id") }
        Timber.i("currentDeckId: %d", currentDeckId)

        FlattenedDeckList(
            data = tree.filterAndFlattenDisplay(filter, currentDeckId),
            hasSubDecks = tree.children.any { it.children.any() },
        )
    }

    /**
     * @see deleteDeck
     * @see DeckDeletionResult
     */
    val deckDeletedNotification = MutableSharedFlow<DeckDeletionResult>()
    val emptyCardsNotification = MutableSharedFlow<EmptyCardsResult>()
    val flowOfDestination = MutableSharedFlow<Destination>()
    val snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessageResId = MutableSharedFlow<Int>()

    override val onError = MutableSharedFlow<String>()

    /**
     * A notification that the study counts have changed
     */
    // TODO: most of the recalculation should be moved inside the ViewModel
    val flowOfDeckCountsChanged = MutableSharedFlow<Unit>()

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

    /** "Studied N cards in 0 seconds today */
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

    fun showRenameDeckDialog(deckId: DeckId, currentName: String) {
        _createDeckDialogState.value = CreateDeckDialogState.Visible(
            type = DeckDialogType.RENAME_DECK,
            titleResId = R.string.rename_deck,
            initialName = currentName,
            deckIdToRename = deckId
        )
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
                                        "Deck no longer exists for rename: %s",
                                        state.initialName
                                    )
                                    operationSucceeded = false
                                }
                            } else {
                                Timber.w("Deck not found for rename: %s", state.initialName)
                                operationSucceeded = false
                            }
                        }

                        DeckDialogType.FILTERED_DECK -> {
                            decks.newFiltered(name)
                        }
                    }
                }

                if (operationSucceeded) {
                    _createDeckDialogState.value = CreateDeckDialogState.Hidden
                    updateDeckList()
                    val messageResId = when (state.type) {
                        DeckDialogType.RENAME_DECK -> R.string.deck_renamed
                        else -> R.string.deck_created
                    }
                    snackbarMessageResId.emit(messageResId)
                } else {
                    // Keep dialog open and show error
                    snackbarMessageResId.emit(R.string.something_wrong)
                }
            } catch (e: CancellationException) {
                throw e // Don't catch coroutine cancellation
            } catch (e: BackendDeckIsFilteredException) {
                snackbarMessage.emit(e.localizedMessage ?: e.message.orEmpty())
            } catch (e: Exception) {
                Timber.w(e, "Failed to create/rename deck")
                snackbarMessageResId.emit(R.string.something_wrong)
            }
        }
    }


    // HACK: dismiss a legacy progress bar
    // TODO: Replace with better progress handling for first load/corrupt collections
    val flowOfDecksReloaded = MutableSharedFlow<Unit>()
    val deckSelectionResult = MutableSharedFlow<DeckSelectionResult>()

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
        deckSelectionResult.emit(result)
    }

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     *
     * This is a slow operation and should be inside `withProgress`
     *
     * @param did ID of the deck to delete
     */
    @CheckResult // This is a slow operation and should be inside `withProgress`
    fun deleteDeck(did: DeckId) = viewModelScope.launch {
        val deckName = withCol { decks.getLegacy(did)!!.name }
        val changes = undoableOp { decks.remove(listOf(did)) }
        // After deletion: decks.current() reverts to Default, necessitating `focusedDeck`
        // to match and avoid unnecessary scrolls in `renderPage()`.
        focusedDeck = Consts.DEFAULT_DECK_ID

        deckDeletedNotification.emit(
            DeckDeletionResult(deckName = deckName, cardsDeleted = changes.count),
        )
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
        emptyCardsNotification.emit(EmptyCardsResult(cardsDeleted = result.count))
    }

    // TODO: move withProgress to the ViewModel, so we don't return 'Job'
    fun emptyFilteredDeck(deckId: DeckId): Job = viewModelScope.launch {
        Timber.i("empty filtered deck %s", deckId)
        withCol {
            decks.select(deckId)
        }
        undoableOp { sched.emptyFilteredDeck(decks.selected()) }
        flowOfDeckCountsChanged.emit(Unit)
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
            refreshSyncState()
            val (deckDueTree, collectionHasNoCards) = withCol {
                Pair(sched.deckDueTree(), isEmpty)
            }
            dueTree = deckDueTree

            flowOfCollectionHasNoCards.value = collectionHasNoCards

            // Backend returns studiedToday() with newlines for HTML formatting,so we replace them with spaces.
            flowOfStudiedTodayStats.value = withCol { sched.studiedToday().replace("\n", " ") }

            _flowOfTimeUntilNextDay.value = withCol {
                calculateTimeUntilNextDay(sched)
            }

            /**
             * Checks the current scheduler version and prompts the upgrade dialog if using the legacy version.
             * Ensures the dialog is only shown once per collection load, even if [updateDeckList()] is called multiple times.
             */
            val currentSchedulerVersion = withCol { config.get("schedVer") as? Long ?: 1L }

            if (currentSchedulerVersion == 1L && schedulerUpgradeDialogShownForVersion != 1L) {
                schedulerUpgradeDialogShownForVersion = 1L
                flowOfPromptUserToUpdateScheduler.emit(Unit)
            } else {
                schedulerUpgradeDialogShownForVersion = currentSchedulerVersion
            }

            // TODO: This is in the wrong place
            // current deck may have changed
            focusedDeck = withCol { decks.current().id }
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
