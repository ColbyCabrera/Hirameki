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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
    icon: Painter? = null,
    isIconSpaceReserved: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // We use a local state for the slider to ensure smooth dragging,
    // and only commit the change when dragging stops (or as needed).
    var sliderPosition by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var lastHapticValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // XML ComposeView handles horizontal padding (?attr/listPreferredItemPaddingStart/End)
    Row(
        modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                ),
                modifier = Modifier
                    .padding(end = 16.dp) // Standard icon padding
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
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!summary.isNullOrEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.38f
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (displayValue && !isDragged) {
                    val displayText =
                        displayFormat?.let { String.format(it, sliderPosition.toInt()) }
                            ?: sliderPosition.toInt().toString()
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Material3 Slider with Label for value indicator
            Slider(
                value = sliderPosition,
                onValueChange = {
                    sliderPosition = it
                    if (stepSize > 0) {
                        val steps = ((it - valueFrom) / stepSize).roundToInt()
                        val roundedValue = valueFrom + (steps * stepSize)
                        if (roundedValue != lastHapticValue) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticValue = roundedValue
                        }
                    } else if (it.toInt() != lastHapticValue.toInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastHapticValue = it.toInt().toFloat()
                    }
                },
                onValueChangeFinished = {
                    // M3 slider distributes steps evenly across the whole range, which may
                    // result in values that are not exact multiples of stepSize if the range is not evenly divisible.
                    // We enforce discrete values by rounding to the nearest step.
                    if (stepSize > 0) {
                        val steps = ((sliderPosition - valueFrom) / stepSize).roundToInt()
                        val roundedValue = valueFrom + (steps * stepSize).toInt()
                        onValueChange(roundedValue.coerceIn(valueFrom, valueTo))
                    } else {
                        onValueChange(sliderPosition.toInt())
                    }
                },
                valueRange = valueFrom.toFloat()..valueTo.toFloat(),
                steps = if (stepSize > 0) maxOf(
                    0, ((valueTo - valueFrom) / stepSize).toInt() - 1
                ) else 0,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,

                interactionSource = interactionSource,
                thumb = {
                    val displayText =
                        displayFormat?.let { String.format(it, sliderPosition.toInt()) }
                            ?: sliderPosition.toInt().toString()
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
    MaterialTheme {
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
    MaterialTheme {
        Box(modifier = Modifier.padding(top = 40.dp, start = 40.dp)) {
            SliderThumbWithLabel(
                isDragged = true,
                displayText = "100%",
                interactionSource = remember { MutableInteractionSource() },
                enabled = true
            )
        }
    }
}
