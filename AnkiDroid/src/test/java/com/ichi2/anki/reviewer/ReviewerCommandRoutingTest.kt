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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReviewerCommandRoutingTest {
    private val question = ReviewerState(isAnswerShown = false)
    private val answer = ReviewerState(isAnswerShown = true)

    @Test
    fun `show answer shows the answer only on the question side`() {
        assertEquals(send(ReviewerEvent.ShowAnswer), ViewerCommand.SHOW_ANSWER.route(question))
        assertEquals(CommandRoute.NotApplicable, ViewerCommand.SHOW_ANSWER.route(answer))
    }

    @Test
    fun `flip or answer flips the question and rates the answer`() {
        val ratings =
            mapOf(
                ViewerCommand.FLIP_OR_ANSWER_EASE1 to Rating.AGAIN,
                ViewerCommand.FLIP_OR_ANSWER_EASE2 to Rating.HARD,
                ViewerCommand.FLIP_OR_ANSWER_EASE3 to Rating.GOOD,
                ViewerCommand.FLIP_OR_ANSWER_EASE4 to Rating.EASY,
            )
        for ((command, rating) in ratings) {
            assertEquals(send(ReviewerEvent.ShowAnswer), command.route(question), "$command on question")
            assertEquals(send(ReviewerEvent.RateCard(rating)), command.route(answer), "$command on answer")
        }
    }

    @Test
    fun `toggling a flag unsets it when the card already has it`() {
        val unflagged = ReviewerState(flag = Flag.NONE.code)
        val red = ReviewerState(flag = Flag.RED.code)

        assertEquals(send(ReviewerEvent.SetFlag(Flag.RED.code)), ViewerCommand.TOGGLE_FLAG_RED.route(unflagged))
        assertEquals(send(ReviewerEvent.SetFlag(Flag.NONE.code)), ViewerCommand.TOGGLE_FLAG_RED.route(red))
        assertEquals(send(ReviewerEvent.SetFlag(Flag.BLUE.code)), ViewerCommand.TOGGLE_FLAG_BLUE.route(red))
        assertEquals(send(ReviewerEvent.SetFlag(Flag.NONE.code)), ViewerCommand.UNSET_FLAG.route(red))
    }

    @Test
    fun `card actions go to the view model`() {
        val expected =
            mapOf(
                ViewerCommand.UNDO to ReviewerEvent.Undo,
                ViewerCommand.EDIT to ReviewerEvent.EditCard,
                ViewerCommand.TAG to ReviewerEvent.EditTags,
                ViewerCommand.MARK to ReviewerEvent.ToggleMark,
                ViewerCommand.BURY_CARD to ReviewerEvent.BuryCard,
                ViewerCommand.SUSPEND_CARD to ReviewerEvent.SuspendCard,
                ViewerCommand.DELETE to ReviewerEvent.DeleteNote,
                ViewerCommand.RESCHEDULE_NOTE to ReviewerEvent.RescheduleCard,
                ViewerCommand.PLAY_MEDIA to ReviewerEvent.ReplayMedia,
            )
        for ((command, event) in expected) {
            assertEquals(send(event), command.route(question), command.name)
        }
    }

    @Test
    fun `card WebView commands run a script in the card`() {
        assertEquals(send(ReviewerEvent.RunCardScript(SHOW_HINT_SCRIPT)), ViewerCommand.SHOW_HINT.route(question))
        assertEquals(send(ReviewerEvent.RunCardScript(SHOW_ALL_HINTS_SCRIPT)), ViewerCommand.SHOW_ALL_HINTS.route(question))
        assertEquals(send(ReviewerEvent.RunCardScript(userActionScript(4))), ViewerCommand.USER_ACTION_4.route(question))
        assertEquals(send(ReviewerEvent.RunCardScript(scrollPageScript(up = true))), ViewerCommand.PAGE_UP.route(question))
        assertEquals(send(ReviewerEvent.RunCardScript(scrollPageScript(up = false))), ViewerCommand.PAGE_DOWN.route(question))
    }

    @Test
    fun `user action script calls the user's function`() {
        assertEquals(
            """(() => { const f = globalThis["userJs7"]; if (typeof f === "function") f(); })();""",
            userActionScript(7),
        )
    }

    @Test
    fun `activity-level commands are not routed`() {
        for (command in listOf(ViewerCommand.EXIT, ViewerCommand.REDO, ViewerCommand.ADD_NOTE, ViewerCommand.CARD_INFO)) {
            assertIs<CommandRoute.Unrouted>(command.route(question), command.name)
        }
    }

    private fun send(event: ReviewerEvent) = CommandRoute.Send(event)
}
