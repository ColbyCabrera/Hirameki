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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.previewer.bodyClassForCardOrd
import com.ichi2.anki.reviewer.CardHtmlBuilder
import com.ichi2.anki.tests.InstrumentedTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Image occlusion on one persistent page, without Flashcard.kt's IO_SETUP_INTERCEPT workarounds. */
@RunWith(AndroidJUnit4::class)
class ImageOcclusionSpikeTest : InstrumentedTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun masksDrawOnEverySide() =
        runBlocking {
            val report = SpikeReport("image-occlusion")
            val collection = emptyCol
            val cards = addOcclusionNote(collection)
            val harness = CardPageHarness(composeRule, mediaDir = collection.media.dir)
            assumeTrue("WebMessageListener is required for ready messages", harness.isWebMessageListenerSupported)
            harness.start()

            var token = 0
            var drawnSides = 0
            val timesToMasks = mutableListOf<Double>()
            repeat(ROUNDS) { round ->
                for (card in cards) {
                    for (isAnswer in listOf(false, true)) {
                        val (question, answer) = card.html(collection)
                        harness.show(
                            token = ++token,
                            html = if (isAnswer) answer else question,
                            isAnswer = isAnswer,
                            answerHtml = answer,
                            bodyClass = bodyClassForCardOrd(card.ord, nightMode = false),
                        )
                        val (state, ms) = harness.awaitMasks()
                        val side = if (isAnswer) "answer" else "question"
                        report.add("round ${round + 1}, card ${card.ord + 1} $side: $state after %.0f ms".format(ms))
                        if (state.startsWith("drawn")) {
                            drawnSides++
                            timesToMasks += ms
                        }
                    }
                }
            }
            report.add("masks drawn on $drawnSides of $token sides")
            if (timesToMasks.isNotEmpty()) report.timings("visual state callback to masks drawn", timesToMasks)
            report.write()
        }

    private fun addOcclusionNote(collection: Collection): List<Card> {
        collection.addImageOcclusionNoteType()
        val notetype = collection.notetypes.all().first { it.isImageOcclusion }
        val image = File(testDir, "spike-occlusion.png").also { writeOcclusionImage(it) }
        collection.addImageOcclusionNote(
            noteTypeId = notetype.id,
            imagePath = image.absolutePath,
            occlusions =
                "{{c1::image-occlusion:rect:left=.1:top=.1:width=.3:height=.2:oi=1}}" +
                    "{{c2::image-occlusion:rect:left=.5:top=.5:width=.3:height=.2:oi=1}}",
            header = "",
            backExtra = "",
            tags = emptyList(),
        )
        return collection.findCards("").map { collection.getCard(it) }.sortedBy { it.ord }
    }

    private fun writeOcclusionImage(file: File) {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.WHITE)
            val paint = Paint().apply { color = android.graphics.Color.DKGRAY }
            drawRect(100f, 80f, 340f, 200f, paint)
            drawRect(420f, 320f, 660f, 440f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The question and answer HTML as ReviewerViewModel.processHtml builds them, without sounds. */
    private fun Card.html(collection: Collection): Pair<String, String> {
        val output = renderOutput(collection, reload = true)

        fun build(html: String) = CardHtmlBuilder.wrapWithStyles(collection.media.escapeMediaFilenames(html), output.css)
        return build(output.questionText) to build(output.answerText)
    }

    private suspend fun CardPageHarness.awaitMasks(): Pair<String, Double> {
        val start = System.nanoTime()
        var state = "not checked"
        while ((System.nanoTime() - start).nanosToMs() < MASK_TIMEOUT_MS) {
            state = evaluate(MASK_STATE_SCRIPT).trim('"')
            if (state.startsWith("drawn")) break
            delay(16)
        }
        return state to (System.nanoTime() - start).nanosToMs()
    }

    private companion object {
        const val ROUNDS = 5
        const val MASK_TIMEOUT_MS = 1500.0

        val MASK_STATE_SCRIPT =
            """
            (() => {
                const canvas = document.getElementById("image-occlusion-canvas");
                if (!canvas) return "no canvas";
                if (canvas.width === 0 || canvas.height === 0) return "empty canvas";
                const box = canvas.getBoundingClientRect();
                const pixels = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
                let painted = 0;
                for (let i = 3; i < pixels.length; i += 4) if (pixels[i] > 0) painted++;
                const size = canvas.width + "x" + canvas.height + " (css " + Math.round(box.width) + "x" + Math.round(box.height) + ")";
                return (painted > 0 ? "drawn " : "blank ") + size + ", " + painted + " painted pixels";
            })()
            """.trimIndent()
    }
}
