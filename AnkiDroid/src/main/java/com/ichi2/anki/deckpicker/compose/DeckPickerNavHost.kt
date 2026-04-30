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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.deckpicker.DeckPickerComposeEffect
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckSelectionResult
import com.ichi2.anki.deckpicker.DeckSelectionType
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.dialogs.compose.CreateDeckDialog
import com.ichi2.anki.dialogs.compose.ErrorDialog
import com.ichi2.anki.dialogs.compose.LoginToAnkiWebDialog
import com.ichi2.anki.dialogs.compose.NetworkErrorDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.navigation.CongratsScreen
import com.ichi2.anki.navigation.ContributeScreen
import com.ichi2.anki.navigation.DeckPickerScreen
import com.ichi2.anki.navigation.HelpScreen
import com.ichi2.anki.navigation.ManageNoteTypesDestination
import com.ichi2.anki.navigation.Navigator
import com.ichi2.anki.navigation.StatisticsDestination
import com.ichi2.anki.navigation.toEntries
import com.ichi2.anki.notetype.ManageNoteTypesUiEvent
import com.ichi2.anki.notetype.ManageNoteTypesViewModel
import com.ichi2.anki.notetype.compose.DeleteSelectedNoteTypesDialog
import com.ichi2.anki.notetype.compose.ManageNoteTypesScreen
import com.ichi2.anki.pages.StatisticsScreen
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.contribute.ContributeScreen
import com.ichi2.anki.ui.compose.help.HelpScreen
import com.ichi2.anki.ui.compose.navigation.AnkiNavigationRail
import com.ichi2.anki.ui.compose.navigation.AppNavigationItem
import com.ichi2.anki.userAcceptsSchemaChange
import kotlinx.coroutines.launch
import com.ichi2.anki.ui.compose.CongratsScreen as CongratsComposable

@OptIn(ExperimentalMaterial3Api::class)
private data class DeckPickerDrawerState(
    val fragmented: Boolean,
    val deckList: DeckPickerViewModel.FlattenedDeckList,
    val isSyncing: Boolean,
    val searchQuery: String,
    val studyOptionsData: StudyOptionsData?,
    val requestSearchFocus: Boolean,
    val snackbarHostState: SnackbarHostState,
    val syncState: SyncIconState,
    val isInInitialState: Boolean?,
    val drawerState: DrawerState,
    val selectedNavigationItem: AppNavigationItem,
)

/**
 * Actions available from the deck picker drawer and its child components.
 *
 * Some actions appear as duplicate pairs (e.g., `onDeckOptions`/`onDeckOptionsItemSelected`,
 * `onRebuild`/`onRebuildDeck`). This is intentional for different UI contexts:
 * - The first variant (e.g., `onRebuild`) is used in the **deck row context menu**, where
 *   the action receives a `DisplayDeckNode` and the caller extracts the deck ID.
 * - The second variant (e.g., `onRebuildDeck`) is used in the **StudyOptions panel**, where
 *   the deck ID is passed directly as a `Long`.
 *
 * Both variants ultimately invoke the same underlying operation.
 *
 * @property onDeckOptions Opens deck options from deck row context menu
 * @property onDeckOptionsItemSelected Opens deck options from StudyOptions panel
 * @property onRebuild Rebuilds filtered deck from deck row context menu
 * @property onEmpty Empties filtered deck from deck row context menu
 * @property onRebuildDeck Rebuilds filtered deck from StudyOptions panel
 * @property onEmptyDeck Empties filtered deck from StudyOptions panel
 */
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
    val onCreateSubdeck: (Long) -> Unit,
    val onDeckOptions: (Long) -> Unit,
    val onDeckOptionsItemSelected: (Long) -> Unit,
    val onRename: (Long) -> Unit,
    val onExportDeck: (Long) -> Unit,
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
    val onNavigationIconClick: () -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
    val onDeleteEmptyCards: () -> Unit,
    val onManageNoteTypes: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeckPickerNavHost(
    navigator: Navigator,
    viewModel: DeckPickerViewModel,
    cardBrowserViewModel: CardBrowserViewModel,
    fragmented: Boolean,
    onLaunchIntent: (Intent) -> Unit,
    onOpenNoteEditor: (Long) -> Unit,
    onAddNote: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onShowDialogFragment: (DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
    onLoginToAnkiWeb: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val timeUntilNextDay by viewModel.flowOfTimeUntilNextDay.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val entryProvider = entryProvider {
        entry<DeckPickerScreen> {
            DeckPickerMainContent(
                navigator = navigator,
                viewModel = viewModel,
                cardBrowserViewModel = cardBrowserViewModel,
                fragmented = fragmented,
                onLaunchIntent = onLaunchIntent,
                onOpenNoteEditor = onOpenNoteEditor,
                onAddNote = onAddNote,
                onAddSharedDeck = onAddSharedDeck,
                onAddFilteredDeck = onAddFilteredDeck,
                onOpenCardInfo = onOpenCardInfo,
                onShowDialogFragment = onShowDialogFragment,
                onInvalidateOptionsMenu = onInvalidateOptionsMenu,
                onLoginToAnkiWeb = onLoginToAnkiWeb,
                onImport = onImport,
                onExport = onExport,
                lifecycle = lifecycle
            )
        }

        entry<HelpScreen> {
            HelpScreen(onNavigateUp = { navigator.goBack() })
        }

        entry<ContributeScreen> {
            ContributeScreen(onNavigateUp = { navigator.goBack() })
        }

        entry<CongratsScreen> { key ->
            CongratsComposable(
                onNavigateUp = { navigator.goBack() },
                onDeckOptions = { viewModel.openDeckOptions(key.deckId) },
                timeUntilNextDay = timeUntilNextDay
            )
        }

        entry<StatisticsDestination> {
            StatisticsScreen(onNavigateUp = { navigator.goBack() })
        }

        entry<ManageNoteTypesDestination> {
            val noteTypesViewModel: ManageNoteTypesViewModel = viewModel()
            val uiState by noteTypesViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val activity = context as AnkiActivity
            var showBatchDeleteConfirmation by remember { mutableStateOf(false) }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        noteTypesViewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(noteTypesViewModel) {
                noteTypesViewModel.uiEvents.collect { event ->
                    when (event) {
                        is ManageNoteTypesUiEvent.ShowErrorMessage -> {
                            activity.showSnackbar(event.message)
                        }

                        is ManageNoteTypesUiEvent.ShowSnackbar -> {
                            activity.showSnackbar(activity.getString(event.messageId))
                        }

                        is ManageNoteTypesUiEvent.PromptSchemaChangeWarning -> {
                            activity.launchCatchingTask {
                                if (activity.userAcceptsSchemaChange()) {
                                    noteTypesViewModel.showDeleteConfirmation(event.noteType)
                                }
                            }
                        }

                        is ManageNoteTypesUiEvent.PromptDeleteSelectedConfirmation -> {
                            activity.launchCatchingTask {
                                if (activity.userAcceptsSchemaChange()) {
                                    showBatchDeleteConfirmation = true
                                }
                            }
                        }
                    }
                }
            }

            ManageNoteTypesScreen(
                uiState = uiState,
                onSearch = { noteTypesViewModel.updateSearchQuery(it) },
                onAddNoteType = { name, option -> noteTypesViewModel.addNoteType(name, option) },
                onShowFields = {
                    onLaunchIntent(
                        Intent(context, NoteTypeFieldEditor::class.java).apply {
                            putExtra("title", it.name)
                            putExtra("noteTypeID", it.id)
                        })
                },
                onEditCards = {
                    onLaunchIntent(
                        Intent(context, CardTemplateEditor::class.java).apply {
                            putExtra("noteTypeId", it.id)
                        })
                },
                onRename = { noteTypesViewModel.renameNoteType(it.id, it.name) },
                onDeleteRequest = { noteTypesViewModel.requestDeleteNoteType(it) },
                onDeleteConfirm = { noteTypesViewModel.confirmDeleteNoteType(it.id) },
                onDeleteDismiss = { noteTypesViewModel.dismissDeleteConfirmation() },
                onToggleSelection = { noteTypesViewModel.toggleNoteTypeSelection(it) },
                onSelectAll = { noteTypesViewModel.selectAllNoteTypes() },
                onDeselectAll = { noteTypesViewModel.deselectAllNoteTypes() },
                onDeleteSelected = { noteTypesViewModel.deleteSelectedNoteTypes() },
                onNavigateUp = { navigator.goBack() })

            if (showBatchDeleteConfirmation) {
                DeleteSelectedNoteTypesDialog(
                    count = uiState.selectedNoteTypeIds.size,
                    onDismissRequest = { showBatchDeleteConfirmation = false },
                    onConfirm = {
                        noteTypesViewModel.confirmDeleteSelectedNoteTypes()
                        showBatchDeleteConfirmation = false
                    },
                )
            }
        }
    }

    NavDisplay(
        entries = navigator.state.toEntries(entryProvider),
        onBack = { navigator.goBack() },
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        popTransitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeckPickerMainContent(
    navigator: Navigator,
    viewModel: DeckPickerViewModel,
    cardBrowserViewModel: CardBrowserViewModel,
    fragmented: Boolean,
    onLaunchIntent: (Intent) -> Unit,
    onOpenNoteEditor: (Long) -> Unit,
    onAddNote: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onShowDialogFragment: (DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
    onLoginToAnkiWeb: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    lifecycle: Lifecycle
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val applicationContext = LocalContext.current.applicationContext
    val deckList by viewModel.flowOfDeckList.collectAsStateWithLifecycle(
        initialValue = DeckPickerViewModel.FlattenedDeckList(emptyList(), false),
    )
    val isInInitialState by viewModel.flowOfDeckListInInitialState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncDialogState by viewModel.syncDialogState.collectAsStateWithLifecycle()
    val showLoginToAnkiWebDialog by viewModel.showLoginToAnkiWebDialog.collectAsStateWithLifecycle()
    val showNetworkErrorDialog by viewModel.showNetworkErrorDialog.collectAsStateWithLifecycle()
    val createDeckDialogState by viewModel.createDeckDialogState.collectAsStateWithLifecycle()

    val errorMessageState = viewModel.onError.collectAsStateWithLifecycle(initialValue = null)
    var errorMessage by remember(errorMessageState.value) { mutableStateOf(errorMessageState.value) }
    errorMessage?.let { message ->
        ErrorDialog(
            errorMessage = message, onDismissRequest = { errorMessage = null })
    }

    if (showLoginToAnkiWebDialog) {
        LoginToAnkiWebDialog(
            onDismissRequest = { viewModel.setShowLoginToAnkiWebDialog(false) },
            onLoginClick = {
                viewModel.setShowLoginToAnkiWebDialog(false)
                onLoginToAnkiWeb()
            })
    }

    if (showNetworkErrorDialog) {
        NetworkErrorDialog(
            onDismissRequest = { viewModel.setShowNetworkErrorDialog(false) },
            onRetry = {
                viewModel.setShowNetworkErrorDialog(false)
                viewModel.sync()
            })
    }

    syncDialogState?.let {
        DeckPickerProgressDialog(
            title = it.title,
            message = it.message,
            onCancel = it.onCancel,
        )
    }

    when (val state = createDeckDialogState) {
        is DeckPickerViewModel.CreateDeckDialogState.Visible -> {
            CreateDeckDialog(
                onDismissRequest = { viewModel.dismissCreateDeckDialog() },
                onConfirm = { name -> viewModel.createDeck(name, state) },
                dialogType = state.type,
                title = stringResource(state.titleResId),
                initialDeckName = state.initialName,
                validateDeckName = { viewModel.validateDeckName(it, state) })
        }

        DeckPickerViewModel.CreateDeckDialogState.Hidden -> {}
    }

    var searchQuery by remember { mutableStateOf("") }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val studyOptionsData by viewModel.studyOptionsData.collectAsStateWithLifecycle()
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
                navigator.navigate(StatisticsDestination)
            }

            AppNavigationItem.Settings -> {
                onLaunchIntent(PreferencesActivity.getIntent(applicationContext))
            }

            AppNavigationItem.Help -> {
                navigator.navigate(HelpScreen)
            }

            AppNavigationItem.Support -> {
                navigator.navigate(ContributeScreen)
            }
        }
    }

    val deckPickerDrawerActions = DeckPickerDrawerActions(
        onSync = { viewModel.sync() },
        onSearchQueryChanged = {
            searchQuery = it
            viewModel.updateDeckFilter(it)
        },
        onDeckClick = { deck ->
            viewModel.onDeckSelected(deck.did, DeckSelectionType.DEFAULT)
        },
        onExpandClick = { deck -> viewModel.toggleDeckExpand(deck.did) },
        onAddNote = onAddNote,
        onAddDeck = { viewModel.showCreateDeckDialog() },
        onAddSharedDeck = onAddSharedDeck,
        onAddFilteredDeck = onAddFilteredDeck,
        onCheckDatabase = { viewModel.checkDatabase() },
        onCreateSubdeck = { viewModel.showCreateSubdeckDialog(it) },
        onDeckOptions = { viewModel.openDeckOptions(it) },
        onDeckOptionsItemSelected = { viewModel.openDeckOptions(it) },
        onRename = { viewModel.showRenameDeckDialog(it) },
        onExportDeck = { viewModel.exportDeck(it) },
        onDelete = { deckId -> viewModel.deleteDeck(deckId) },
        onRebuild = { viewModel.rebuildFilteredDeck(it) },
        onEmpty = { viewModel.emptyFilteredDeck(it) },
        onStartStudy = { viewModel.openReviewer() },
        onRebuildDeck = { viewModel.rebuildFilteredDeck(it) },
        onEmptyDeck = { viewModel.emptyFilteredDeck(it) },
        onCustomStudy = { viewModel.showCustomStudyDialog(it) },
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
        },
        onImport = onImport,
        onExport = onExport,
        onDeleteEmptyCards = { viewModel.showEmptyCardsDialog() },
        onManageNoteTypes = {
            navigator.navigate(ManageNoteTypesDestination)
        },
    )

    val deckPickerDrawerState = DeckPickerDrawerState(
        fragmented = fragmented,
        deckList = deckList,
        isSyncing = isSyncing,
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
                DeckPickerTabletCardBrowser(
                    cardBrowserViewModel = cardBrowserViewModel,
                    onNavigateToDecks = {
                        selectedNavigationItem = AppNavigationItem.Decks
                    },
                    onAddFilteredDeck = onAddFilteredDeck,
                    onOpenNoteEditor = onOpenNoteEditor,
                    onOpenCardInfo = onOpenCardInfo,
                    onAddNote = onAddNote,
                    onShowDialogFragment = onShowDialogFragment,
                    onInvalidateOptionsMenu = onInvalidateOptionsMenu
                )
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
        DeckPickerScreen(
            fragmented = state.fragmented,
            decks = state.deckList.data,
            isSyncing = state.isSyncing,
            onRefresh = actions.onSync,
            searchQuery = state.searchQuery,
            onSearchQueryChanged = actions.onSearchQueryChanged,
            deckRowActions = DeckRowActions(
                onDeckClick = actions.onDeckClick,
                onExpandClick = actions.onExpandClick,
                onDeckOptions = { deck -> actions.onDeckOptions(deck.did) },
                onRename = { deck -> actions.onRename(deck.did) },
                onCustomStudy = { deck -> actions.onCustomStudy(deck.did) },
                onUnbury = { deck -> actions.onUnbury(deck.did) },
                onExportDeck = { deck -> actions.onExportDeck(deck.did) },
                onDelete = { deck -> actions.onDelete(deck.did) },
                onRebuild = { deck -> actions.onRebuild(deck.did) },
                onEmpty = { deck -> actions.onEmpty(deck.did) },
                onCreateSubdeck = { deck -> actions.onCreateSubdeck(deck.did) },
            ),
            fabActions = FabActions(
                onAddNote = actions.onAddNote,
                onAddDeck = actions.onAddDeck,
                onAddSharedDeck = actions.onAddSharedDeck,
                onAddFilteredDeck = actions.onAddFilteredDeck,
                onImport = actions.onImport,
            ),
            moreOptionsMenuActions = MoreOptionsMenuActions(
                onDeleteEmptyCards = actions.onDeleteEmptyCards,
                onCheckDatabase = actions.onCheckDatabase,
                onExport = actions.onExport,
                onManageNoteTypes = actions.onManageNoteTypes,
            ),
            onNavigationIconClick = actions.onNavigationIconClick,
            onStartStudy = actions.onStartStudy,
            onCustomStudy = actions.onCustomStudy,
            studyOptionsData = state.studyOptionsData,
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
    lifecycle: Lifecycle
) {
    val applicationContext = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        viewModel.composeEffects.flowWithLifecycle(lifecycle).collect { effect ->
            launch {
                when (effect) {
                    is DeckPickerComposeEffect.ShowUndoSnackbar -> {
                        showUndoSnackbar(
                            snackbarHostState,
                            effect.message,
                            applicationContext.getString(R.string.undo)
                        ) { viewModel.undo() }
                    }

                    is DeckPickerComposeEffect.ShowSnackbar -> {
                        snackbarHostState.showSnackbar(
                            applicationContext.getString(effect.messageResId),
                            duration = SnackbarDuration.Short
                        )
                    }

                    is DeckPickerComposeEffect.ShowSnackbarMessage -> {
                        snackbarHostState.showSnackbar(
                            effect.message, duration = SnackbarDuration.Short
                        )
                    }

                    is DeckPickerComposeEffect.HandleDeckSelection -> {
                        when (val result = effect.result) {
                            is DeckSelectionResult.HasCardsToStudy -> {
                                when (result.selectionType) {
                                    DeckSelectionType.DEFAULT -> viewModel.openReviewer()
                                    DeckSelectionType.SHOW_STUDY_OPTIONS -> viewModel.openStudyOptionsActivity()
                                    DeckSelectionType.SKIP_STUDY_OPTIONS -> viewModel.openReviewer()
                                }
                            }

                            is DeckSelectionResult.Empty -> {
                                val snackbarResult = snackbarHostState.showSnackbar(
                                    message = applicationContext.getString(R.string.empty_deck),
                                    actionLabel = applicationContext.getString(R.string.menu_add),
                                    duration = SnackbarDuration.Short,
                                )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    viewModel.addNote(result.deckId, true)
                                }
                            }

                            is DeckSelectionResult.NoCardsToStudy -> {
                                navigator.navigate(CongratsScreen(result.deckId))
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        cardBrowserViewModel.flowOfSnackbarMessage.flowWithLifecycle(lifecycle)
            .collect { messageRes ->
                launch {
                    snackbarHostState.showSnackbar(
                        applicationContext.getString(messageRes), duration = SnackbarDuration.Short
                    )
                }
            }
    }
}

private suspend fun showUndoSnackbar(
    snackbarHostState: SnackbarHostState, message: String, undoLabel: String, onUndo: () -> Unit
) {
    val result = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = undoLabel,
        duration = SnackbarDuration.Long,
    )
    if (result == SnackbarResult.ActionPerformed) {
        onUndo()
    }
}
