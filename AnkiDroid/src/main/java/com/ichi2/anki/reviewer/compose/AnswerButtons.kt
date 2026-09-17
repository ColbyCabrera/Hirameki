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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import anki.scheduler.CardAnswer
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.LocalAnkiColors

private object AnswerButtonsConstants {
    val ColumnSpacing = 12.dp
    val TextFieldBorderWidth = 2.dp
    const val TEXT_FIELD_MAX_WIDTH_FRACTION = 0.8f
    val ToolbarIconHeight = 48.dp
    val MainButtonHeight = 56.dp
    val RatingButtonGroupSpacing = 2.dp

    /** Room kept between the answer bar and each side of the screen. */
    val BarEdgeMargin = 28.dp

    /** Widest the answer bar gets, so on a tablet the ratings stay within a thumb's reach of each other. */
    val BarMaxWidth = 400.dp
    val BadgeBottomPadding = 6.dp
    val AdjustedBadgeBottomPadding = 2.dp
    val AdjustedTextTopPadding = 14.dp
    val AdjustedButtonHorizontalPadding = 28.dp

    /** Smallest a long "Show answer" label shrinks to before it is cut short with an ellipsis. */
    val MinLabelFontSize = 12.sp
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
    /**
     * Whether the bar acts on touches. False on a step the card view performs itself: the bar is laid out
     * there anyway, so the card's room never changes across the reveal, but nothing on a bar drawn away to
     * nothing may answer a tap the user cannot see they made. Every control honours it, the type-in field
     * and the overflow button included: the card view serves neither, so those two are only ever composed
     * with it true today, and they follow the one contract rather than carry a private rule that the next
     * layout change would quietly break.
     */
    enabled: Boolean = true,
    isAnswerShown: Boolean,
    showButtonBadges: Boolean,
    colorizeAnswerButtons: Boolean = false,
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

    BoxWithConstraints(modifier = modifier.imePadding()) {
        // one width for the whole bar, from the screen rather than from the labels: the same before and
        // after the reveal, so the bar never resizes as the answer appears, and shared evenly by the
        // ratings, so a short label such as "Good" gets as much room as "Again"
        val barWidth =
            (maxWidth - AnswerButtonsConstants.BarEdgeMargin * 2).coerceIn(0.dp, AnswerButtonsConstants.BarMaxWidth)
        // capped with the bar it sits on: a fraction of the screen alone stretched it far past the bar on a tablet
        val typeInWidth = min(maxWidth * AnswerButtonsConstants.TEXT_FIELD_MAX_WIDTH_FRACTION, barWidth)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AnswerButtonsConstants.ColumnSpacing)
        ) {
            if (showTypeInAnswer) {
                AnswerTypeInTextField(
                    typedAnswer = typedAnswer,
                    onTypedAnswerChanged = onTypedAnswerChanged,
                    isAnswerShown = isAnswerShown,
                    onShowAnswer = onShowAnswer,
                    enabled = enabled,
                    width = typeInWidth,
                )
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.width(barWidth),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!moreOptionsInTopAppBar) {
                        IconButton(
                            onClick = onMoreOptionsClick,
                            enabled = enabled,
                            modifier = Modifier.height(AnswerButtonsConstants.ToolbarIconHeight),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(motionScheme.fastSpatialSpec())
                    ) {
                        if (!isAnswerShown) {
                            ShowAnswerButton(onShowAnswer = onShowAnswer, enabled = enabled)
                        } else {
                            RatingButtons(
                                showButtonBadges = showButtonBadges,
                                colorizeAnswerButtons = colorizeAnswerButtons,
                                adjustButtonStylesForBadges = adjustButtonStylesForBadges,
                                onRateCard = onRateCard,
                                nextTimes = nextTimes,
                                enabled = enabled
                            )
                        }
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
    onShowAnswer: () -> Unit,
    enabled: Boolean,
    width: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    TextField(
        value = typedAnswer,
        onValueChange = onTypedAnswerChanged,
        label = { Text(stringResource(R.string.type_in_the_answer)) },
        modifier = Modifier
            .width(width)
            .border(
                AnswerButtonsConstants.TextFieldBorderWidth,
                if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                MaterialTheme.shapes.extraLargeIncreased
            ),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        interactionSource = interactionSource,
        // a covered bar is invisible, and an invisible field that still takes focus would raise the
        // keyboard over a card the user is only looking at
        enabled = enabled,
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
    onShowAnswer: () -> Unit,
    enabled: Boolean
) {
    val view = LocalView.current

    // as wide as the rating buttons it turns into, so the bar keeps its size across the reveal
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onShowAnswer()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(AnswerButtonsConstants.MainButtonHeight),
        colors = ButtonDefaults.buttonColors(
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary
        )
    ) {
        // a long translation or a large font scale shrinks the label to fit the fixed-width bar, and only past a readable
        // floor is it cut short, with an ellipsis rather than clipped mid-letter
        Text(
            text = stringResource(R.string.show_answer),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = AnswerButtonsConstants.MinLabelFontSize,
                    maxFontSize = LocalTextStyle.current.fontSize,
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RatingButtons(
    showButtonBadges: Boolean,
    colorizeAnswerButtons: Boolean,
    adjustButtonStylesForBadges: Boolean,
    onRateCard: (CardAnswer.Rating) -> Unit,
    nextTimes: List<String>,
    enabled: Boolean
) {
    val view = LocalView.current
    val ratingColors = LocalAnkiColors.current.ratings

    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AnswerButtonsConstants.RatingButtonGroupSpacing),
        overflowIndicator = { }) {
        ratings.forEachIndexed { index, (labelResId, rating) ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val labelText = stringResource(labelResId)
                    val nextTime = nextTimes.getOrElse(index) { "" }
                    val tonalRole = ratingColors.forRating(rating)

                    val (buttonContainerColor, buttonContentColor) = if (colorizeAnswerButtons) {
                        tonalRole.colorContainer to tonalRole.onColorContainer
                    } else {
                        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                    }

                    Box(
                        modifier = Modifier
                            // equal shares of the bar; a press still widens its button for a moment
                            .weight(1f)
                            .animateWidth(interactionSource)
                            .semantics {
                                contentDescription = "$labelText, $nextTime"
                            }, contentAlignment = Alignment.BottomCenter
                    ) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onRateCard(rating)
                            },
                            enabled = enabled,
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
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor
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
                            val (badgeContainerColor, badgeContentColor) = if (colorizeAnswerButtons) {
                                tonalRole.color to tonalRole.onColor
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                            }

                            Badge(
                                modifier = if (adjustButtonStylesForBadges) {
                                    Modifier.padding(bottom = AnswerButtonsConstants.AdjustedBadgeBottomPadding)
                                } else Modifier,
                                containerColor = badgeContainerColor,
                                contentColor = badgeContentColor,
                            ) {
                                // one line within the button's share of the bar: a long rating name wrapped and grew the
                                // badge past its button. the full name stays in the button's description
                                Text(
                                    modifier = Modifier.padding(1.dp),
                                    text = labelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
            colorizeAnswerButtons = false,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = emptyList(),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Rating Buttons (Default)", showBackground = true)
@Composable
fun AnswerButtonsRatingPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showButtonBadges = true,
            colorizeAnswerButtons = false,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = listOf("1m", "2d", "4d", "7d"),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Rating Buttons (Colorized Harmonized)", showBackground = true)
@Composable
fun AnswerButtonsColorizedRatingPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showButtonBadges = true,
            colorizeAnswerButtons = true,
            showTypeInAnswer = false,
            typedAnswer = "",
            onTypedAnswerChanged = {},
            onShowAnswer = {},
            onRateCard = {},
            nextTimes = listOf("1m", "2d", "4d", "7d"),
            onMoreOptionsClick = {})
    }
}

@Preview(name = "Rating Buttons (Colorized, No Badges)", showBackground = true)
@Composable
fun AnswerButtonsColorizedNoBadgesPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showButtonBadges = false,
            colorizeAnswerButtons = true,
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
            colorizeAnswerButtons = false,
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
            colorizeAnswerButtons = false,
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
            colorizeAnswerButtons = false,
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

@Preview(name = "Expanded Colorized Rating Buttons", showBackground = true)
@Composable
fun AnswerButtonsExpandedColorizedRatingPreview() {
    AnkiDroidTheme {
        AnswerButtons(
            isAnswerShown = true,
            showButtonBadges = true,
            colorizeAnswerButtons = true,
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
            colorizeAnswerButtons = false,
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

