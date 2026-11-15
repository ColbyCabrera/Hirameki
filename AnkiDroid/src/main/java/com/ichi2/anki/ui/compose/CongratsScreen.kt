// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.RobotoMono
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun CongratsScreen(
    onNavigateUp: () -> Unit,
    onDeckOptions: () -> Unit,
    onCustomStudy: () -> Unit,
    timeUntilNextDay: Long,
) {
    BackHandler { onNavigateUp() }
    AnkiDroidTheme {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.displayMediumEmphasized,
                    )
                },
                subtitle = {},
                titleHorizontalAlignment = Alignment.CenterHorizontally,
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = onNavigateUp,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = onDeckOptions,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.deck_options),
                        )
                    }
                },
            )
        }, content = { contentPadding ->
            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(scrollState)
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.studyoptions_congrats_finished),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.daily_limit_reached),
                    style = MaterialTheme.typography.bodyLarge,
                )
                StudyMoreClickableText(
                    onCustomStudy = onCustomStudy,
                )

                Column(
                    modifier =
                        Modifier
                            .padding(top = 32.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    var remainingTime by remember(timeUntilNextDay) {
                        mutableLongStateOf(
                            timeUntilNextDay.coerceAtLeast(0L),
                        )
                    }

                    LaunchedEffect(timeUntilNextDay) {
                        while (remainingTime > 0) {
                            delay(1000)
                            remainingTime = (remainingTime - 1000).coerceAtLeast(0L)
                        }
                    }

                    val hours = TimeUnit.MILLISECONDS.toHours(remainingTime)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60

                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = stringResource(R.string.next_review_in),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontFamily = RobotoMono,
                        fontSize = MaterialTheme.typography.displayMedium.fontSize,
                        lineHeight = MaterialTheme.typography.displayLarge.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                "%02d:%02d:%02d".format(
                                    Locale.current.platformLocale,
                                    hours,
                                    minutes,
                                    seconds,
                                ),
                            fontFamily = RobotoMono,
                            fontSize = 70.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 70.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            softWrap = false,
                        )
                    }
                }
            }
        })
    }
}

@Composable
fun StudyMoreClickableText(
    onCustomStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customStudyText = stringResource(id = R.string.custom_study)
    val studyMoreText = stringResource(id = R.string.study_more, customStudyText)

    val range =
        remember(studyMoreText, customStudyText) {
            findSubstringRange(studyMoreText, customStudyText)
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val annotatedString =
        remember(studyMoreText, range, primaryColor) {
            buildAnnotatedString {
                append(studyMoreText)
                if (range != null) {
                    addLink(
                        LinkAnnotation.Clickable(tag = "custom_study") { onCustomStudy() },
                        range.first,
                        range.last + 1,
                    )
                    addStyle(
                        style =
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold,
                            ),
                        range.first,
                        range.last + 1,
                    )
                }
            }
        }

    Text(
        text = annotatedString,
        modifier = modifier,
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
    )
}

@Preview
@Composable
fun CongratsScreenPreview() {
    CongratsScreen(
        onNavigateUp = {},
        onDeckOptions = {},
        timeUntilNextDay = 1000 * 60 * 60 * 4,
        onCustomStudy = { },
    )
}

private fun findSubstringRange(
    mainString: String,
    subString: String,
): IntRange? {
    val index = mainString.indexOf(subString, ignoreCase = true)
    return if (index != -1) {
        index until (index + subString.length)
    } else {
        null
    }
}
