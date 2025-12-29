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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    var rememberedChildren by remember { mutableStateOf<List<DisplayDeckNode>?>(null) }
    if (!deck.collapsed) {
        rememberedChildren = children
    }

    val content = @Composable {
        DeckItem(
            deck = deck,
            onDeckClick = onDeckClick,
            onExpandClick = onExpandClick,
            onDeckOptions = onDeckOptions,
            onRename = onRename,
            onExport = onExport,
            onDelete = onDelete,
            onRebuild = onRebuild,
            onEmpty = onEmpty,
        )
        AnimatedVisibility(
            visible = !deck.collapsed,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(
                initialScale = 0.3f,
                animationSpec = motionScheme.defaultSpatialSpec()
            ),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.defaultEffectsSpec()) + scaleOut(
                targetScale = 0.92f,
                animationSpec = motionScheme.fastSpatialSpec()
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
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(Modifier.padding(8.dp)) {
                content()
            }
        }
    } else {
        Column(
            modifier = Modifier.padding(
                start = if (deck.depth == 1) 0.dp else subDeckPadding,
            )
        ) {
            content()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeckPickerContent(
    decks: List<DisplayDeckNode>,
    isRefreshing: Boolean,
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
            morph = morph, percentage = state.distanceFraction
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
            isRefreshing = isRefreshing,
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
                        .background(MaterialTheme.colorScheme.primary)) {
                    Box(modifier = Modifier.padding(16.dp))
                }
            }) {
            val isLoading = isInInitialState == null || (!isInInitialState && decks.isEmpty())
            val isEmpty = !isLoading && isInInitialState
            val hasDecks = !isLoading && !isEmpty

            AnimatedVisibility(visible = isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            AnimatedVisibility(visible = isEmpty) {
                NoDecks(
                    onCreateDeck = onAddDeck, onGetSharedDecks = onAddSharedDeck
                )
            }

            AnimatedVisibility(
                visible = hasDecks, enter = fadeIn(motionScheme.slowEffectsSpec()) + scaleIn(
                    initialScale = 0.85f, animationSpec = motionScheme.slowSpatialSpec()
                ) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 4 }) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = contentPadding,
                    state = listState
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
fun DeckPickerScreen(
    decks: List<DisplayDeckNode>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    syncState: SyncIconState,
    isInInitialState: Boolean?,
    searchFocusRequester: FocusRequester = FocusRequester(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    fabMenuExpanded: Boolean,
    onFabMenuExpandedChange: (Boolean) -> Unit
) {
    var isSearchOpen by remember { mutableStateOf(false) }
    val searchAnim by animateFloatAsState(
        targetValue = if (isSearchOpen) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec()
    )
    val density = LocalDensity.current
    val searchOffsetPx = with(density) { (-8).dp.toPx() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = SnackbarPaddingBottom)
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
                LargeFlexibleTopAppBar(
                    title = {
                        if (!isSearchOpen) Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displayMediumEmphasized,
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - searchAnim
                            }
                        )
                    },
                    navigationIcon = {
                        if (!isSearchOpen) {
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
                                    contentDescription = stringResource(R.string.navigation_drawer_open)
                                )
                            }
                        }
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
                                                translationY = searchOffsetPx * (1f - searchAnim)
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
                                                    contentDescription = stringResource(R.string.close)
                                                )
                                            }
                                        },
                                    )
                                },
                                expanded = false,
                                onExpandedChange = { },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
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
                                    }
                                    .padding(end = 4.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search_24px),
                                    contentDescription = stringResource(R.string.search_decks)
                                )
                            }
                            SyncIcon(
                                isSyncing = isRefreshing,
                                syncState = syncState,
                                onRefresh = onRefresh,
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(48.dp)
                                    .padding(end = 8.dp)
                                    .graphicsLayer {
                                        alpha = 1f - searchAnim
                                    }
                            )
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
            },
        ) { paddingValues ->
            DeckPickerContent(
                decks = decks,
                isRefreshing = isRefreshing,
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
        Scrim(
            visible = fabMenuExpanded, onDismiss = { onFabMenuExpandedChange(false) })
        ExpandableFabContainer {
            ExpandableFab(
                expanded = fabMenuExpanded,
                onExpandedChange = onFabMenuExpandedChange,
                onAddNote = onAddNote,
                onAddDeck = onAddDeck,
                onAddSharedDeck = onAddSharedDeck,
                onAddFilteredDeck = onAddFilteredDeck,
                onCheckDatabase = onCheckDatabase
            )
        }
        BackHandler(fabMenuExpanded) { onFabMenuExpandedChange(false) }
    }
}

@Preview
@Composable
fun DeckPickerContentPreview() {
    DeckPickerContent(
        decks = emptyList(),
        isRefreshing = false,
        onRefresh = {},
        onDeckClick = {},
        onExpandClick = {},
        onDeckOptions = {},
        onRename = {},
        onExport = {},
        onDelete = {},
        onRebuild = {},
        onEmpty = {},
        listState = rememberLazyListState(),
        onAddDeck = {},
        onAddSharedDeck = {},
        isInInitialState = false,
    )
}

@Preview
@Composable
fun DeckPickerScreenPreview() {
    DeckPickerScreen(
        decks = emptyList(),
        isRefreshing = false,
        onRefresh = {},
        searchQuery = "",
        onSearchQueryChanged = {},
        onDeckClick = {},
        onExpandClick = {},
        onAddNote = {},
        onAddDeck = {},
        onAddSharedDeck = {},
        onAddFilteredDeck = {},
        onCheckDatabase = {},
        onDeckOptions = {},
        onRename = {},
        onExport = {},
        onDelete = {},
        onRebuild = {},
        onEmpty = {},
        onNavigationIconClick = {},
        syncState = SyncIconState.Normal,
        isInInitialState = false,
        fabMenuExpanded = false,
        onFabMenuExpandedChange = {})
}
