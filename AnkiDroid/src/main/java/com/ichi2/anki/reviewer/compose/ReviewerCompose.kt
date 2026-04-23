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
package com.ichi2.anki.reviewer.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anki.scheduler.CardAnswer
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.DeleteConfirmationDialog
import com.ichi2.anki.dialogs.compose.TagsDialog
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.reviewer.AnswerFeedback
import com.ichi2.anki.reviewer.ReviewerEffect
import com.ichi2.anki.reviewer.ReviewerEvent
import com.ichi2.anki.reviewer.ReviewerViewModel
import com.ichi2.anki.reviewer.VoicePlaybackViewModel
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.whiteboard.ToolbarAlignment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val WhiteboardToolbarWidth = 56.dp
private val WhiteboardBottomBarOffset = 48.dp
private const val AnswerIndicatorDuration = 1000L

// You can rename this class to be more descriptive
class InvertedTopCornersShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }

        val path = Path().apply {
            // --- Top-Left Corner Path ---
            moveTo(0f, 0f) // Start at the top-left point
            lineTo(cornerRadiusPx, 0f) // Line to the start of the arc
            // Arc from (r, 0) down to (0, r)
            arcTo(
                rect = Rect(
                    left = 0f, top = 0f, right = 2 * cornerRadiusPx, bottom = 2 * cornerRadiusPx
                ), startAngleDegrees = 270f,   // Top-center of the rect
                sweepAngleDegrees = -90f, // Sweep counter-clockwise
                forceMoveTo = false
            )
            // lineTo(0f, 0f) is implicitly added by close()
            close() // Close the path, drawing a line from (0, r) back to (0, 0)

            // --- Top-Right Corner Path ---
            moveTo(size.width, 0f) // Start at the top-right point
            lineTo(size.width - cornerRadiusPx, 0f) // Line to the start of the arc
            // Arc from (width - r, 0) down to (width, r)
            arcTo(
                rect = Rect(
                    left = size.width - 2 * cornerRadiusPx,
                    top = 0f,
                    right = size.width,
                    bottom = 2 * cornerRadiusPx
                ), startAngleDegrees = 270f, // Top-center of the rect
                sweepAngleDegrees = 90f,  // Sweep clockwise
                forceMoveTo = false
            )
            // lineTo(size.width, 0f) is implicitly added by close()
            close() // Close the path, drawing a line from (width, r) back to (width, 0)
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewerContent(
    viewModel: ReviewerViewModel,
    whiteboardViewModel: WhiteboardViewModel?,
    voicePlaybackViewModel: VoicePlaybackViewModel?
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showBrushOptions by remember { mutableStateOf(false) }
    var showEraserOptions by remember { mutableStateOf(false) }
    var brushIndexToRemove by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteCardId by rememberSaveable { mutableStateOf<CardId?>(null) }
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var whiteboardToolbarHeight by remember { mutableIntStateOf(0) }
    val toolbarHeightDp = with(LocalDensity.current) { toolbarHeight.toDp() }
    val whiteboardToolbarHeightDp = with(LocalDensity.current) { whiteboardToolbarHeight.toDp() }
    val totalBottomPadding =
        toolbarHeightDp + (if (state.isWhiteboardEnabled) whiteboardToolbarHeightDp + 8.dp else 0.dp)
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val layoutDirection = LocalLayoutDirection.current
    val undoLabel = stringResource(R.string.undo)

    // Tags dialog state
    val showTagsDialog by viewModel.showTagsDialog.collectAsStateWithLifecycle()
    val tagsState by viewModel.tagsState.collectAsStateWithLifecycle()
    val currentNoteTags by viewModel.currentNoteTags.collectAsStateWithLifecycle()
    val deckTags by viewModel.deckTags.collectAsStateWithLifecycle()
    val filterByDeck by viewModel.filterByDeck.collectAsStateWithLifecycle()
    val javascriptCommand by viewModel.evalCommand.collectAsStateWithLifecycle()

    // Load whiteboard state when first enabled
    // Capture isDarkMode once to prevent re-loading state on system theme changes
    val currentDarkMode = isSystemInDarkTheme()
    val initialDarkMode by rememberSaveable { mutableStateOf(currentDarkMode) }
    LaunchedEffect(state.isWhiteboardEnabled, whiteboardViewModel) {
        if (state.isWhiteboardEnabled && whiteboardViewModel != null) {
            whiteboardViewModel.loadState(initialDarkMode)
        }
    }

    val editCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onEvent(ReviewerEvent.ReloadCard)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ReviewerEffect.NavigateToEditCard -> {
                    val intent = NoteEditorLauncher.EditCard(
                        effect.cardId, ActivityTransitionAnimation.Direction.FADE
                    ).toIntent(currentContext)
                    editCardLauncher.launch(intent)
                }

                is ReviewerEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is ReviewerEffect.ShowDeleteNoteDialog -> {
                    pendingDeleteCardId = effect.card.id
                }

                else -> {
                    // All other effects are handled by the Activity
                }
            }
        }
    }

    LaunchedEffect(state.mediaError) {
        state.mediaError?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.onEvent(ReviewerEvent.MediaErrorHandled)
        }
    }

    LaunchedEffect(viewModel.flowOfDeleteResult) {
        viewModel.flowOfDeleteResult.collectLatest { count ->
            val message = currentContext.resources.getQuantityString(
                R.plurals.card_browser_cards_deleted,
                count,
                count,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    pendingDeleteCardId?.let { id ->
        DeleteConfirmationDialog(
            quantity = 1,
            onDismissRequest = { pendingDeleteCardId = null },
            onConfirm = {
                viewModel.confirmDeleteNote(id)
                pendingDeleteCardId = null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = {
            SnackbarHost(
                snackbarHostState, modifier = Modifier.padding(bottom = toolbarHeightDp + 32.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    actionColor = MaterialTheme.colorScheme.onSecondary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }, topBar = {
            ReviewerTopBar(
                newCount = state.newCount,
                learnCount = state.learnCount,
                reviewCount = state.reviewCount,
                chosenAnswer = state.chosenAnswer,
                isMarked = state.isMarked,
                flag = state.flag,
                onToggleMark = { viewModel.onEvent(ReviewerEvent.ToggleMark) },
                onSetFlag = { viewModel.onEvent(ReviewerEvent.SetFlag(it)) },
                isAnswerShown = state.isAnswerShown
            ) { viewModel.onEvent(ReviewerEvent.UnanswerCard) }
        }) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection)
                    ),
            ) {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    val invertedTopCornersShape =
                        remember { InvertedTopCornersShape(cornerRadius = 32.dp) }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(1F)
                            .height(100.dp)
                            .fillMaxWidth(),
                        shape = invertedTopCornersShape,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {}

                    Flashcard(
                        baseUrl = state.baseUrl,
                        questionHtml = state.questionHtml,
                        answerHtml = state.answerHtml,
                        bodyClass = state.bodyClass,
                        isMediaAutoplayEnabled = state.isMediaAutoplayEnabled,
                        javascriptCommand = javascriptCommand,
                        onJavascriptCommandConsumed = viewModel::onJavascriptCommandConsumed,
                        onTap = { },
                        onLinkClick = {
                            viewModel.onEvent(ReviewerEvent.LinkClicked(it))
                        },
                        isAnswerShown = state.isAnswerShown,
                        toolbarHeight = (toolbarHeightDp + WhiteboardBottomBarOffset).value.toInt()
                    )

                    // Whiteboard canvas and toolbar
                    if (state.isWhiteboardEnabled && whiteboardViewModel != null) {
                        val toolbarAlignment by whiteboardViewModel.toolbarAlignment.collectAsStateWithLifecycle()

                        // Canvas padding based on toolbar alignment
                        val canvasPadding = when (toolbarAlignment) {
                            ToolbarAlignment.BOTTOM -> Modifier.padding(bottom = totalBottomPadding + WhiteboardBottomBarOffset)
                            ToolbarAlignment.LEFT -> Modifier.padding(start = WhiteboardToolbarWidth)
                            ToolbarAlignment.RIGHT -> Modifier.padding(end = WhiteboardToolbarWidth)
                        }
                        WhiteboardCanvas(
                            viewModel = whiteboardViewModel, modifier = canvasPadding
                        )

                        // Toolbar positioning
                        val composeAlignment = when (toolbarAlignment) {
                            ToolbarAlignment.BOTTOM -> Alignment.BottomCenter
                            ToolbarAlignment.LEFT -> Alignment.CenterStart
                            ToolbarAlignment.RIGHT -> Alignment.CenterEnd
                        }
                        val toolbarPadding = when (toolbarAlignment) {
                            ToolbarAlignment.BOTTOM -> Modifier
                                .offset(y = -ScreenOffset - toolbarHeightDp - 8.dp)
                                .padding(bottom = paddingValues.calculateBottomPadding())

                            ToolbarAlignment.LEFT -> Modifier.padding(
                                start = 8.dp
                            )

                            ToolbarAlignment.RIGHT -> Modifier.padding(
                                end = 8.dp
                            )
                        }
                        WhiteboardToolbar(
                            viewModel = whiteboardViewModel,
                            onBrushClick = { _, index ->
                                if (whiteboardViewModel.activeBrushIndex.value == index && !whiteboardViewModel.isEraserActive.value) {
                                    showBrushOptions = true
                                } else {
                                    whiteboardViewModel.setActiveBrush(index)
                                }
                            },
                            onBrushLongClick = { index ->
                                if (whiteboardViewModel.brushes.value.size > 1) {
                                    brushIndexToRemove = index
                                }
                            },
                            onAddBrush = {
                                showColorPickerDialog = true
                            },
                            onEraserClick = {
                                if (whiteboardViewModel.isEraserActive.value) {
                                    showEraserOptions = true
                                } else {
                                    whiteboardViewModel.enableEraser()
                                }
                            },
                            modifier = Modifier
                                .align(composeAlignment)
                                .then(toolbarPadding)
                                .onSizeChanged { whiteboardToolbarHeight = it.height })
                    }

                    // Voice Playback Toolbar
                    if (state.isVoicePlaybackEnabled && voicePlaybackViewModel != null) {
                        val voicePlaybackIsVisible by voicePlaybackViewModel.isVisible.collectAsStateWithLifecycle()
                        if (voicePlaybackIsVisible) {
                            VoicePlaybackToolbar(
                                viewModel = voicePlaybackViewModel,
                                onToggleRecording = {
                                    voicePlaybackViewModel.toggleRecording(currentContext)
                                },
                                onDismiss = {
                                    viewModel.onEvent(ReviewerEvent.ToggleVoicePlayback)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = -ScreenOffset - toolbarHeightDp - 16.dp)
                                    .padding(bottom = paddingValues.calculateBottomPadding())
                            )
                        }
                    }

                    AnswerButtons(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -ScreenOffset)
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .onSizeChanged { toolbarHeight = it.height },
                        isAnswerShown = state.isAnswerShown,
                        showAnswerFeedback = Prefs.showAnswerButtonBadges,
                        showTypeInAnswer = state.showTypeInAnswer,
                        typedAnswer = state.typedAnswer,
                        onTypedAnswerChanged = {
                            viewModel.onEvent(
                                ReviewerEvent.OnTypedAnswerChanged(it)
                            )
                        },
                        onShowAnswer = { viewModel.onEvent(ReviewerEvent.ShowAnswer) },
                        onRateCard = { viewModel.onEvent(ReviewerEvent.RateCard(it)) },
                        nextTimes = state.nextTimes,
                        onMoreOptionsClick = { showBottomSheet = true })

                    AnswerIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 16.dp),
                        feedback = state.answerFeedback,
                        onDismissed = { viewModel.onEvent(ReviewerEvent.AnswerFeedbackShown) })
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                val menuOptions =
                    remember(state.isWhiteboardEnabled, state.isVoicePlaybackEnabled) {
                        listOf(
                            Triple(
                            if (state.isWhiteboardEnabled) R.string.disable_whiteboard else R.string.enable_whiteboard,
                            Icons.Filled.Edit
                        ) {
                            viewModel.onEvent(ReviewerEvent.ToggleWhiteboard)
                        }, Triple(R.string.cardeditor_title_edit_card, Icons.Filled.EditNote) {
                            viewModel.onEvent(ReviewerEvent.EditCard)
                        }, Triple(R.string.menu_edit_tags, Icons.AutoMirrored.Filled.Label) {
                            viewModel.onEvent(ReviewerEvent.EditTags)
                        }, Triple(R.string.menu_bury_card, Icons.Filled.VisibilityOff) {
                            viewModel.onEvent(ReviewerEvent.BuryCard)
                        }, Triple(R.string.menu_suspend_card, Icons.Filled.Pause) {
                            viewModel.onEvent(ReviewerEvent.SuspendCard)
                        }, Triple(R.string.menu_delete_note, Icons.Filled.Delete) {
                            viewModel.onEvent(ReviewerEvent.DeleteNote)
                        }, Triple(R.string.card_editor_reschedule_card, Icons.Filled.Schedule) {
                            viewModel.onEvent(ReviewerEvent.RescheduleCard)
                        }, Triple(R.string.replay_media, Icons.Filled.Replay) {
                            viewModel.onEvent(ReviewerEvent.ReplayMedia)
                        }, Triple(
                            if (state.isVoicePlaybackEnabled) R.string.menu_disable_voice_playback else R.string.menu_enable_voice_playback,
                            Icons.Filled.RecordVoiceOver
                        ) {
                            viewModel.onEvent(ReviewerEvent.ToggleVoicePlayback)
                        }, Triple(R.string.deck_options, Icons.Filled.Tune) {
                            viewModel.onEvent(ReviewerEvent.DeckOptions)
                        })
                    }
                menuOptions.forEach { (textRes, icon, action) ->
                    ListItem(
                        headlineContent = { Text(stringResource(textRes)) },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                }
                            }
                            action()
                        })
                }
            }
        }

        // Color picker dialog for adding new brush
        if (showColorPickerDialog && whiteboardViewModel != null) {
            val defaultColor by whiteboardViewModel.brushColor.collectAsStateWithLifecycle()

            ColorPickerDialog(
                defaultColor = defaultColor,
                showAlpha = true,
                onColorPicked = { color ->
                    whiteboardViewModel.addBrush(color)
                    showColorPickerDialog = false
                },
                onDismiss = { showColorPickerDialog = false })
        }

        // Brush removal confirmation dialog
        if (brushIndexToRemove != null && whiteboardViewModel != null) {
            AlertDialog(
                onDismissRequest = { brushIndexToRemove = null },
                text = { Text(stringResource(R.string.whiteboard_remove_brush_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            brushIndexToRemove?.let { index ->
                                whiteboardViewModel.removeBrush(index)
                            }
                            brushIndexToRemove = null
                        }) {
                        Text(stringResource(R.string.dialog_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { brushIndexToRemove = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                })
        }

        // Brush options dialog
        if (showBrushOptions && whiteboardViewModel != null) {
            BrushOptionsDialog(
                viewModel = whiteboardViewModel, onDismissRequest = { showBrushOptions = false })
        }

        // Eraser options dialog
        if (showEraserOptions && whiteboardViewModel != null) {
            EraserOptionsDialog(
                viewModel = whiteboardViewModel, onDismissRequest = { showEraserOptions = false })
        }

        // Tags dialog
        if (showTagsDialog) {
            TagsDialog(
                onDismissRequest = { viewModel.dismissTagsDialog() },
                onConfirm = { checked, _ ->
                    viewModel.updateNoteTags(checked)
                },
                allTags = tagsState,
                initialSelection = currentNoteTags,
                deckTags = deckTags,
                initialFilterByDeck = filterByDeck,
                onFilterByDeckChanged = { viewModel.setFilterByDeck(it) },
                title = stringResource(R.string.card_details_tags),
                confirmButtonText = stringResource(R.string.dialog_ok),
                showFilterByDeckToggle = true,
                onAddTag = { viewModel.registerNewTag(it) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnswerIndicator(
    modifier: Modifier = Modifier, feedback: AnswerFeedback?, onDismissed: () -> Unit
) {
    var lastFeedback by remember { mutableStateOf<AnswerFeedback?>(null) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    LaunchedEffect(feedback) {
        if (feedback != null) {
            lastFeedback = feedback
            delay(AnswerIndicatorDuration)
            currentOnDismissed()
        }
    }

    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()),
        modifier = modifier
    ) {
        // Use the cached lastFeedback to avoid disappearing mid-animation
        lastFeedback?.let { current ->
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    text = stringResource(current.rating.toResId()),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun CardAnswer.Rating.toResId(): Int = when (this) {
    CardAnswer.Rating.AGAIN -> R.string.ease_button_again
    CardAnswer.Rating.HARD -> R.string.ease_button_hard
    CardAnswer.Rating.GOOD -> R.string.ease_button_good
    CardAnswer.Rating.EASY -> R.string.ease_button_easy
    else -> R.string.unknown_rating
}