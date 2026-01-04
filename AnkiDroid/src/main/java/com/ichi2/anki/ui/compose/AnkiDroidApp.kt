/*
 Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it is useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.deckpicker.compose.DeckPickerContent
import com.ichi2.anki.deckpicker.compose.DeckPickerScreen
import com.ichi2.anki.deckpicker.compose.StudyOptionsData
import com.ichi2.anki.deckpicker.compose.StudyOptionsScreen
import com.ichi2.anki.ui.compose.components.ExpandableFab
import com.ichi2.anki.ui.compose.components.ExpandableFabContainer
import com.ichi2.anki.ui.compose.components.Scrim
import com.ichi2.anki.ui.compose.components.SyncIcon


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnkiDroidApp(
    fragmented: Boolean,
    decks: List<DisplayDeckNode>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onDeckClick: (DisplayDeckNode) -> Unit,
    onExpandClick: (DisplayDeckNode) -> Unit,
    onAddNote: () -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onCheckDatabase: () -> Unit,
    onDeckOptions: (DisplayDeckNode) -> Unit,
    onRename: (DisplayDeckNode) -> Unit,
    onExport: (DisplayDeckNode) -> Unit,
    onDelete: (DisplayDeckNode) -> Unit,
    onRebuild: (DisplayDeckNode) -> Unit,
    onEmpty: (DisplayDeckNode) -> Unit,
    onNavigationIconClick: () -> Unit,
    studyOptionsData: StudyOptionsData?,
    onStartStudy: () -> Unit,
    onRebuildDeck: (Long) -> Unit,
    onEmptyDeck: (Long) -> Unit,
    onCustomStudy: (Long) -> Unit,
    onDeckOptionsItemSelected: (Long) -> Unit,
    onUnbury: (Long) -> Unit,
    requestSearchFocus: Boolean,
    onSearchFocusRequested: () -> Unit,
    syncState: SyncIconState,
    isInInitialState: Boolean?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val searchFocusRequester = remember {
        androidx.compose.ui.focus.FocusRequester()
    }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocusRequester.requestFocus()
            onSearchFocusRequested()
        }
    }

    if (fragmented) {
        var isSearchOpen by remember { mutableStateOf(false) }
        var isStudyOptionsMenuOpen by remember { mutableStateOf(false) }
        val searchAnim by animateFloatAsState(
            targetValue = if (isSearchOpen) 1f else 0f,
            animationSpec = motionScheme.defaultEffectsSpec()
        )
        val density = LocalDensity.current
        val searchOffsetPx = with(density) { (-8).dp.toPx() }
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        val listState = rememberLazyListState()
        BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

        // Tablet layout
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                floatingActionButton = {
                    Scrim(
                        opacity = 0F,
                        visible = fabMenuExpanded,
                        onDismiss = { fabMenuExpanded = false })
                    ExpandableFabContainer {
                        ExpandableFab(
                            expanded = fabMenuExpanded,
                            onExpandedChange = { fabMenuExpanded = it },
                            onAddNote = onAddNote,
                            onAddDeck = onAddDeck,
                            onAddSharedDeck = onAddSharedDeck,
                            onAddFilteredDeck = onAddFilteredDeck,
                            onCheckDatabase = onCheckDatabase
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { snackbarData ->
                        Snackbar(
                            snackbarData = snackbarData,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                topBar = {
                    LargeTopAppBar(
                        title = {
                            if (!isSearchOpen) Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.displayMediumEmphasized,
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - searchAnim
                                }
                            )
                        },
                        actions = {
                            if (isSearchOpen) {
                                SearchBar(
                                    inputField = {
                                        SearchBarDefaults.InputField(
                                            query = searchQuery,
                                            onQueryChange = onSearchQueryChanged,
                                            onSearch = { /* Search is performed as user types */ },
                                            expanded = true,
                                            onExpandedChange = { },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(searchFocusRequester)
                                                .graphicsLayer {
                                                    alpha = searchAnim
                                                    translationY =
                                                        searchOffsetPx * (1f - searchAnim)
                                                    scaleX = 0.98f + 0.02f * searchAnim
                                                    scaleY = 0.98f + 0.02f * searchAnim
                                                },
                                            placeholder = { Text(stringResource(R.string.search_decks)) },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.search_24px),
                                                    contentDescription = stringResource(R.string.search_decks)
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    onSearchQueryChanged("")
                                                    isSearchOpen = false
                                                }) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.close),
                                                    )
                                                }
                                            },
                                        )
                                    },
                                    expanded = false,
                                    onExpandedChange = { },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                        .graphicsLayer {
                                            alpha = searchAnim
                                        },
                                    shape = SearchBarDefaults.inputFieldShape,
                                    content = { }
                                )
                            } else {
                                FilledIconButton(
                                    onClick = { isSearchOpen = true },
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = 1f - searchAnim
                                        },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.search_24px),
                                        contentDescription = stringResource(R.string.search_decks),
                                    )
                                }
                                SyncIcon(
                                    isSyncing = isRefreshing,
                                    syncState = syncState,
                                    onRefresh = onRefresh,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .width(48.dp)
                                        .graphicsLayer {
                                            alpha = 1f - searchAnim
                                        }
                                )
                            }
                            if (studyOptionsData != null) {
                                FilledIconButton(
                                    onClick = { isStudyOptionsMenuOpen = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }
                                DropdownMenu(
                                    expanded = isStudyOptionsMenuOpen,
                                    onDismissRequest = { isStudyOptionsMenuOpen = false },
                                ) {
                                    if (studyOptionsData.isFiltered) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rebuild)) },
                                            onClick = {
                                                isStudyOptionsMenuOpen = false
                                                onRebuildDeck(studyOptionsData.deckId)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = null,
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.empty_cards_action)) },
                                            onClick = {
                                                isStudyOptionsMenuOpen = false
                                                onEmptyDeck(studyOptionsData.deckId)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    contentDescription = null,
                                                )
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.custom_study)) },
                                            onClick = {
                                                isStudyOptionsMenuOpen = false
                                                onCustomStudy(studyOptionsData.deckId)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                )
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.deck_options)) },
                                        onClick = {
                                            isStudyOptionsMenuOpen = false
                                            onDeckOptionsItemSelected(studyOptionsData.deckId)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                    if (studyOptionsData.haveBuried) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unbury)) },
                                            onClick = {
                                                isStudyOptionsMenuOpen = false
                                                onUnbury(studyOptionsData.deckId)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.undo_24px),
                                                    contentDescription = null,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { paddingValues ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DeckPickerContent(
                            decks = decks,
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            isInInitialState = isInInitialState,
                            onDeckClick = onDeckClick,
                            onExpandClick = onExpandClick,
                            onDeckOptions = onDeckOptions,
                            onRename = onRename,
                            onExport = onExport,
                            onDelete = onDelete,
                            onRebuild = onRebuild,
                            onEmpty = onEmpty,
                            onAddDeck = onAddDeck,
                            onAddSharedDeck = onAddSharedDeck,
                            listState = listState
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StudyOptionsScreen(
                            studyOptionsData = studyOptionsData,
                            onStartStudy = onStartStudy,
                            onCustomStudy = onCustomStudy,
                        )
                    }
                }
            }
        }
    } else {
        // Phone layout
        DeckPickerScreen(
            decks = decks,
            isRefreshing = isRefreshing,
            searchFocusRequester = searchFocusRequester,
            snackbarHostState = snackbarHostState,
            onRefresh = onRefresh,
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onDeckClick = onDeckClick,
            onExpandClick = onExpandClick,
            onAddNote = onAddNote,
            onAddDeck = onAddDeck,
            onAddSharedDeck = onAddSharedDeck,
            onAddFilteredDeck = onAddFilteredDeck,
            onCheckDatabase = onCheckDatabase,
            onDeckOptions = onDeckOptions,
            onRename = onRename,
            onExport = onExport,
            onDelete = onDelete,
            onRebuild = onRebuild,
            onEmpty = onEmpty,
            onNavigationIconClick = onNavigationIconClick,
            fabMenuExpanded = fabMenuExpanded,
            onFabMenuExpandedChange = { fabMenuExpanded = it },
            syncState = syncState,
            isInInitialState = isInInitialState,
        )
    }
}