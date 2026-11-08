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

package com.ichi2.anki.reviewer.spike

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 0 of docs/SINGLE_WEBVIEW_REVIEWER_PLAN.md. Reports measurements; only fails if the page can't load. */
@RunWith(AndroidJUnit4::class)
class CardRenderingSpikeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var harness: CardPageHarness

    @Before
    fun startPage() =
        runBlocking {
            harness = CardPageHarness(composeRule)
            assumeTrue("WebMessageListener is required for ready messages", harness.isWebMessageListenerSupported)
            harness.start()
        }

    @Test
    fun captureMethods() =
        runBlocking {
            val report = SpikeReport("capture")
            val floatingUi = harness.floatingUiCenterFraction()
            for ((index, background) in listOf(BLUE, GREEN).withIndex()) {
                harness.show(token = index + 1, html = colouredCardWithCanvas(background, "Card ${index + 1}"))
                letCaptureLayersCatchUp()
                for (method in CaptureMethod.entries) {
                    val durations = mutableListOf<Double>()
                    lateinit var capture: Capture
                    repeat(CAPTURE_RUNS) {
                        capture = harness.capture(method)
                        durations += capture.ms
                    }
                    val backgroundPixel = capture.bitmap.sample(0.85f, 0.55f)
                    val canvasPixel = capture.bitmap.sample(0.2f, 0.375f)
                    val floatingUiPixel = capture.bitmap.sample(floatingUi.x, floatingUi.y)
                    report.timings("$method, card ${index + 1}", durations)
                    report.add(
                        "  succeeded=${capture.succeeded}" +
                            " background=${backgroundPixel.isCloseTo(background)} (${backgroundPixel.toHex()})" +
                            " canvas=${canvasPixel.isCloseTo(CANVAS_RED)} (${canvasPixel.toHex()})" +
                            " floatingUiExcluded=${floatingUiPixel.isCloseTo(background)} (${floatingUiPixel.toHex()})",
                    )
                }
            }
            report.write()
        }

    @Test
    fun showLatency() =
        runBlocking {
            val report = SpikeReport("latency")
            val ready = mutableListOf<Double>()
            val drawn = mutableListOf<Double>()
            repeat(LATENCY_RUNS) { run ->
                val isAnswer = run % 2 == 1
                val timing =
                    harness.show(
                        token = run + 1,
                        html = textCard(if (isAnswer) "Answer ${run / 2}" else "Question ${run / 2}"),
                        isAnswer = isAnswer,
                    )
                ready += timing.readyMs
                drawn += timing.drawnMs
            }
            report.timings("show to ready message", ready)
            report.timings("show to visual state callback", drawn)
            report.write()
        }

    @Test
    fun topLevelDeclarationsAcrossCards() =
        runBlocking {
            val report = SpikeReport("redeclaration")
            val html =
                "<div>Declares a top-level const</div>" +
                    "<script>const spikeTopLevel = 1; window.spikeRuns = (window.spikeRuns || 0) + 1;</script>"
            harness.show(token = 1, html = html)
            harness.show(token = 2, html = html)
            report.add("script runs after two cards: ${harness.evaluate("window.spikeRuns")} (2 = no error)")
            report.add("console: ${harness.consoleMessages.filter { "declared" in it }}")
            report.write()
        }

    private suspend fun letCaptureLayersCatchUp() {
        composeRule.waitForIdle()
        delay(200)
    }

    private fun colouredCardWithCanvas(
        background: Int,
        text: String,
    ): String =
        """
        <style>html, body.card { background: ${background.toHex()} !important; }</style>
        <div style="font-size: 32px; color: white">$text</div>
        <canvas id="spikeCanvas" width="300" height="150"
            style="position: fixed; left: 5vw; top: 30vh; width: 30vw; height: 15vh"></canvas>
        <script>
            (() => {
                const context = document.getElementById("spikeCanvas").getContext("2d");
                context.fillStyle = "${CANVAS_RED.toHex()}";
                context.fillRect(0, 0, 300, 150);
            })();
        </script>
        """.trimIndent()

    private fun textCard(text: String) = "<div style=\"font-size: 32px\">$text</div>"

    private companion object {
        const val CAPTURE_RUNS = 15
        const val LATENCY_RUNS = 30
        const val BLUE = 0xFF1E5AA0.toInt()
        const val GREEN = 0xFF2E7D32.toInt()
        const val CANVAS_RED = 0xFFE01010.toInt()
    }
}
