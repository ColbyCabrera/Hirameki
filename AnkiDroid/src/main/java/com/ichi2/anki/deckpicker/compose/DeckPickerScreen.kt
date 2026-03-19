/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
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
package com.ichi2.anki.deckpicker.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.InputField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.ui.compose.SnackbarPaddingBottom
import com.ichi2.anki.ui.compose.components.ExpandableFab
import com.ichi2.anki.ui.compose.components.ExpandableFabContainer
import com.ichi2.anki.ui.compose.components.Scrim
import com.ichi2.anki.ui.compose.components.SyncIcon
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.utils.MorphShape

private val expandedDeckCardRadius = 24.dp
private val collapsedDeckCardRadius = 70.dp
private val subDeckPadding = 16.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RenderDeck(
    deck: DisplayDeckNode,
    children: List<DisplayDeckNode>,
    deckToChildrenMap: Map<DisplayDeckNode, List<DisplayDeckNode>>,
    onDeckClick: (DisplayDeckNode) -> Unit,
    onExpandClick: (DisplayDeckNode) -> Unit,
    onDeckOptions: (DisplayDeckNode) -> Unit,
    onRename: (DisplayDeckNode) -> Unit,
    onExport: (DisplayDeckNode) -> Unit,
    onDelete: (DisplayDeckNode) -> Unit,
    onRebuild: (DisplayDeckNode) -> Unit,
    onEmpty: (DisplayDeckNode) -> Unit,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (!deck.collapsed && deck.canCollapse) expandedDeckCardRadius else collapsedDeckCardRadius,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )

    var rememberedChildren by remember { mutableStateOf<List<DisplayDeckNode>?>(null) }
    if (!deck.collapsed) {
        rememberedChildren = children
    }

    val content = @Composable {
        DeckItem(
            deck = deck,
            onDeckClick = { onDeckClick(deck) },
            onExpandClick = { onExpandClick(deck) },
            onDeckOptions = { onDeckOptions(deck) },
            onRename = { onRename(deck) },
            onExport = { onExport(deck) },
            onDelete = { onDelete(deck) },
            onRebuild = { onRebuild(deck) },
            onEmpty = { onEmpty(deck) },
        )
        AnimatedVisibility(
            visible = !deck.collapsed,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(
                initialScale = 0.3f,
                animationSpec = motionScheme.defaultSpatialSpec(),
            ),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.defaultEffectsSpec()) + scaleOut(
                targetScale = 0.92f,
                animationSpec = motionScheme.fastSpatialSpec(),
            ),
        ) {
            Column {
                for (child in (rememberedChildren ?: emptyList())) {
                    key(child.did) {
                        val grandChildren = deckToChildrenMap[child] ?: emptyList()
                        RenderDeck(
                            deck = child,
                            children = grandChildren,
                            deckToChildrenMap = deckToChildrenMap,
                            onDeckClick = onDeckClick,
                            onExpandClick = onExpandClick,
                            onDeckOptions = onDeckOptions,
                            onRename = onRename,
                            onExport = onExport,
                            onDelete = onDelete,
                            onRebuild = onRebuild,
                            onEmpty = onEmpty,
                        )
                    }
                }
            }
        }
    }

    if (deck.depth == 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.padding(8.dp)) {
                content()
            }
        }
    } else {
        Column(
            modifier = Modifier.padding(
                start = if (deck.depth == 1) 0.dp else subDeckPadding,
            ),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeckPickerContent(
    decks: List<DisplayDeckNode>,
    onRefresh: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onDeckClick: (DisplayDeckNode) -> Unit,
    onExpandClick: (DisplayDeckNode) -> Unit,
    onDeckOptions: (DisplayDeckNode) -> Unit,
    onRename: (DisplayDeckNode) -> Unit,
    onExport: (DisplayDeckNode) -> Unit,
    onDelete: (DisplayDeckNode) -> Unit,
    onRebuild: (DisplayDeckNode) -> Unit,
    onEmpty: (DisplayDeckNode) -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    isInInitialState: Boolean?,
) {
    val state = rememberPullToRefreshState()
    val morph = remember {
        Morph(
            start = MaterialShapes.Pentagon,
            end = MaterialShapes.Cookie12Sided,
        )
    }
    val morphingShape = remember(state.distanceFraction) {
        MorphShape(
            morph = morph,
            percentage = state.distanceFraction,
        )
    }

    // Build the deck tree
    // We remember the result to avoid rebuilding the tree on every recomposition
    // if the deck list hasn't changed.
    val (deckToChildrenMap, rootDecks) = remember(decks) {
        val deckToChildrenMap = mutableMapOf<DisplayDeckNode, MutableList<DisplayDeckNode>>()
        val rootDecks = mutableListOf<DisplayDeckNode>()
        val deckMap = decks.associateBy { it.did }

        for (deck in decks) {
            val parentId = deck.deckNode.parent?.get()?.did
            if (parentId != null && deckMap.containsKey(parentId)) {
                val parent = deckMap[parentId]!!
                deckToChildrenMap.getOrPut(parent) { mutableListOf() }.add(deck)
            } else {
                rootDecks.add(deck)
            }
        }
        deckToChildrenMap to rootDecks
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = false, // Always false to prevent pinning and allow immediate retraction animation
            onRefresh = onRefresh,
            state = state,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                Box(
                    modifier = Modifier
                        .padding(top = contentPadding.calculateTopPadding() + 16.dp)
                        .align(Alignment.TopCenter)
                        .width(42.dp)
                        .height(42.dp)
                        .graphicsLayer {
                            alpha = state.distanceFraction * 5
                            rotationZ = state.distanceFraction * 180
                            translationY = (state.distanceFraction * 140) - 60
                        }
                        .clip(morphingShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Box(modifier = Modifier.padding(16.dp))
                }
            },
        ) {
            val isLoading = isInInitialState == null || (!isInInitialState && decks.isEmpty())
            val isEmpty = !isLoading && isInInitialState
            val hasDecks = !isLoading && !isEmpty

            AnimatedVisibility(visible = isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            AnimatedVisibility(visible = isEmpty) {
                NoDecks(
                    onCreateDeck = onAddDeck,
                    onGetSharedDecks = onAddSharedDeck,
                )
            }

            AnimatedVisibility(
                visible = hasDecks,
                enter = fadeIn(motionScheme.slowEffectsSpec()) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = motionScheme.slowSpatialSpec(),
                ) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 4 },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = contentPadding,
                    state = listState,
                ) {
                    items(rootDecks, key = { it.did }) { rootDeck ->
                        val children = deckToChildrenMap[rootDeck] ?: emptyList()
                        RenderDeck(
                            deck = rootDeck,
                            children = children,
                            deckToChildrenMap = deckToChildrenMap,
                            onDeckClick = onDeckClick,
                            onExpandClick = onExpandClick,
                            onDeckOptions = onDeckOptions,
                            onRename = onRename,
                            onExport = onExport,
                            onDelete = onDelete,
                            onRebuild = onRebuild,
                            onEmpty = onEmpty,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeckPickerTopBar(
    isSearchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    searchAnim: Float,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    searchOffsetPx: Float,
    isSyncing: Boolean,
    syncState: SyncIconState,
    onRefresh: () -> Unit,
    onNavigationIconClick: (() -> Unit)?,
    studyOptionsData: StudyOptionsData?,
    onRebuildDeck: (Long) -> Unit,
    onEmptyDeck: (Long) -> Unit,
    onCustomStudy: (Long) -> Unit,
    onDeckOptionsItemSelected: (Long) -> Unit,
    onUnbury: (Long) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var isStudyOptionsMenuOpen by remember { mutableStateOf(false) }

    LargeFlexibleTopAppBar(
        title = {
            if (!isSearchOpen) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - searchAnim
                    },
                )
            }
        },
        navigationIcon = {
            if (!isSearchOpen && onNavigationIconClick != null) {
                FilledIconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = onNavigationIconClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.menu_24px),
                        contentDescription = stringResource(R.string.navigation_drawer_open),
                    )
                }
            }
        },
        actions = {
            if (isSearchOpen) {
                SearchBar(
                    inputField = {
                        InputField(
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
                                    translationY = searchOffsetPx * (1f - searchAnim)
                                    scaleX = 0.98f + 0.02f * searchAnim
                                    scaleY = 0.98f + 0.02f * searchAnim
                                },
                            placeholder = { Text(stringResource(R.string.search_decks)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.search_24px),
                                    contentDescription = stringResource(R.string.search_decks),
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onSearchQueryChanged("")
                                    onSearchOpenChange(false)
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close_24px),
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
                        .padding(start = 16.dp, end = 12.dp, bottom = 16.dp)
                        .graphicsLayer {
                            alpha = searchAnim
                        },
                    shape = SearchBarDefaults.inputFieldShape,
                    content = { },
                )
            } else {
                Row(
                    modifier = Modifier.padding(end = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { onSearchOpenChange(true) },
                        modifier = Modifier.graphicsLayer { alpha = 1f - searchAnim },
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
                        isSyncing = isSyncing,
                        syncState = syncState,
                        onRefresh = onRefresh,
                        modifier = Modifier
                            .height(40.dp)
                            .width(48.dp)
                            .graphicsLayer {
                                alpha = 1f - searchAnim
                            },
                    )
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
                            shape = MaterialTheme.shapes.large,
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
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeckPickerScreen(
    fragmented: Boolean,
    decks: List<DisplayDeckNode>,
    isSyncing: Boolean,
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
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester = FocusRequester(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var isSearchOpen by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val searchAnim by animateFloatAsState(
        targetValue = if (isSearchOpen) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )
    val density = LocalDensity.current
    val searchOffsetPx = with(density) { (-8).dp.toPx() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocusRequester.requestFocus()
            onSearchFocusRequested()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = SnackbarPaddingBottom),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        actionColor = MaterialTheme.colorScheme.primary,
                        dismissActionContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
            topBar = {
                DeckPickerTopBar(
                    isSearchOpen = isSearchOpen,
                    onSearchOpenChange = { isSearchOpen = it },
                    searchAnim = searchAnim,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    searchFocusRequester = searchFocusRequester,
                    searchOffsetPx = searchOffsetPx,
                    isSyncing = isSyncing,
                    syncState = syncState,
                    onRefresh = onRefresh,
                    onNavigationIconClick = if (!fragmented) onNavigationIconClick else null,
                    studyOptionsData = studyOptionsData,
                    onRebuildDeck = onRebuildDeck,
                    onEmptyDeck = onEmptyDeck,
                    onCustomStudy = onCustomStudy,
                    onDeckOptionsItemSelected = onDeckOptionsItemSelected,
                    onUnbury = onUnbury,
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (fragmented) {
                    Scrim(
                        opacity = 0F,
                        visible = fabMenuExpanded,
                        onDismiss = { fabMenuExpanded = false },
                    )
                    ExpandableFabContainer {
                        ExpandableFab(
                            expanded = fabMenuExpanded,
                            onExpandedChange = { fabMenuExpanded = it },
                            onAddNote = onAddNote,
                            onAddDeck = onAddDeck,
                            onAddSharedDeck = onAddSharedDeck,
                            onAddFilteredDeck = onAddFilteredDeck,
                            onCheckDatabase = onCheckDatabase,
                        )
                    }
                }
            }) { paddingValues ->
            if (fragmented) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DeckPickerContent(
                            decks = decks,
                            onRefresh = onRefresh,
                            onDeckClick = onDeckClick,
                            onExpandClick = onExpandClick,
                            onDeckOptions = onDeckOptions,
                            onRename = onRename,
                            onExport = onExport,
                            onDelete = onDelete,
                            onRebuild = onRebuild,
                            onEmpty = onEmpty,
                            listState = listState,
                            onAddDeck = onAddDeck,
                            onAddSharedDeck = onAddSharedDeck,
                            isInInitialState = isInInitialState,
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
            } else {
                DeckPickerContent(
                    decks = decks,
                    onRefresh = onRefresh,
                    onDeckClick = onDeckClick,
                    onExpandClick = onExpandClick,
                    onDeckOptions = onDeckOptions,
                    onRename = onRename,
                    onExport = onExport,
                    onDelete = onDelete,
                    onRebuild = onRebuild,
                    onEmpty = onEmpty,
                    listState = listState,
                    contentPadding = paddingValues,
                    onAddDeck = onAddDeck,
                    onAddSharedDeck = onAddSharedDeck,
                    isInInitialState = isInInitialState,
                )
            }
        }
        if (!fragmented) {
            Scrim(
                visible = fabMenuExpanded,
                onDismiss = { fabMenuExpanded = false },
            )
            ExpandableFabContainer {
                ExpandableFab(
                    expanded = fabMenuExpanded,
                    onExpandedChange = { fabMenuExpanded = it },
                    onAddNote = onAddNote,
                    onAddDeck = onAddDeck,
                    onAddSharedDeck = onAddSharedDeck,
                    onAddFilteredDeck = onAddFilteredDeck,
                    onCheckDatabase = onCheckDatabase,
                )
            }
            BackHandler(fabMenuExpanded) { fabMenuExpanded = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun DeckPickerTopBarPreview() {
    AnkiDroidTheme {
        DeckPickerTopBar(
            isSearchOpen = false,
            onSearchOpenChange = {},
            searchAnim = 0f,
            searchQuery = "",
            onSearchQueryChanged = {},
            searchFocusRequester = remember { FocusRequester() },
            searchOffsetPx = 0f,
            isSyncing = false,
            syncState = SyncIconState.Normal,
            onRefresh = {},
            onNavigationIconClick = {},
            studyOptionsData = StudyOptionsData(
                deckId = 1,
                deckName = "Default",
                deckDescription = "This is a great deck for learning Compose.",
                newCount = 10,
                lrnCount = 5,
                revCount = 20,
                buriedNew = 2,
                buriedLrn = 1,
                buriedRev = 3,
                totalNewCards = 50,
                totalCards = 200,
                isFiltered = false,
                haveBuried = true
            ),
            onRebuildDeck = {},
            onEmptyDeck = {},
            onCustomStudy = {},
            onDeckOptionsItemSelected = {},
            onUnbury = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun DeckPickerTopBarSearchOpenPreview() {
    AnkiDroidTheme {
        DeckPickerTopBar(
            isSearchOpen = true,
            onSearchOpenChange = {},
            searchAnim = 1f,
            searchQuery = "Japanese",
            onSearchQueryChanged = {},
            searchFocusRequester = remember { FocusRequester() },
            searchOffsetPx = 0f,
            isSyncing = false,
            syncState = SyncIconState.Normal,
            onRefresh = {},
            onNavigationIconClick = {},
            studyOptionsData = null,
            onRebuildDeck = {},
            onEmptyDeck = {},
            onCustomStudy = {},
            onDeckOptionsItemSelected = {},
            onUnbury = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}
