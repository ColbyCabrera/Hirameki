/*
 * Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.reviewer.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.width
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import anki.scheduler.CardAnswer
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.AnswerFeedback
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * The card as something other than a pair of gestures.
 *
 * With both card gestures on, the answer bar is never composed, so the card is the only thing a screen
 * reader can find on the whole step: it has to say which side is up, take a reader's tap as the turn a
 * finger's tap performs, and offer the four ratings. And while the card is hidden - graded away, or not yet
 * arrived - it keeps both faces' pages loaded behind an alpha of 0, which stops no touch by itself.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DraggableFlashcardTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var answerShown by mutableStateOf(false)
    private var recordedRating by mutableStateOf<AnswerFeedback?>(null)
    private var reveals = 0
    private var unanswers = 0
    private val ratings = mutableListOf<CardAnswer.Rating>()

    /** whether each touch that reached the container around the card had already been taken by the card */
    private val consumedTouches = mutableListOf<Boolean>()

    @Test
    fun `the card is a labelled button a screen reader can turn over and rate`() {
        showCard()
        settle()

        composeTestRule
            .onNodeWithContentDescription(getResourceString(R.string.card_view_question_side))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat("a screen reader's tap reveals the answer", reveals, equalTo(1))

        answerShown = true
        settle()

        val answerSide = getResourceString(R.string.card_view_answer_side)
        composeTestRule.onNodeWithContentDescription(answerSide).performSemanticsAction(SemanticsActions.OnClick)
        assertThat("and turns the card back over", unanswers, equalTo(1))

        composeTestRule
            .onNodeWithContentDescription(answerSide)
            .performCustomAccessibilityActionWithLabel(getResourceString(R.string.ease_button_good))
        assertThat("the ratings on that node are reachable", ratings, equalTo(listOf(CardAnswer.Rating.GOOD)))
    }

    @Test
    fun `a tap on a card that has not arrived never reaches its page`() {
        composeTestRule.mainClock.autoAdvance = false
        showCard()
        composeTestRule.mainClock.advanceTimeByFrame()

        tapTheCard()
        assertThat("the touch reached the container", consumedTouches.isNotEmpty(), equalTo(true))
        assertThat("a hidden card takes it, so its page cannot open a link or replay audio", consumedTouches.all { it }, equalTo(true))

        consumedTouches.clear()
        // robolectric's webview never reports a paint, so the card is let in by its paint timeout
        composeTestRule.mainClock.advanceTimeBy(CardMotionSpec.Default.paintWaitMillis * 2L)
        composeTestRule.waitForIdle()

        tapTheCard()
        assertThat("a card on screen leaves the touch to its page", consumedTouches.any { it }, equalTo(false))
    }

    @Test
    fun `a card dragged into a corner is graded there`() {
        showCard()
        answerShown = true
        settle()

        dragFromTheMiddle { Offset(right, bottom) }

        assertThat("the drag this file relies on grades at all", ratings, equalTo(listOf(CardAnswer.Rating.GOOD)))
    }

    @Test
    fun `a card cannot be graded while its answer is still turning into view`() {
        showCard()
        settle()
        answerShown = true
        // the answer is up, but the back face has not painted it, so the card still shows its question: robolectric's
        // webview never reports a paint, and the turn waits for it until the paint timeout. idle, not past that timeout
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
        val answerSide = getResourceString(R.string.card_view_answer_side)
        assertThat(
            "the answer has not turned into view yet",
            composeTestRule
                .onNodeWithContentDescription(answerSide)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions),
            equalTo(emptyList()),
        )

        // well inside the paint timeout: a graded flight lands within maxDropMillis
        dragFromTheMiddle(advanceClock = false) { Offset(right, bottom) }
        composeTestRule.mainClock.advanceTimeBy(CardMotionSpec.Default.maxDropMillis * 2L)

        assertThat("a card showing its question was graded", ratings, equalTo(emptyList()))
    }

    @Test
    fun `a flick straight down grades neither bottom corner`() {
        showCard()
        answerShown = true
        settle()

        // a pixel to the right of straight down: the good corner's side, by a pixel
        dragFromTheMiddle { Offset(centerX + 1f, bottom) }

        assertThat("straight down is between again and good, and grades neither", ratings, equalTo(emptyList()))
    }

    @Test
    fun `a long press that selected text leaves the card where it is as the finger moves on`() =
        withPageMessages { webViewCompat ->
            showCard()
            answerShown = true
            settle()

            holdThenDragToTheGoodCorner(webViewCompat, selectedText = true)

            assertThat("the card followed the selection into a corner", ratings, equalTo(emptyList()))
        }

    @Test
    fun `a card held still on blank card area before it is dragged is still graded`() =
        withPageMessages { webViewCompat ->
            showCard()
            answerShown = true
            settle()

            holdThenDragToTheGoodCorner(webViewCompat, selectedText = false)

            assertThat("a pause before the drag refused to move the card", ratings, equalTo(listOf(CardAnswer.Rating.GOOD)))
        }

    @Test
    fun `a grade made through the card's actions is confirmed by its corner once it is recorded`() {
        showCard()
        answerShown = true
        settle()
        val good = getResourceString(R.string.ease_button_good)
        val cornerLabel = composeTestRule.onNodeWithText(good)
        val atRest = cornerLabel.getBoundsInRoot().width

        composeTestRule
            .onNodeWithContentDescription(getResourceString(R.string.card_view_answer_side))
            .performCustomAccessibilityActionWithLabel(good)
        composeTestRule.mainClock.autoAdvance = false
        recordedRating = AnswerFeedback(CardAnswer.Rating.GOOD)
        // the label pops once over the ripple, and is back to its size when it ends: sampled a frame at a time
        val widths =
            List(CardMotionSpec.Default.landMillis / FRAME_MILLIS) {
                composeTestRule.mainClock.advanceTimeByFrame()
                cornerLabel.getBoundsInRoot().width
            }

        // switch access and voice access rate through the actions, see no notice of the grade, and saw nothing at all
        assertThat("the good corner pops", widths.max(), greaterThan(atRest))
    }

    /**
     * holds the card past the long-press timeout, moves past the touch slop before the page has said what the press did,
     * then has the page say it and drags on into the good corner
     */
    // the listeners are captured from the mocked static call; no real webview is asked for the web message feature
    @SuppressLint("RequiresFeature")
    private fun holdThenDragToTheGoodCorner(
        webViewCompat: MockedStatic<WebViewCompat>,
        selectedText: Boolean,
    ) {
        // the clock stands still while the finger is down: a card picked up too early is never idle under a finger, and
        // the test has to fail on where the card went rather than on waiting for idle
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onRoot().performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(lerp(center, Offset(right, bottom), 1f / DRAG_STEPS))
        }
        val listeners = argumentCaptor<WebViewCompat.WebMessageListener>()
        webViewCompat.verify(
            { WebViewCompat.addWebMessageListener(any(), eq(LONG_PRESS_BRIDGE), any(), listeners.capture()) },
            atLeastOnce(),
        )
        val report = WebMessageCompat(if (selectedText) LONG_PRESS_SELECTED else "none")
        // only the face on show is touched, so only its page reports; telling both faces changes nothing
        composeTestRule.runOnUiThread { listeners.allValues.forEach { it.onPostMessage(mock(), report, BASE_URL.toUri(), true, mock()) } }
        composeTestRule.onRoot().performTouchInput {
            for (step in 2..DRAG_STEPS) moveTo(lerp(center, Offset(right, bottom), step.toFloat() / DRAG_STEPS))
            up()
        }
        composeTestRule.mainClock.autoAdvance = true
        settle()
    }

    /** runs [block] with webviews that can message the app, as every webview the app supports can */
    private fun withPageMessages(block: (MockedStatic<WebViewCompat>) -> Unit) {
        mockStatic(WebViewFeature::class.java).use { feature ->
            feature.`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }.thenReturn(true)
            mockStatic(WebViewCompat::class.java).use(block)
        }
    }

    /** a quick drag from the card's middle to [end], computed in the touch input scope */
    private fun dragFromTheMiddle(
        advanceClock: Boolean = true,
        end: TouchInjectionScope.() -> Offset,
    ) {
        composeTestRule.onRoot().performTouchInput {
            val target = end()
            down(center)
            repeat(DRAG_STEPS) { step -> moveTo(lerp(center, target, (step + 1f) / DRAG_STEPS)) }
            up()
        }
        if (advanceClock) settle()
    }

    private fun tapTheCard() =
        composeTestRule.onRoot().performTouchInput {
            down(center)
            up()
        }

    /**
     * lets the card's entrance and any turn finish, past the paint timeout that robolectric's webview (it never reports a
     * paint) leaves them to wait out. idle first, so a change made just before, such as the answer being shown, has
     * started its wait before the clock moves: a card only offers its ratings once the answer has turned into view
     */
    private fun settle() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(CardMotionSpec.Default.paintWaitMillis * 2L)
        composeTestRule.waitForIdle()
    }

    private fun showCard() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        // the main pass runs from the card outwards, so a touch the card took
                                        // on the initial pass arrives here already consumed
                                        awaitPointerEvent(PointerEventPass.Main).changes.forEach {
                                            consumedTouches += it.isConsumed
                                        }
                                    }
                                }
                            },
                ) {
                    DraggableFlashcard(
                        cardKey = 1L,
                        baseUrl = BASE_URL,
                        questionHtml = "question",
                        answerHtml = "answer",
                        bodyClass = "card",
                        isMediaAutoplayEnabled = false,
                        javascriptCommand = null,
                        onJavascriptCommandConsumed = {},
                        onLinkClick = {},
                        isAnswerShown = answerShown,
                        tapToFlip = true,
                        dragToGrade = true,
                        nextTimes = List(4) { "" },
                        replayFinished = 0,
                        onShowAnswer = { reveals += 1 },
                        onUnanswer = { unanswers += 1 },
                        onRateCard = { ratings += it },
                        recordedRating = recordedRating,
                    )
                }
            }
        }
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"

        /** moves in a drag: at the injector's 16 ms a move, a quick flick */
        const val DRAG_STEPS = 12

        /** the test clock's frame */
        const val FRAME_MILLIS = 16
    }
}
