/*
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.TooltipCompat
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import anki.frontend.SetSchedulingStatesRequest
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.generateTempAudioFile
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.isAudioRecordingSaved
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.isRecording
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.setEditorStatus
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.tempAudioPath
import com.ichi2.anki.multimedia.audio.AudioRecordingController.RecordingState
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer.Companion.ANKIDROID_JS_PREFIX
import com.ichi2.anki.pages.AnkiServer.Companion.ANKI_PREFIX
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ActionButtons
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.FullScreenMode.Companion.isFullScreenReview
import com.ichi2.anki.reviewer.ReviewerConstants
import com.ichi2.anki.reviewer.ReviewerEffect
import com.ichi2.anki.reviewer.ReviewerEvent
import com.ichi2.anki.reviewer.ReviewerUi
import com.ichi2.anki.reviewer.ReviewerViewModel
import com.ichi2.anki.reviewer.WhiteboardController
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.NoteService.toggleMark
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardViewModel
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.themes.Themes
import com.ichi2.utils.HandlerUtils.executeFunctionWithDelay
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.utils.cancelable
import com.ichi2.utils.iconAlpha
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.tintOverflowMenuIcons
import com.ichi2.utils.title
import com.ichi2.widget.WidgetStatus.updateInBackground
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

@NeedsTest("#14709: Timebox shouldn't appear instantly when the Reviewer is opened")
open class Reviewer :
    AbstractFlashcardViewer(),
    ReviewerUi {
    private var queueState: CurrentQueueState? = null
    private val customSchedulingKey = TimeManager.time.intTimeMS().toString()
    private var hasDrawerSwipeConflicts = false

    // Whiteboard controller
    @VisibleForTesting
    internal lateinit var whiteboardController: WhiteboardController
    private val whiteboardViewModel: WhiteboardViewModel by viewModels {
        WhiteboardViewModel.factory(sharedPrefs())
    }
    private var prefFullscreenReview = false

    // A flag that determines if the SchedulingStates in CurrentQueueState are
    // safe to persist in the database when answering a card. This is used to
    // ensure that the custom JS scheduler has persisted its SchedulingStates
    // back to the Reviewer before we save it to the database. If the custom
    // scheduler has not been configured, then it is safe to immediately set
    // this to true
    //
    // This flag should be set to false when we show the front of the card
    // and only set to true once we know the custom scheduler has finished its
    // execution, or set to true immediately if the custom scheduler has not
    // been configured
    private var statesMutated = false

    // Record Audio
    private var isMicToolBarVisible = false

    /** Controller for 'Voice Playback' feature */
    private var audioRecordingController: AudioRecordingController? = null
    private var isAudioUIInitialized = false
    private lateinit var micToolBarLayer: LinearLayout

    // ETA
    private var eta = 0
    private var prefShowETA = false

    // Preferences from the collection
    private var showRemainingCardCount = false
    private var stopTimerOnAnswer = false
    private val actionButtons = ActionButtons()

    private lateinit var addNoteLauncher: ActivityResultLauncher<Intent>

    private val flagItemIds = mutableSetOf<Int>()

    @VisibleForTesting
    internal val viewModel: ReviewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        addNoteLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
                FlashCardViewerResultCallback(),
            )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val composeView = ComposeView(this)
        setContentView(composeView)

        if (!ensureStoragePermissions()) {
            return
        }

        composeView.setContent {
            AnkiDroidTheme {
                com.ichi2.anki.reviewer.compose
                    .ReviewerContent(viewModel, whiteboardViewModel)
            }
        }

        lifecycleScope.launch {
            viewModel.effect.collectLatest { effect ->
                when (effect) {
                    is ReviewerEffect.NavigateToDeckPicker -> finish()
                    is ReviewerEffect.NavigateToEditCard -> {
                        // Handled in Compose
                    }

                    is ReviewerEffect.PerformRedo -> redo()
                    is ReviewerEffect.ToggleWhiteboard -> toggleWhiteboard()
                    is ReviewerEffect.ShowTagsDialog -> {
                        currentCard = effect.card
                        showTagsDialog()
                    }

                    is ReviewerEffect.ShowDeleteNoteDialog -> {
                        currentCard = effect.card
                        showDeleteNoteDialog()
                    }

                    is ReviewerEffect.ShowDueDateDialog -> {
                        currentCard = effect.card
                        showDueDateDialog()
                    }

                    is ReviewerEffect.ReplayMedia -> {
                        currentCard = effect.card
                        playMedia(true)
                    }

                    is ReviewerEffect.ToggleVoicePlayback -> openOrToggleMicToolbar()
                    is ReviewerEffect.NavigateToDeckOptions -> {
                        val i =
                            com.ichi2.anki.pages.DeckOptions.getIntent(
                                this@Reviewer,
                                getColUnsafe.decks.current().id,
                            )
                        deckOptionsLauncher.launch(i)
                    }

                    else -> {}
                }
            }
        }

        // Initialize whiteboard controller directly (collection is already loaded from DeckPicker)
        whiteboardController = WhiteboardController(this, whiteboardViewModel, viewModel)
        whiteboardController.initialize()
    }

    override fun onResume() {
        super.onResume()
        // answerField?.focusWithKeyboard() logic handled by Compose
    }

    override fun onDestroy() {
        if (::micToolBarLayer.isInitialized) {
            micToolBarLayer.removeAllViews()
        }
        super.onDestroy()
    }

    override fun recreateWebView() {
        super.recreateWebView()
        setRenderWorkaround(this)
    }

    @NeedsTest("is hidden if marked is on app bar")
    @NeedsTest("is not hidden if marked is not on app bar")
    @NeedsTest("is not hidden if marked is on app bar and fullscreen is enabled")
    override fun shouldDisplayMark(): Boolean {
        val markValue = super.shouldDisplayMark()
        if (!markValue) {
            return false
        }

        // If we don't know: assume it's not shown
        val shownAsToolbarButton =
            actionButtons.findMenuItem(ActionButtons.RES_MARK)?.isActionButton == true
        return !shownAsToolbarButton || prefFullscreenReview
    }

    protected open fun onMark(card: Card?) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            toggleMark(card.note(getColUnsafe), handler = this@Reviewer)
            refreshActionBar()
        }
    }

    protected open fun onFlag(
        card: Card?,
        flag: Flag,
    ) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            card.setUserFlag(flag.code)
            undoableOp(this@Reviewer) {
                setUserFlagForCards(listOf(card.id), flag)
            }
            refreshActionBar()
        }
    }

    private fun selectDeckFromExtra() {
        val extras = intent.extras
        if (extras == null || !extras.containsKey(EXTRA_DECK_ID)) {
            // deckId is not set, load default
            return
        }
        val did = extras.getLong(EXTRA_DECK_ID, Long.MIN_VALUE)
        Timber.d("selectDeckFromExtra() with deckId = %d", did)

        // deckId does not exist, load default
        if (getColUnsafe.decks.getLegacy(did) == null) {
            Timber.w("selectDeckFromExtra() deckId '%d' doesn't exist", did)
            return
        }
        // Select the deck
        getColUnsafe.decks.select(did)
    }

    override fun getContentViewAttr(fullscreenMode: com.ichi2.anki.reviewer.FullScreenMode): Int =
        when (fullscreenMode) {
            com.ichi2.anki.reviewer.FullScreenMode.BUTTONS_ONLY -> R.layout.reviewer_fullscreen
            com.ichi2.anki.reviewer.FullScreenMode.FULLSCREEN_ALL_GONE -> R.layout.reviewer_fullscreen_noanswers
            com.ichi2.anki.reviewer.FullScreenMode.BUTTONS_AND_MENU -> R.layout.reviewer
        }

    public override fun fitsSystemWindows(): Boolean = !fullscreenMode.isFullScreenReview()

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        if (Intent.ACTION_VIEW == intent.action) {
            Timber.d("onCreate() :: received Intent with action = %s", intent.action)
            selectDeckFromExtra()
        }
        // Load the first card and start reviewing. Uses the answer card
        // task to load a card, but since we send null
        // as the card to answer, no card will be answered.
        lifecycleScope.launch {
            val isMicToolbarEnabled =
                withContext(ioDispatcher) { MetaDB.getMicToolbarState(this@Reviewer, parentDid) }
            if (isMicToolbarEnabled) {
                openMicToolbar()
            }
            viewModel.onEvent(ReviewerEvent.OnVoicePlaybackStateChanged(isMicToolBarVisible))

            withCol { startTimebox() }
            updateCardAndRedraw()
        }
        disableDrawerSwipeOnConflicts()

        setRenderWorkaround(this)
    }

    fun redo() {
        launchCatchingTask { redoAndShowSnackbar(ReviewerConstants.ACTION_SNACKBAR_DURATION_MS) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        Flag.entries.find { it.id == item.itemId }?.let { flag ->
            Timber.i("Reviewer:: onOptionItemSelected Flag - ${flag.name} clicked")
            onFlag(currentCard, flag)
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                Timber.i("Reviewer:: Home button pressed")
                closeReviewer(RESULT_OK)
            }

            R.id.action_undo -> {
                Timber.i("Reviewer:: Undo button pressed")
                if (::whiteboardController.isInitialized && whiteboardController.isVisible && whiteboardController.canUndo()) {
                    whiteboardController.undo()
                } else {
                    undo()
                }
            }

            R.id.action_redo -> {
                Timber.i("Reviewer:: Redo button pressed")
                redo()
            }

            R.id.action_reset_card_progress -> {
                Timber.i("Reviewer:: Reset progress button pressed")
                showResetCardDialog()
            }

            R.id.action_mark_card -> {
                Timber.i("Reviewer:: Mark button pressed")
                onMark(currentCard)
            }

            R.id.action_replay -> {
                Timber.i("Reviewer:: Replay media button pressed (from menu)")
                playMedia(doMediaReplay = true)
            }

            R.id.action_toggle_mic_tool_bar -> {
                Timber.i("Reviewer:: Voice playback visibility set to %b", !isMicToolBarVisible)
                // Check permission to record and request if not granted
                openOrToggleMicToolbar()
            }

            R.id.action_tag -> {
                Timber.i("Reviewer:: Tag button pressed")
                showTagsDialog()
            }

            R.id.action_edit -> {
                Timber.i("Reviewer:: Edit note button pressed")
                editCard()
            }

            R.id.action_bury_card -> buryCard()
            R.id.action_bury_note -> buryNote()
            R.id.action_suspend_card -> suspendCard()
            R.id.action_suspend_note -> suspendNote()
            R.id.action_reschedule_card -> showDueDateDialog()
            R.id.action_delete -> {
                Timber.i("Reviewer:: Delete note button pressed")
                showDeleteNoteDialog()
            }

            R.id.action_toggle_auto_advance -> {
                Timber.i("Reviewer:: Toggle Auto Advance button pressed")
                toggleAutoAdvance()
            }

            R.id.action_change_whiteboard_pen_color -> {
                Timber.i("Reviewer:: Pen Color button pressed")
                changeWhiteboardPenColor()
            }

            R.id.action_save_whiteboard -> {
                Timber.i("Reviewer:: Save whiteboard button pressed")
                if (::whiteboardController.isInitialized) {
                    whiteboardController.saveToFile()
                }
            }

            R.id.action_clear_whiteboard -> {
                Timber.i("Reviewer:: Clear whiteboard button pressed")
                clearWhiteboard()
            }

            R.id.action_hide_whiteboard -> { // toggle whiteboard visibility
                if (::whiteboardController.isInitialized) {
                    Timber.i(
                        "Reviewer:: Whiteboard visibility set to %b",
                        !whiteboardController.isVisible,
                    )
                    whiteboardController.setVisibility(!whiteboardController.isVisible)
                    refreshActionBar()
                }
            }

            R.id.action_toggle_eraser -> { // toggle eraser mode
                Timber.i("Reviewer:: Eraser button pressed")
                toggleEraser()
            }

            R.id.action_toggle_stylus -> { // toggle stylus mode
                Timber.i("Reviewer:: Stylus set to %b", !whiteboardViewModel.isStylusOnlyMode.value)
                whiteboardViewModel.toggleStylusOnlyMode()
                refreshActionBar()
            }

            R.id.action_toggle_whiteboard -> {
                toggleWhiteboard()
            }

            R.id.action_open_deck_options -> {
                Timber.i("Reviewer:: Opening deck options")
                val i =
                    com.ichi2.anki.pages.DeckOptions.getIntent(
                        this,
                        getColUnsafe.decks.current().id,
                    )
                deckOptionsLauncher.launch(i)
            }

            R.id.action_select_tts -> {
                Timber.i("Reviewer:: Select TTS button pressed")
                showSelectTtsDialogue()
            }

            R.id.action_add_note_reviewer -> {
                Timber.i("Reviewer:: Add note button pressed")
                addNote()
            }

            R.id.action_card_info -> {
                Timber.i("Card Viewer:: Card Info")
                openCardInfo()
            }

            in USER_ACTION_MENU_IDS -> {
                val actionNumber = USER_ACTION_MENU_IDS.indexOf(item.itemId) + 1
                userAction(actionNumber)
            }

            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    public override fun toggleWhiteboard() {
        Timber.d(
            "toggleWhiteboard() called, isInitialized: %s",
            ::whiteboardController.isInitialized,
        )
        if (::whiteboardController.isInitialized) {
            whiteboardController.toggle()
        } else {
            Timber.w("toggleWhiteboard() called but whiteboardController not initialized")
        }
    }

    public override fun toggleEraser() {
        if (::whiteboardController.isInitialized) {
            whiteboardController.toggleEraser()
        }
    }

    public override fun clearWhiteboard() {
        if (::whiteboardController.isInitialized) {
            whiteboardController.clear()
        }
    }

    public override fun changeWhiteboardPenColor() {
        if (::whiteboardController.isInitialized) {
            whiteboardController.changePenColor()
        }
    }

    override fun replayVoice() {
        if (!openMicToolbar()) {
            return
        }
        if (isAudioRecordingSaved) {
            audioRecordingController?.playPausePlayer()
        } else {
            return
        }
    }

    override fun recordVoice() {
        if (!openMicToolbar()) {
            return
        }
        audioRecordingController?.toggleToRecorder()
    }

    override fun saveRecording() {
        if (!openMicToolbar()) {
            return
        }
        if (isRecording) {
            audioRecordingController?.toggleSave()
        } else {
            return
        }
    }

    override fun updateForNewCard() {
        Timber.i("updateForNewCard")
        if (::whiteboardController.isInitialized) {
            whiteboardController.updateForNewCard()
        }
        audioRecordingController?.updateUIForNewCard()
    }

    override fun closeReviewer(result: Int) {
        // Stop the mic recording if still pending
        if (isRecording) audioRecordingController?.stopAndSaveRecording()

        // Remove the temporary audio file
        tempAudioPath?.let { tempAudioPathToDelete ->
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete()
            }
        }
        super.closeReviewer(result)
    }

    /**
     *
     * @return Whether the mic toolbar is usable
     */
    @VisibleForTesting
    fun openMicToolbar(): Boolean {
        if (micToolBarLayer.visibility != View.VISIBLE || audioRecordingController == null) {
            openOrToggleMicToolbar()
        }
        return audioRecordingController != null
    }

    private fun openOrToggleMicToolbar() {
        if (!canRecordAudio(this)) {
            Timber.i("requesting 'RECORD_AUDIO' permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                ReviewerConstants.REQUEST_AUDIO_PERMISSION,
            )
        } else {
            toggleMicToolBar()
        }
    }

    private fun toggleMicToolBar() {
        Timber.i("toggle mic toolbar")
        tempAudioPath = generateTempAudioFile(this)
        if (isMicToolBarVisible) {
            micToolBarLayer.visibility = View.GONE
        } else {
            setEditorStatus(false)
            if (!isAudioUIInitialized) {
                try {
                    audioRecordingController = AudioRecordingController(context = this)
                    audioRecordingController?.createUI(
                        this,
                        micToolBarLayer,
                        initialState = RecordingState.ImmediatePlayback.CLEARED,
                        R.layout.activity_audio_recording_reviewer,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "unable to add the audio recorder to toolbar")
                    CrashReportService.sendExceptionReport(e, "Unable to create recorder tool bar")
                    showThemedToast(
                        this,
                        this
                            .getText(R.string.multimedia_editor_audio_view_create_failed)
                            .toString(),
                        true,
                    )
                }
                isAudioUIInitialized = true
            }
            micToolBarLayer.visibility = View.VISIBLE
        }
        isMicToolBarVisible = !isMicToolBarVisible
        viewModel.onEvent(ReviewerEvent.OnVoicePlaybackStateChanged(isMicToolBarVisible))

        lifecycleScope.launch(ioDispatcher) {
            MetaDB.storeMicToolbarState(this@Reviewer, parentDid, isMicToolBarVisible)
        }

        refreshActionBar()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ReviewerConstants.REQUEST_AUDIO_PERMISSION &&
            permissions.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Get get audio record permission, so we can create the record tool bar
            toggleMicToolBar()
        }
    }

    private fun showDueDateDialog() =
        launchCatchingTask {
            Timber.i("showing due date dialog")
            val cardId = currentCardId ?: return@launchCatchingTask
            val dialog = SetDueDateDialog.newInstance(listOf(cardId))
            showDialogFragment(dialog)
        }

    private fun showResetCardDialog() {
        Timber.i("showResetCardDialog() Reset progress button pressed")
        showDialogFragment(ForgetCardsDialog())
    }

    fun addNote(fromGesture: Gesture? = null) {
        val animation = getAnimationTransitionFromGesture(fromGesture)
        val inverseAnimation = getInverseTransition(animation)
        Timber.i("launching 'add note'")
        val intent = NoteEditorLauncher.AddNoteFromReviewer(inverseAnimation).toIntent(this)
        addNoteLauncher.launch(intent)
    }

    @NeedsTest("Starting animation from swipe is inverse to the finishing one")
    protected fun openCardInfo(fromGesture: Gesture? = null) {
        if (currentCard == null) {
            showSnackbar(
                getString(R.string.multimedia_editor_something_wrong),
                Snackbar.LENGTH_SHORT,
            )
            return
        }
        Timber.i("opening card info")
        val intent =
            CardInfoDestination(
                currentCard!!.id,
                TR.cardStatsCurrentCard(TR.decksStudy()),
            ).toIntent(this)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        intent.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        startActivityWithAnimation(intent, animation)
    }

    // Related to https://github.com/ankidroid/Anki-Android/pull/11061#issuecomment-1107868455
    @NeedsTest("Order of operations needs Testing around Menu (Overflow) Icons and their colors.")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        // NOTE: This is called every time a new question is shown via invalidate options menu
        menuInflater.inflate(R.menu.reviewer, menu)
        menu.findItem(R.id.action_flag).subMenu?.let { subMenu -> setupFlags(subMenu) }
        displayIcons(menu)
        actionButtons.setCustomButtonsStatus(menu)
        val alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
        val markCardIcon = menu.findItem(R.id.action_mark_card)
        if (currentCard != null && isMarked(getColUnsafe, currentCard!!.note(getColUnsafe))) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white)
        } else {
            markCardIcon.setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_border_white)
        }
        markCardIcon.iconAlpha = alpha

        val flagIcon = menu.findItem(R.id.action_flag)
        if (flagIcon != null) {
            if (currentCard != null) {
                val flag = currentCard!!.flag
                flagIcon.setIcon(flag.drawableRes)
                if (flag == Flag.NONE && actionButtons.status.flagsIsOverflown()) {
                    val flagColor =
                        ThemeUtils.getThemeAttrColor(this, android.R.attr.colorControlNormal)
                    flagIcon.icon?.mutate()?.setTint(flagColor)
                }
            }
        }

        // Anki Desktop Translations
        menu.findItem(R.id.action_reschedule_card).title =
            TR.actionsSetDueDate().toSentenceCase(this, R.string.sentence_set_due_date)

        // Undo button
        @DrawableRes val undoIconId: Int
        val undoEnabled: Boolean
        val whiteboardIsShownAndHasStrokes =
            ::whiteboardController.isInitialized && whiteboardController.isVisible && whiteboardController.canUndo()
        if (whiteboardIsShownAndHasStrokes) {
            undoIconId = R.drawable.ic_arrow_u_left_top
            undoEnabled = true
        } else {
            undoIconId = R.drawable.ic_undo_white
            undoEnabled = colIsOpenUnsafe() && getColUnsafe.undoAvailable()
            // Eraser state is now managed by WhiteboardViewModel
        }
        val alphaUndo = Themes.ALPHA_ICON_ENABLED_LIGHT
        val undoIcon = menu.findItem(R.id.action_undo)
        undoIcon.setIcon(undoIconId)
        undoIcon.setEnabled(undoEnabled).iconAlpha = alphaUndo
        undoIcon.actionView!!.isEnabled = undoEnabled
        val toggleEraserIcon = menu.findItem((R.id.action_toggle_eraser))
        if (colIsOpenUnsafe()) { // Required mostly because there are tests where `col` is null
            if (whiteboardIsShownAndHasStrokes) {
                // Make Undo action title to whiteboard Undo specific one
                undoIcon.title = resources.getString(R.string.undo_action_whiteboard_last_stroke)

                // Show whiteboard Eraser action button
                if (!actionButtons.status.toggleEraserIsDisabled()) {
                    toggleEraserIcon.isVisible = true
                }
            } else {
                // Disable whiteboard eraser action button (eraser state is managed by ViewModel)

                if (getColUnsafe.undoAvailable()) {
                    // set the undo title to a named action ('Undo Answer Card' etc...)
                    undoIcon.title = getColUnsafe.undoLabel()
                } else {
                    // In this case, there is no object word for the verb, "Undo",
                    // so in some languages such as Japanese, which have pre/post-positional particle with the object,
                    // we need to use the string for just "Undo" instead of the string for "Undo %s".
                    undoIcon.title = resources.getString(R.string.undo)
                    undoIcon.iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                }
            }

            // Set the undo tooltip, only if the icon is shown in the action bar
            undoIcon.actionView?.let { undoView ->
                TooltipCompat.setTooltipText(undoView, undoIcon.title)
            }

            menu.findItem(R.id.action_redo)?.apply {
                if (getColUnsafe.redoAvailable()) {
                    title = getColUnsafe.redoLabel()
                    iconAlpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                    isEnabled = true
                } else {
                    setTitle(R.string.redo)
                    iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                    isEnabled = false
                }
            }
        }
        menu.findItem(R.id.action_toggle_auto_advance).apply {
            if (actionButtons.status.autoAdvanceMenuIsNeverShown()) return@apply
            // always show if enabled (to allow disabling)
            // otherwise show if it will have an effect
            isVisible = automaticAnswer.isEnabled() || automaticAnswer.isUsable()
        }

        val toggleWhiteboardIcon = menu.findItem(R.id.action_toggle_whiteboard)
        val toggleStylusIcon = menu.findItem(R.id.action_toggle_stylus)
        val hideWhiteboardIcon = menu.findItem(R.id.action_hide_whiteboard)
        val changePenColorIcon = menu.findItem(R.id.action_change_whiteboard_pen_color)
        // White board button
        if (::whiteboardController.isInitialized && whiteboardController.isEnabled) {
            // Configure the whiteboard related items in the action bar
            toggleWhiteboardIcon.setTitle(R.string.disable_whiteboard)
            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggleWhiteboardIcon.isVisible = true
            if (!actionButtons.status.toggleStylusIsDisabled()) {
                toggleStylusIcon.isVisible = true
            }
            if (!actionButtons.status.hideWhiteboardIsDisabled()) {
                hideWhiteboardIcon.isVisible = true
            }
            if (!actionButtons.status.clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).isVisible = true
            }
            if (!actionButtons.status.saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).isVisible = true
            }
            if (!actionButtons.status.whiteboardPenColorIsDisabled()) {
                changePenColorIcon.isVisible = true
            }
            val whiteboardIcon =
                ContextCompat
                    .getDrawable(applicationContext, R.drawable.ic_gesture_white)!!
                    .mutate()
            val stylusIcon =
                ContextCompat.getDrawable(this, R.drawable.ic_gesture_stylus)!!.mutate()
            val whiteboardColorPaletteIcon =
                ContextCompat
                    .getDrawable(applicationContext, R.drawable.ic_color_lens_white_24dp)!!
                    .mutate()
            val eraserIcon =
                ContextCompat.getDrawable(applicationContext, R.drawable.ic_eraser)!!.mutate()
            if (::whiteboardController.isInitialized && whiteboardController.isVisible) {
                // "hide whiteboard" icon
                whiteboardIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.hide_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                // eraser icon
                toggleEraserIcon.icon = eraserIcon
                if (whiteboardViewModel.isEraserActive.value) {
                    toggleEraserIcon.setTitle(R.string.disable_eraser)
                } else { // default
                    toggleEraserIcon.setTitle(R.string.enable_eraser)
                }
                // whiteboard editor icon
                changePenColorIcon.icon = whiteboardColorPaletteIcon
                if (whiteboardViewModel.isStylusOnlyMode.value) {
                    toggleStylusIcon.setTitle(R.string.disable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                } else {
                    toggleStylusIcon.setTitle(R.string.enable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                }
                toggleStylusIcon.icon = stylusIcon
            } else {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.show_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                toggleStylusIcon.isEnabled = false
                toggleStylusIcon.icon = stylusIcon
                changePenColorIcon.isEnabled = false
                changePenColorIcon.icon = whiteboardColorPaletteIcon
            }
        } else {
            toggleWhiteboardIcon.setTitle(R.string.enable_whiteboard)
        }
        if (colIsOpenUnsafe() && getColUnsafe.decks.isFiltered(parentDid)) {
            menu.findItem(R.id.action_open_deck_options).isVisible = false
        }
        if (tts.enabled && !actionButtons.status.selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).isVisible = true
        }
        if (!suspendNoteAvailable() && !actionButtons.status.suspendIsDisabled()) {
            menu.findItem(R.id.action_suspend).isVisible = false
            menu.findItem(R.id.action_suspend_card).isVisible = true
        }
        if (!buryNoteAvailable() && !actionButtons.status.buryIsDisabled()) {
            menu.findItem(R.id.action_bury).isVisible = false
            menu.findItem(R.id.action_bury_card).isVisible = true
        }

        val voicePlaybackIcon = menu.findItem(R.id.action_toggle_mic_tool_bar)
        if (isMicToolBarVisible) {
            voicePlaybackIcon.setTitle(R.string.menu_disable_voice_playback)
            // #18477: always show 'disable', even if 'enable' was invisible
            voicePlaybackIcon.isVisible = true
        } else {
            voicePlaybackIcon.setTitle(R.string.menu_enable_voice_playback)
        }

        increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        tintOverflowMenuIcons(menu, skipIf = { isFlagItem(it) })

        return super.onCreateOptionsMenu(menu)
    }

    private fun setupFlags(subMenu: SubMenu) {
        lifecycleScope.launch {
            for ((flag, displayName) in Flag.queryDisplayNames()) {
                subMenu.findItem(flag.id).title = displayName
                flagItemIds.add(flag.id)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun displayIcons(menu: Menu) {
        try {
            if (menu is MenuBuilder) {
                // Use reflection to bypass package-private visibility
                val method =
                    menu.javaClass.getDeclaredMethod(
                        "setOptionalIconsVisible",
                        Boolean::class.javaPrimitiveType,
                    )
                method.isAccessible = true
                method.invoke(menu, true)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        } catch (e: Error) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        }
    }

    private fun isFlagItem(menuItem: MenuItem): Boolean = flagItemIds.contains(menuItem.itemId)

    override fun canAccessScheduler(): Boolean = true

    override fun performReload() {
        launchCatchingTask { updateCardAndRedraw() }
    }

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // explicitly do not call super
        action.execute(this)
    }

    override fun restorePreferences(): SharedPreferences {
        val preferences = super.restorePreferences()
        prefShowETA = preferences.getBoolean("showETA", false)
        prefFullscreenReview = isFullScreenReview(preferences)
        actionButtons.setup(preferences)
        return preferences
    }

    override fun fillFlashcard() {
        super.fillFlashcard()
        if (::whiteboardController.isInitialized && !isDisplayingAnswer && whiteboardController.isVisible) {
            whiteboardController.clear()
        }
    }

    override fun onPageFinished(view: WebView) {
        super.onPageFinished(view)
        if (!displayAnswer) {
            runStateMutationHook()
        }
    }

    override suspend fun updateCurrentCard() {
        val state =
            withCol {
                sched.currentQueueState()?.apply {
                    topCard.renderOutput(this@withCol, reload = true)
                }
            }
        state?.timeboxReached?.let { dealWithTimeBox(it) }
        currentCard = state?.topCard
        queueState = state
    }

    override suspend fun answerCardInner(rating: Rating) {
        val state = queueState ?: return
        val card = currentCard ?: return
        Timber.d("answerCardInner: ${card.id} $rating")
        undoableOp(this) {
            sched.answerCard(state, rating)
        }
    }

    private suspend fun dealWithTimeBox(timebox: Collection.TimeboxReached) {
        val nCards = timebox.reps
        val nMins = timebox.secs / 60
        val mins = resources.getQuantityString(R.plurals.in_minutes, nMins, nMins)
        val timeboxMessage =
            resources.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins)
        suspendCancellableCoroutine { coroutines ->
            Timber.i("Showing timebox reached dialog")
            AlertDialog.Builder(this).show {
                title(R.string.timebox_reached_title)
                message(text = timeboxMessage)
                positiveButton(R.string.dialog_continue) {
                    coroutines.resume(Unit)
                }
                negativeButton(text = TR.studyingFinish()) {
                    coroutines.resume(Unit)
                    finish()
                }
                cancelable(true)
                setOnCancelListener {
                    coroutines.resume(Unit)
                }
            }
        }
    }

    override fun displayCardQuestion() {
        statesMutated = false
        // show timer, if activated in the deck's preferences
        super.displayCardQuestion()
    }

    @VisibleForTesting
    override fun displayCardAnswer() {
        if (queueState?.customSchedulingJs?.isEmpty() == true) {
            statesMutated = true
        }
        if (!statesMutated) {
            executeFunctionWithDelay(ReviewerConstants.STATE_MUTATION_RETRY_DELAY_MS) { displayCardAnswer() }
            return
        }

        super.displayCardAnswer()
    }

    private fun runStateMutationHook() {
        val state = queueState ?: return
        if (state.customSchedulingJs.isEmpty()) {
            statesMutated = true
            return
        }
        val key = customSchedulingKey
        val js = state.customSchedulingJs
        webView?.evaluateJavascript(
            """
        anki.mutateNextCardStates('$key', async (states, customData, ctx) => { $js })
            .catch(err => { console.log(err); window.location.href = "state-mutation-error:"; });
""",
        ) { result ->
            if ("null" == result) {
                // eval failed, usually a syntax error
                // Note, we get "null" (string) and not null
                statesMutated = true
            }
        }
    }

    override fun onStateMutationError() {
        super.onStateMutationError()
        statesMutated = true
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && colIsOpenUnsafe()) {
            updateInBackground(this)
        }
    }

    override fun initControls() {
        super.initControls()
        if (::whiteboardController.isInitialized && whiteboardController.isEnabled) {
            whiteboardController.setVisibility(whiteboardController.isVisible)
        }
    }

    override fun executeCommand(
        which: ViewerCommand,
        fromGesture: Gesture?,
    ): Boolean {
        // Handle flag toggle commands via lookup
        VIEWER_COMMAND_TO_FLAG[which]?.let { flag ->
            toggleFlag(flag)
            return true
        }

        // Handle user action commands via lookup
        VIEWER_COMMAND_TO_USER_ACTION[which]?.let { actionNumber ->
            userAction(actionNumber)
            return true
        }

        when (which) {
            ViewerCommand.UNSET_FLAG -> {
                onFlag(currentCard, Flag.NONE)
                return true
            }

            ViewerCommand.MARK -> {
                onMark(currentCard)
                return true
            }

            ViewerCommand.REDO -> {
                redo()
                return true
            }

            ViewerCommand.ADD_NOTE -> {
                addNote(fromGesture)
                return true
            }

            ViewerCommand.CARD_INFO -> {
                openCardInfo(fromGesture)
                return true
            }

            ViewerCommand.RESCHEDULE_NOTE -> {
                showDueDateDialog()
                return true
            }

            ViewerCommand.TOGGLE_AUTO_ADVANCE -> {
                toggleAutoAdvance()
                return true
            }

            else -> return super.executeCommand(which, fromGesture)
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(1, 2, 3, 4, 5, 6, 7, 8, 9)
    annotation class UserAction

    private fun userAction(
        @UserAction number: Int,
    ) {
        Timber.v("userAction%d", number)
        loadUrlInViewer("javascript: userAction($number);")
    }

    private fun toggleFlag(flag: Flag) {
        val card = currentCard ?: return
        if (card.flag == flag) {
            Timber.i("Toggle flag: unsetting flag")
            onFlag(card, Flag.NONE)
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag.code)
            onFlag(card, flag)
        }
    }

    override fun restoreCollectionPreferences(col: Collection) {
        super.restoreCollectionPreferences(col)
        showRemainingCardCount = col.config.get("dueCounts") ?: true
        stopTimerOnAnswer = col.decks.configDictForDeckId(col.decks.current().id).stopTimerOnAnswer
    }

    override fun onSingleTap(): Boolean = false

    override fun onCardEdited(card: Card) {
        super.onCardEdited(card)
        if (::whiteboardController.isInitialized && whiteboardController.isEnabled) {
            whiteboardController.clear()
        }
        if (!isDisplayingAnswer) {
            // Editing the card may reuse mCurrentCard. If so, the scheduler won't call startTimer() to reset the timer
            // QUESTIONABLE(legacy code): Only perform this if editing the question
            card.startTimer()
        }
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray =
        if (uri.startsWith(ANKI_PREFIX)) {
            when (val methodName = uri.substring(ANKI_PREFIX.length)) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
                else -> throw IllegalArgumentException("unhandled request: $methodName")
            }
        } else if (uri.startsWith(ANKIDROID_JS_PREFIX)) {
            jsApi.handleJsApiRequest(
                uri.substring(ANKIDROID_JS_PREFIX.length),
                bytes,
                returnDefaultValues = false,
            )
        } else {
            throw IllegalArgumentException("unhandled request: $uri")
        }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState ?: return ByteArray(0)
        return state
            .schedulingStatesWithContext()
            .toBuilder()
            .mergeStates(
                state.states
                    .toBuilder()
                    .mergeCurrent(
                        state.states.current
                            .toBuilder()
                            .setCustomData(state.topCard.customData)
                            .build(),
                    ).build(),
            ).build()
            .toByteArray()
    }

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState
        if (state == null) {
            statesMutated = true
            return ByteArray(0)
        }
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == customSchedulingKey) {
            state.states = req.states
        }
        statesMutated = true
        return ByteArray(0)
    }

    private fun disableDrawerSwipeOnConflicts() {
        if (gestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            hasDrawerSwipeConflicts = true
            super.disableDrawerSwipe()
        }
    }

    private fun toggleAutoAdvance() {
        if (automaticAnswer.isDisabled) {
            Timber.i("Re-enabling auto advance")
            automaticAnswer.reEnable(isDisplayingAnswer)
            showSnackbar(TR.actionsAutoAdvanceActivated())
        } else {
            Timber.i("Disabling auto advance")
            automaticAnswer.disable()
            showSnackbar(TR.actionsAutoAdvanceDeactivated())
        }
    }

    override val currentCardId: CardId?
        get() = currentCard?.id

    /**
     * Whether or not dismiss note is available for current card and specified DismissType
     * @return true if there is another card of same note that could be dismissed
     */
    private fun suspendNoteAvailable(): Boolean {
        val card = currentCard ?: return false
        // whether there exists a sibling not suspended
        return getColUnsafe.db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue != ${QueueType.Suspended.code} limit 1",
            card.nid,
            card.id,
        ) == 1
    }

    private fun buryNoteAvailable(): Boolean {
        val card = currentCard ?: return false
        // whether there exists a sibling which is neither suspended nor buried
        return getColUnsafe.db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue >= ${QueueType.New.code} limit 1",
            card.nid,
            card.id,
        ) == 1
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun hasDrawerSwipeConflicts(): Boolean = hasDrawerSwipeConflicts

    override fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi {
        val cardDataForJsAPI =
            AnkiDroidJsAPI.CardDataForJsApi().apply {
                newCardCount = queueState?.counts?.new ?: -1
                lrnCardCount = queueState?.counts?.lrn ?: -1
                revCardCount = queueState?.counts?.rev ?: -1
                if (currentCard != null) {
                    val s = getColUnsafe.sched
                    nextTime1 = s.nextIvlStr(currentCard!!, Rating.AGAIN)
                    nextTime2 = s.nextIvlStr(currentCard!!, Rating.HARD)
                    nextTime3 = s.nextIvlStr(currentCard!!, Rating.GOOD)
                    nextTime4 = s.nextIvlStr(currentCard!!, Rating.EASY)
                } else {
                    nextTime1 = ""
                    nextTime2 = ""
                    nextTime3 = ""
                    nextTime4 = ""
                }
                eta = this@Reviewer.eta
            }
        return cardDataForJsAPI
    }

    companion object {
        /**
         * Bundle key for the deck id to review.
         */
        const val EXTRA_DECK_ID = "deckId"

        /** Maps ViewerCommand to corresponding Flag for toggle operations */
        private val VIEWER_COMMAND_TO_FLAG =
            mapOf(
                ViewerCommand.TOGGLE_FLAG_RED to Flag.RED,
                ViewerCommand.TOGGLE_FLAG_ORANGE to Flag.ORANGE,
                ViewerCommand.TOGGLE_FLAG_GREEN to Flag.GREEN,
                ViewerCommand.TOGGLE_FLAG_BLUE to Flag.BLUE,
                ViewerCommand.TOGGLE_FLAG_PINK to Flag.PINK,
                ViewerCommand.TOGGLE_FLAG_TURQUOISE to Flag.TURQUOISE,
                ViewerCommand.TOGGLE_FLAG_PURPLE to Flag.PURPLE,
            )

        /** Maps ViewerCommand to corresponding user action number */
        private val VIEWER_COMMAND_TO_USER_ACTION =
            mapOf(
                ViewerCommand.USER_ACTION_1 to 1,
                ViewerCommand.USER_ACTION_2 to 2,
                ViewerCommand.USER_ACTION_3 to 3,
                ViewerCommand.USER_ACTION_4 to 4,
                ViewerCommand.USER_ACTION_5 to 5,
                ViewerCommand.USER_ACTION_6 to 6,
                ViewerCommand.USER_ACTION_7 to 7,
                ViewerCommand.USER_ACTION_8 to 8,
                ViewerCommand.USER_ACTION_9 to 9,
            )

        /** Menu item IDs for user actions, ordered 1-9 */
        private val USER_ACTION_MENU_IDS =
            listOf(
                R.id.user_action_1,
                R.id.user_action_2,
                R.id.user_action_3,
                R.id.user_action_4,
                R.id.user_action_5,
                R.id.user_action_6,
                R.id.user_action_7,
                R.id.user_action_8,
                R.id.user_action_9,
            )

        fun getIntent(context: Context): Intent = Intent(context, Reviewer::class.java)
    }
}
