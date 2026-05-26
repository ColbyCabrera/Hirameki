/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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
package com.ichi2.anki.deckpicker.compose

import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.RobotoMono
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * UI-ready study summary for the currently selected deck.
 *
 * This aggregates deck metadata, visible counts, buried counts, and filtered-deck state so the
 * study options pane can render without reaching back into deck or scheduler APIs.
 */
data class StudyOptionsData(
    val deckId: Long,
    val deckName: String,
    val deckDescription: String,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
    val buriedNew: Int,
    val buriedLrn: Int,
    val buriedRev: Int,
    val totalNewCards: Int,
    val totalCards: Int,
    val isFiltered: Boolean,
    val haveBuried: Boolean,
)

/**
 * Chooses the appropriate tablet-side study options content for the selected deck.
 *
 * A `null` [studyOptionsData] value represents the transient loading state before a selection is
 * available.
 */
@Composable
fun StudyOptionsScreen(
    studyOptionsData: StudyOptionsData?,
    modifier: Modifier = Modifier,
    onStartStudy: () -> Unit,
    onCustomStudy: (Long) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        if (studyOptionsData == null) {
            // Show a loading indicator or an empty state
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        } else {
            when {
                studyOptionsData.totalCards == 0 && !studyOptionsData.isFiltered -> {
                    EmptyDeckView(studyOptionsData, modifier)
                }

                studyOptionsData.newCount + studyOptionsData.lrnCount + studyOptionsData.revCount == 0 -> {
                    CongratsView(studyOptionsData, onCustomStudy, modifier)
                }

                else -> {
                    StudyOptionsView(studyOptionsData, onStartStudy, modifier)
                }
            }
        }
    }
}

/**
 * Full study options view for a deck that still has cards available today.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudyOptionsView(
    studyOptionsData: StudyOptionsData,
    onStartStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = studyOptionsData.deckName,
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (studyOptionsData.deckDescription.isNotEmpty()) {
            val linkColor = MaterialTheme.colorScheme.primary
            val annotatedString = remember(studyOptionsData.deckDescription, linkColor) {
                // Sanitize stored HTML before converting it into clickable Compose text spans.
                val cleanDescription =
                    Jsoup.clean(studyOptionsData.deckDescription, Safelist.basic())
                val spanned = HtmlCompat.fromHtml(
                    cleanDescription, HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                buildAnnotatedString {
                    append(spanned.toString())
                    val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
                    for (span in urlSpans) {
                        val start = spanned.getSpanStart(span)
                        val end = spanned.getSpanEnd(span)
                        val url = span.url
                        if (url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true)
                        ) {
                            addLink(LinkAnnotation.Url(url), start, end)
                            addStyle(
                                SpanStyle(
                                    color = linkColor, textDecoration = TextDecoration.Underline
                                ), start, end
                            )
                        }
                    }
                }
            }
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StudyOptionCardCount(
                label = stringResource(R.string.tags_dialog_option_new_cards),
                count = studyOptionsData.newCount,
                shape = RoundedPolygonShape(MaterialShapes.SoftBurst),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            StudyOptionCardCount(
                label = stringResource(R.string.learning),
                count = studyOptionsData.lrnCount,
                shape = RoundedPolygonShape(MaterialShapes.Square),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
            StudyOptionCardCount(
                label = stringResource(R.string.review),
                count = studyOptionsData.revCount,
                shape = RoundedPolygonShape(MaterialShapes.Ghostish),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        }

        if (studyOptionsData.haveBuried) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.buried_cards_are_not_included_in_counts_above),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StudyOptionCardCount(
                    label = stringResource(R.string.tags_dialog_option_new_cards),
                    count = studyOptionsData.buriedNew,
                    shape = RoundedPolygonShape(MaterialShapes.Sunny),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    small = true
                )
                StudyOptionCardCount(
                    label = stringResource(R.string.learning),
                    count = studyOptionsData.buriedLrn,
                    shape = RoundedPolygonShape(MaterialShapes.Cookie4Sided),
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f),
                    small = true
                )
                StudyOptionCardCount(
                    label = stringResource(R.string.review),
                    count = studyOptionsData.buriedRev,
                    shape = RoundedPolygonShape(MaterialShapes.Cookie7Sided),
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f),
                    small = true
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.total_new),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = studyOptionsData.totalNewCards.toString(),
                        fontSize = 48.sp,
                        fontFamily = RobotoMono,
                        lineHeight = 48.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.studyoptions_total_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = studyOptionsData.totalCards.toString(),
                        fontSize = 48.sp,
                        fontFamily = RobotoMono,
                        lineHeight = 48.sp
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartStudy,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.book_ribbon_24px),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = stringResource(R.string.studyoptions_start), fontSize = 24.sp)
        }
    }
}

/**
 * Empty-state study options content for a deck that contains no cards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyDeckView(
    studyOptionsData: StudyOptionsData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = studyOptionsData.deckName,
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_deck),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Post-study state shown when the selected deck has no cards left for the day.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CongratsView(
    studyOptionsData: StudyOptionsData,
    onCustomStudy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.studyoptions_congrats_finished),
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.daily_limit_reached),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.study_more), style = MaterialTheme.typography.bodyLarge
        )
        if (!studyOptionsData.isFiltered) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = 12.dp),
                onClick = { onCustomStudy(studyOptionsData.deckId) }) {
                Icon(
                    painter = painterResource(id = R.drawable.book_ribbon_24px),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.custom_study), fontSize = 24.sp)
            }
        }
    }
}

/**
 * Displays a labeled study count badge used throughout the study options pane.
 */
@Composable
fun StudyOptionCardCount(
    label: String,
    count: Int,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val size = if (small) 40.dp else 64.dp
    val fontSize = if (small) 12.sp else 20.sp

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = contentColor,
                fontFamily = RobotoMono,
                fontSize = fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .basicMarquee()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true)
@Composable
fun StudyOptionsScreenPreview() {
    val sampleData = StudyOptionsData(
        deckId = 1,
        deckName = "My Awesome Deck",
        deckDescription = "This is a great deck for learning Compose.",
        newCount = 10,
        lrnCount = 5,
        revCount = 20,
        buriedNew = 2,
        buriedLrn = 1,
        buriedRev = 3,
        totalNewCards = 50,
        totalCards = 200,
        isFiltered = false,
        haveBuried = true,
    )
    StudyOptionsScreen(studyOptionsData = sampleData, onStartStudy = {}, onCustomStudy = {})
}

@Preview(showBackground = true)
@Composable
fun CongratsViewPreview() {
    val sampleData = StudyOptionsData(
        deckId = 1,
        deckName = "My Awesome Deck",
        deckDescription = "",
        newCount = 0,
        lrnCount = 0,
        revCount = 0,
        buriedNew = 0,
        buriedLrn = 0,
        buriedRev = 0,
        totalNewCards = 0,
        totalCards = 100,
        isFiltered = false,
        haveBuried = false,
    )
    CongratsView(studyOptionsData = sampleData, onCustomStudy = {})
}

@Preview(showBackground = true)
@Composable
fun EmptyDeckViewPreview() {
    val sampleData = StudyOptionsData(
        deckId = 1,
        deckName = "My Awesome Deck",
        deckDescription = "",
        newCount = 0,
        lrnCount = 0,
        revCount = 0,
        buriedNew = 0,
        buriedLrn = 0,
        buriedRev = 0,
        totalNewCards = 0,
        totalCards = 0,
        isFiltered = false,
        haveBuried = false,
    )
    EmptyDeckView(studyOptionsData = sampleData)
}
