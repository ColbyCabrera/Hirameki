/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.multimedia.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.withStyledAttributes
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * Legacy Android View for audio waveforms.
 * Redesigned to be a simple renderer; calculation logic should be handled by the caller.
 */
class AudioWaveform(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val spikePaint = Paint().apply { isAntiAlias = true }
    private val verticalLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val backgroundPaint = Paint()

    private val amplitudes = ArrayList<Float>()
    private val audioSpikes = ArrayList<RectF>()
    private var radius = 3f
    private var w = 6f
    private var d = 4f
    private var displayVerticalLine: Boolean = true

    init {
        context.withStyledAttributes(attrs, R.styleable.AudioWaveform, 0, 0) {
            displayVerticalLine = getBoolean(R.styleable.AudioWaveform_display_vertical_line, true)
            backgroundPaint.color = getColor(R.styleable.AudioWaveform_android_background, 0)
        }
    }

    fun addAmplitude(normalizedAmplitude: Float) {
        amplitudes.add(normalizedAmplitude)
        updateSpikes()
        invalidate()
    }

    fun clear() {
        amplitudes.clear()
        audioSpikes.clear()
        invalidate()
    }

    private fun updateSpikes() {
        audioSpikes.clear()
        val percentageOfWidthToFill = if (displayVerticalLine) 0.5f else 1f
        val spikeCount = (width / (w + d) * percentageOfWidthToFill).toInt()
        val amps = amplitudes.takeLast(spikeCount)

        for ((index, amp) in amps.withIndex()) {
            val left = index * (w + d)
            val top = height / 2f - amp / 2f
            val right = left + w
            val bottom = top + amp
            audioSpikes.add(RectF(left, top, right, bottom))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        audioSpikes.forEach { canvas.drawRoundRect(it, radius, radius, spikePaint) }
        if (displayVerticalLine) {
            val centerX = width / 2f
            canvas.drawLine(centerX, 0f, centerX, height.toFloat(), verticalLinePaint)
        }
    }
}

@Composable
fun AudioWaveformCompose(
    modifier: Modifier = Modifier,
    amplitudes: List<Float>,
    isRecording: Boolean,
    displayVerticalLine: Boolean = false
) {
    val spikeColor = MaterialTheme.colorScheme.primary
    val verticalLineColor = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.surface

    ComposeCanvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val w = 6.dp.toPx()
        val d = 4.dp.toPx()
        val radius = 3.dp.toPx()
        val percentageOfWidthToFill = if (displayVerticalLine) 0.5f else 1f
        val spikeCount = (size.width / (w + d) * percentageOfWidthToFill).toInt()

        // Draw background
        drawRect(color = backgroundColor)

        if (isRecording) {
            val relevantAmplitudes = amplitudes.takeLast(spikeCount)
            relevantAmplitudes.forEachIndexed { index, ampFactor ->
                val spikeHeight = (ampFactor * size.height).coerceIn(6.dp.toPx(), size.height)
                val left = index * (w + d)
                val top = (size.height - spikeHeight) / 2

                drawRoundRect(
                    color = spikeColor,
                    topLeft = Offset(left, top),
                    size = Size(w, spikeHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }

        if (displayVerticalLine) {
            val centerX = size.width / 2f
            drawLine(
                color = verticalLineColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Preview(name = "Recording", showBackground = true)
@Composable
private fun AudioWaveformRecordingPreview() {
    AnkiDroidTheme {
        AudioWaveformCompose(
            amplitudes = listOf(
                0.1f,
                0.2f,
                0.5f,
                0.3f,
                0.8f,
                0.6f,
                0.4f,
                0.9f,
                0.7f,
                0.5f,
                0.2f,
                0.4f,
                0.6f,
                0.8f,
                1.0f
            ), isRecording = true
        )
    }
}

@Preview(name = "Not Recording", showBackground = true)
@Composable
private fun AudioWaveformNotRecordingPreview() {
    AnkiDroidTheme {
        AudioWaveformCompose(
            amplitudes = listOf(
                0.1f,
                0.2f,
                0.5f,
                0.3f,
                0.8f,
                0.6f,
                0.4f,
                0.9f,
                0.7f,
                0.5f,
                0.2f,
                0.4f,
                0.6f,
                0.8f,
                1.0f
            ), isRecording = false
        )
    }
}
