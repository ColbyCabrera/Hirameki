/*
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

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.Flag
import com.ichi2.anki.cardviewer.ViewerCommand

/**
 * How the Compose reviewer handles a [ViewerCommand] coming from a gesture, a shake or a key.
 *
 * The legacy implementations in `AbstractFlashcardViewer` draw into a card WebView which the
 * Compose reviewer does not have, so commands which affect the card must go to [ReviewerViewModel].
 */
internal sealed interface CommandRoute {
    /** Send [event] to the [ReviewerViewModel]. */
    data class Send(
        val event: ReviewerEvent,
    ) : CommandRoute

    /** The command does not apply right now, e.g. 'show answer' while the answer is shown. */
    data object NotApplicable : CommandRoute

    /** The ViewModel has no equivalent; the activity handles the command. */
    data object Unrouted : CommandRoute
}

internal fun ViewerCommand.route(state: ReviewerState): CommandRoute {
    fun send(event: ReviewerEvent) = CommandRoute.Send(event)

    fun flipOrAnswer(rating: Rating) = send(if (state.isAnswerShown) ReviewerEvent.RateCard(rating) else ReviewerEvent.ShowAnswer)

    fun toggleFlag(flag: Flag) = send(ReviewerEvent.SetFlag(if (state.flag == flag.code) Flag.NONE.code else flag.code))

    fun userAction(number: Int) = send(ReviewerEvent.RunCardScript(userActionScript(number)))

    return when (this) {
        ViewerCommand.SHOW_ANSWER ->
            if (state.isAnswerShown) CommandRoute.NotApplicable else send(ReviewerEvent.ShowAnswer)
        ViewerCommand.FLIP_OR_ANSWER_EASE1 -> flipOrAnswer(Rating.AGAIN)
        ViewerCommand.FLIP_OR_ANSWER_EASE2 -> flipOrAnswer(Rating.HARD)
        ViewerCommand.FLIP_OR_ANSWER_EASE3 -> flipOrAnswer(Rating.GOOD)
        ViewerCommand.FLIP_OR_ANSWER_EASE4 -> flipOrAnswer(Rating.EASY)
        ViewerCommand.UNDO -> send(ReviewerEvent.Undo)
        ViewerCommand.EDIT -> send(ReviewerEvent.EditCard)
        ViewerCommand.TAG -> send(ReviewerEvent.EditTags)
        ViewerCommand.MARK -> send(ReviewerEvent.ToggleMark)
        ViewerCommand.BURY_CARD -> send(ReviewerEvent.BuryCard)
        ViewerCommand.SUSPEND_CARD -> send(ReviewerEvent.SuspendCard)
        ViewerCommand.DELETE -> send(ReviewerEvent.DeleteNote)
        ViewerCommand.RESCHEDULE_NOTE -> send(ReviewerEvent.RescheduleCard)
        ViewerCommand.PLAY_MEDIA -> send(ReviewerEvent.ReplayMedia)
        ViewerCommand.TOGGLE_FLAG_RED -> toggleFlag(Flag.RED)
        ViewerCommand.TOGGLE_FLAG_ORANGE -> toggleFlag(Flag.ORANGE)
        ViewerCommand.TOGGLE_FLAG_GREEN -> toggleFlag(Flag.GREEN)
        ViewerCommand.TOGGLE_FLAG_BLUE -> toggleFlag(Flag.BLUE)
        ViewerCommand.TOGGLE_FLAG_PINK -> toggleFlag(Flag.PINK)
        ViewerCommand.TOGGLE_FLAG_TURQUOISE -> toggleFlag(Flag.TURQUOISE)
        ViewerCommand.TOGGLE_FLAG_PURPLE -> toggleFlag(Flag.PURPLE)
        ViewerCommand.UNSET_FLAG -> send(ReviewerEvent.SetFlag(Flag.NONE.code))
        ViewerCommand.SHOW_HINT -> send(ReviewerEvent.RunCardScript(SHOW_HINT_SCRIPT))
        ViewerCommand.SHOW_ALL_HINTS -> send(ReviewerEvent.RunCardScript(SHOW_ALL_HINTS_SCRIPT))
        ViewerCommand.PAGE_UP -> send(ReviewerEvent.RunCardScript(scrollPageScript(up = true)))
        ViewerCommand.PAGE_DOWN -> send(ReviewerEvent.RunCardScript(scrollPageScript(up = false)))
        ViewerCommand.USER_ACTION_1 -> userAction(1)
        ViewerCommand.USER_ACTION_2 -> userAction(2)
        ViewerCommand.USER_ACTION_3 -> userAction(3)
        ViewerCommand.USER_ACTION_4 -> userAction(4)
        ViewerCommand.USER_ACTION_5 -> userAction(5)
        ViewerCommand.USER_ACTION_6 -> userAction(6)
        ViewerCommand.USER_ACTION_7 -> userAction(7)
        ViewerCommand.USER_ACTION_8 -> userAction(8)
        ViewerCommand.USER_ACTION_9 -> userAction(9)
        ViewerCommand.REDO,
        ViewerCommand.EXIT,
        ViewerCommand.BURY_NOTE,
        ViewerCommand.SUSPEND_NOTE,
        ViewerCommand.CARD_INFO,
        ViewerCommand.RECORD_VOICE,
        ViewerCommand.SAVE_VOICE,
        ViewerCommand.REPLAY_VOICE,
        ViewerCommand.TOGGLE_WHITEBOARD,
        ViewerCommand.TOGGLE_ERASER,
        ViewerCommand.CLEAR_WHITEBOARD,
        ViewerCommand.CHANGE_WHITEBOARD_PEN_COLOR,
        ViewerCommand.ADD_NOTE,
        ViewerCommand.TOGGLE_AUTO_ADVANCE,
        -> CommandRoute.Unrouted
    }
}

// The Compose card page doesn't load scripts/card.js, so these are inlined.

internal const val SHOW_HINT_SCRIPT =
    """document.querySelector("a.hint:not([style*='display: none'])")?.click();"""

internal const val SHOW_ALL_HINTS_SCRIPT =
    """document.querySelectorAll("a.hint").forEach(el => el.click());"""

internal fun userActionScript(number: Int) = """(() => { const f = globalThis["userJs$number"]; if (typeof f === "function") f(); })();"""

internal fun scrollPageScript(up: Boolean): String {
    val sign = if (up) "-" else ""
    return """window.scrollBy({ top: ${sign}window.innerHeight, behavior: "smooth" });"""
}
