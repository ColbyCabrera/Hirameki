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
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/** Runs the real page scripts and draws real frames, which Robolectric and the Compose test clock do not. */
@RunWith(AndroidJUnit4::class)
class FlashcardOnDeviceTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private lateinit var activity: ComponentActivity
    private var display by mutableStateOf(card(shownText("Question")))
    private var command by mutableStateOf<ReviewerJavascriptCommand?>(null)
    private val consumed = mutableListOf<Int>()
    private var themeSurfaceColor = 0

    @Before
    fun showFlashcard() {
        activityRule.scenario.onActivity {
            activity = it
            it.setContent {
                AnkiDroidTheme {
                    val surface = MaterialTheme.colorScheme.surface.toArgb()
                    SideEffect { themeSurfaceColor = surface }
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
        }
    }

    @Test
    fun eachSideHasItsOwnFontSize() =
        runBlocking {
            awaitShownText("Question")
            val questionSize = bodyFontSize()

            show(card(shownText("Question"), answer = shownText("Answer"), isAnswerShown = true))
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

    @After
    fun disableAnimationsAgain() {
        setAnimatorDurationScale(0)
    }

    @Test
    fun aCommandRunsOnceAfterItsCard() =
        runBlocking {
            awaitShownText("Question")
            awaitCrossfadeReady()

            show(card(shownText("Next"), index = 2))
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
            show(card(shownText("Next"), index = 2))

            awaitShownText("Next")
        }

    @Test
    fun imageOcclusionDrawsMasksOnEveryShow() =
        runBlocking {
            awaitShownText("Question")

            for (index in 2L..3L) {
                show(card(imageOcclusionHtml(), index = index))
                awaitScriptResult(PAINTED_MASK_PIXELS_ARE_PRESENT, "true")
            }
        }

    @Test
    fun crossfadingBetweenCardsBlendsOnlyTheirBackgrounds() =
        runBlocking {
            startSlowMotion()
            show(card(backgroundCard(BLUE, "Blue"), index = 2))
            awaitShownText("Blue")
            awaitCrossfadeReady()

            val samples = sampleScreenWhile { show(card(backgroundCard(GREEN, "Green"), index = 3)) }

            assertBlendsOnly(samples, from = BLUE, to = GREEN)
        }

    @Test
    fun crossfadingBetweenSidesBlendsOnlyTheirBackgrounds() =
        runBlocking {
            startSlowMotion()
            val question = backgroundCard(BLUE, "Blue")
            val answer = backgroundCard(GREEN, "Green")
            show(card(question, answer = answer, index = 2))
            awaitShownText("Blue")
            awaitCrossfadeReady()

            val samples = sampleScreenWhile { show(card(question, answer = answer, index = 2, isAnswerShown = true)) }

            assertBlendsOnly(samples, from = BLUE, to = GREEN)
        }

    @Test
    fun aNewCardDuringTheFadeNeverShowsTheThemeColour() =
        runBlocking {
            startSlowMotion()
            show(card(backgroundCard(BLUE, "Blue"), index = 2))
            awaitShownText("Blue")
            awaitCrossfadeReady()

            val samples =
                sampleScreenWhile {
                    show(card(backgroundCard(GREEN, "Green"), index = 3))
                    delay(MID_FADE_DELAY_MS)
                    show(card(backgroundCard(RED, "Red"), index = 4))
                }

            assertThat("ends on the last card", samples.last().isCloseTo(RED), equalTo(true))
            assertThat(samples.filter { it.isCloseTo(themeSurfaceColor) }, empty())
        }

    @Test
    fun aNewCardResetsPinchZoom() =
        runBlocking {
            awaitShownText("Question")
            onMain { requireWebView().zoomBy(2f) }
            awaitScriptResult("visualViewport.scale > 1.5", "true")

            show(card(shownText("Next"), index = 2))

            awaitShownText("Next")
            awaitScriptResult("Math.abs(visualViewport.scale - 1) < 0.01", "true")
        }

    @Test
    fun aZoomResetThatNeverFinishesDoesNotStopLaterCards() =
        runBlocking {
            awaitShownText("Question")

            evaluate("hirameki.awaitZoomReset(5)")
            show(card(shownText("Next"), index = 2))

            awaitShownText("Next")
        }

    @Test
    fun theCardComesBackAfterARendererLoss() =
        runBlocking {
            awaitShownText("Question")
            awaitCrossfadeReady()

            crashRendererAndAwaitReplacement()

            awaitShownText("Question")
            show(card(shownText("Next"), index = 2))
            awaitShownText("Next")
        }

    private suspend fun crashRendererAndAwaitReplacement() {
        val crashed = onMain { requireWebView() }
        onMain { crashed.loadUrl("chrome://crash") }
        withTimeout(10.seconds) {
            while (onMain { findWebView(activity.window.decorView) }.let { it == null || it === crashed }) delay(16)
        }
    }

    /** Compose starts watching this setting at a composition's first animation, so one fade has to run first. */
    private suspend fun startSlowMotion() {
        awaitShownText("Question")
        awaitCrossfadeReady()
        show(card(shownText("Warm-up"), index = WARM_UP_INDEX))
        awaitShownText("Warm-up")
        awaitCrossfadeReady()
        setAnimatorDurationScale(SLOW_MOTION)
        delay(ANIMATION_SETTING_DELAY_MS)
    }

    /** Reading to the end waits for the command to finish. */
    private fun setAnimatorDurationScale(scale: Int) =
        ParcelFileDescriptor
            .AutoCloseInputStream(
                InstrumentationRegistry
                    .getInstrumentation()
                    .uiAutomation
                    .executeShellCommand("settings put global animator_duration_scale $scale"),
            ).use { it.readBytes() }

    private fun assertBlendsOnly(
        samples: List<Int>,
        from: Int,
        to: Int,
    ) {
        assertThat("ends on the new card", samples.last().isCloseTo(to), equalTo(true))
        assertThat("only blends of the two cards", samples.filterNot { it.isBlendOf(from, to) }.map(::hex), empty())
        assertThat("a fade, not a cut (${samples.map(::hex)})", (samples.toSet() - setOf(from, to)).size, greaterThan(0))
    }

    private suspend fun show(next: CardDisplay) = onMain { display = next }

    private suspend fun sendCommand(next: ReviewerJavascriptCommand) = onMain { command = next }

    private suspend fun bodyFontSize() = evaluate("getComputedStyle(document.body).fontSize")

    private suspend fun awaitShownText(text: String) = awaitScriptResult("document.getElementById('shown')?.innerText", "\"$text\"")

    private suspend fun awaitScriptResult(
        script: String,
        expected: String,
    ) = withTimeout(10.seconds) {
        while (evaluate(script) != expected) delay(16)
    }

    /** The page reports a shown card a few frames after its text appears. */
    private suspend fun awaitCrossfadeReady() = delay(CROSSFADE_READY_DELAY_MS)

    private suspend fun evaluate(script: String): String =
        onMain {
            val webView = requireWebView()
            suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(script) { continuation.resume(it ?: "null") }
            }
        }

    private fun requireWebView() = findWebView(activity.window.decorView)!!

    private fun findWebView(view: View): WebView? =
        view as? WebView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findWebView(group.getChildAt(it)) }
            }

    private suspend fun <T> onMain(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun sampleScreenWhile(change: suspend () -> Unit): List<Int> {
        val samples = mutableListOf(screenSample())
        change()
        val start = System.nanoTime()
        while ((System.nanoTime() - start) / 1_000_000 < SAMPLING_MS) {
            samples += screenSample()
            delay(SAMPLE_INTERVAL_MS)
        }
        return samples
    }

    /** A patch of card background: to the right of the centred text, clear of any other element. */
    private suspend fun screenSample(): Int =
        onMain {
            val decor = activity.window.decorView
            val x = (decor.width * 0.9f).toInt()
            val y = (decor.height * 0.6f).toInt()
            val bitmap = createBitmap(SAMPLE_SIZE, SAMPLE_SIZE)
            suspendCancellableCoroutine { continuation ->
                PixelCopy.request(
                    activity.window,
                    Rect(x, y, x + SAMPLE_SIZE, y + SAMPLE_SIZE),
                    bitmap,
                    { continuation.resume(Unit) },
                    Handler(Looper.getMainLooper()),
                )
            }
            bitmap[SAMPLE_SIZE / 2, SAMPLE_SIZE / 2]
        }

    private fun Int.channels() = listOf(this shr 16 and 0xFF, this shr 8 and 0xFF, this and 0xFF)

    private fun Int.isCloseTo(other: Int) = channels().zip(other.channels()).all { (a, b) -> abs(a - b) <= COLOR_TOLERANCE }

    private fun Int.isBlendOf(
        from: Int,
        to: Int,
    ): Boolean {
        val sample = channels()
        val start = from.channels()
        val end = to.channels()
        return (0..BLEND_STEPS).any { step ->
            val amount = step.toFloat() / BLEND_STEPS
            sample.indices.all { abs(sample[it] - (start[it] + (end[it] - start[it]) * amount)) <= COLOR_TOLERANCE }
        }
    }

    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    private fun shownText(text: String) = "<p id=\"shown\">$text</p>"

    private fun backgroundCard(
        color: Int,
        text: String,
    ) = "<style>html, body.card { background: ${hex(color)} !important; }</style>${shownText(text)}"

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
        val bitmap = createBitmap(800, 600).apply { eraseColor(android.graphics.Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun card(
        question: String,
        answer: String = shownText("Answer"),
        index: Long = 1,
        isAnswerShown: Boolean = false,
    ) = CardDisplay(index, isAnswerShown, question, answer, "card card1", BASE_URL)

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1/"
        const val BLUE = 0xFF1E5AA0.toInt()
        const val GREEN = 0xFF2E7D32.toInt()
        const val RED = 0xFFA01E1E.toInt()
        const val SAMPLE_SIZE = 8
        const val COLOR_TOLERANCE = 10
        const val BLEND_STEPS = 50

        /** Gives a fade many frames even on a software-rendered emulator. */
        const val SLOW_MOTION = 10
        const val WARM_UP_INDEX = 100L
        const val ANIMATION_SETTING_DELAY_MS = 500L
        const val SAMPLING_MS = 4000L

        /** Leaves the main thread free to draw the fade. */
        const val SAMPLE_INTERVAL_MS = 8L
        const val MID_FADE_DELAY_MS = 800L
        const val CROSSFADE_READY_DELAY_MS = 1500L

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
