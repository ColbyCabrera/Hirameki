/****************************************************************************************
 * Copyright (c) 2025 AnkiDroid Open Source Team                                       *
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
package com.ichi2.anki.deckpicker.compose

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.compose.CardBrowserLayout
import com.ichi2.anki.browser.compose.FilterByTagsDialog
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckSelectionResult
import com.ichi2.anki.deckpicker.DeckSelectionType
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.compose.FlagRenameDialog
import com.ichi2.anki.navigation.CongratsScreen
import com.ichi2.anki.navigation.DeckPickerScreen
import com.ichi2.anki.navigation.HelpScreen
import com.ichi2.anki.navigation.Navigator
import com.ichi2.anki.pages.Statistics
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.ui.compose.help.HelpScreen
import com.ichi2.anki.ui.compose.navigation.AnkiNavigationRail
import com.ichi2.anki.ui.compose.navigation.AppNavigationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ichi2.anki.ui.compose.AnkiDroidApp as AnkiDroidAppComposable
import com.ichi2.anki.ui.compose.CongratsScreen as CongratsComposable

@OptIn(ExperimentalMaterial3Api::class)
private data class DeckPickerDrawerState(
    val fragmented: Boolean,
    val deckList: DeckPickerViewModel.FlattenedDeckList,
    val isRefreshing: Boolean,
    val searchQuery: String,
    val studyOptionsData: StudyOptionsData?,
    val requestSearchFocus: Boolean,
    val snackbarHostState: SnackbarHostState,
    val syncState: SyncIconState,
    val isInInitialState: Boolean?,
    val drawerState: DrawerState,
    val selectedNavigationItem: AppNavigationItem,
)

private data class DeckPickerDrawerActions(
    val onSync: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onDeckClick: (DisplayDeckNode) -> Unit,
    val onExpandClick: (DisplayDeckNode) -> Unit,
    val onAddNote: () -> Unit,
    val onAddDeck: () -> Unit,
    val onAddSharedDeck: () -> Unit,
    val onAddFilteredDeck: () -> Unit,
    val onCheckDatabase: () -> Unit,
    val onDeckOptions: (Long) -> Unit,
    val onDeckOptionsItemSelected: (Long) -> Unit,
    val onRename: (Long) -> Unit,
    val onExport: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onRebuild: (Long) -> Unit,
    val onEmpty: (Long) -> Unit,
    val onStartStudy: () -> Unit,
    val onRebuildDeck: (Long) -> Unit,
    val onEmptyDeck: (Long) -> Unit,
    val onCustomStudy: (Long) -> Unit,
    val onUnbury: (Long) -> Unit,
    val onSearchFocusRequested: () -> Unit,
    val onNavigationItemClick: (AppNavigationItem) -> Unit,
    val onNavigationIconClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeckPickerNavHost(
    navigator: Navigator,
    viewModel: DeckPickerViewModel,
    cardBrowserViewModel: CardBrowserViewModel,
    fragmented: Boolean,
    onLaunchIntent: (Intent) -> Unit,
    onLaunchUrl: (String) -> Unit,
    onUndo: () -> Unit,
    onOpenReviewer: () -> Unit,
    onOpenStudyOptions: () -> Unit,
    onOpenNoteEditor: (Long) -> Unit,
    onAddNote: () -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onCheckDatabase: () -> Unit,
    onRenameDeck: (Long) -> Unit,
    onExportDeck: (Long) -> Unit,
    onDeleteDeck: (Long) -> Unit,
    onRebuildFiltered: (Long) -> Unit,
    onEmptyFiltered: (Long) -> Unit,
    onCustomStudy: (Long) -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onShowDialogFragment: (androidx.fragment.app.DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
    onSync: () -> Unit,
) {
    val timeUntilNextDay by viewModel.flowOfTimeUntilNextDay.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        popTransitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
        entryProvider = { key ->
            when (key) {
                is DeckPickerScreen -> {
                    NavEntry(key) {
                        DeckPickerMainContent(
                            navigator = navigator,
                            viewModel = viewModel,
                            cardBrowserViewModel = cardBrowserViewModel,
                            fragmented = fragmented,
                            onLaunchIntent = onLaunchIntent,
                            onLaunchUrl = onLaunchUrl,
                            onUndo = onUndo,
                            onOpenReviewer = onOpenReviewer,
                            onOpenStudyOptions = onOpenStudyOptions,
                            onOpenNoteEditor = onOpenNoteEditor,
                            onAddNote = onAddNote,
                            onAddDeck = onAddDeck,
                            onAddSharedDeck = onAddSharedDeck,
                            onAddFilteredDeck = onAddFilteredDeck,
                            onCheckDatabase = onCheckDatabase,
                            onRenameDeck = onRenameDeck,
                            onExportDeck = onExportDeck,
                            onDeleteDeck = onDeleteDeck,
                            onRebuildFiltered = onRebuildFiltered,
                            onEmptyFiltered = onEmptyFiltered,
                            onCustomStudy = onCustomStudy,
                            onOpenCardInfo = onOpenCardInfo,
                            onShowDialogFragment = onShowDialogFragment,
                            onInvalidateOptionsMenu = onInvalidateOptionsMenu,
                            onSync = onSync,
                            lifecycle = lifecycle
                        )
                    }
                }

                is HelpScreen -> {
                    NavEntry(key) {
                        HelpScreen(onNavigateUp = { navigator.goBack() })
                    }
                }

                is CongratsScreen -> {
                    NavEntry(key) {
                        CongratsComposable(
                            onNavigateUp = { navigator.goBack() },
                            onDeckOptions = { viewModel.openDeckOptions(key.deckId) },
                            timeUntilNextDay = timeUntilNextDay
                        )
                    }
                }

                else -> NavEntry(key) {
                    Timber.w("Unknown navigation route: %s", key)
                    Text("Unknown Route")
                }
            }
        })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeckPickerMainContent(
    navigator: Navigator,
    viewModel: DeckPickerViewModel,
    cardBrowserViewModel: CardBrowserViewModel,
    fragmented: Boolean,
    onLaunchIntent: (Intent) -> Unit,
    onLaunchUrl: (String) -> Unit,
    onUndo: () -> Unit,
    onOpenReviewer: () -> Unit,
    onOpenStudyOptions: () -> Unit,
    onOpenNoteEditor: (Long) -> Unit,
    onAddNote: () -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onCheckDatabase: () -> Unit,
    onRenameDeck: (Long) -> Unit,
    onExportDeck: (Long) -> Unit,
    onDeleteDeck: (Long) -> Unit,
    onRebuildFiltered: (Long) -> Unit,
    onEmptyFiltered: (Long) -> Unit,
    onCustomStudy: (Long) -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onShowDialogFragment: (androidx.fragment.app.DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
    onSync: () -> Unit,
    lifecycle: Lifecycle
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val applicationContext = LocalContext.current.applicationContext
    val deckList by viewModel.flowOfDeckList.collectAsState(
        initial = DeckPickerViewModel.FlattenedDeckList(emptyList(), false),
    )
    val isInInitialState by viewModel.flowOfDeckListInInitialState.collectAsState()
    val isRefreshing by viewModel.isSyncing.collectAsState(initial = false)
    val syncState by viewModel.syncState.collectAsState()
    val syncDialogState by viewModel.syncDialogState.collectAsState()

    syncDialogState?.let {
        SyncProgressDialog(
            title = it.title,
            message = it.message,
            onCancel = it.onCancel,
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val focusedDeckId by viewModel.flowOfFocusedDeck.collectAsState()
    var studyOptionsData by remember {
        mutableStateOf<StudyOptionsData?>(
            null,
        )
    }
    var selectedNavigationItem by remember {
        mutableStateOf(
            AppNavigationItem.Decks
        )
    } // For NavigationRail
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val handleNavigation: (AppNavigationItem) -> Unit = { item ->
        when (item) {
            AppNavigationItem.Decks -> {
                coroutineScope.launch { drawerState.close() }
            }

            AppNavigationItem.CardBrowser -> {
                if (!fragmented) {
                    onLaunchIntent(
                        Intent(
                            applicationContext, CardBrowser::class.java
                        )
                    )
                }
            }

            AppNavigationItem.Statistics -> {
                onLaunchIntent(Statistics.getIntent(applicationContext))
            }

            AppNavigationItem.Settings -> {
                onLaunchIntent(PreferencesActivity.getIntent(applicationContext))
            }

            AppNavigationItem.Help -> {
                navigator.goTo(HelpScreen)
            }

            AppNavigationItem.Support -> {
                onLaunchUrl("https://github.com/ankidroid/Anki-Android/wiki/Contributing")
            }
        }
    }

    LaunchedEffect(focusedDeckId) {
        val currentFocusedDeck = focusedDeckId
        if (currentFocusedDeck != null) {
            studyOptionsData = withContext(Dispatchers.IO) {
                CollectionManager.withCol {
                    decks.select(currentFocusedDeck)
                    val deck = decks.current()
                    val counts = sched.counts()
                    var buriedNew = 0
                    var buriedLearning = 0
                    var buriedReview = 0
                    val tree = sched.deckDueTree(currentFocusedDeck)
                    if (tree != null) {
                        buriedNew = tree.newCount - counts.new
                        buriedLearning = tree.learnCount - counts.lrn
                        buriedReview = tree.reviewCount - counts.rev
                    }
                    StudyOptionsData(
                        deckId = currentFocusedDeck,
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
                            currentFocusedDeck,
                            includeSubdecks = true,
                        ),
                        isFiltered = deck.isFiltered,
                        haveBuried = sched.haveBuried(),
                    )
                }
            }
        } else {
            studyOptionsData = null
        }
    }

    val deckPickerDrawerActions = DeckPickerDrawerActions(
        onSync = onSync,
        onSearchQueryChanged = {
            searchQuery = it
            viewModel.updateDeckFilter(it)
        },
        onDeckClick = { deck ->
            viewModel.onDeckSelected(deck.did, DeckSelectionType.DEFAULT)
        },
        onExpandClick = { deck -> viewModel.toggleDeckExpand(deck.did) },
        onAddNote = onAddNote,
        onAddDeck = onAddDeck,
        onAddSharedDeck = onAddSharedDeck,
        onAddFilteredDeck = onAddFilteredDeck,
        onCheckDatabase = onCheckDatabase,
        onDeckOptions = { viewModel.openDeckOptions(it) },
        onDeckOptionsItemSelected = { viewModel.openDeckOptions(it) },
        onRename = onRenameDeck,
        onExport = onExportDeck,
        onDelete = onDeleteDeck,
        onRebuild = onRebuildFiltered,
        onEmpty = onEmptyFiltered,
        onStartStudy = onOpenReviewer,
        onRebuildDeck = onRebuildFiltered,
        onEmptyDeck = onEmptyFiltered,
        onCustomStudy = onCustomStudy,
        onUnbury = { viewModel.unburyDeck(it) },
        onSearchFocusRequested = { requestSearchFocus = false },
        onNavigationItemClick = { item ->
            selectedNavigationItem = item
            coroutineScope.launch {
                drawerState.close()
                handleNavigation(item)
                selectedNavigationItem = AppNavigationItem.Decks
            }
        },
        onNavigationIconClick = {
            coroutineScope.launch { drawerState.open() }
        })

    val deckPickerDrawerState = DeckPickerDrawerState(
        fragmented = fragmented,
        deckList = deckList,
        isRefreshing = isRefreshing,
        searchQuery = searchQuery,
        studyOptionsData = studyOptionsData,
        requestSearchFocus = requestSearchFocus,
        snackbarHostState = snackbarHostState,
        syncState = syncState,
        isInInitialState = isInInitialState,
        drawerState = drawerState,
        selectedNavigationItem = selectedNavigationItem,
    )

    if (fragmented) {
        Row {
            AnkiNavigationRail(
                selectedItem = selectedNavigationItem,
                onNavigate = { item ->
                    selectedNavigationItem = item
                    handleNavigation(item)
                },
            )
            if (selectedNavigationItem == AppNavigationItem.CardBrowser) {
                BackHandler {
                    selectedNavigationItem = AppNavigationItem.Decks
                }
                val allTagsState by cardBrowserViewModel.allTags.collectAsState()
                val selectedTags by cardBrowserViewModel.selectedTags.collectAsState()
                val deckTags by cardBrowserViewModel.deckTags.collectAsState()
                val filterTagsByDeck by cardBrowserViewModel.filterTagsByDeck.collectAsState()
                var showBrowserOptionsDialog by remember {
                    mutableStateOf(
                        false
                    )
                }
                var showFilterByTagsDialog by remember {
                    mutableStateOf(
                        false
                    )
                }
                var showFlagRenameDialog by remember {
                    mutableStateOf(
                        false
                    )
                }

                if (showBrowserOptionsDialog) {
                    BrowserOptionsDialog(
                        onDismissRequest = {
                            showBrowserOptionsDialog = false
                        },
                        onConfirm = { cardsOrNotes, isTruncated, shouldIgnoreAccents ->
                            cardBrowserViewModel.setCardsOrNotes(
                                cardsOrNotes
                            )
                            cardBrowserViewModel.setTruncated(
                                isTruncated
                            )
                            cardBrowserViewModel.setIgnoreAccents(
                                shouldIgnoreAccents
                            )
                        },
                        initialCardsOrNotes = cardBrowserViewModel.cardsOrNotes,
                        initialIsTruncated = cardBrowserViewModel.isTruncated,
                        initialShouldIgnoreAccents = cardBrowserViewModel.shouldIgnoreAccents,
                        onManageColumnsClicked = {
                            val dialog = BrowserColumnSelectionFragment.createInstance(
                                cardBrowserViewModel.cardsOrNotes
                            )
                            onShowDialogFragment(dialog)
                        },
                        onRenameFlagClicked = {
                            showBrowserOptionsDialog = false
                            showFlagRenameDialog = true
                        },
                    )
                }
                if (showFilterByTagsDialog) {
                    FilterByTagsDialog(
                        onDismissRequest = {
                            showFilterByTagsDialog = false
                        },
                        onConfirm = { tags ->
                            cardBrowserViewModel.filterByTags(tags)
                            showFilterByTagsDialog = false
                        },
                        allTags = allTagsState,
                        initialSelection = selectedTags,
                        deckTags = deckTags,
                        initialFilterByDeck = filterTagsByDeck,
                        onFilterByDeckChanged = cardBrowserViewModel::setFilterTagsByDeck,
                    )
                }
                if (showFlagRenameDialog) {
                    FlagRenameDialog(
                        onDismissRequest = {
                            showFlagRenameDialog = false
                            onInvalidateOptionsMenu()
                        },
                    )
                }

                CardBrowserLayout(
                    viewModel = cardBrowserViewModel,
                    fragmented = false, // Rail handled by DeckPicker
                    onNavigateUp = {
                        selectedNavigationItem = AppNavigationItem.Decks
                    },
                    onCardClicked = { row ->
                        if (cardBrowserViewModel.isInMultiSelectMode) {
                            cardBrowserViewModel.toggleRowSelection(
                                CardBrowserViewModel.RowSelection(
                                    rowId = CardOrNoteId(row.id),
                                    topOffset = 0,
                                ),
                            )
                        } else {
                            onOpenNoteEditor(row.id)
                        }
                    },
                    onAddNote = { onAddNote() },
                    onPreview = { /* ActionHandler handled by Activity */ }, // This might need a callback
                    onFilter = cardBrowserViewModel::search,
                    onSelectAll = { cardBrowserViewModel.toggleSelectAllOrNone() },
                    onOptions = { showBrowserOptionsDialog = true },
                    onCreateFilteredDeck = { onAddFilteredDeck() },
                    onEditNote = {
                        onOpenNoteEditor(cardBrowserViewModel.currentCardId)
                    },
                    onCardInfo = {
                        onOpenCardInfo(cardBrowserViewModel.currentCardId)
                    },
                    onChangeDeck = { /* ActionHandler handled by Activity */ },
                    onReposition = { /* ActionHandler handled by Activity */ },
                    onSetDueDate = { /* ActionHandler handled by Activity */ },
                    onGradeNow = { /* ActionHandler handled by Activity */ },
                    onResetProgress = { /* ActionHandler handled by Activity */ },
                    onExportCard = { /* ActionHandler handled by Activity */ },
                    onFilterByTag = {
                        cardBrowserViewModel.loadAllTags()
                        cardBrowserViewModel.loadDeckTags()
                        showFilterByTagsDialog = true
                    })
            } else {
                DeckPickerWithDrawer(
                    state = deckPickerDrawerState, actions = deckPickerDrawerActions
                )
            }
        }
    } else {
        DeckPickerWithDrawer(state = deckPickerDrawerState, actions = deckPickerDrawerActions)
    }

    SetupFlows(
        navigator = navigator,
        viewModel = viewModel,
        cardBrowserViewModel = cardBrowserViewModel,
        snackbarHostState = snackbarHostState,
        onUndo = onUndo,
        onLaunchIntent = onLaunchIntent,
        onOpenReviewer = onOpenReviewer,
        onOpenStudyOptions = onOpenStudyOptions,
        lifecycle = lifecycle
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DeckPickerWithDrawer(
    state: DeckPickerDrawerState, actions: DeckPickerDrawerActions
) {
    ModalNavigationDrawer(
        drawerState = state.drawerState,
        gesturesEnabled = !state.fragmented || state.drawerState.targetValue != DrawerValue.Closed,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(NavigationDrawerItemDefaults.ItemPadding),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLargeEmphasized,
                        modifier = Modifier.padding(
                            start = 8.dp,
                            bottom = 24.dp,
                        ),
                    )
                    AppNavigationItem.entries.forEach { item ->
                        if (item == AppNavigationItem.Settings) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                thickness = 3.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            )
                        }
                        NavigationDrawerItem(
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.labelResId)) },
                            selected = state.selectedNavigationItem == item,
                            onClick = { actions.onNavigationItemClick(item) },
                        )
                    }
                }
            }
        },
    ) {
        AnkiDroidAppComposable(
            fragmented = state.fragmented,
            decks = state.deckList.data,
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onSync,
            searchQuery = state.searchQuery,
            onSearchQueryChanged = actions.onSearchQueryChanged,
            onDeckClick = actions.onDeckClick,
            onExpandClick = actions.onExpandClick,
            onAddNote = actions.onAddNote,
            onAddDeck = actions.onAddDeck,
            onAddSharedDeck = actions.onAddSharedDeck,
            onAddFilteredDeck = actions.onAddFilteredDeck,
            onCheckDatabase = actions.onCheckDatabase,
            onDeckOptions = { deck -> actions.onDeckOptions(deck.did) },
            onRename = { deck -> actions.onRename(deck.did) },
            onExport = { deck -> actions.onExport(deck.did) },
            onDelete = { deck -> actions.onDelete(deck.did) },
            onRebuild = { deck -> actions.onRebuild(deck.did) },
            onEmpty = { deck -> actions.onEmpty(deck.did) },
            onNavigationIconClick = actions.onNavigationIconClick,
            studyOptionsData = state.studyOptionsData,
            onStartStudy = actions.onStartStudy,
            onRebuildDeck = actions.onRebuildDeck,
            onEmptyDeck = actions.onEmptyDeck,
            onCustomStudy = actions.onCustomStudy,
            onDeckOptionsItemSelected = actions.onDeckOptionsItemSelected,
            onUnbury = actions.onUnbury,
            requestSearchFocus = state.requestSearchFocus,
            onSearchFocusRequested = actions.onSearchFocusRequested,
            snackbarHostState = state.snackbarHostState,
            syncState = state.syncState,
            isInInitialState = state.isInInitialState,
        )
    }
}

@Composable
private fun SetupFlows(
    navigator: Navigator,
    viewModel: DeckPickerViewModel,
    cardBrowserViewModel: CardBrowserViewModel,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    onLaunchIntent: (Intent) -> Unit,
    onOpenReviewer: () -> Unit,
    onOpenStudyOptions: () -> Unit,
    lifecycle: Lifecycle
) {
    val applicationContext = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        viewModel.deckDeletedNotification.flowWithLifecycle(lifecycle).collect {
            showUndoSnackbar(
                snackbarHostState,
                it.toHumanReadableString(),
                applicationContext.getString(R.string.undo),
                onUndo
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.emptyCardsNotification.flowWithLifecycle(lifecycle).collect {
            showUndoSnackbar(
                snackbarHostState,
                it.toHumanReadableString(),
                applicationContext.getString(R.string.undo),
                onUndo
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deckSelectionResult.flowWithLifecycle(lifecycle).collect { result ->
            when (result) {
                is DeckSelectionResult.HasCardsToStudy -> {
                    when (result.selectionType) {
                        DeckSelectionType.DEFAULT -> onOpenReviewer()
                        DeckSelectionType.SHOW_STUDY_OPTIONS -> onOpenStudyOptions()
                        DeckSelectionType.SKIP_STUDY_OPTIONS -> onOpenReviewer()
                    }
                }

                is DeckSelectionResult.Empty -> {
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = applicationContext.getString(R.string.empty_deck),
                        actionLabel = applicationContext.getString(R.string.menu_add),
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        viewModel.addNote(result.deckId, true)
                    }
                }

                is DeckSelectionResult.NoCardsToStudy -> {
                    navigator.goTo(CongratsScreen(result.deckId))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.flowWithLifecycle(lifecycle).collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        cardBrowserViewModel.flowOfSnackbarMessage.flowWithLifecycle(lifecycle)
            .collect { messageRes ->
                snackbarHostState.showSnackbar(applicationContext.getString(messageRes))
            }
    }
}

private suspend fun showUndoSnackbar(
    snackbarHostState: SnackbarHostState, message: String, undoLabel: String, onUndo: () -> Unit
) {
    val result = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = undoLabel,
    )
    if (result == SnackbarResult.ActionPerformed) {
        onUndo()
    }
}
