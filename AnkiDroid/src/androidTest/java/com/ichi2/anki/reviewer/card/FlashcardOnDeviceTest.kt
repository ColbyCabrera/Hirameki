/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.reviewer.card

import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.compose.Flashcard
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/** Runs the real page scripts, which Robolectric's WebView does not. */
@RunWith(AndroidJUnit4::class)
class FlashcardOnDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var display by mutableStateOf(card("<p id=\"shown\">Question</p>"))
    private var command by mutableStateOf<ReviewerJavascriptCommand?>(null)
    private val consumed = mutableListOf<Int>()

    @Before
    fun showFlashcard() {
        composeRule.setContent {
            AnkiDroidTheme {
                Flashcard(
                    display = display,
                    isMediaAutoplayEnabled = false,
                    javascriptCommand = command,
                    onJavascriptCommandConsumed = { id ->
                        consumed += id
                        if (command?.id == id) command = null
                    },
                    onGesture = {},
                    onLinkClick = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun sidesAndCardsShareOnePage() =
        runBlocking {
            awaitShownText("Question")
            evaluate("window.samePageMarker = 'kept'")

            show(card("<p id=\"shown\">Question</p>", answer = "<p id=\"shown\">Answer</p>", isAnswerShown = true))
            awaitShownText("Answer")
            show(card("<p id=\"shown\">Next</p>", index = 2))
            awaitShownText("Next")

            assertThat(evaluate("window.samePageMarker"), equalTo("\"kept\""))
        }

    @Test
    fun eachSideHasItsOwnFontSize() =
        runBlocking {
            awaitShownText("Question")
            val questionSize = bodyFontSize()

            show(card("<p id=\"shown\">Question</p>", answer = "<p id=\"shown\">Answer</p>", isAnswerShown = true))
            awaitShownText("Answer")

            assertThat(bodyFontSize(), not(equalTo(questionSize)))
        }

    @Test
    fun viewportHeightUnitsAreNotZero() =
        runBlocking {
            show(card("<div id=\"shown\" style=\"height: 50vh\">Tall</div>", index = 2))
            awaitShownText("Tall")

            assertThat(evaluate("document.getElementById('shown').clientHeight").toInt(), greaterThan(0))
        }

    @Test
    fun aCommandRunsOnceAfterItsCard() =
        runBlocking {
            awaitShownText("Question")

            show(card("<p id=\"shown\">Next</p>", index = 2))
            sendCommand(
                ReviewerJavascriptCommand(
                    id = 1,
                    script =
                        "window.commandSaw = document.getElementById('shown').innerText;" +
                            "window.commandRuns = (window.commandRuns || 0) + 1;",
                ),
            )
            awaitShownText("Next")

            awaitScriptResult("window.commandRuns", "1")
            assertThat(evaluate("window.commandSaw"), equalTo("\"Next\""))
            assertThat(consumed, contains(1))
        }

    @Test
    fun aThrowingCommandDoesNotStopLaterCards() =
        runBlocking {
            awaitShownText("Question")

            sendCommand(ReviewerJavascriptCommand(id = 1, script = "throw new Error('from a card')"))
            show(card("<p id=\"shown\">Next</p>", index = 2))

            awaitShownText("Next")
        }

    @Test
    fun imageOcclusionDrawsMasksOnEveryShow() =
        runBlocking {
            awaitShownText("Question")

            for (index in 2L..6L) {
                show(card(imageOcclusionHtml(), index = index))
                awaitScriptResult(PAINTED_MASK_PIXELS_ARE_PRESENT, "true")
            }
        }

    private fun show(next: CardDisplay) {
        display = next
        composeRule.waitForIdle()
    }

    private fun sendCommand(next: ReviewerJavascriptCommand) {
        command = next
        composeRule.waitForIdle()
    }

    private suspend fun bodyFontSize() = evaluate("getComputedStyle(document.body).fontSize")

    private suspend fun awaitShownText(text: String) = awaitScriptResult("document.getElementById('shown')?.innerText", "\"$text\"")

    private suspend fun awaitScriptResult(
        script: String,
        expected: String,
    ) = withTimeout(10.seconds) {
        while (evaluate(script) != expected) delay(16)
    }

    private suspend fun evaluate(script: String): String =
        withContext(Dispatchers.Main) {
            val webView = findWebView(composeRule.activity.window.decorView)!!
            suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(script) { continuation.resume(it ?: "null") }
            }
        }

    private fun findWebView(view: View): WebView? =
        view as? WebView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findWebView(group.getChildAt(it)) }
            }

    private fun imageOcclusionHtml(): String =
        """
        <div style="display: none">
            <div class="cloze" data-ordinal="1" data-shape="rect" data-left=".1" data-top=".1"
                data-width=".3" data-height=".2" data-occludeinactive="1"></div>
        </div>
        <div id="err"></div>
        <div id="image-occlusion-container">
            <img src="data:image/png;base64,${whitePngBase64()}">
            <canvas id="image-occlusion-canvas"></canvas>
        </div>
        <script>anki.imageOcclusion.setup();</script>
        """.trimIndent()

    private fun whitePngBase64(): String {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun card(
        question: String,
        answer: String = "<p id=\"shown\">Answer</p>",
        index: Long = 1,
        isAnswerShown: Boolean = false,
    ) = CardDisplay(index, isAnswerShown, question, answer, "card card1", BASE_URL)

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"

        val PAINTED_MASK_PIXELS_ARE_PRESENT =
            """
            (() => {
                const canvas = document.getElementById("image-occlusion-canvas");
                if (!canvas || canvas.width === 0 || canvas.height === 0) return false;
                const pixels = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
                for (let i = 3; i < pixels.length; i += 4) if (pixels[i] > 0) return true;
                return false;
            })()
            """.trimIndent()
    }
}
