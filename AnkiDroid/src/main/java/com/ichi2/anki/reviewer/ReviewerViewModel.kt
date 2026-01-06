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
import androidx.core.net.toUri
import androidx.core.text.htmlEncode
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import anki.scheduler.CardAnswer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.MediaErrorBehavior
import com.ichi2.anki.cardviewer.MediaErrorListener
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.TypeAnswer
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Sound
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.NoteService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ichi2.anki.dialogs.compose.TagsState
import timber.log.Timber
import java.io.File

sealed class MediaError(open val message: String) {
    data class PlaybackError(val uri: Uri, override val message: String) : MediaError(message)
    data class TtsError(val error: TtsPlayer.TtsError, override val message: String) :
        MediaError(message)
}

data class ReviewerState(
    val newCount: Int = 0,
    val learnCount: Int = 0,
    val reviewCount: Int = 0,
    val chosenAnswer: String = "",
    val isAnswerShown: Boolean = false,
    val html: String = "<html><body></body></html>",
    val nextTimes: List<String> = List(4) { "" },
    val showTypeInAnswer: Boolean = false,
    val typedAnswer: String = "",
    val isMarked: Boolean = false,
    val flag: Int = 0,
    val mediaDirectory: File? = null,
    val isFinished: Boolean = false,
    val isWhiteboardEnabled: Boolean = false,
    val isVoicePlaybackEnabled: Boolean = false,
    val mediaError: MediaError? = null
)

sealed class ReviewerEvent {
    object ShowAnswer : ReviewerEvent()
    data class RateCard(val rating: CardAnswer.Rating) : ReviewerEvent()
    object LoadInitialCard : ReviewerEvent()
    data class OnTypedAnswerChanged(val newText: String) : ReviewerEvent()
    object ToggleMark : ReviewerEvent()
    data class SetFlag(val flag: Int) : ReviewerEvent()
    data class LinkClicked(val url: String) : ReviewerEvent()
    data class PlayAudio(val side: String, val index: Int) : ReviewerEvent()
    object EditCard : ReviewerEvent()
    object BuryCard : ReviewerEvent()
    object SuspendCard : ReviewerEvent()
    object UnanswerCard : ReviewerEvent()
    object ReloadCard : ReviewerEvent()
    object Redo : ReviewerEvent()
    object ToggleWhiteboard : ReviewerEvent()
    data class OnWhiteboardStateChanged(val enabled: Boolean) : ReviewerEvent()
    object EditTags : ReviewerEvent()
    object DeleteNote : ReviewerEvent()
    object RescheduleCard : ReviewerEvent()
    object ReplayMedia : ReviewerEvent()
    object ToggleVoicePlayback : ReviewerEvent()
    data class OnVoicePlaybackStateChanged(val enabled: Boolean) : ReviewerEvent()
    object DeckOptions : ReviewerEvent()
    object MediaErrorHandled : ReviewerEvent()
}

sealed class ReviewerEffect {
    data class NavigateToEditCard(val cardId: CardId) : ReviewerEffect()
    object NavigateToDeckPicker : ReviewerEffect()
    data class ShowSnackbar(val message: String) : ReviewerEffect()
    object PerformRedo : ReviewerEffect()
    object ToggleWhiteboard : ReviewerEffect()
    // ShowTagsDialog removed - now handled via ViewModel state in Compose
    data class ShowDeleteNoteDialog(val card: Card) : ReviewerEffect()
    data class ShowDueDateDialog(val card: Card) : ReviewerEffect()
    data class ReplayMedia(val card: Card) : ReviewerEffect()
    object ToggleVoicePlayback : ReviewerEffect()
    object NavigateToDeckOptions : ReviewerEffect()
}

class ReviewerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ReviewerState())
    val state: StateFlow<ReviewerState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ReviewerEffect>()
    val effect: SharedFlow<ReviewerEffect> = _effect.asSharedFlow()

    private var currentCard: Card? = null
    private var queueState: CurrentQueueState? = null
    
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
    private val typeAnswer = TypeAnswer.createInstance(app.sharedPrefs())
    private val cardMediaPlayer: CardMediaPlayer =
        CardMediaPlayer({ }, object : MediaErrorListener {
            override fun onError(uri: Uri): MediaErrorBehavior {
                Timber.w("Error playing media: %s", uri)
                val message = getApplication<Application>().getString(R.string.media_load_failed)
                _state.update { it.copy(mediaError = MediaError.PlaybackError(uri, message)) }
                return MediaErrorBehavior.CONTINUE_MEDIA
            }

            override fun onMediaPlayerError(
                mp: MediaPlayer?, which: Int, extra: Int, uri: Uri
            ): MediaErrorBehavior {
                Timber.w("Error playing media: %s", uri)
                val message = getApplication<Application>().getString(R.string.media_load_failed)
                _state.update { it.copy(mediaError = MediaError.PlaybackError(uri, message)) }
                return MediaErrorBehavior.CONTINUE_MEDIA
            }

            override fun onTtsError(error: TtsPlayer.TtsError, isAutomaticPlayback: Boolean) {
                Timber.w("TTS error: %s", error)
                if (!isAutomaticPlayback) {
                    val message =
                        getApplication<Application>().getString(R.string.tts_playback_failed)
                    _state.update { it.copy(mediaError = MediaError.TtsError(error, message)) }
                }
            }
        })

    /** A job that is running for the current card. This is used to prevent multiple actions from running at the same time. */
    private var cardActionJob: Job? = null

    /**
     * Launches a card action job, preventing concurrent execution.
     * If another job is active or the reviewer is finished, the new action is ignored.
     * @param block The suspend function to execute
     */
    private fun launchCardAction(block: suspend () -> Unit) {
        if (cardActionJob?.isActive == true || _state.value.isFinished) return
        cardActionJob = viewModelScope.launch {
            block()
        }.also {
            it.invokeOnCompletion { cardActionJob = null }
        }
    }

    init {
        onEvent(ReviewerEvent.LoadInitialCard)
    }

    override fun onCleared() {
        cardMediaPlayer.close()
    }

    fun onEvent(event: ReviewerEvent) {
        when (event) {
            is ReviewerEvent.ShowAnswer -> showAnswer()
            is ReviewerEvent.RateCard -> rateCard(event.rating)
            is ReviewerEvent.LoadInitialCard -> loadCard()
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
            is ReviewerEvent.ReplayMedia -> replayMedia()
            is ReviewerEvent.ToggleVoicePlayback -> toggleVoicePlayback()
            is ReviewerEvent.OnVoicePlaybackStateChanged -> onVoicePlaybackStateChanged(event.enabled)
            is ReviewerEvent.DeckOptions -> deckOptions()
            is ReviewerEvent.MediaErrorHandled -> onMediaErrorHandled()
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
        viewModelScope.launch { _effect.emit(ReviewerEffect.ShowDueDateDialog(card)) }
    }

    private fun deleteNote() {
        val card = currentCard ?: return
        viewModelScope.launch { _effect.emit(ReviewerEffect.ShowDeleteNoteDialog(card)) }
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
        
        CollectionManager.withCol {
            val note = card.note(this)
            val allTags = this.tags.all().sorted()
            _currentNoteTags.value = note.tags.toSet()
            _tagsState.value = TagsState.Loaded(allTags)
            
            // Load tags specific to the current deck for filtering
            // Use findNotes with deck query for efficiency instead of iterating over all cards
            val deckName = this.decks.name(card.did)
            val escapedDeckName = deckName.replace("\"", "\\\"")
            val noteIds = this.findNotes("deck:\"$escapedDeckName\"")
            
            // Limit to 1000 notes to prevent extremely slow loads for massive decks
            val tagsInDeck = mutableSetOf<String>()
            for (noteId in noteIds.take(10000)) {
                val deckNote = this.getNote(noteId)
                tagsInDeck.addAll(deckNote.tags)
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
            CollectionManager.withCol {
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
    
    fun addTag(tag: String) {
        viewModelScope.launch {
            CollectionManager.withCol {
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

    private suspend fun reloadCardSuspend() {
        val card = currentCard ?: return

        cardMediaPlayer.loadCardAvTags(card)
        CollectionManager.withCol {
            val note = card.note(this)
            typeAnswer.updateInfo(this, card, getApplication<Application>().resources)
            val renderOutput = card.renderOutput(this, reload = true)

            _state.update {
                it.copy(
                    mediaError = null,
                    html = processHtml(renderOutput.questionText, renderOutput),
                    isAnswerShown = false,
                    showTypeInAnswer = typeAnswer.correct != null,
                    nextTimes = List(4) { "" },
                    chosenAnswer = "",
                    typedAnswer = "",
                    isMarked = note.hasTag(this, "marked"),
                    flag = card.userFlag(),
                    mediaDirectory = this.media.dir,
                    isFinished = false
                )
            }
        }
    }

    private fun editCard() {
        val card = currentCard ?: return
        viewModelScope.launch {
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

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    private fun playAudio(side: String, index: Int) {
        viewModelScope.launch {
            val card = currentCard ?: return@launch
            val avTag = CollectionManager.withCol {
                val renderOutput = card.renderOutput(this)
                when (side) {
                    "q" -> renderOutput.questionAvTags.getOrNull(index)
                    "a" -> renderOutput.answerAvTags.getOrNull(index)
                    else -> null
                }
            }
            if (avTag is SoundOrVideoTag) {
                cardMediaPlayer.playOne(avTag)
            }
        }
    }

    private fun reloadCard() = launchCardAction { reloadCardSuspend() }

    private fun onTypedAnswerChanged(newText: String) {
        _state.update { it.copy(typedAnswer = newText) }
    }

    private fun loadCard() = launchCardAction { loadCardSuspend() }

    private suspend fun getNextCard(): Pair<Card, CurrentQueueState>? = CollectionManager.withCol {
        this.sched.currentQueueState()?.let {
            it.topCard.renderOutput(this, reload = true)
            Pair(it.topCard, it)
        }
    }

    private suspend fun loadCardSuspend() {
        val cardAndQueueState = getNextCard()
        if (cardAndQueueState == null) {
            _state.update {
                it.copy(
                    isFinished = true, newCount = 0, learnCount = 0, reviewCount = 0
                )
            }
            _effect.emit(ReviewerEffect.NavigateToDeckPicker)
            currentCard = null
            queueState = null
            return
        }
        val (card, queue) = cardAndQueueState
        currentCard = card
        queueState = queue
        cardMediaPlayer.loadCardAvTags(card)
        CollectionManager.withCol {
            val note = card.note(this)
            typeAnswer.updateInfo(this, card, getApplication<Application>().resources)
            val renderOutput = card.renderOutput(this)
            _state.update {
                it.copy(
                    mediaError = null,
                    newCount = queue.counts.new,
                    learnCount = queue.counts.lrn,
                    reviewCount = queue.counts.rev,
                    html = processHtml(renderOutput.questionText, renderOutput),
                    isAnswerShown = false,
                    showTypeInAnswer = typeAnswer.correct != null,
                    nextTimes = List(4) { "" },
                    chosenAnswer = "",
                    typedAnswer = "",
                    isMarked = note.hasTag(this, "marked"),
                    flag = card.userFlag(),
                    mediaDirectory = this.media.dir,
                    isFinished = false
                )
            }
        }
        if (cardMediaPlayer.config.autoplay) {
            cardMediaPlayer.playAllForSide(SingleCardSide.FRONT.toCardSide())
        }
    }

    private fun showAnswer() {
        val card = currentCard ?: return
        val queue = queueState ?: return

        launchCardAction {
            CollectionManager.withCol {
                val labels = this.sched.describeNextStates(queue.states)
                typeAnswer.input = _state.value.typedAnswer
                val renderOutput = card.renderOutput(this)
                val answerHtml = typeAnswer.filterAnswer(renderOutput.answerText)

                val paddedLabels = (labels + List(4) { "" }).take(4)

                _state.update {
                    it.copy(
                        html = processHtml(answerHtml, renderOutput),
                        isAnswerShown = true,
                        nextTimes = paddedLabels
                    )
                }
            }
            if (cardMediaPlayer.config.autoplay) {
                cardMediaPlayer.playAllForSide(SingleCardSide.BACK.toCardSide())
            }
        }
    }

    private fun rateCard(rating: CardAnswer.Rating) {
        val queue = queueState ?: return

        launchCardAction {
            var wasLeech = false
            CollectionManager.withCol {
                this.sched.answerCard(queue, rating).also {
                    wasLeech = this.sched.stateIsLeech(queue.states.again)
                }
            }

            if (rating == CardAnswer.Rating.AGAIN && wasLeech) {
                val leechMessage: String = if (queue.topCard.queue.buriedOrSuspended()) {
                    getApplication<Application>().resources.getString(R.string.leech_suspend_notification)
                } else {
                    getApplication<Application>().resources.getString(R.string.leech_notification)
                }
                _effect.emit(ReviewerEffect.ShowSnackbar(leechMessage))
            }

            loadCardSuspend()
        }
    }

    private fun unanswerCard() {
        val card = currentCard ?: return
        viewModelScope.launch {
            CollectionManager.withCol {
                val renderOutput = card.renderOutput(this)
                _state.update {
                    it.copy(
                        html = processHtml(renderOutput.questionText, renderOutput),
                        isAnswerShown = false,
                        nextTimes = List(4) { "" },
                        chosenAnswer = ""
                    )
                }
            }
        }
    }

    private fun toggleMark() {
        viewModelScope.launch {
            val card = currentCard ?: return@launch
            val note = CollectionManager.withCol {
                card.note(this)
            }
            NoteService.toggleMark(note)
            _state.update { it.copy(isMarked = !_state.value.isMarked) }
        }
    }

    private fun setFlag(flag: Int) {
        viewModelScope.launch {
            val card = currentCard ?: return@launch
            CollectionManager.withCol {
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
            CollectionManager.withCol {
                this.sched.buryCards(listOf(card.id))
            }
        }
    }

    private fun suspendCard() {
        performCardAction { card ->
            CollectionManager.withCol {
                this.sched.suspendCards(listOf(card.id))
            }
        }
    }

    private fun processHtml(
        html: String, renderOutput: TemplateManager.TemplateRenderContext.TemplateRenderOutput
    ): String {
        val processedHtml = Sound.replaceAvRefsWith(html, renderOutput) { avTag, avRef ->
            when (avTag) {
                is SoundOrVideoTag -> {
                    val url = "playsound:${avRef.side}:${avRef.index}"
                    val content = avTag.filename.htmlEncode()
                    CardHtmlBuilder.createPlayButton(url, content)
                }

                else -> null
            }
        }
        return CardHtmlBuilder.wrapWithStyles(processedHtml, renderOutput.css)
    }
}
