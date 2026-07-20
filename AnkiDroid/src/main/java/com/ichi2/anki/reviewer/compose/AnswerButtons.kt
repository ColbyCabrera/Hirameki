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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import anki.scheduler.CardAnswer
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

private object AnswerButtonsConstants {
    val ColumnSpacing = 12.dp
    val TextFieldBorderWidth = 2.dp
    val TextFieldMaxWidthFraction = 0.8f
    val ToolbarIconHeight = 48.dp
    val MainButtonHeight = 56.dp
    val RatingButtonGroupSpacing = 2.dp
    val ExpandedButtonHorizontalPadding = 24.dp
    val PressedAnimationExtraPadding = 4.dp
    val BadgeBottomPadding = 6.dp
    val AdjustedBadgeBottomPadding = 2.dp
    val AdjustedTextTopPadding = 14.dp
    val AdjustedButtonHorizontalPadding = 28.dp
}

private val ratings = listOf(
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
    showButtonBadges: Boolean,
    showTypeInAnswer: Boolean,
    typedAnswer: String,
    onTypedAnswerChanged: (String) -> Unit,
    onShowAnswer: () -> Unit,
    onRateCard: (CardAnswer.Rating) -> Unit,
    nextTimes: List<String>,
    moreOptionsInTopAppBar: Boolean = false,
    onMoreOptionsClick: () -> Unit
) {
    val adjustButtonStylesForBadges = showButtonBadges && moreOptionsInTopAppBar

    Column(
        modifier = modifier.imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AnswerButtonsConstants.ColumnSpacing)
    ) {
        if (showTypeInAnswer) {
            AnswerTypeInTextField(
                typedAnswer = typedAnswer,
                onTypedAnswerChanged = onTypedAnswerChanged,
                isAnswerShown = isAnswerShown,
                onShowAnswer = onShowAnswer
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
                        modifier = Modifier.height(AnswerButtonsConstants.ToolbarIconHeight),
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
                        ShowAnswerButton(
                            moreOptionsInTopAppBar = moreOptionsInTopAppBar,
                            onShowAnswer = onShowAnswer
                        )
                    } else {
                        RatingButtons(
                            showButtonBadges = showButtonBadges,
                            adjustButtonStylesForBadges = adjustButtonStylesForBadges,
                            onRateCard = onRateCard,
                            nextTimes = nextTimes
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerTypeInTextField(
    typedAnswer: String,
    onTypedAnswerChanged: (String) -> Unit,
    isAnswerShown: Boolean,
    onShowAnswer: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    TextField(
        value = typedAnswer,
        onValueChange = onTypedAnswerChanged,
        label = { Text(stringResource(R.string.type_in_the_answer)) },
        modifier = Modifier
            .fillMaxWidth(AnswerButtonsConstants.TextFieldMaxWidthFraction)
            .border(
                AnswerButtonsConstants.TextFieldBorderWidth,
                if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                MaterialTheme.shapes.extraLargeIncreased
            ),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        interactionSource = interactionSource,
        readOnly = isAnswerShown,
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

@Composable
private fun ShowAnswerButton(
    moreOptionsInTopAppBar: Boolean,
    onShowAnswer: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseHorizontalPadding = ButtonDefaults.MediumContentPadding.calculateLeftPadding(
        layoutDirection = LocalLayoutDirection.current
    ) + if (moreOptionsInTopAppBar) AnswerButtonsConstants.ExpandedButtonHorizontalPadding else 0.dp

    val horizontalPadding by animateDpAsState(
        if (isPressed) baseHorizontalPadding + AnswerButtonsConstants.PressedAnimationExtraPadding else baseHorizontalPadding,
        motionScheme.fastSpatialSpec(),
        label = "ShowAnswerButtonPadding"
    )

    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onShowAnswer()
        },
        modifier = Modifier.height(AnswerButtonsConstants.MainButtonHeight),
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RatingButtons(
    showButtonBadges: Boolean,
    adjustButtonStylesForBadges: Boolean,
    onRateCard: (CardAnswer.Rating) -> Unit,
    nextTimes: List<String>
) {
    val view = LocalView.current
    ButtonGroup(
        horizontalArrangement = Arrangement.spacedBy(AnswerButtonsConstants.RatingButtonGroupSpacing),
        overflowIndicator = { }
    ) {
        ratings.forEachIndexed { index, (labelResId, rating) ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val labelText = stringResource(labelResId)
                    val nextTime = nextTimes.getOrElse(index) { "" }

                    Box(
                        modifier = Modifier
                            .animateWidth(interactionSource)
                            .semantics {
                                contentDescription = "$labelText, $nextTime"
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onRateCard(rating)
                            },
                            modifier = Modifier
                                .height(AnswerButtonsConstants.MainButtonHeight)
                                .fillMaxWidth()
                                .then(
                                    if (showButtonBadges && !adjustButtonStylesForBadges) {
                                        Modifier.padding(bottom = AnswerButtonsConstants.BadgeBottomPadding)
                                    } else Modifier
                                ),
                            contentPadding = if (adjustButtonStylesForBadges) {
                                PaddingValues(horizontal = AnswerButtonsConstants.AdjustedButtonHorizontalPadding)
                            } else ButtonDefaults.ExtraSmallContentPadding,
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
                                modifier = if (adjustButtonStylesForBadges) {
                                    Modifier
                                        .fillMaxHeight()
                                        .padding(top = AnswerButtonsConstants.AdjustedTextTopPadding)
                                } else Modifier,
                                text = nextTime,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }

                        if (showButtonBadges) {
                            Badge(
                                modifier = if (adjustButtonStylesForBadges) {
                                    Modifier.padding(bottom = AnswerButtonsConstants.AdjustedBadgeBottomPadding)
                                } else Modifier,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    modifier = Modifier.padding(1.dp),
                                    text = labelText,
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

@Preview(name = "Show Answer", showBackground = true)
@Composable
fun AnswerButtonsShowAnswerPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = false,
            showButtonBadges = true,
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
            showButtonBadges = true,
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
            showButtonBadges = false,
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
            showButtonBadges = true,
            showTypeInAnswer = true,
            typedAnswer = "Typed Answer",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = emptyList(),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Expanded Rating Buttons (More Options in Top Bar)", showBackground = true)
@Composable
fun AnswerButtonsExpandedRatingPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showButtonBadges = true,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = listOf("1m", "2d", "4d", "7d"),
            moreOptionsInTopAppBar = true,
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Expanded Show Answer (More Options in Top Bar)", showBackground = true)
@Composable
fun AnswerButtonsExpandedShowAnswerPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = false,
            showButtonBadges = true,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = emptyList(),
            moreOptionsInTopAppBar = true,
            onMoreOptionsClick = {})
    }
}
