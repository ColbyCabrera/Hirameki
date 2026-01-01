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
package com.ichi2.anki.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.RobotoMono
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CongratsScreen(onNavigateUp: () -> Unit, onDeckOptions: () -> Unit, onBack: () -> Unit, timeUntilNextDay: Long) {
    AnkiDroidTheme {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.displayMediumEmphasized
                )
            },
                subtitle = {},
                titleHorizontalAlignment = Alignment.CenterHorizontally,
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }, actions = {
                    FilledIconButton(
                        onClick = onDeckOptions,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.deck_options)
                        )
                    }
                })
        }, content = { contentPadding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
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
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.study_more),
                    style = MaterialTheme.typography.bodyLarge
                )

                Column(
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    var remainingTime by remember(timeUntilNextDay) {
                        mutableLongStateOf(
                            timeUntilNextDay.coerceAtLeast(0L)
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds
                            ),
                            fontFamily = RobotoMono,
                            fontSize = 70.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 70.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            softWrap = false
                        )
                    }
                }
            }
        })
    }
}

@Preview
@Composable
fun CongratsScreenPreview() {
    CongratsScreen(
        onNavigateUp = {},
        onDeckOptions = {},
        onBack = {},
        timeUntilNextDay = 1000 * 60 * 60 * 4
    )
}
