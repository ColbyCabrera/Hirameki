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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import anki.decks.deckTreeNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SyncIconState
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.ui.compose.SnackbarPaddingBottom
import com.ichi2.anki.ui.compose.components.AnkiSearchBar
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
    deckRowActions: DeckRowActions,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (!deck.collapsed && deck.canCollapse) expandedDeckCardRadius else collapsedDeckCardRadius,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )

    var rememberedChildren by remember { mutableStateOf<List<DisplayDeckNode>?>(null) }
    if (!deck.collapsed) {
        rememberedChildren = children
    }

    val actions = remember(deck, deckRowActions) {
        DeckItemActions(
            onDeckClick = { deckRowActions.onDeckClick(deck) },
            onExpandClick = { deckRowActions.onExpandClick(deck) },
            onDeckOptions = { deckRowActions.onDeckOptions(deck) },
            onRename = { deckRowActions.onRename(deck) },
            onCustomStudy = { deckRowActions.onCustomStudy(deck) },
            onUnbury = { deckRowActions.onUnbury(deck) },
            onExportDeck = { deckRowActions.onExportDeck(deck) },
            onDelete = { deckRowActions.onDelete(deck) },
            onRebuild = { deckRowActions.onRebuild(deck) },
            onEmpty = { deckRowActions.onEmpty(deck) },
            onCreateSubdeck = { deckRowActions.onCreateSubdeck(deck) },
        )
    }

    val content = @Composable {
        DeckItem(
            deck = deck,
            actions = actions,
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
                            deckRowActions = deckRowActions,
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
    deckRowActions: DeckRowActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
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
                            deckRowActions = deckRowActions,
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
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSyncing: Boolean,
    syncState: SyncIconState,
    onRefresh: () -> Unit,
    onNavigationIconClick: (() -> Unit)?,
    moreOptionsMenuActions: MoreOptionsMenuActions,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var isMoreOptionsMenuOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val searchAnim by animateFloatAsState(
        targetValue = if (isSearchOpen) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )

    BackHandler(isSearchOpen) {
        onSearchQueryChanged("")
        onSearchOpenChange(false)
    }

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
                AnkiSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    onSearch = { /* Search is performed as user types */ },
                    onActiveChange = onSearchOpenChange,
                    placeholder = stringResource(R.string.search_decks),
                    focusRequester = searchFocusRequester,
                    searchAnim = searchAnim,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            } else {
                Row(
                    modifier = Modifier.graphicsLayer { alpha = 1f - searchAnim },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { onSearchOpenChange(true) },
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
                    )
                    MoreOptionsMenu(
                        isMoreOptionsMenuOpen,
                        onMoreOptionsMenuOpenChange = { isMoreOptionsMenuOpen = it },
                        moreOptionsMenuActions,
                    )
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

@Composable
fun MoreOptionsMenu(
    isMoreOptionsMenuOpen: Boolean,
    onMoreOptionsMenuOpenChange: (Boolean) -> Unit,
    moreOptionsMenuActions: MoreOptionsMenuActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilledIconButton(
            onClick = { onMoreOptionsMenuOpenChange(true) },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
            )
        }
        DropdownMenu(
            expanded = isMoreOptionsMenuOpen,
            onDismissRequest = { onMoreOptionsMenuOpenChange(false) },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.check_db)) },
                onClick = {
                    onMoreOptionsMenuOpenChange(false)
                    moreOptionsMenuActions.onCheckDatabase()
                },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.checklist_24px),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.model_browser_label)) },
                onClick = {
                    onMoreOptionsMenuOpenChange(false)
                    moreOptionsMenuActions.onManageNoteTypes()
                },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.list_24px),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(text = TR.actionsExport()) },
                onClick = {
                    onMoreOptionsMenuOpenChange(false)
                    moreOptionsMenuActions.onExport()
                },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.file_export_24px),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(TR.actionsEmptyCards()) },
                onClick = {
                    onMoreOptionsMenuOpenChange(false)
                    moreOptionsMenuActions.onDeleteEmptyCards()
                },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = null,
                    )
                },
            )
        }
    }
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
    deckRowActions: DeckRowActions,
    fabActions: FabActions,
    moreOptionsMenuActions: MoreOptionsMenuActions,
    onNavigationIconClick: () -> Unit,
    onStartStudy: () -> Unit,
    onCustomStudy: (Long) -> Unit,
    studyOptionsData: StudyOptionsData?,
    requestSearchFocus: Boolean,
    onSearchFocusRequested: () -> Unit,
    syncState: SyncIconState,
    isInInitialState: Boolean?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var isSearchOpen by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            isSearchOpen = true
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
                        actionColor = MaterialTheme.colorScheme.onSecondary,
                        dismissActionContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
            topBar = {
                DeckPickerTopBar(
                    isSearchOpen = isSearchOpen,
                    onSearchOpenChange = { isSearchOpen = it },
                    searchQuery = searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    isSyncing = isSyncing,
                    syncState = syncState,
                    onRefresh = onRefresh,
                    onNavigationIconClick = if (!fragmented) onNavigationIconClick else null,
                    moreOptionsMenuActions = moreOptionsMenuActions,
                    scrollBehavior = scrollBehavior
                )
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
                            deckRowActions = deckRowActions,
                            listState = listState,
                            onAddDeck = fabActions.onAddDeck,
                            onAddSharedDeck = fabActions.onAddSharedDeck,
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
                    deckRowActions = deckRowActions,
                    listState = listState,
                    contentPadding = paddingValues,
                    onAddDeck = fabActions.onAddDeck,
                    onAddSharedDeck = fabActions.onAddSharedDeck,
                    isInInitialState = isInInitialState,
                )
            }
        }
        DeckPickerFab(
            expanded = fabMenuExpanded,
            onExpandedChange = { fabMenuExpanded = it },
            fabActions = fabActions,
            scrimOpacity = if (fragmented) 0F else 0.5f,
        )
    }
}

@Composable
private fun DeckPickerFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    fabActions: FabActions,
    scrimOpacity: Float = 0.5f,
) {
    Scrim(
        opacity = scrimOpacity,
        visible = expanded,
        onDismiss = { onExpandedChange(false) },
    )
    ExpandableFabContainer {
        ExpandableFab(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            onAddNote = fabActions.onAddNote,
            onAddDeck = fabActions.onAddDeck,
            onAddSharedDeck = fabActions.onAddSharedDeck,
            onAddFilteredDeck = fabActions.onAddFilteredDeck,
            onImport = fabActions.onImport,
        )
    }
    BackHandler(expanded) { onExpandedChange(false) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun DeckPickerTopBarPreview() {
    AnkiDroidTheme {
        DeckPickerTopBar(
            isSearchOpen = false,
            onSearchOpenChange = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            isSyncing = false,
            syncState = SyncIconState.Normal,
            onRefresh = {},
            onNavigationIconClick = {},
            moreOptionsMenuActions = MoreOptionsMenuActions(
                onDeleteEmptyCards = {},
                onCheckDatabase = {},
                onExport = {},
                onManageNoteTypes = {}),
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
            searchQuery = "Japanese",
            onSearchQueryChanged = {},
            isSyncing = false,
            syncState = SyncIconState.Normal,
            onRefresh = {},
            onNavigationIconClick = {},
            moreOptionsMenuActions = MoreOptionsMenuActions(
                onDeleteEmptyCards = {},
                onCheckDatabase = {},
                onExport = {},
                onManageNoteTypes = {}),
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
fun RenderDeckPreview() {
    AnkiDroidTheme {
        val rootDeckNode = DeckNode(
            node = deckTreeNode {
                name = "Japanese"
                deckId = 1
                level = 1
                collapsed = false
                reviewCount = 10
                newCount = 5
                learnCount = 2
                filtered = false
                // Add a child node to the underlying DeckTreeNode to ensure canCollapse is true
                children.add(deckTreeNode {
                    name = "Kanji"
                    deckId = 2
                    level = 2
                })
            }, fullDeckName = "Japanese"
        )
        val rootDeck = DisplayDeckNode.from(
            node = rootDeckNode, matchesSearchOrChild = true, selectedDeckId = 1L, hasBuried = false
        )

        val childDeck = DisplayDeckNode.from(
            node = rootDeckNode.children[0],
            matchesSearchOrChild = true,
            selectedDeckId = 0L,
            hasBuried = false
        )
        RenderDeck(
            deck = rootDeck,
            children = listOf(childDeck),
            deckToChildrenMap = mapOf(rootDeck to listOf(childDeck)),
            deckRowActions = DeckRowActions(
                onDeckClick = {},
                onExpandClick = {},
                onDeckOptions = {},
                onRename = {},
                onExportDeck = {},
                onDelete = {},
                onRebuild = {},
                onEmpty = {},
                onCreateSubdeck = {},
                onCustomStudy = {},
                onUnbury = {},
            ),
        )
    }
}
