/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import anki.scheduler.CardAnswer
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

val ratings = listOf(
    R.string.ease_button_again to CardAnswer.Rating.AGAIN,
    R.string.ease_button_hard to CardAnswer.Rating.HARD,
    R.string.ease_button_good to CardAnswer.Rating.GOOD,
    R.string.ease_button_easy to CardAnswer.Rating.EASY
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnswerButtons(
    modifier: Modifier = Modifier,
    isAnswerShown: Boolean,
    showAnswerFeedback: Boolean,
    showTypeInAnswer: Boolean,
    typedAnswer: String,
    onTypedAnswerChanged: (String) -> Unit,
    onShowAnswer: () -> Unit,
    onRateCard: (CardAnswer.Rating) -> Unit,
    nextTimes: List<String>,
    moreOptionsInTopAppBar: Boolean = false,
    onMoreOptionsClick: () -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = modifier.imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showTypeInAnswer) {
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            TextField(
                value = typedAnswer,
                onValueChange = onTypedAnswerChanged,
                label = { Text(stringResource(R.string.type_in_the_answer)) },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .border(
                        2.dp,
                        if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                        MaterialTheme.shapes.extraLargeIncreased
                    ),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                interactionSource = interactionSource,
                readOnly = isAnswerShown, // when answer shown, don't allow typing
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (!isAnswerShown) {
                        onShowAnswer()
                    }
                }),
            )
        }

        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!moreOptionsInTopAppBar) {
                    IconButton(
                        onClick = onMoreOptionsClick,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                }
                Box(
                    modifier = Modifier.animateContentSize(motionScheme.fastSpatialSpec())
                ) {
                    if (!isAnswerShown) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val defaultHorizontalPadding =
                            ButtonDefaults.MediumContentPadding.calculateLeftPadding(
                                layoutDirection = LocalLayoutDirection.current
                            )
                        val horizontalPadding by animateDpAsState(
                            if (isPressed) defaultHorizontalPadding + 4.dp else defaultHorizontalPadding,
                            motionScheme.fastSpatialSpec()
                        )
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onShowAnswer()
                            },
                            modifier = Modifier.height(56.dp),
                            interactionSource = interactionSource,
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            colors = ButtonDefaults.buttonColors(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.show_answer),
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    } else {
                        ButtonGroup(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            overflowIndicator = { }) {
                            ratings.forEachIndexed { index, (labelResId, rating) ->
                                customItem(
                                    buttonGroupContent = {
                                        val interactionSource =
                                            remember { MutableInteractionSource() }

                                        Box(
                                            modifier = Modifier.animateWidth(interactionSource),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Button(
                                                onClick = {
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.KEYBOARD_TAP
                                                    )
                                                    onRateCard(rating)
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (showAnswerFeedback) Modifier.padding(
                                                            bottom = 6.dp
                                                        )
                                                        else Modifier
                                                    ), // add slight padding so the badge doesn't overlap excessively
                                                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                                                shape = when (index) {
                                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShape
                                                    ratings.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShape
                                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes().shape
                                                },
                                                interactionSource = interactionSource,
                                                colors = ButtonDefaults.buttonColors(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Text(
                                                    nextTimes.getOrElse(index) { "" },
                                                    softWrap = false,
                                                    overflow = TextOverflow.Visible
                                                )
                                            }

                                            if (showAnswerFeedback) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                ) {
                                                    Text(
                                                        modifier = Modifier.padding(1.dp),
                                                        text = stringResource(labelResId),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    menuContent = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Show Answer", showBackground = true)
@Composable
fun AnswerButtonsShowAnswerPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = false,
            showAnswerFeedback = true,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = emptyList(),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Rating Buttons", showBackground = true)
@Composable
fun AnswerButtonsRatingPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showAnswerFeedback = true,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = listOf("1m", "2d", "4d", "7d"),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Rating Buttons (No Feedback)", showBackground = true)
@Composable
fun AnswerButtonsNoFeedbackPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showAnswerFeedback = false,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = listOf("1m", "2d", "4d", "7d"),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Type In Answer", showBackground = true)
@Composable
fun AnswerButtonsTypeInPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = false,
            showAnswerFeedback = true,
            showTypeInAnswer = true,
            typedAnswer = "Typed Answer",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = emptyList(),
            onMoreOptionsClick = {})
    }
}
