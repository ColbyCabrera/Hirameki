/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
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

package com.ichi2.anki.browser.compose

// TODO: Re-enable NoteEditor in split view after migration is complete
// import com.ichi2.anki.noteeditor.compose.NoteEditor
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserRowWithId
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.dialogs.compose.CreateDeckDialog
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.pages.Statistics
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.ui.compose.components.DeckSelector
import com.ichi2.anki.ui.compose.navigation.AnkiNavigationRail
import com.ichi2.anki.ui.compose.navigation.AppNavigationItem
import com.ichi2.anki.utils.ext.showDialogFragment
import kotlinx.coroutines.launch

private val transparentTextFieldColors: @Composable () -> TextFieldColors = {
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )
}

@OptIn(
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun CardBrowserLayout(
    viewModel: CardBrowserViewModel,
    fragmented: Boolean,
    onNavigateUp: () -> Unit,
    onCardClicked: (BrowserRowWithId) -> Unit,
    onAddNote: () -> Unit,
    onPreview: () -> Unit,
    onFilter: (String) -> Unit,
    onSelectAll: () -> Unit,
    onOptions: () -> Unit,
    onCreateFilteredDeck: () -> Unit,
    onEditNote: () -> Unit,
    onCardInfo: () -> Unit,
    onChangeDeck: () -> Unit,
    onReposition: () -> Unit,
    onSetDueDate: () -> Unit,
    onGradeNow: () -> Unit,
    onResetProgress: () -> Unit,
    onExportCard: () -> Unit,
    onFilterByTag: () -> Unit,
) {
    val activity = LocalActivity.current
    val isTablet = if (activity != null) {
        val windowSizeClass = calculateWindowSizeClass(activity)
        windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    } else {
        false
    }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.flowOfSearchQueryExpanded.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var availableDecks by remember { mutableStateOf<List<SelectableDeck.Deck>>(emptyList()) }
    val searchAnim by animateFloatAsState(
        targetValue = if (isSearchOpen) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )
    val density = LocalDensity.current
    val searchOffsetPx = with(density) { (-8).dp.toPx() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Use an integer revision counter as an event-style trigger so that
    // requesting the keyboard doesn't reset the trigger inside the effect
    // (which would cause an extra recomposition and restart the effect).
    var keyboardTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyboardTrigger) {
        // Only run after an explicit trigger increment (> 0). This avoids
        // running on initial composition when keyboardTrigger == 0.
        if (keyboardTrigger > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        availableDecks = viewModel.getAvailableDecks()
    }

    // Create Deck Dialog
    val createDeckDialogState by viewModel.createDeckDialogState.collectAsStateWithLifecycle()
    when (val state = createDeckDialogState) {
        is CardBrowserViewModel.CreateDeckDialogState.Visible -> {
            CreateDeckDialog(
                onDismissRequest = { viewModel.dismissCreateDeckDialog() },
                onConfirm = { name -> viewModel.createDeck(name, state) },
                dialogType = state.type,
                title = stringResource(state.titleResId),
                initialDeckName = state.initialName,
                validateDeckName = { viewModel.validateDeckName(it, state) },
            )
        }

        CardBrowserViewModel.CreateDeckDialogState.Hidden -> {}
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (fragmented) {
            AnkiNavigationRail(
                selectedItem = AppNavigationItem.CardBrowser,
                onNavigate = { item ->
                    when (item) {
                        AppNavigationItem.Decks -> onNavigateUp()
                        AppNavigationItem.CardBrowser -> { // Already here
                        }

                        AppNavigationItem.Statistics -> activity?.startActivity(
                            Statistics.getIntent(
                                activity,
                            ),
                        )

                        AppNavigationItem.Settings -> activity?.startActivity(
                            PreferencesActivity.getIntent(
                                activity,
                            ),
                        )

                        AppNavigationItem.Help -> (activity as? AnkiActivity)?.showDialogFragment(
                            HelpDialog.newHelpInstance(),
                        )

                        AppNavigationItem.Support -> {
                            val uri =
                                "https://github.com/ankidroid/Anki-Android/wiki/Contributing".toUri()
                            activity?.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    }
                },
            )
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                TopAppBar(title = {
                    Row(
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - searchAnim
                        },
                    ) {
                        DeckSelector(
                            selectedDeck = viewModel.flowOfDeckSelection.collectAsStateWithLifecycle(
                                    null,
                                ).value,
                            availableDecks = availableDecks,
                            onDeckSelected = { deck ->
                                coroutineScope.launch {
                                    viewModel.setSelectedDeck(deck)
                                }
                            },
                        )
                    }
                }, navigationIcon = {
                    if (!isSearchOpen) {
                        FilledIconButton(
                            onClick = onNavigateUp,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                }, actions = {
                    if (isSearchOpen) {
                        var textFieldValue by remember {
                            mutableStateOf(
                                TextFieldValue(
                                    searchQuery,
                                    selection = TextRange(0, searchQuery.length),
                                ),
                            )
                        }

                        LaunchedEffect(searchQuery) {
                            if (textFieldValue.text != searchQuery) {
                                textFieldValue = textFieldValue.copy(text = searchQuery)
                            }
                        }

                        SearchBar(
                            inputField = {
                                TextField(
                                    value = textFieldValue,
                                    onValueChange = {
                                        textFieldValue = it
                                        viewModel.setSearchQuery(it.text)
                                    },
                                    placeholder = { Text(text = stringResource(R.string.card_browser_search_hint)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = stringResource(R.string.card_browser_search_hint),
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { viewModel.collapseSearchQuery() }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.close),
                                            )
                                        }
                                    },
                                    colors = transparentTextFieldColors(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            viewModel.search(textFieldValue.text)
                                            keyboardController?.hide()
                                        },
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .graphicsLayer {
                                            alpha = searchAnim
                                            translationY = searchOffsetPx * (1f - searchAnim)
                                            scaleX = 0.98f + 0.02f * searchAnim
                                            scaleY = 0.98f + 0.02f * searchAnim
                                        },
                                )
                            },
                            expanded = false,
                            onExpandedChange = { },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 6.dp, bottom = 16.dp),
                            shape = SearchBarDefaults.inputFieldShape,
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            content = { },
                        )
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.expandSearchQuery()
                                keyboardTrigger++
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search_24px),
                                contentDescription = stringResource(R.string.card_browser_search_hint),
                            )
                        }
                    }
                })
            },
        ) { paddingValues ->
            // Don't apply paddingValues here to allow content to draw behind system bars
            // Instead pass them to CardBrowserScreen
            CardBrowserScreen(
                viewModel = viewModel,
                onCardClicked = onCardClicked,
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                onAddNote = onAddNote,
                onPreview = onPreview,
                onFilter = onFilter,
                onSelectAll = onSelectAll,
                onOptions = onOptions,
                onCreateFilteredDeck = onCreateFilteredDeck,
                onEditNote = onEditNote,
                onCardInfo = onCardInfo,
                onChangeDeck = onChangeDeck,
                onReposition = onReposition,
                onSetDueDate = onSetDueDate,
                onGradeNow = onGradeNow,
                onResetProgress = onResetProgress,
                onExportCard = onExportCard,
                onFilterByTag = onFilterByTag,
            )
            if (isTablet) {
                // TODO: Re-enable NoteEditor split view after migration is complete
                // NoteEditor(
                //     modifier = Modifier.weight(1f)
                // )
            }
        }
    }
}

// buildDeckHierarchy and DeckHierarchyMenu removed in favor of shared DeckSelector.kt
