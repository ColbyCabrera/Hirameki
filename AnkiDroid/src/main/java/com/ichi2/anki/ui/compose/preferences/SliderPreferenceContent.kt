/***************************************************************************************
 * Copyright (c) 2026 Colby Cabrera <colbycabrera@gmail.com>                            *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.ui.compose.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SliderPreferenceContent(
    title: String,
    summary: String?,
    value: Int,
    valueFrom: Int,
    valueTo: Int,
    stepSize: Float,
    displayValue: Boolean,
    displayFormat: String?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    isIconSpaceReserved: Boolean = false,
    enabled: Boolean = true
) {
    // We use a local state for the slider to ensure smooth dragging.
    // Sync with the external value when it changes.
    var sliderPosition by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var lastHapticValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // Derived state for display text to avoid redundant formatting calls
    val displayText by remember(displayFormat, sliderPosition) {
        derivedStateOf {
            val roundedValue = sliderPosition.roundToInt()
            displayFormat?.let { String.format(it, roundedValue) } ?: roundedValue.toString()
        }
    }

    val disabledAlpha = 0.38f
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Colors based on enabled state
    val titleColor = if (enabled) onSurface else onSurface.copy(alpha = disabledAlpha)
    val secondaryColor = if (enabled) onSurfaceVariant else onSurface.copy(alpha = disabledAlpha)

    Row(
        modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = secondaryColor,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        } else if (isIconSpaceReserved) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!summary.isNullOrEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (displayValue && !isDragged) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    sliderPosition = newValue
                    if (stepSize > 0) {
                        val steps = ((newValue - valueFrom) / stepSize).roundToInt()
                        val roundedValue = valueFrom + (steps * stepSize)
                        if (roundedValue != lastHapticValue) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticValue = roundedValue
                        }
                    } else {
                        val rangeSpan = (valueTo - valueFrom).toFloat()
                        if (abs(newValue - lastHapticValue) > rangeSpan * 0.05f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticValue = newValue
                        }
                    }
                },
                onValueChangeFinished = {
                    val finalValue = if (stepSize > 0) {
                        val steps = ((sliderPosition - valueFrom) / stepSize).roundToInt()
                        valueFrom + (steps * stepSize).roundToInt()
                    } else {
                        sliderPosition.roundToInt()
                    }
                    onValueChange(finalValue.coerceIn(valueFrom, valueTo))
                },
                valueRange = valueFrom.toFloat()..valueTo.toFloat(),
                steps = if (stepSize > 0) maxOf(
                    0, ((valueTo - valueFrom) / stepSize).toInt() - 1
                ) else 0,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                interactionSource = interactionSource,
                thumb = {
                    SliderThumbWithLabel(
                        isDragged = isDragged,
                        displayText = displayText,
                        interactionSource = interactionSource,
                        enabled = enabled
                    )
                },
                colors = SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SliderThumbWithLabel(
    isDragged: Boolean,
    displayText: String,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            if (isDragged) {
                Box(
                    modifier = Modifier
                        .layoutId("label")
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraExtraLarge
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = displayText,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                modifier = Modifier.layoutId("thumb")
            )
        }, modifier = modifier
    ) { measurables, constraints ->
        val thumbPlaceable = measurables.first { it.layoutId == "thumb" }.measure(constraints)
        val labelPlaceable = measurables.find { it.layoutId == "label" }
            ?.measure(constraints.copy(minWidth = 0, minHeight = 0))

        layout(thumbPlaceable.width, thumbPlaceable.height) {
            thumbPlaceable.placeRelative(0, 0)
            labelPlaceable?.let {
                val x = (thumbPlaceable.width - labelPlaceable.width) / 2
                // Place label above the thumb
                val y = -it.height - 8.dp.roundToPx()
                it.placeRelative(x, y)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSliderPreferenceContent() {
    AnkiDroidTheme {
        SliderPreferenceContent(
            title = "Text Size",
            summary = "Adjust the text size for readability",
            value = 100,
            valueFrom = 50,
            valueTo = 200,
            stepSize = 10f,
            displayValue = true,
            displayFormat = "%d%%",
            onValueChange = {},
            isIconSpaceReserved = true
        )
    }
}



@Preview(showBackground = true)
@Composable
fun PreviewSliderThumbWithLabel() {
    AnkiDroidTheme {
        Box(
            modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center
        ) {
            SliderThumbWithLabel(
                isDragged = true,
                displayText = "100%",
                interactionSource = remember { MutableInteractionSource() },
                enabled = true
            )
        }
    }
}
