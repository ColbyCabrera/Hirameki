/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.reviewer

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.scheduler.CardAnswer
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.MediaErrorBehavior
import com.ichi2.anki.cardviewer.MediaErrorListener
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.TypeAnswer
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Sound
import com.ichi2.anki.libanki.Tags
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.multimedia.expandSounds
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.bodyClassForCardOrd
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

sealed class MediaError(
    open val message: String,
) {
    data class PlaybackError(
        val uri: Uri,
        override val message: String,
    ) : MediaError(message)

    data class TtsError(
        val error: TtsPlayer.TtsError,
        override val message: String,
    ) : MediaError(message)
}

data class ReviewerState(
    val cardDisplayIndex: Long = 0L,
    val newCount: Int = 0,
    val learnCount: Int = 0,
    val reviewCount: Int = 0,
    val chosenAnswer: String = "",
    val answerFeedback: AnswerFeedback? = null,
    val isAnswerShown: Boolean = false,
    val questionHtml: String = "",
    val answerHtml: String = "",
    val bodyClass: String = "",
    val baseUrl: String = "",
    val nextTimes: List<String> = List(4) { "" },
    val showTypeInAnswer: Boolean = false,
    val typedAnswer: String = "",
    val isMarked: Boolean = false,
    val flag: Int = 0,
    val isMediaAutoplayEnabled: Boolean = false,
    val mediaDirectory: File? = null,
    val isFinished: Boolean = false,
    val isWhiteboardEnabled: Boolean = false,
    val isVoicePlaybackEnabled: Boolean = false,
    val mediaError: MediaError? = null,
    val colorizeAnswerButtons: Boolean = false,
    val showAnswerButtonBadges: Boolean = true,
    /**
     * Counts finished replay-button taps, successful or not. The card page dims a replay button the
     * moment it is tapped and restores it whenever this changes. A count rather than an is-playing flag:
     * state flows drop a flag that turns on and off within one frame, and the button would stay dim.
     */
    val replayFinished: Int = 0,
    /**
     * The card the rest of this state describes; null before the first card and once the queue is empty.
     * What android saves is built from this state alone, so a save taken while the next card is loading can
     * never pair that card's id with the last card's revealed answer and typed text.
     */
    val cardId: CardId? = null,
)

data class AnswerFeedback(
    val rating: CardAnswer.Rating,
    val id: String = UUID.randomUUID().toString(),
)

data class ReviewerJavascriptCommand(
    val id: Int,
    val script: String,
)

private const val MAX_PENDING_JAVASCRIPT_COMMANDS = 16

sealed class ReviewerEvent {
    object ShowAnswer : ReviewerEvent()

    data class RateCard(
        val rating: CardAnswer.Rating,
    ) : ReviewerEvent()

    object LoadInitialCard : ReviewerEvent()

    data class OnTypedAnswerChanged(
        val newText: String,
    ) : ReviewerEvent()

    object ToggleMark : ReviewerEvent()

    data class SetFlag(
        val flag: Int,
    ) : ReviewerEvent()

    data class LinkClicked(
        val url: String,
    ) : ReviewerEvent()

    data class PlayAudio(
        val side: String,
        val index: Int,
    ) : ReviewerEvent()

    object EditCard : ReviewerEvent()

    object BuryCard : ReviewerEvent()

    object SuspendCard : ReviewerEvent()

    object UnanswerCard : ReviewerEvent()

    object ReloadCard : ReviewerEvent()

    object Redo : ReviewerEvent()

    object ToggleWhiteboard : ReviewerEvent()

    data class OnWhiteboardStateChanged(
        val enabled: Boolean,
    ) : ReviewerEvent()

    object EditTags : ReviewerEvent()

    object DeleteNote : ReviewerEvent()

    object RescheduleCard : ReviewerEvent()

    object DismissSetDueDateDialog : ReviewerEvent()

    data class SetDueDateConfirmed(
        val count: Int,
    ) : ReviewerEvent()

    object ReplayMedia : ReviewerEvent()

    object ToggleVoicePlayback : ReviewerEvent()

    data class OnVoicePlaybackStateChanged(
        val enabled: Boolean,
    ) : ReviewerEvent()

    object DeckOptions : ReviewerEvent()

    object MediaErrorHandled : ReviewerEvent()

    object AnswerFeedbackShown : ReviewerEvent()

    object Undo : ReviewerEvent()
}

sealed class ReviewerEffect {
    data class NavigateToEditCard(
        val cardId: CardId,
    ) : ReviewerEffect()

    object NavigateToDeckPicker : ReviewerEffect()

    data class ShowSnackbar(
        val message: String,
    ) : ReviewerEffect()

    object PerformRedo : ReviewerEffect()

    object ToggleWhiteboard : ReviewerEffect()

    data class ShowDeleteNoteDialog(
        val card: Card,
    ) : ReviewerEffect()

    data class ReplayMedia(
        val card: Card,
    ) : ReviewerEffect()

    object ToggleVoicePlayback : ReviewerEffect()

    object NavigateToDeckOptions : ReviewerEffect()

    data class ShowTimeboxReachedDialog(
        val timebox: Collection.TimeboxReached,
    ) : ReviewerEffect()
}

/**
 * whether the backend refused an answer because the reviewer's queue state is out of date, not because
 * of a real fault. nothing is written then; showing the queue's current top card is the recovery.
 * the backend has no typed error for this, so it matches the texts from rslib 25.09:
 * scheduler/answering/mod.rs ("card was modified") and scheduler/queue/mod.rs ("not at top of queue").
 * a reworded message silently disables the recovery. ReviewerViewModelTest checks both texts against the
 * real backend; AbstractFlashcardViewerTest only mocks the exception. shared so the compose and legacy
 * reviewers agree
 */
internal fun BackendException.isStaleQueueAnswer(): Boolean {
    val text = message ?: return false
    return text.contains("card was modified", ignoreCase = true) ||
        text.contains("not at top of queue", ignoreCase = true)
}

class ReviewerViewModel(
    app: Application,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
    // outlives process death, unlike the rest of this class (see onScreenState)
    private val savedState: SavedStateHandle = SavedStateHandle(),
    // a seam for the bind failure below, which nothing but a test can provoke on demand
    serverFactory: (PostRequestHandler) -> AnkiServer = { AnkiServer(it) },
) : AndroidViewModel(app),
    PostRequestHandler {
    private val server = serverFactory(this)
    var jsApi: com.ichi2.anki.AnkiDroidJsAPI? = null

    private val _state = MutableStateFlow(
        ReviewerState(
            colorizeAnswerButtons = Prefs.colorizeAnswerButtons,
            showAnswerButtonBadges = Prefs.showAnswerButtonBadges,
        )
    )
    val state: StateFlow<ReviewerState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ReviewerEffect>()
    val effect: SharedFlow<ReviewerEffect> = _effect.asSharedFlow()

    private val _evalCommand = MutableStateFlow<List<ReviewerJavascriptCommand>>(emptyList())
    val evalCommand: StateFlow<List<ReviewerJavascriptCommand>> = _evalCommand.asStateFlow()

    private val _currentCard = MutableStateFlow<Card?>(null)
    val currentCardFlow: StateFlow<Card?> = _currentCard.asStateFlow()

    internal var currentCard: Card?
        get() = _currentCard.value
        set(value) {
            _currentCard.value = value
        }
    private var queueState: CurrentQueueState? = null
    private val _queueStateFlow = MutableStateFlow<CurrentQueueState?>(null)
    val queueStateFlow: StateFlow<CurrentQueueState?> = _queueStateFlow.asStateFlow()

    // Tags dialog state
    private val _tagsState = MutableStateFlow<TagsState>(TagsState.Loading)
    val tagsState: StateFlow<TagsState> = _tagsState.asStateFlow()

    private val _currentNoteTags = MutableStateFlow<Set<String>>(emptySet())
    val currentNoteTags: StateFlow<Set<String>> = _currentNoteTags.asStateFlow()

    private val _deckTags = MutableStateFlow<Set<String>>(emptySet())
    val deckTags: StateFlow<Set<String>> = _deckTags.asStateFlow()

    private val _filterByDeck = MutableStateFlow(true)
    val filterByDeck: StateFlow<Boolean> = _filterByDeck.asStateFlow()

    private val _showTagsDialog = MutableStateFlow(false)
    val showTagsDialog: StateFlow<Boolean> = _showTagsDialog.asStateFlow()

    private val _setDueDateCardId = MutableStateFlow<Long?>(null)
    val setDueDateCardId: StateFlow<Long?> = _setDueDateCardId.asStateFlow()

    private val _flowOfDeleteResult = MutableSharedFlow<Int>()
    val flowOfDeleteResult: SharedFlow<Int> = _flowOfDeleteResult.asSharedFlow()
    private val nextJavascriptCommandId = AtomicInteger(0)
    internal val typeAnswer = TypeAnswer.createInstance(app.sharedPrefs())
    internal val cardMediaPlayer: CardMediaPlayer =
        CardMediaPlayer(
            { script ->
                enqueueJavascriptCommand(script)
            },
            object : MediaErrorListener {
                override fun onError(uri: Uri): MediaErrorBehavior {
                    Timber.w("Error playing media: %s", uri)
                    val message = getApplication<Application>().getString(R.string.media_load_failed)
                    _state.update { it.copy(mediaError = MediaError.PlaybackError(uri, message)) }
                    return MediaErrorBehavior.CONTINUE_MEDIA
                }

                override fun onMediaPlayerError(
                    mp: MediaPlayer?,
                    which: Int,
                    extra: Int,
                    uri: Uri,
                ): MediaErrorBehavior {
                    Timber.w("Error playing media: %s", uri)
                    val message = getApplication<Application>().getString(R.string.media_load_failed)
                    _state.update { it.copy(mediaError = MediaError.PlaybackError(uri, message)) }
                    return MediaErrorBehavior.CONTINUE_MEDIA
                }

                override fun onTtsError(
                    error: TtsPlayer.TtsError,
                    isAutomaticPlayback: Boolean,
                ) {
                    Timber.w("TTS error: %s", error)
                    if (!isAutomaticPlayback) {
                        val message = getApplication<Application>().getString(R.string.tts_playback_failed)
                        _state.update { it.copy(mediaError = MediaError.TtsError(error, message)) }
                    }
                }
            },
        )

    /** A job that is running for the current card. This is used to prevent multiple actions from running at the same time. */
    private var cardActionJob: Job? = null

    private fun trackCardAction(job: Job) {
        cardActionJob = job
        job.invokeOnCompletion {
            if (cardActionJob === job) {
                cardActionJob = null
            }
        }
    }

    /**
     * Launches a card action job, preventing concurrent execution.
     * If another job is active or the reviewer is finished, the new action is ignored.
     * @param block The suspend function to execute
     */
    private fun launchCardAction(block: suspend () -> Unit) {
        if (cardActionJob?.isActive == true || _state.value.isFinished) return
        trackCardAction(
            viewModelScope.launch(dispatcher) {
                block()
            },
        )
    }

    /**
     * Enqueues a card action behind the current in-flight action instead of dropping it.
     * This is used for delete undo so it still runs if a delete-triggered reload is completing,
     * and for a rating, which must not be lost to the reveal still finishing (see [rateCard]).
     */
    private fun enqueueCardAction(block: suspend () -> Unit) {
        val currentJob = cardActionJob
        if (currentJob?.isActive != true) {
            trackCardAction(viewModelScope.launch(dispatcher) { block() })
            return
        }

        trackCardAction(
            viewModelScope.launch(dispatcher) {
                currentJob.join()
                block()
            },
        )
    }

    /**
     * what was on screen when android last saved this reviewer, until the first load has put it back;
     * null on a fresh start. volatile: the load clears it on [dispatcher], android saves on the main thread
     */
    @Volatile
    private var pendingRestore: Bundle? = savedState.get<Bundle>(STATE_ON_SCREEN)

    init {
        // #143: android kills a backgrounded app and rebuilds the reviewer from saved state, but this
        // class started from scratch: the rebuilt screen showed the front of the queue's first card and
        // lost a revealed answer and a typed answer. read only when android saves, on the main thread,
        // so the state flows stay the one source of truth
        savedState.setSavedStateProvider(STATE_ON_SCREEN) { onScreenState() }
        ensureServerStarted()
        onEvent(ReviewerEvent.LoadInitialCard)
    }

    /**
     * every card is drawn from a page this loopback server hands the webview, so no server means no card.
     * binding its ephemeral port can still fail (no free port, or the socket is refused), and doing it in the
     * constructor with nothing catching made that a crash before the reviewer ever appeared. a failure is
     * reported by [serverBaseUrl] instead, and the next card load retries the bind.
     */
    private fun ensureServerStarted(): Boolean {
        if (server.isAlive) return true
        return try {
            server.start()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start the reviewer's AnkiServer")
            false
        }
    }

    /**
     * the url the card page loads from, or an empty string while the server is down: the flashcard keeps the
     * last painted card for an empty url rather than loading a port that isn't listening, and the snackbar says
     * the failure out loud, since the retry the user needs is another card load.
     */
    private fun serverBaseUrl(): String {
        if (ensureServerStarted()) return server.baseUrl()
        reportServerFailure()
        return ""
    }

    /** one message for a card that could not be served, said again wherever the failure blocks an action */
    private fun reportServerFailure() {
        val message = getApplication<Application>().getString(R.string.something_wrong)
        viewModelScope.launch { _effect.emit(ReviewerEffect.ShowSnackbar(message)) }
    }

    private fun onScreenState(): Bundle {
        // until the first load has put a restored screen back, the live state is half loaded: no card yet,
        // or the question side before the answer is revealed again. saving that would lose the restore
        // if android killed the process a second time, so the restored bundle is saved as it came
        pendingRestore?.let { return it }
        // one source, not currentCard beside it: the two are written at different moments of a card load, and
        // a save landing between them stored the new card's id with the last card's answer and typed text
        val shown = _state.value
        val cardId = shown.cardId ?: return Bundle()
        // typed setters: bundleOf is deprecated in this core-ktx for lacking compile-time type safety
        return Bundle().apply {
            putLong(KEY_CARD_ID, cardId)
            putBoolean(KEY_ANSWER_SHOWN, shown.isAnswerShown)
            putString(KEY_TYPED_ANSWER, shown.typedAnswer)
        }
    }

    override fun onCleared() {
        server.stop()
        cardMediaPlayer.close()
    }

    fun onJavascriptCommandConsumed(commandId: Int) {
        _evalCommand.update { commands ->
            commands.filterNot { it.id == commandId }
        }
    }

    private fun enqueueJavascriptCommand(script: String) {
        _evalCommand.update { commands ->
            (
                commands +
                    ReviewerJavascriptCommand(
                        nextJavascriptCommandId.incrementAndGet(),
                        script,
                    )
            ).takeLast(MAX_PENDING_JAVASCRIPT_COMMANDS)
        }
    }

    private fun clearPendingJavascriptCommands() {
        _evalCommand.value = emptyList()
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray =
        if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            val path = uri.substring(AnkiServer.ANKI_PREFIX.length)
            when {
                path.startsWith("jsapi/") -> {
                    val api =
                        checkNotNull(jsApi) { "jsApi must be set before handling jsapi/ requests" }
                    api.handleJsApiRequest(
                        path.substring("jsapi/".length),
                        bytes,
                        returnDefaultValues = false,
                    )
                }

                path == "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
                else -> throw IllegalArgumentException("Unhandled Anki request: $uri")
            }
        } else {
            throw IllegalArgumentException("Unhandled POST request: $uri")
        }

    fun onEvent(event: ReviewerEvent) {
        when (event) {
            is ReviewerEvent.ShowAnswer -> showAnswer()
            is ReviewerEvent.RateCard -> rateCard(event.rating)
            is ReviewerEvent.LoadInitialCard ->
                launchCardAction {
                    withCol { startTimebox() }
                    val restore = pendingRestore
                    // a restore that reveals the answer plays the back, which cuts off a question autoplay
                    // just started by the load; the restore plays whichever side it leaves shown instead
                    loadCardSuspend(autoplayQuestion = restore == null)
                    if (restore != null) {
                        // inside the load action: launchCardAction ignores a second action while one runs
                        restoreOnScreenState(restore)
                        pendingRestore = null
                    }
                }

            is ReviewerEvent.OnTypedAnswerChanged -> onTypedAnswerChanged(event.newText)
            is ReviewerEvent.ToggleMark -> toggleMark()
            is ReviewerEvent.SetFlag -> setFlag(event.flag)
            is ReviewerEvent.LinkClicked -> linkClicked(event.url)
            is ReviewerEvent.PlayAudio -> playAudio(event.side, event.index)
            is ReviewerEvent.UnanswerCard -> unanswerCard()
            is ReviewerEvent.EditCard -> editCard()
            is ReviewerEvent.BuryCard -> buryCard()
            is ReviewerEvent.SuspendCard -> suspendCard()
            is ReviewerEvent.ReloadCard -> reloadCard()
            is ReviewerEvent.Redo -> redo()
            is ReviewerEvent.ToggleWhiteboard -> toggleWhiteboard()
            is ReviewerEvent.OnWhiteboardStateChanged -> onWhiteboardStateChanged(event.enabled)
            is ReviewerEvent.EditTags -> editTags()
            is ReviewerEvent.DeleteNote -> deleteNote()
            is ReviewerEvent.RescheduleCard -> rescheduleCard()
            is ReviewerEvent.DismissSetDueDateDialog -> dismissSetDueDateDialog()
            is ReviewerEvent.SetDueDateConfirmed -> setDueDateConfirmed(event.count)
            is ReviewerEvent.ReplayMedia -> replayMedia()
            is ReviewerEvent.ToggleVoicePlayback -> toggleVoicePlayback()
            is ReviewerEvent.OnVoicePlaybackStateChanged -> onVoicePlaybackStateChanged(event.enabled)
            is ReviewerEvent.DeckOptions -> deckOptions()
            is ReviewerEvent.MediaErrorHandled -> onMediaErrorHandled()
            is ReviewerEvent.AnswerFeedbackShown -> onAnswerFeedbackShown()
            is ReviewerEvent.Undo -> undoAction()
        }
    }

    private fun onMediaErrorHandled() {
        _state.update { it.copy(mediaError = null) }
    }

    private fun deckOptions() {
        viewModelScope.launch { _effect.emit(ReviewerEffect.NavigateToDeckOptions) }
    }

    private fun onVoicePlaybackStateChanged(enabled: Boolean) {
        _state.update { it.copy(isVoicePlaybackEnabled = enabled) }
    }

    private fun toggleVoicePlayback() {
        viewModelScope.launch { _effect.emit(ReviewerEffect.ToggleVoicePlayback) }
    }

    private fun replayMedia() {
        currentCard ?: return
        viewModelScope.launch {
            val side = if (_state.value.isAnswerShown) SingleCardSide.BACK else SingleCardSide.FRONT
            cardMediaPlayer.replayAll(side)
        }
    }

    private fun rescheduleCard() {
        val card = currentCard ?: return
        _setDueDateCardId.value = card.id
    }

    private fun dismissSetDueDateDialog() {
        _setDueDateCardId.value = null
    }

    private fun setDueDateConfirmed(count: Int) {
        val message = TR.schedulingSetDueDateDone(count)
        viewModelScope.launch { _effect.emit(ReviewerEffect.ShowSnackbar(message)) }
        reloadCard()
        _setDueDateCardId.value = null
    }

    private fun deleteNote() {
        val card = currentCard ?: return
        viewModelScope.launch { _effect.emit(ReviewerEffect.ShowDeleteNoteDialog(card)) }
    }

    fun confirmDeleteNote(cardId: CardId? = currentCard?.id) {
        val targetCardId = cardId ?: return
        launchCardAction {
            cardMediaPlayer.stop()
            val deletedCount =
                undoableOp(this@ReviewerViewModel) {
                    removeNotes(cardIds = listOf(targetCardId))
                }.count
            loadCardSuspend()
            _flowOfDeleteResult.emit(deletedCount)
        }
    }

    fun undoDelete() {
        undoAction()
    }

    private fun undoAction() {
        enqueueCardAction {
            undoableOp(this@ReviewerViewModel) {
                undo()
            }
            loadCardSuspend()
        }
    }

    private fun editTags() {
        currentCard ?: return
        viewModelScope.launch {
            loadTagsForCurrentCard()
            _showTagsDialog.value = true
        }
    }

    private suspend fun loadTagsForCurrentCard() {
        val card = currentCard ?: return
        _tagsState.value = TagsState.Loading

        withCol {
            val note = card.note(this)
            val allTags = this.tags.all().sorted()
            _currentNoteTags.value = note.tags.toSet()
            _tagsState.value = TagsState.Loaded(allTags)

            // Load tags specific to the current deck for filtering
            // Use findNotes with deck query for efficiency instead of iterating over all cards
            val deckName = this.decks.name(card.did)
            val escapedDeckName = deckName.replace("\"", "\\\"")
            val noteIds = this.findNotes("deck:\"$escapedDeckName\"")

            // one query for the whole deck: building a Note per id cost a backend round trip per note, which is
            // what the cap was there to survive (it said 1000 and took 10000, so a bigger deck lost tags silently)
            val tagsInDeck = mutableSetOf<String>()
            this.db.query("SELECT DISTINCT tags FROM notes WHERE id IN ${Utils.ids2str(noteIds)}").use { cursor ->
                while (cursor.moveToNext()) {
                    tagsInDeck.addAll(this.tags.split(cursor.getString(0)))
                }
            }
            _deckTags.value = tagsInDeck
        }
    }

    fun setFilterByDeck(filterByDeck: Boolean) {
        _filterByDeck.value = filterByDeck
    }

    fun dismissTagsDialog() {
        _showTagsDialog.value = false
    }

    fun updateNoteTags(newTags: Set<String>) {
        val card = currentCard ?: return
        viewModelScope.launch {
            withCol {
                val note = card.note(this)
                note.setTagsFromStr(this, newTags.joinToString(" "))
                this.updateNote(note)
            }
            _currentNoteTags.value = newTags
            _showTagsDialog.value = false

            // Reload card to update UI (e.g., marked state if "marked" tag changed)
            reloadCardSuspend()
        }
    }

    /**
     * Registers a new tag in the collection by expanding it in the tag hierarchy.
     * This method was formerly called addTag, but has been renamed to registerNewTag to clarify
     * its purpose: it ensures the tag exists and is expanded via [Tags.setCollapsed] using
     * [withCol], then refreshes the available tags via [loadTagsForCurrentCard].
     * It does not directly attach the tag to the current note.
     */
    fun registerNewTag(tag: String) {
        viewModelScope.launch {
            withCol {
                this.tags.setCollapsed(tag, collapsed = false)
            }
            // Refresh tags list
            loadTagsForCurrentCard()
        }
    }

    private fun onWhiteboardStateChanged(enabled: Boolean) {
        _state.update { it.copy(isWhiteboardEnabled = enabled) }
    }

    private fun toggleWhiteboard() {
        viewModelScope.launch { _effect.emit(ReviewerEffect.ToggleWhiteboard) }
    }

    private fun redo() {
        viewModelScope.launch { _effect.emit(ReviewerEffect.PerformRedo) }
    }

    internal suspend fun reloadCardSuspend() {
        val card = currentCard ?: return
        val showAudioPlayButtons = !CollectionPreferences.getHidePlayAudioButtons()

        clearPendingJavascriptCommands()

        try {
            withCol { card.load(this) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Card might have been deleted (e.g. note type changed to one with fewer templates)
            loadCardSuspend()
            return
        }

        cardMediaPlayer.loadCardAvTags(card)
        var queue: CurrentQueueState? = null
        var updatedState: ReviewerState? = null
        // outside withCol: a card load is also the reviewer's retry of a server that failed to bind
        val cardBaseUrl = serverBaseUrl()
        withCol {
            val note = card.note(this)
            typeAnswer.updateInfo(this, card, getApplication<Application>().resources)
            val renderOutput = card.renderOutput(this, reload = true)
            val questionHtml = typeAnswer.filterQuestion(renderOutput.questionText)
            val answerHtml = typeAnswer.filterAnswer(renderOutput.answerText)
            val processedQuestionHtml =
                processHtml(questionHtml, renderOutput, this, showAudioPlayButtons)
            val processedAnswerHtml =
                processHtml(answerHtml, renderOutput, this, showAudioPlayButtons)

            queue = this.sched.currentQueueState()

            updatedState =
                _state.value.copy(
                    mediaError = null,
                    newCount = queue?.counts?.new ?: 0,
                    learnCount = queue?.counts?.lrn ?: 0,
                    reviewCount = queue?.counts?.rev ?: 0,
                    questionHtml = processedQuestionHtml,
                    answerHtml = processedAnswerHtml,
                    bodyClass = bodyClassForCardOrd(card.ord),
                    baseUrl = cardBaseUrl,
                    isAnswerShown = false,
                    showTypeInAnswer = typeAnswer.correct != null,
                    nextTimes = List(4) { "" },
                    chosenAnswer = "",
                    typedAnswer = "",
                    isMarked = note.hasTag(this, "marked"),
                    flag = card.userFlag(),
                    mediaDirectory = this.media.dir,
                    isFinished = false,
                )
        }
        // the backend grades whichever card leads this queue state, and the reveal labels the answer buttons from it.
        // a reload keeps the card on screen, which may no longer lead: a due date set from the reviewer moves it out
        // of the queue, and a learning card can fall due while the note editor is open. paired with that queue, the
        // card showed another card's intervals and its rating graded that other card unseen; with no queue left, it
        // could be neither revealed nor rated. so the card that does lead is loaded, as every other load shows it
        if (queue?.topCard?.id != card.id) {
            Timber.i("card %d no longer leads the queue after a reload; loading the card that does", card.id)
            loadCardSuspend()
            return
        }
        queue?.timeboxReached?.let { _effect.emit(ReviewerEffect.ShowTimeboxReachedDialog(it)) }
        _state.value = requireNotNull(updatedState)
        _queueStateFlow.value = queue
        queueState = queue
    }

    private fun editCard() {
        val card = currentCard ?: return
        viewModelScope.launch {
            clearPendingJavascriptCommands()
            _effect.emit(ReviewerEffect.NavigateToEditCard(card.id))
        }
    }

    private fun linkClicked(url: String) {
        val match = Sound.AV_PLAYLINK_RE.find(url)
        if (match != null) {
            val (side, indexString) = match.destructured
            val index = indexString.toInt()
            onEvent(ReviewerEvent.PlayAudio(side, index))
            return
        }

        when {
            url.startsWith("videoended:") -> {
                onVideoFinished()
                return
            }

            url.startsWith("videopause:") -> {
                onVideoPaused()
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun onVideoFinished() = cardMediaPlayer.onVideoFinished()

    fun onVideoPaused() = cardMediaPlayer.onVideoPaused()

    private fun playAudio(
        side: String,
        index: Int,
    ) {
        // taken before any suspension, so a sound that ends while this tap is still looking up its tag
        // cannot count as this tap finishing
        val generation = ++replayGeneration
        viewModelScope.launch {
            try {
                val card = currentCard ?: return@launch
                val avTag =
                    withCol {
                        val renderOutput = card.renderOutput(this)
                        when (side) {
                            "q" -> renderOutput.questionAvTags.getOrNull(index)
                            "a" -> renderOutput.answerAvTags.getOrNull(index)
                            else -> null
                        }
                    }
                // any tag the player understands, text to speech included: playOne handles both, and
                // filtering to sound files left tts replay buttons silent
                if (avTag != null) {
                    // this playback's own job, not awaitIdle(): that waits on whatever is playing when it is
                    // called, which a newer tap or the next card's autoplay may already have replaced
                    cardMediaPlayer.playOne(avTag)?.join()
                }
            } finally {
                // every tap must end in a signal, found tag or not, or its button stays dimmed. a second
                // tap cancels this playback and starts its own; only the latest may report finishing
                if (generation == replayGeneration) {
                    _state.update { it.copy(replayFinished = it.replayFinished + 1) }
                }
            }
        }
    }

    /** Counts replay-button taps, so an older playback cannot clear a newer one's indicator. */
    private var replayGeneration = 0

    private fun reloadCard() = launchCardAction { reloadCardSuspend() }

    private fun onTypedAnswerChanged(newText: String) {
        _state.update { it.copy(typedAnswer = newText) }
    }

    private suspend fun getNextCard(): Pair<Card, CurrentQueueState>? =
        withCol {
            this.sched.currentQueueState()?.let {
                it.topCard.renderOutput(this, reload = true)
                Pair(it.topCard, it)
            }
        }

    /** @param autoplayQuestion false when the caller plays the side it ends up showing (see LoadInitialCard) */
    internal suspend fun loadCardSuspend(autoplayQuestion: Boolean = true) {
        val cardAndQueueState = getNextCard()
        val showAudioPlayButtons = !CollectionPreferences.getHidePlayAudioButtons()
        if (cardAndQueueState == null) {
            clearPendingJavascriptCommands()
            _state.update {
                it.copy(
                    // no card on screen: what android saves follows this state, so the card that was on screen
                    // before the queue emptied must not be saved as if it still were
                    cardId = null,
                    isFinished = true,
                    newCount = 0,
                    learnCount = 0,
                    reviewCount = 0,
                    isMediaAutoplayEnabled = false,
                )
            }
            _effect.emit(ReviewerEffect.NavigateToDeckPicker)
            currentCard = null
            queueState = null
            return
        }
        val (card, queue) = cardAndQueueState
        clearPendingJavascriptCommands()
        currentCard = card
        queueState = queue
        _queueStateFlow.value = queue
        queue.timeboxReached?.let { _effect.emit(ReviewerEffect.ShowTimeboxReachedDialog(it)) }
        cardMediaPlayer.loadCardAvTags(card)
        // outside withCol: a card load is also the reviewer's retry of a server that failed to bind
        val cardBaseUrl = serverBaseUrl()
        withCol {
            val note = card.note(this)
            typeAnswer.updateInfo(this, card, getApplication<Application>().resources)
            val renderOutput = card.renderOutput(this)
            val questionHtml = typeAnswer.filterQuestion(renderOutput.questionText)
            val answerHtml = typeAnswer.filterAnswer(renderOutput.answerText)
            val processedQuestionHtml =
                processHtml(questionHtml, renderOutput, this, showAudioPlayButtons)
            val processedAnswerHtml =
                processHtml(answerHtml, renderOutput, this, showAudioPlayButtons)
            _state.update {
                it.copy(
                    cardDisplayIndex = it.cardDisplayIndex + 1,
                    // written with the answer and typed text it belongs to, in one update
                    cardId = card.id,
                    mediaError = null,
                    newCount = queue.counts.new,
                    learnCount = queue.counts.lrn,
                    reviewCount = queue.counts.rev,
                    questionHtml = processedQuestionHtml,
                    answerHtml = processedAnswerHtml,
                    bodyClass = bodyClassForCardOrd(card.ord),
                    baseUrl = cardBaseUrl,
                    isAnswerShown = false,
                    showTypeInAnswer = typeAnswer.correct != null,
                    nextTimes = List(4) { "" },
                    chosenAnswer = "",
                    typedAnswer = "",
                    isMarked = note.hasTag(this, "marked"),
                    flag = card.userFlag(),
                    isMediaAutoplayEnabled = cardMediaPlayer.config.autoplay,
                    mediaDirectory = this.media.dir,
                    isFinished = false,
                )
            }
        }
        if (autoplayQuestion) cardMediaPlayer.autoplayAllForSide(SingleCardSide.FRONT.toCardSide())
    }

    private fun showAnswer() {
        if (currentCard == null || queueState == null) return
        launchCardAction { showAnswerSuspend() }
    }

    private suspend fun showAnswerSuspend() {
        val card = currentCard ?: return
        val queue = queueState ?: return
        val showAudioPlayButtons = !CollectionPreferences.getHidePlayAudioButtons()
        withCol {
            val labels = this.sched.describeNextStates(queue.states)
            typeAnswer.input = _state.value.typedAnswer
            val renderOutput = card.renderOutput(this)
            val answerHtml = typeAnswer.filterAnswer(renderOutput.answerText)
            val processedAnswerHtml =
                processHtml(answerHtml, renderOutput, this, showAudioPlayButtons)

            val paddedLabels = (labels + List(4) { "" }).take(4)

            _state.update {
                it.copy(
                    answerHtml = processedAnswerHtml,
                    isAnswerShown = true,
                    nextTimes = paddedLabels,
                )
            }
        }
        cardMediaPlayer.autoplayAllForSide(SingleCardSide.BACK.toCardSide())
    }

    /**
     * puts back the typed answer and revealed side saved for a card, if that card still leads the
     * queue, then autoplays the side left shown (the load held its question autoplay back). the backend
     * only accepts an answer for its top card ("not at top of queue"), so a restored reviewer always
     * shows that card; it is the one that was on screen unless another card, such as a learning card
     * that fell due, took the lead while the app was away
     */
    private suspend fun restoreOnScreenState(saved: Bundle) {
        // an empty queue: no card shown, nothing to play
        val card = currentCard ?: return
        if (saved.containsKey(KEY_CARD_ID) && saved.getLong(KEY_CARD_ID) == card.id) {
            onTypedAnswerChanged(saved.getString(KEY_TYPED_ANSWER).orEmpty())
            if (saved.getBoolean(KEY_ANSWER_SHOWN)) {
                showAnswerSuspend()
                return
            }
        }
        cardMediaPlayer.autoplayAllForSide(SingleCardSide.FRONT.toCardSide())
    }

    private fun rateCard(rating: CardAnswer.Rating) {
        if (queueState == null) return
        val rated = _state.value
        // a rating is only meaningful once the answer has been seen. the answer buttons already only
        // offer ratings after reveal, but a gesture that began on the previous card can land after the
        // next card loads; grading that card unseen would write a wrong review and a wrong interval
        if (!rated.isAnswerShown) {
            Timber.w("ignoring a rating for a card whose answer is not shown")
            return
        }
        // the page every card is drawn on comes from the loopback server, so while that server is down the
        // webview shows nothing at all, or still the card before it (Flashcard keeps the last paint for an
        // empty base url). rating then would write a review for a card the user never saw, which is worse
        // than refusing the tap; the load's snackbar is said again so the tap does not look accepted
        if (rated.baseUrl.isEmpty()) {
            Timber.w("ignoring a rating for a card whose page was never served")
            reportServerFailure()
            return
        }

        // queued, not dropped: the answer is up as soon as the reveal publishes it, while the reveal's card
        // action is still finishing, and the card view arms grading from that state. launchCardAction threw
        // away a rating made in that window without a word: the card flew into its corner, nothing was
        // recorded, and the same card came back seconds later
        enqueueCardAction {
            val shown = _state.value
            val queue = queueState
            // re-checked once the action ahead is done, which may have changed the card: a second tap on a card
            // already graded would otherwise grade the next one unseen
            if (queue == null ||
                shown.cardDisplayIndex != rated.cardDisplayIndex ||
                shown.cardId != rated.cardId ||
                !shown.isAnswerShown ||
                shown.baseUrl.isEmpty()
            ) {
                Timber.w("ignoring a rating for card %d: the card on screen changed before it was recorded", rated.cardId)
                return@enqueueCardAction
            }
            // the answer grades the queue's top card, not the card on screen, and the two can differ with every check
            // above still passing: a load run outside card actions (Reviewer.updateCurrentCard, which the screen runs
            // after a collection op it did not run itself) stores the next card's queue before it publishes that
            // card. that rating would grade a card the user never saw; nothing is recorded, and the card that leads
            // is shown, as for a stale queue below, so the card that would be graded is on screen before any rating
            if (queue.topCard.id != rated.cardId) {
                Timber.w("ignoring a rating for card %d: card %d leads the queue; showing it", rated.cardId, queue.topCard.id)
                loadCardSuspend()
                return@enqueueCardAction
            }
            var wasLeech = false
            try {
                withCol {
                    this.sched.answerCard(queue, rating).also {
                        wasLeech = this.sched.stateIsLeech(queue.states.again)
                    }
                }
            } catch (e: BackendException) {
                if (!e.isStaleQueueAnswer()) throw e
                // #143: the queue state was read before the app sat in the background. past the day
                // cutoff the backend recomputes the card's state and refuses the answer ("card was
                // modified"); a rebuilt queue can also lead with another card ("not at top of queue").
                // uncaught, this crashed the app and android closed the reviewer. nothing was
                // recorded, so show the card that now leads the queue
                Timber.w(e, "answer refused for a stale queue; reloading")
                loadCardSuspend()
                return@enqueueCardAction
            }

            if (rating == CardAnswer.Rating.AGAIN && wasLeech) {
                val leechMessage: String =
                    if (queue.topCard.queue.buriedOrSuspended()) {
                        getApplication<Application>().resources.getString(R.string.leech_suspend_notification)
                    } else {
                        getApplication<Application>().resources.getString(R.string.leech_notification)
                    }
                _effect.emit(ReviewerEffect.ShowSnackbar(leechMessage))
            }

            _state.update { it.copy(answerFeedback = AnswerFeedback(rating)) }
            loadCardSuspend()
        }
    }

    private fun onAnswerFeedbackShown() {
        _state.update { it.copy(answerFeedback = null) }
    }

    private fun unanswerCard() {
        currentCard ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isAnswerShown = false,
                    nextTimes = List(4) { "" },
                    chosenAnswer = "",
                )
            }
        }
    }

    private fun toggleMark() {
        viewModelScope.launch {
            val card = currentCard ?: return@launch
            val note =
                withCol {
                    card.note(this)
                }
            try {
                NoteService.toggleMark(note, handler = this@ReviewerViewModel)
                val isMarked = NoteService.isMarked(note)
                _state.update { it.copy(isMarked = isMarked) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to toggle note mark")
            }
        }
    }

    private fun setFlag(flag: Int) {
        viewModelScope.launch {
            val card = currentCard ?: return@launch
            withCol {
                this.setUserFlagForCards(listOf(card.id), flag)
            }
            _state.update { it.copy(flag = flag) }
        }
    }

    private fun performCardAction(action: suspend (Card) -> Unit) {
        val card = currentCard ?: return

        launchCardAction {
            action(card)
            loadCardSuspend()
        }
    }

    private fun buryCard() {
        performCardAction { card ->
            withCol {
                this.sched.buryCards(listOf(card.id))
            }
        }
    }

    private fun suspendCard() {
        performCardAction { card ->
            withCol {
                this.sched.suspendCards(listOf(card.id))
            }
        }
    }

    private fun processHtml(
        html: String,
        renderOutput: TemplateRenderOutput,
        collection: Collection,
        showAudioPlayButtons: Boolean,
    ): String {
        val escapedHtml = collection.media.escapeMediaFilenames(html)
        val processedHtml =
            expandSounds(
                content = escapedHtml,
                renderOutput = renderOutput,
                showAudioPlayButtons = showAudioPlayButtons,
                mediaDir = collection.media.dir,
                replayButtonContentDescription = getApplication<Application>().getString(R.string.replay_media),
            )
        return CardHtmlBuilder.wrapWithStyles(processedHtml, renderOutput.css)
    }

    companion object {
        private const val STATE_ON_SCREEN = "reviewer_on_screen"
        private const val KEY_CARD_ID = "card_id"
        private const val KEY_ANSWER_SHOWN = "answer_shown"
        private const val KEY_TYPED_ANSWER = "typed_answer"

        fun factory(dispatcher: CoroutineDispatcher = ioDispatcher): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application =
                        checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    ReviewerViewModel(application, dispatcher, createSavedStateHandle())
                }
            }
    }
}
