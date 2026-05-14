/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer.compose

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * A Compose-based color picker dialog with HSV sliders.
 *
 * @param defaultColor The initial color to display (as an Android Color int)
 * @param showAlpha Whether to show the alpha slider
 * @param onColorPicked Called when the user confirms their color selection
 * @param onDismiss Called when the dialog is dismissed
 */
@Composable
fun ColorPickerDialog(
    defaultColor: Int,
    showAlpha: Boolean = true,
    onColorPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Convert default color to HSV
    val defaultHsv =
        remember(defaultColor) {
            FloatArray(3).also { Color.colorToHSV(defaultColor, it) }
        }
    val defaultAlpha = remember(defaultColor) { Color.alpha(defaultColor) / 255f }

    var hue by remember { mutableFloatStateOf(defaultHsv[0]) }
    var saturation by remember { mutableFloatStateOf(defaultHsv[1]) }
    var value by remember { mutableFloatStateOf(defaultHsv[2]) }
    var alpha by remember { mutableFloatStateOf(defaultAlpha) }

    // Current color based on HSV values
    val currentColor =
        remember(hue, saturation, value, alpha) {
            val rgb = Color.HSVToColor(floatArrayOf(hue, saturation, value))
            if (showAlpha) {
                Color.argb((alpha * 255).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            } else {
                rgb
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onColorPicked(currentColor) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        title = { Text(stringResource(R.string.add_brush)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Color preview
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ComposeColor(currentColor))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hue slider (rainbow gradient)
                ColorSliderRow(
                    label = stringResource(R.string.color_picker_hue),
                    value = hue,
                    valueRange = 0f..360f,
                    onValueChange = { hue = it },
                    gradientColors =
                        List(7) { i ->
                            ComposeColor(Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
                        },
                )

                // Saturation slider
                ColorSliderRow(
                    label = stringResource(R.string.color_picker_saturation),
                    value = saturation,
                    valueRange = 0f..1f,
                    onValueChange = { saturation = it },
                    gradientColors =
                        listOf(
                            ComposeColor(Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                            ComposeColor(Color.HSVToColor(floatArrayOf(hue, 1f, value))),
                        ),
                )

                // Value/Brightness slider
                ColorSliderRow(
                    label = stringResource(R.string.color_picker_brightness),
                    value = value,
                    valueRange = 0f..1f,
                    onValueChange = { value = it },
                    gradientColors =
                        listOf(
                            ComposeColor.Black,
                            ComposeColor(Color.HSVToColor(floatArrayOf(hue, saturation, 1f))),
                        ),
                )

                // Alpha slider (optional)
                if (showAlpha) {
                    ColorSliderRow(
                        label = stringResource(R.string.color_picker_alpha),
                        value = alpha,
                        valueRange = 0f..1f,
                        onValueChange = { alpha = it },
                        gradientColors =
                            listOf(
                                ComposeColor.Transparent,
                                ComposeColor(Color.HSVToColor(floatArrayOf(hue, saturation, value))),
                            ),
                    )
                }
            }
        },
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    gradientColors: List<ComposeColor>,
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Brush.horizontalGradient(gradientColors)),
        ) {
            var lastValue by remember { mutableFloatStateOf(value) }
            Slider(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    // Haptic feedback when the value changes significantly (e.g., 5%)
                    val rangeSpan = valueRange.endInclusive - valueRange.start
                    if (kotlin.math.abs(it - lastValue) > rangeSpan * 0.05f) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastValue = it
                    }
                },
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = ComposeColor.Transparent,
                        inactiveTrackColor = ComposeColor.Transparent,
                    ),
            )
        }
    }
}
